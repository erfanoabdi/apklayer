#include "daemon.h"
#include "broker.h"
#include "flatpak.h"
#include "apps.h"
#include "sandbox.h"

#include <cerrno>
#include <csignal>
#include <cstring>
#include <fcntl.h>
#include <map>
#include <poll.h>
#include <sys/file.h>
#include <sys/prctl.h>
#include <sys/signalfd.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

extern char** environ;

namespace apklayer {

std::string daemonSocket(const Layout& l) { return join(l.runtimeHome, "apklayer/apklayerd.sock"); }

namespace {
// One SOCK_SEQPACKET request, client to daemon: the argument count, the arguments, the environment's size and entries, the working directory (NUL
// separated), with the client's fds 0, 1, 2. Later "signal <n>" (a signal the client got). Daemon to client: "exit <status>".
const size_t kMaxMessage = 1 << 20;

std::string pack(const std::vector<std::string>& fields) {
    std::string out;
    for (const std::string& f : fields) { out += f; out.push_back('\0'); }
    return out;
}

bool sendMessage(int fd, const std::string& text, const int* fds = nullptr, int count = 0) {
    iovec iov{const_cast<char*>(text.data()), text.size()};
    msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    char control[CMSG_SPACE(sizeof(int) * 3)]{};
    if (count > 0) {
        msg.msg_control = control;
        msg.msg_controllen = CMSG_SPACE(sizeof(int) * count);
        cmsghdr* c = CMSG_FIRSTHDR(&msg);
        c->cmsg_level = SOL_SOCKET;
        c->cmsg_type = SCM_RIGHTS;
        c->cmsg_len = CMSG_LEN(sizeof(int) * count);
        memcpy(CMSG_DATA(c), fds, sizeof(int) * count);
    }
    return sendmsg(fd, &msg, MSG_NOSIGNAL) == (ssize_t)text.size();
}

std::string receive(int fd, std::vector<int>* fds = nullptr) {
    std::string buffer(kMaxMessage, '\0');
    iovec iov{buffer.data(), buffer.size()};
    msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    char control[CMSG_SPACE(sizeof(int) * 3)]{};
    msg.msg_control = control;
    msg.msg_controllen = sizeof control;
    ssize_t n = recvmsg(fd, &msg, MSG_CMSG_CLOEXEC);
    if (n <= 0) return "";
    for (cmsghdr* c = CMSG_FIRSTHDR(&msg); c; c = CMSG_NXTHDR(&msg, c))
        if (c->cmsg_level == SOL_SOCKET && c->cmsg_type == SCM_RIGHTS) {
            int count = (c->cmsg_len - CMSG_LEN(0)) / sizeof(int);
            for (int i = 0; i < count; i++) {
                int got;
                memcpy(&got, CMSG_DATA(c) + i * sizeof(int), sizeof got);
                if (fds) fds->push_back(got); else close(got);
            }
        }
    buffer.resize(n);
    return buffer;
}

int connectTo(const std::string& path) {
    int fd = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", path.c_str());
    if (connect(fd, (sockaddr*)&addr, sizeof addr) == 0) return fd;
    close(fd);
    return -1;
}

// The instance a sandboxed process belongs to: the one whose stack made its user namespace (the kernel's word, not anything the process says)
std::string instanceOf(const Layout& l, int pid) {
    struct stat own{};
    if (::stat(("/proc/" + std::to_string(pid) + "/ns/user").c_str(), &own) != 0) return "";
    std::string states = join(l.stateHome, "apklayer");
    for (const std::string& id : listDir(states)) {
        auto stack = readFile(join(join(states, id), "stack.pid"));
        struct stat ns{};
        if (stack && ::stat(("/proc/" + std::to_string(atoi(stack->c_str())) + "/ns/user").c_str(), &ns) == 0 && ns.st_ino == own.st_ino) return id;
    }
    return "";
}

// What an app may ask of the user's side: start an installed app, its instance's hub, a picker or a link opener. The rest is the user's.
bool appMayRun(const Layout& l, const std::vector<std::string>& a) {
    if (a.size() == 2 && a[0] == "run") return a[1].find('/') == std::string::npos && exists(join(appDir(l, a[1]), "info.properties"));
    if (a.size() == 1 && a[0] == "hub") return true;
    if (a.size() == 2 && a[0] == "wait" && a[1] == "hub") return true;
    return a.size() >= 2 && a[0] == "portal";
}

std::string selfExe() {
    char path[4096];
    ssize_t n = readlink("/proc/self/exe", path, sizeof path - 1);
    return n > 0 ? std::string(path, n) : "apklayer";
}

// Starts the request's command as a child (the client's fds, environment and directory; a sandboxed app's command with the daemon's own
// environment): its pid, 0 when the request is refused, -1 for a broker request (answered and closed).
int startRequest(const Layout& l, int conn, const std::string& exe, const sigset_t& oldMask) {
    std::vector<int> fds;
    std::string message = receive(conn, &fds);
    std::vector<std::string> f;
    for (size_t at = 0; at < message.size();) { size_t end = message.find('\0', at); if (end == std::string::npos) break; f.push_back(message.substr(at, end - at)); at = end + 1; }
    ucred peer{};
    socklen_t len = sizeof peer;
    if (brokerRequest(f)) {  // the file broker (cli/broker.cpp): answered here, the connection closed
        for (int fd : fds) close(fd);
        if (getsockopt(conn, SOL_SOCKET, SO_PEERCRED, &peer, &len) == 0) brokerAnswer(l, conn, f, peer, instanceOf(l, peer.pid));
        close(conn);
        return -1;
    }
    size_t argc = f.empty() ? 0 : strtoul(f[0].c_str(), nullptr, 10);
    if (fds.size() != 3 || getsockopt(conn, SOL_SOCKET, SO_PEERCRED, &peer, &len) != 0 || f.size() < argc + 2) { for (int fd : fds) close(fd); return 0; }
    std::vector<std::string> args(f.begin() + 1, f.begin() + 1 + argc);
    size_t envc = strtoul(f[argc + 1].c_str(), nullptr, 10);
    if (f.size() < argc + 3 + envc) { for (int fd : fds) close(fd); return 0; }
    std::vector<std::string> env(f.begin() + argc + 2, f.begin() + argc + 2 + envc);
    std::string cwd = f[argc + 2 + envc];
    if (peer.uid != getuid()) {  // an app in a sandbox: its instance from its namespace, its command checked, nothing of its environment
        std::string id = instanceOf(l, peer.pid);
        if (args.size() >= 2 && args[0] == "--instance") args.erase(args.begin(), args.begin() + 2);
        if (id.empty() || !appMayRun(l, args)) {
            dprintf(fds[2], "apklayer: apklayerd refuses this for uid %u\n", peer.uid);
            for (int fd : fds) close(fd);
            return 0;
        }
        args.insert(args.begin(), {"--instance", id});
        env.clear();
        for (char** e = environ; *e; e++) env.push_back(*e);
        env.push_back("APKLAYER_SANDBOX=1");
        env.push_back("APKLAYER_PEER_PID=" + std::to_string(peer.pid));
        cwd = "/";
    }
    pid_t child = fork();
    if (child == 0) {
        sigprocmask(SIG_SETMASK, &oldMask, nullptr);
        setpgid(0, 0);  // the command's processes are a group, as they were the client's: its signals reach all of them
        for (int i = 0; i < 3; i++) dup2(fds[i], i);
        if (chdir(cwd.c_str()) != 0 && chdir("/") != 0) _exit(1);
        std::vector<std::string> argv = {exe, "--no-daemon"};
        argv.insert(argv.end(), args.begin(), args.end());
        std::vector<char*> av, ev;
        for (std::string& a : argv) av.push_back(a.data());
        av.push_back(nullptr);
        for (std::string& e : env) ev.push_back(e.data());
        ev.push_back(nullptr);
        execve(av[0], av.data(), ev.data());
        _exit(127);
    }
    if (child > 0) setpgid(child, child);  // also here: a signal forwarded at once finds the group
    for (int fd : fds) close(fd);
    return child;
}
}  // namespace

int runDaemon(const Layout& l, bool onDemand) {
    std::string path = daemonSocket(l);
    makeDirs(dirName(path));
    int lock = open((path + ".lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0600);
    if (lock < 0 || flock(lock, LOCK_EX | LOCK_NB) != 0) { fprintf(stderr, "apklayer: apklayerd already runs (%s)\n", path.c_str()); return 0; }
    prctl(PR_SET_CHILD_SUBREAPER, 1);  // what its commands leave running (stacks, hubs, app processes) stays its own
    unlink(path.c_str());
    int listener = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", path.c_str());
    if (bind(listener, (sockaddr*)&addr, sizeof addr) != 0 || listen(listener, 64) != 0) die("apklayerd cannot listen on " + path + ": " + strerror(errno));
    chmod(path.c_str(), 0666);  // sandboxed apps connect too: the runtime directory (0700) is the gate, SO_PEERCRED decides per request
    std::string exe = selfExe();
    sigset_t sigs, oldMask;
    sigemptyset(&sigs);
    for (int s : {SIGCHLD, SIGTERM, SIGINT}) sigaddset(&sigs, s);
    sigprocmask(SIG_BLOCK, &sigs, &oldMask);
    int sfd = signalfd(-1, &sigs, SFD_CLOEXEC);
    if (const char* ready = getenv("APKLAYER_DAEMON_READY_FD")) { int fd = atoi(ready); (void)!write(fd, "r", 1); close(fd); unsetenv("APKLAYER_DAEMON_READY_FD"); }
    std::map<int, int> connOf;  // child pid -> its client's connection
    for (bool run = true; run;) {
        std::vector<pollfd> fds = {{listener, POLLIN, 0}, {sfd, POLLIN, 0}};
        for (const auto& [child, conn] : connOf) fds.push_back({conn, POLLIN, 0});
        if (poll(fds.data(), fds.size(), -1) < 0) continue;
        if (fds[0].revents & POLLIN) {
            int conn = accept4(listener, nullptr, nullptr, SOCK_CLOEXEC);
            int child = conn >= 0 ? startRequest(l, conn, exe, oldMask) : 0;
            if (child > 0) connOf[child] = conn;
            else if (conn >= 0 && child == 0) { sendMessage(conn, "exit 126"); close(conn); }
        }
        for (size_t i = 2; i < fds.size(); i++) {
            if (!(fds[i].revents & (POLLIN | POLLHUP))) continue;
            for (auto& [child, conn] : connOf) {
                if (conn != fds[i].fd) continue;
                std::string m = receive(conn);
                if (m.rfind("signal ", 0) == 0) kill(-child, atoi(m.c_str() + 7));
                else if (m.empty()) { kill(-child, SIGKILL); close(conn); conn = -1; }  // the client is gone (killed): so is what it ran
            }
        }
        if (fds[1].revents & POLLIN) {
            signalfd_siginfo info{};
            (void)!read(sfd, &info, sizeof info);
            if (info.ssi_signo != SIGCHLD) break;
            int st = 0, pid;
            while ((pid = waitpid(-1, &st, WNOHANG)) > 0) {
                auto it = connOf.find(pid);
                if (it == connOf.end()) continue;  // an orphan of a command (a stack, a hub): reaped
                if (it->second >= 0) { sendMessage(it->second, "exit " + std::to_string(WIFEXITED(st) ? WEXITSTATUS(st) : 128 + WTERMSIG(st))); close(it->second); }
                connOf.erase(it);
            }
            if (onDemand && pid < 0 && errno == ECHILD) run = false;  // nothing of it runs any more
        }
    }
    unlink(path.c_str());
    return 0;
}

bool forwardToDaemon(const Layout& l, const std::vector<std::string>& args, int* status) {
    std::string path = daemonSocket(l);
    int fd = connectTo(path);
    // the sandbox's apps start through it, and in the Flatpak everything (each `flatpak run` has its own pid namespace, the runtime directory is
    // shared: the daemon's namespace holds the stacks): started on demand, one at a time
    if (fd < 0 && (sandboxOn() || inFlatpak())) {
        makeDirs(dirName(path));
        int lock = open((path + ".start").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0600);
        flock(lock, LOCK_EX);
        if ((fd = connectTo(path)) < 0) {
            int ready[2];
            if (pipe2(ready, O_CLOEXEC) != 0) die("pipe failed");
            fcntl(ready[1], F_SETFD, 0);
            Spawn d;
            d.argv = {selfExe(), "daemon", "--on-demand"};
            d.env = currentEnv();
            d.env["APKLAYER_DAEMON_READY_FD"] = std::to_string(ready[1]);
            d.logFile = join(dirName(path), "apklayerd.log");
            d.detach = true;
            spawn(d);
            close(ready[1]);
            char byte;
            if (read(ready[0], &byte, 1) == 1) fd = connectTo(path);
            close(ready[0]);
        }
        close(lock);
        if (fd < 0) die("apklayerd did not start (" + join(dirName(path), "apklayerd.log") + ")");
    }
    if (fd < 0) return false;
    std::vector<std::string> fields = {std::to_string(args.size())};
    fields.insert(fields.end(), args.begin(), args.end());
    std::vector<std::string> env;
    for (char** e = environ; *e; e++) env.push_back(*e);
    fields.push_back(std::to_string(env.size()));
    fields.insert(fields.end(), env.begin(), env.end());
    char cwd[4096];
    fields.push_back(getcwd(cwd, sizeof cwd) ? cwd : "/");
    int own[3] = {0, 1, 2};
    if (!sendMessage(fd, pack(fields), own, 3)) die("cannot reach apklayerd");
    sigset_t sigs;
    sigemptyset(&sigs);
    for (int s : {SIGINT, SIGTERM, SIGHUP, SIGQUIT, SIGUSR1, SIGUSR2}) sigaddset(&sigs, s);
    sigprocmask(SIG_BLOCK, &sigs, nullptr);
    int sfd = signalfd(-1, &sigs, SFD_CLOEXEC);
    for (;;) {
        pollfd fds[2] = {{fd, POLLIN, 0}, {sfd, POLLIN, 0}};
        if (poll(fds, 2, -1) < 0) continue;
        if (fds[1].revents & POLLIN) {  // the command gets the signals its client gets
            signalfd_siginfo info{};
            if (read(sfd, &info, sizeof info) == sizeof info) sendMessage(fd, "signal " + std::to_string(info.ssi_signo));
        }
        if (fds[0].revents & (POLLIN | POLLHUP)) {
            std::string m = receive(fd);
            *status = m.rfind("exit ", 0) == 0 ? atoi(m.c_str() + 5) : 1;
            return true;
        }
    }
}

}  // namespace apklayer
