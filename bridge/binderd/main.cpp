// apklayer-binderd: an instance's userspace binder (docs/BINDER.md). The CLI binds the instance's socket and hands it over
// (--listen-fd, cli/binder.cpp), or the daemon binds it itself (standalone, for tests). One epoll loop, one thread: connections are
// procs (an opened binder fd) and threads (a thread's ioctls); a closed proc socket is that process's death. The daemon ends a few seconds
// after its last connection closed, under the lock the CLI takes to start one (no client can slip in between).
#include <fcntl.h>
#include <sys/epoll.h>
#include <sys/file.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <string>

#include "driver.h"
#include "protocol.h"

namespace {

using namespace binderd;

struct Conn {
    enum Kind { NEW, PROC, THREAD } kind = NEW;
    Proc* proc = nullptr;
    Thread* thread = nullptr;
};

int epfd = -1;
std::map<int, Conn> conns;
Driver driver;
bool debug = false;

void closeSock(int fd) {
    epoll_ctl(epfd, EPOLL_CTL_DEL, fd, nullptr);
    close(fd);
    conns.erase(fd);
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
    std::vector<uint8_t> payload(r.data);
    payload.insert(payload.end(), (const uint8_t*)r.fixes.data(), (const uint8_t*)(r.fixes.data() + r.fixes.size()));
    h.len = sizeof h + payload.size();
    bd_send(sock, &h, sizeof h, payload.data(), payload.size(), r.fds.data(), r.fds.size());  // a failed send: the peer is gone, its EOF follows
    for (int fd : r.fds) close(fd);
    r.fds.clear();
}

void sendReply(Thread* t, Reply& r) { send(t->sock, t->proc, r); }

bool peer(int fd, pid_t* pid, uid_t* uid) {
    ucred c{};
    socklen_t n = sizeof c;
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &c, &n) != 0) return false;
    *pid = c.pid;
    *uid = c.uid;
    return true;
}

// one request from a connection; false: drop the connection
bool handle(int fd, Conn& c) {
    bd_req h{};
    int fds[BD_MAX_FDS];
    uint32_t got = 0;
    if (bd_recv_hdr(fd, &h, sizeof h, fds, &got) != 0 || h.len < sizeof h || h.len - sizeof h > (64u << 20) || h.nfds > (1u << 16)) {
        for (uint32_t i = 0; i < got; i++) close(fds[i]);
        return false;
    }
    std::vector<uint8_t> payload(h.len - sizeof h);
    std::vector<int> all(fds, fds + got);
    all.resize(h.nfds > got ? h.nfds : got, -1);
    bool ok = bd_recv_all(fd, payload.data(), payload.size()) == 0 && bd_recv_more_fds(fd, all.data(), got, h.nfds) == 0;
    if (!ok || all.size() != h.nfds) {
        for (int x : all) if (x >= 0) close(x);
        return false;
    }
    Reply r;
    if (c.kind == Conn::NEW) {
        pid_t pid;
        uid_t uid;
        if (!peer(fd, &pid, &uid)) return false;
        if (h.op == BD_HELLO_PROC && h.a0 == BD_VERSION) {
            std::string name(payload.begin(), payload.end());
            if (name != "binder" && name != "hwbinder" && name != "vndbinder") return false;
            c.kind = Conn::PROC;
            c.proc = driver.newProc(fd, name, pid, uid);
            r.write_consumed = c.proc->cookie;
            if (debug) fprintf(stderr, "binderd: proc %d (%s) uid %u\n", pid, name.c_str(), uid);
            send(fd, nullptr, r);
            return true;
        }
        if (h.op == BD_HELLO_THREAD) {
            Proc* p = driver.procByCookie(h.a0);
            if (!p || p->pid != pid) return false;  // a thread of that very process (SO_PEERCRED), not someone holding the cookie
            c.kind = Conn::THREAD;
            c.thread = driver.newThread(p, fd, int(h.a1));
            send(fd, p, r);
            return true;
        }
        return false;
    }
    if (c.kind != Conn::THREAD) return false;  // a proc socket says nothing after its hello
    Thread* t = c.thread;
    switch (h.op) {
        case BD_IOCTL:
            if (h.a0 == BINDER_THREAD_EXIT) {
                send(fd, t->proc, r);
                driver.releaseThread(t);  // closes fd
                return true;
            }
            if (driver.ioctl(t, uint32_t(h.a0), payload, h.a1, h.a2, all, r)) sendReply(t, r);
            break;
        case BD_MMAP: {
            int memfd = -1;
            r.ret = driver.mmap(t, h.a0, &memfd);
            if (memfd >= 0) r.fds.push_back(memfd);
            sendReply(t, r);
        } break;
        case BD_MAPPED:
            r.ret = driver.mapped(t, h.a0);
            sendReply(t, r);
            break;
        default:
            return false;
    }
    for (int x : all) if (x >= 0) close(x);
    return true;
}

void hangup(int fd) {
    auto it = conns.find(fd);
    if (it == conns.end()) return;
    Conn c = it->second;
    if (c.kind == Conn::PROC) driver.releaseProc(c.proc);  // closes its threads' sockets and its own
    else if (c.kind == Conn::THREAD) driver.releaseThread(c.thread);
    else closeSock(fd);
}

int bindSocket(const std::string& path) {
    int s = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    sockaddr_un a{};
    a.sun_family = AF_UNIX;
    if (s < 0 || path.size() >= sizeof a.sun_path) return -1;
    memcpy(a.sun_path, path.c_str(), path.size());
    unlink(path.c_str());
    if (bind(s, (sockaddr*)&a, sizeof a) != 0 || listen(s, 64) != 0) { close(s); return -1; }
    return s;
}

void accepted(int listen_fd) {
    for (;;) {
        int fd = accept4(listen_fd, nullptr, nullptr, SOCK_CLOEXEC | SOCK_NONBLOCK);
        if (fd < 0) return;
        // blocking after all (a message's body is read whole, the client writes it at once), with a bound: a stopped client cannot hold the daemon
        fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) & ~O_NONBLOCK);
        timeval tv{5, 0};
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof tv);
        setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof tv);
        epoll_event ev{};
        ev.events = EPOLLIN | EPOLLRDHUP;
        ev.data.fd = fd;
        epoll_ctl(epfd, EPOLL_CTL_ADD, fd, &ev);
        conns[fd] = Conn{};
    }
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
    driver.sendReply = sendReply;
    driver.closeSocket = closeSock;
    epfd = epoll_create1(EPOLL_CLOEXEC);
    epoll_event ev{};
    ev.events = EPOLLIN;
    ev.data.fd = listen_fd;
    epoll_ctl(epfd, EPOLL_CTL_ADD, listen_fd, &ev);
    const int idle_ms = 3000;
    for (;;) {
        epoll_event evs[64];
        int n = epoll_wait(epfd, evs, 64, conns.empty() ? idle_ms : -1);
        if (n < 0 && errno == EINTR) continue;
        if (n == 0 && conns.empty()) {
            // idle: under the CLI's lock, with nothing waiting to be accepted, the socket goes and so does the daemon
            if (lock_fd >= 0) flock(lock_fd, LOCK_EX);
            accepted(listen_fd);
            if (conns.empty()) {
                unlink(path.c_str());
                return 0;
            }
            if (lock_fd >= 0) flock(lock_fd, LOCK_UN);
            continue;
        }
        for (int i = 0; i < n; i++) {
            int fd = evs[i].data.fd;
            if (fd == listen_fd) { accepted(listen_fd); continue; }
            auto it = conns.find(fd);
            if (it == conns.end()) continue;  // closed by an earlier event of this round (a proc's death closes its threads)
            if ((evs[i].events & EPOLLIN) && (it->second.kind != Conn::PROC)) {
                if (!handle(fd, it->second)) hangup(fd);
            } else {
                hangup(fd);  // EOF or error; a proc socket readable after its hello is its end too
            }
            driver.flush();
        }
    }
}
