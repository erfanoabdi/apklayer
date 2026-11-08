// apklayer-binderd: an instance's userspace binder (docs/BINDER.md). The CLI binds the instance's socket and hands it over
// (--listen-fd, cli/binder.cpp), or the daemon binds it itself (standalone, for tests). The main thread accepts connections and reads each
// one's hello; then the connection belongs to its context's loop (binder, hwbinder, vndbinder: a Driver and a thread each, no state shared).
// Connections are procs (an opened binder fd: its close is that process's death) and threads (a thread's ioctls). The daemon ends a few
// seconds after its last connection closed, under the lock the CLI takes to start one (no client can slip in between).
#include <fcntl.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/file.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

#include <atomic>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <thread>

#include "driver.h"
#include "protocol.h"

namespace {

using namespace binderd;

const char* const kContexts[] = {"binder", "hwbinder", "vndbinder"};
constexpr int kNumContexts = 3;
std::atomic<int> live{0};  // connections open (main thread's and loops')
bool debug = false;

struct Message {
    int fd;
    bd_req h;
    std::vector<uint8_t> payload;
    std::vector<int> fds;
};

// one message from fd (and the rest of its fds); false: drop the connection
bool readMessage(int fd, Message& m, std::vector<uint8_t>& buf) {
    int first[BD_MAX_FDS];
    uint32_t got = 0;
    int r = bd_recv(fd, buf.data(), buf.size(), first, &got);
    m.fds.assign(first, first + got);
    if (r < int(sizeof m.h)) return false;
    memcpy(&m.h, buf.data(), sizeof m.h);
    if (m.h.len != uint32_t(r) || m.h.nfds > (1u << 16) || got > m.h.nfds) return false;
    m.payload.assign(buf.data() + sizeof m.h, buf.data() + r);
    m.fds.resize(m.h.nfds, -1);
    return bd_recv_more_fds(fd, m.fds.data(), got, m.h.nfds) == 0;
}

void closeAll(std::vector<int>& fds) {
    for (int x : fds) if (x >= 0) close(x);
    fds.clear();
}

bool peer(int fd, pid_t* pid, uid_t* uid) {
    ucred c{};
    socklen_t n = sizeof c;
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &c, &n) != 0) return false;
    *pid = c.pid;
    *uid = c.uid;
    return true;
}

void send(int sock, Proc* p, Reply& r) {
    bd_rep h{};
    h.ret = r.ret;
    h.nfds = r.fds.size();
    h.ndrain = p ? p->wakes : 0;
    if (p) p->wakes = 0;
    h.write_consumed = r.write_consumed;
    h.read_consumed = r.data.size();
    h.nfix = r.fixes.size();
    r.data.insert(r.data.end(), (const uint8_t*)r.fixes.data(), (const uint8_t*)(r.fixes.data() + r.fixes.size()));
    h.len = sizeof h + r.data.size();
    bd_send(sock, &h, sizeof h, r.data.data(), r.data.size(), r.fds.data(), r.fds.size());  // a failed send: the peer is gone, its EOF follows
    for (int fd : r.fds) close(fd);
    r.fds.clear();
}

// a context: its driver, its connections, its thread
struct Loop {
    struct Conn {
        Proc* proc = nullptr;
        Thread* thread = nullptr;
    };
    Driver driver;
    int epfd = -1, wake = -1;
    std::map<int, Conn> conns;
    std::mutex lock;
    std::vector<Message> inbox;  // hellos handed over by the main thread
    std::vector<uint8_t> buf = std::vector<uint8_t>(BD_MAX_MSG);

    // a reply to one of this context's procs: when it tells the route to read wake bytes away, whether a polling thread still has work is
    // looked at again (Driver::flush -> wakePoll), or the poller would sleep with work queued
    void answer(int fd, Proc* p, Reply& r) {
        bool drains = p && p->wakes;
        send(fd, p, r);
        if (drains) driver.dirty.insert(p);
    }

    void closeSock(int fd) {
        epoll_ctl(epfd, EPOLL_CTL_DEL, fd, nullptr);
        close(fd);
        if (conns.erase(fd)) live--;
    }

    void hello(Message& m) {
        pid_t pid;
        uid_t uid;
        Reply r;
        int fd = m.fd;
        conns[fd] = Conn{};  // counted in live since the accept
        epoll_event ev{};
        ev.events = EPOLLIN | EPOLLRDHUP;
        ev.data.fd = fd;
        epoll_ctl(epfd, EPOLL_CTL_ADD, fd, &ev);
        if (!peer(fd, &pid, &uid)) return closeAll(m.fds), closeSock(fd);
        if (m.h.op == BD_HELLO_PROC) {
            Proc* p = driver.newProc(fd, pid, uid);
            conns[fd].proc = p;
            r.write_consumed = p->cookie;
            if (debug) fprintf(stderr, "binderd: proc %d (%s) uid %u\n", pid, driver.context.name.c_str(), uid);
            closeAll(m.fds);
            return answer(fd, nullptr, r);
        }
        // a thread: of that very process (SO_PEERCRED), with its shared buffer, sealed against shrinking (the daemon reads it)
        Proc* p = driver.procByCookie(m.h.a0);
        struct stat st{};
        if (!p || p->pid != pid || m.fds.size() != 1 || fstat(m.fds[0], &st) != 0 || st.st_size < off_t(BD_TX_SIZE) ||
            !(fcntl(m.fds[0], F_GET_SEALS) & F_SEAL_SHRINK))
            return closeAll(m.fds), closeSock(fd);
        void* tx = mmap(nullptr, BD_TX_SIZE, PROT_READ, MAP_SHARED, m.fds[0], 0);
        closeAll(m.fds);
        if (tx == MAP_FAILED) return closeSock(fd);
        Thread* t = driver.newThread(p, fd, int(m.h.a1));
        t->tx = static_cast<const uint8_t*>(tx);
        conns[fd].thread = t;
        answer(fd, p, r);
    }

    // a request on a thread socket; false: drop it
    bool request(int fd, Thread* t) {
        Message m;
        m.fd = fd;
        if (!readMessage(fd, m, buf)) return closeAll(m.fds), false;
        Reply r;
        switch (m.h.op) {
            case BD_IOCTL:
                if (m.h.a0 == BINDER_THREAD_EXIT) {
                    answer(fd, t->proc, r);
                    driver.releaseThread(t);  // closes fd
                    return closeAll(m.fds), true;
                }
                if (driver.ioctl(t, uint32_t(m.h.a0), m.payload, m.h.a1, m.h.a2, m.h.a3, m.fds, r)) {
                    driver.flush();  // the threads this request woke first (a reply's waiting caller), then the requester's own answer
                    answer(fd, t->proc, r);
                }
                break;
            case BD_MMAP: {
                int memfd = -1;
                r.ret = driver.mmap(t, m.h.a0, &memfd);
                if (memfd >= 0) r.fds.push_back(memfd);
                answer(fd, t->proc, r);
            } break;
            case BD_MAPPED:
                r.ret = driver.mapped(t, m.h.a0);
                answer(fd, t->proc, r);
                break;
            default:
                closeAll(m.fds);
                return false;
        }
        closeAll(m.fds);
        return true;
    }

    void hangup(int fd) {
        auto it = conns.find(fd);
        if (it == conns.end()) return;
        Conn c = it->second;
        if (c.proc) driver.releaseProc(c.proc);  // closes its threads' sockets and its own
        else if (c.thread) driver.releaseThread(c.thread);
        else closeSock(fd);
    }

    void run() {
        for (;;) {
            epoll_event evs[64];
            int n = epoll_wait(epfd, evs, 64, -1);
            for (int i = 0; i < n; i++) {
                int fd = evs[i].data.fd;
                if (fd == wake) {
                    uint64_t v;
                    (void)!read(wake, &v, sizeof v);
                    std::vector<Message> in;
                    {
                        std::lock_guard<std::mutex> g(lock);
                        in.swap(inbox);
                    }
                    for (Message& m : in) hello(m);
                } else {
                    auto it = conns.find(fd);
                    if (it == conns.end()) continue;  // closed by an earlier event of this round (a proc's death closes its threads)
                    Thread* t = it->second.thread;
                    if (!(evs[i].events & EPOLLIN) || !t || !request(fd, t)) hangup(fd);  // a proc socket says nothing after its hello: its end
                }
                driver.flush();
            }
        }
    }

    void start(int index) {
        driver.index = uint8_t(index);
        driver.debug = debug;
        driver.context.name = kContexts[index];
        driver.sendReply = [](Thread* t, Reply& r) { send(t->sock, t->proc, r); };  // from flush, which marks the proc itself
        driver.closeSocket = [this](int fd) { closeSock(fd); };
        epfd = epoll_create1(EPOLL_CLOEXEC);
        wake = eventfd(0, EFD_CLOEXEC);
        epoll_event ev{};
        ev.events = EPOLLIN;
        ev.data.fd = wake;
        epoll_ctl(epfd, EPOLL_CTL_ADD, wake, &ev);
        std::thread([this] { run(); }).detach();
    }

    void handOver(Message&& m) {
        {
            std::lock_guard<std::mutex> g(lock);
            inbox.push_back(std::move(m));
        }
        uint64_t one = 1;
        (void)!write(wake, &one, sizeof one);
    }
};

Loop loops[kNumContexts];

int bindSocket(const std::string& path) {
    int s = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    sockaddr_un a{};
    a.sun_family = AF_UNIX;
    if (s < 0 || path.size() >= sizeof a.sun_path) return -1;
    memcpy(a.sun_path, path.c_str(), path.size());
    unlink(path.c_str());
    if (bind(s, (sockaddr*)&a, sizeof a) != 0 || listen(s, 64) != 0) { close(s); return -1; }
    return s;
}

int accepted(int epfd, int listen_fd) {
    int n = 0;
    for (;; n++) {
        int fd = accept4(listen_fd, nullptr, nullptr, SOCK_CLOEXEC);
        if (fd < 0) return n;
        timeval tv{5, 0};  // a stopped client cannot hold a loop: a send that blocks this long ends that connection
        setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof tv);
        live++;
        epoll_event ev{};
        ev.events = EPOLLIN | EPOLLRDHUP;
        ev.data.fd = fd;
        epoll_ctl(epfd, EPOLL_CTL_ADD, fd, &ev);
    }
}

// a new connection's hello: to its context's loop
void dispatch(int epfd, int fd, std::vector<uint8_t>& buf) {
    epoll_ctl(epfd, EPOLL_CTL_DEL, fd, nullptr);
    Message m;
    m.fd = fd;
    int k = -1;
    if (readMessage(fd, m, buf)) {
        if (m.h.op == BD_HELLO_PROC && m.h.a0 == BD_VERSION) {
            std::string name(m.payload.begin(), m.payload.end());
            for (int i = 0; i < kNumContexts; i++)
                if (name == kContexts[i]) k = i;
        } else if (m.h.op == BD_HELLO_THREAD && (m.h.a0 >> 56) < uint64_t(kNumContexts)) {
            k = int(m.h.a0 >> 56);
        }
    }
    if (k < 0) {
        closeAll(m.fds);
        close(fd);
        live--;
        return;
    }
    loops[k].handOver(std::move(m));
}

}  // namespace

int main(int argc, char** argv) {
    std::string path, lock;
    int listen_fd = -1;
    for (int i = 1; i + 1 < argc; i += 2) {
        if (!strcmp(argv[i], "--socket")) path = argv[i + 1];
        else if (!strcmp(argv[i], "--listen-fd")) listen_fd = atoi(argv[i + 1]);
        else if (!strcmp(argv[i], "--lock")) lock = argv[i + 1];
    }
    if (path.empty()) {
        fprintf(stderr, "usage: apklayer-binderd --socket PATH [--listen-fd N] [--lock PATH]\n");
        return 2;
    }
    debug = getenv("APKLAYER_BINDERD_DEBUG") != nullptr;
    if (listen_fd < 0) listen_fd = bindSocket(path);
    if (listen_fd < 0) { perror("apklayer-binderd: socket"); return 1; }
    fcntl(listen_fd, F_SETFD, FD_CLOEXEC);
    fcntl(listen_fd, F_SETFL, fcntl(listen_fd, F_GETFL) | O_NONBLOCK);
    int lock_fd = lock.empty() ? -1 : open(lock.c_str(), O_RDWR | O_CREAT | O_CLOEXEC, 0600);
    for (int i = 0; i < kNumContexts; i++) loops[i].start(i);
    int epfd = epoll_create1(EPOLL_CLOEXEC);
    epoll_event ev{};
    ev.events = EPOLLIN;
    ev.data.fd = listen_fd;
    epoll_ctl(epfd, EPOLL_CTL_ADD, listen_fd, &ev);
    std::vector<uint8_t> buf(BD_MAX_MSG);
    const int idle_ms = 3000;
    for (;;) {
        epoll_event evs[64];
        // the loops close connections without telling: while any is open, look again now and then
        int n = epoll_wait(epfd, evs, 64, live ? 1000 : idle_ms);
        if (n < 0 && errno == EINTR) continue;
        if (n == 0 && live == 0) {
            // idle: under the CLI's lock, with nothing waiting to be accepted, the socket goes and so does the daemon
            if (lock_fd >= 0) flock(lock_fd, LOCK_EX);
            if (accepted(epfd, listen_fd) == 0 && live == 0) {
                unlink(path.c_str());
                _exit(0);
            }
            if (lock_fd >= 0) flock(lock_fd, LOCK_UN);
            continue;
        }
        for (int i = 0; i < n; i++) {
            if (evs[i].data.fd == listen_fd) accepted(epfd, listen_fd);
            else dispatch(epfd, evs[i].data.fd, buf);
        }
    }
}
