#include "spare.h"
#include "env.h"
#include "run.h"
#include "sandbox.h"
#include "stack.h"

#include <cerrno>
#include <csignal>
#include <cstring>
#include <fcntl.h>
#include <poll.h>
#include <set>
#include <sys/file.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

namespace apklayer {

namespace {
// One SOCK_SEQPACKET message each way. Launch -> holder: key, NUL, target lines, with fds 1, 2 and its started pipe; or "now" (wakeAppSpare: no
// answer). Holder -> launch: "pid <n>" (taken) or "stale <why>"
// (another key: the holder ends, the launcher starts a spare of its own key), later "exit <status>". Holder -> spare JVM: target lines, with the fds.
std::string socketPath(const Instance& inst) { return join(inst.stateDir, "app-spare.sock"); }

bool sendWithFds(int fd, const std::string& text, const int* fds, int count) {
    iovec iov{const_cast<char*>(text.data()), text.size()};
    msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    alignas(cmsghdr) char control[CMSG_SPACE(3 * sizeof(int))];
    if (count > 0) {
        msg.msg_control = control;
        msg.msg_controllen = CMSG_SPACE(count * sizeof(int));
        cmsghdr* c = CMSG_FIRSTHDR(&msg);
        c->cmsg_level = SOL_SOCKET;
        c->cmsg_type = SCM_RIGHTS;
        c->cmsg_len = CMSG_LEN(count * sizeof(int));
        memcpy(CMSG_DATA(c), fds, count * sizeof(int));
    }
    ssize_t n;
    while ((n = sendmsg(fd, &msg, MSG_NOSIGNAL)) < 0 && errno == EINTR) {}
    return n == static_cast<ssize_t>(text.size());
}

// A message (empty on EOF or error) and, into fds (count), descriptors that came with it.
std::string receive(int fd, int* fds = nullptr, int* count = nullptr) {
    static char text[1 << 18];
    iovec iov{text, sizeof text};
    msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    alignas(cmsghdr) char control[CMSG_SPACE(3 * sizeof(int))];
    msg.msg_control = control;
    msg.msg_controllen = sizeof control;
    ssize_t n;
    while ((n = recvmsg(fd, &msg, MSG_CMSG_CLOEXEC)) < 0 && errno == EINTR) {}
    if (count) *count = 0;
    cmsghdr* c = n > 0 ? CMSG_FIRSTHDR(&msg) : nullptr;
    if (c && c->cmsg_level == SOL_SOCKET && c->cmsg_type == SCM_RIGHTS) {
        int got[3], n = static_cast<int>((c->cmsg_len - CMSG_LEN(0)) / sizeof(int));  // at most 3: the control buffer's room
        memcpy(got, CMSG_DATA(c), n * sizeof(int));
        if (fds && count) { memcpy(fds, got, n * sizeof(int)); *count = n; }
        else for (int i = 0; i < n; i++) close(got[i]);
    }
    return n > 0 ? std::string(text, static_cast<size_t>(n)) : std::string();
}

std::string stamp(const std::string& path) {
    struct stat st{};
    if (stat(path.c_str(), &st) != 0) return path + " -\n";
    return path + " " + std::to_string(st.st_ino) + " " + std::to_string(st.st_mtim.tv_sec) + "." + std::to_string(st.st_mtim.tv_nsec) + "\n";
}

volatile sig_atomic_t forwardTo = 0;
void forward(int sig) { if (forwardTo > 0) kill(forwardTo, sig); }

// A launch's own environment, which the runtime reads after its target: a spare gets it with the target (spare_target.c sets it, Java reads
// -Dapklayer.env.<name>), a cold JVM in its environment. Neither in the key nor in the spare's own environment.
const std::set<std::string> kLaunchEnv = {"APKLAYER_INTENT_FILE", "APKLAYER_INTENT_TEXT", "APKLAYER_RESULT_FILE", "APKLAYER_COMPONENT_HOST", "APKLAYER_ISOLATED", "APKLAYER_PROCESS"};
// What differs from launch to launch and the runtime never reads: the JOPTS (in argv, but the launch's own late ones), a desktop launcher's
// activation token, pipes of the process that started the launch (the hub's ready pipe, a launch's started pipe), the login session's and the shell's bookkeeping
const std::set<std::string> kIgnoredEnv = {"APKLAYER_JOPTS", "XDG_ACTIVATION_TOKEN", "DESKTOP_STARTUP_ID", "APKLAYER_HUB_READY_FD", "APKLAYER_STARTED_FD",
                                           "SSH_CLIENT", "SSH_CONNECTION", "SSH_TTY", "XDG_SESSION_ID", "PWD", "OLDPWD", "SHLVL", "_",
                                           // the systemd unit's own (each launch and the spare run in units of their own: cli/unit.h)
                                           "INVOCATION_ID", "JOURNAL_STREAM", "SYSTEMD_EXEC_PID", "MANAGERPID",
                                           // who started the launch (lomiri-app-launch's identity of apklayer-manager or of a drawer entry, the
                                           // manager's scale), not the app: the spare the hub started has none of them
                                           "APP_ID", "APP_DESKTOP_FILE_PATH", "APP_EXEC", "APP_URIS", "DESKTOP_FILE_HINT", "XDG_SESSION_TYPE",
                                           "SLINT_SCALE_FACTOR"};

// The first entry (NUL-separated) in which a launch's key differs from the spare's: why a spare could not take it.
std::string firstDifference(const std::string& spare, const std::string& launch) {
    size_t i = 0;
    while (i < spare.size() && i < launch.size() && spare[i] == launch[i]) i++;
    const std::string separators("\0\n", 2);  // argv and environment entries end with NUL, the build's stamps with a newline
    size_t start = i == 0 ? std::string::npos : spare.find_last_of(separators, i - 1);
    start = start == std::string::npos ? 0 : start + 1;
    auto entry = [&](const std::string& s) {
        size_t end = s.find_first_of(separators, start);
        return start > s.size() ? std::string() : s.substr(start, (end == std::string::npos ? s.size() : end) - start).substr(0, 200);
    };
    return "spare has '" + entry(spare) + "', the launch '" + entry(launch) + "'";
}

// The spare's output before its launch (its holder's log: the native runtime's registration lines), first in the launch's own output.
void copyEarlyOutput(const Instance& inst, int out) {
    int in = open(join(inst.logs(), "app-spare.log").c_str(), O_RDONLY | O_CLOEXEC);
    char buffer[65536];
    for (ssize_t n; in >= 0 && (n = read(in, buffer, sizeof buffer)) > 0;)
        for (ssize_t done = 0, w; done < n; done += w) if ((w = write(out, buffer + done, n - done)) <= 0) { close(in); return; }
    if (in >= 0) close(in);
}
}  // namespace

std::string appSpareKey(const Layout& l, const Instance& inst, const std::vector<std::string>& argv, const Env& env, const std::string& sandboxPackage) {
    std::string key;
    for (const std::string& a : argv) key += a + '\0';
    for (const auto& [name, value] : env)
        if (!kIgnoredEnv.count(name) && !kLaunchEnv.count(name)) key += name + "=" + value + '\0';
    // the runtime build it loaded (a rebuild replaces these) and the stack whose binder it holds
    for (const std::string& path : {l.hostClasses(), join(l.native, "native-runtime.jar"), join(l.native, "aosp/Robolectric_all.jar"),
                                    join(l.native, "aosp/android-all-instrumented"), l.native, l.bionic, l.androidLibs, l.cliBinary()})
        key += stamp(path);
    // and its view: one made before `home-access` changed is not this launch's
    if (!sandboxPackage.empty()) key += "sandboxed as " + sandboxPackage + (homeAccess(l, inst, sandboxPackage) ? " with the home" : "") + "\n";
    return key + readFile(inst.pidFile()).value_or("");
}

int takeAppSpare(const Instance& inst, const std::string& key, const std::vector<std::string>& target, const Env& env, int startedFd, int* conn) {
    int fd = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", socketPath(inst).c_str());
    std::string text = key + '\0';
    for (const auto& [name, value] : env) if (kLaunchEnv.count(name)) text += "-Dapklayer.env." + name + "=" + value + "\n";
    for (const std::string& line : target) text += line + "\n";
    int out[3] = {1, 2, startedFd};
    if (fd < 0 || connect(fd, reinterpret_cast<sockaddr*>(&addr), sizeof addr) != 0 || !sendWithFds(fd, text, out, 3)) { if (fd >= 0) close(fd); return -1; }
    std::string reply = receive(fd);
    if (reply.rfind("stale ", 0) == 0) printf("[apklayer] the instance's spare is not for this launch: %s\n", reply.c_str() + 6);
    int pid = reply.rfind("pid ", 0) == 0 ? atoi(reply.c_str() + 4) : -1;
    if (pid <= 0) { close(fd); return -1; }
    *conn = fd;
    return pid;
}

void passSignalsTo(int pid) {
    forwardTo = pid;
    struct sigaction sa{};
    sa.sa_handler = forward;
    for (int sig : {SIGINT, SIGTERM, SIGHUP}) sigaction(sig, &sa, nullptr);
}

int awaitAppSpare(int conn, int jvmPid) {
    passSignalsTo(jvmPid);
    std::string reply = receive(conn);  // the holder's word once the JVM ended (its parent: the exit status); none when the holder ended first
    close(conn);
    if (reply.rfind("exit ", 0) == 0) return atoi(reply.c_str() + 5);
    // the JVM outlives its holder (spare_target.c): its end, without a status
    int exited = pidfdOpen(jvmPid);
    pollfd fd{exited, POLLIN, 0};
    while (exited >= 0 && poll(&fd, 1, -1) < 0 && errno == EINTR) {}
    if (exited >= 0) close(exited);
    printf("[apklayer] the spare's holder ended before the app: its exit status is unknown\n");
    return 1;
}

void startAppSpare(const Layout& l, const Instance& inst, int afterFd, const std::string& sandboxPackage) {
    fcntl(afterFd, F_SETFD, 0);  // the holder's: the caller closes its own copy
    Spawn holder;
    holder.argv = {l.cliBinary(), "--instance", inst.id, "app-spare", "--after", std::to_string(afterFd)};
    if (!sandboxPackage.empty()) holder.argv.insert(holder.argv.end(), {"--package", sandboxPackage});
    holder.env = currentEnv();  // the launcher's own: the holder derives the spare's environment and key as the launcher does
    holder.logFile = join(inst.logs(), "app-spare.log");  // never the launcher's stdout (a caller reading it to its end would wait for the spare)
    holder.detach = true;
    spawn(holder);
}

void wakeAppSpare(const Layout& l, const Instance& inst) {
    int fd = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", socketPath(inst).c_str());
    bool held = fd >= 0 && connect(fd, reinterpret_cast<sockaddr*>(&addr), sizeof addr) == 0 && sendWithFds(fd, "now", nullptr, 0);
    if (fd >= 0) close(fd);
    if (!held) startAppSpare(l, inst, -1);
}

int runAppSpare(const Layout& l, const Instance& inst, int afterFd, const std::string& sandboxPackage) {
    int lock = open(join(inst.stateDir, "app-spare.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0644);
    if (lock < 0 || flock(lock, LOCK_EX | LOCK_NB) != 0) return 0;  // one spare per instance
    if (ftruncate(1, 0) != 0) {}  // app-spare.log (startAppSpare) holds the current spare's output only
    // `apklayer stop` ends the holder (and with it the spare JVM) with the instance's launches
    std::string record = join(inst.stateDir, "run." + std::to_string(getpid()));
    writeFile(record, std::to_string(getpid()) + "\n");
    unlink(socketPath(inst).c_str());
    int server = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    snprintf(addr.sun_path, sizeof addr.sun_path, "%s", socketPath(inst).c_str());
    if (server < 0 || bind(server, reinterpret_cast<sockaddr*>(&addr), sizeof addr) != 0 || listen(server, 8) != 0) { unlink(record.c_str()); return 1; }
    // a JVM preparing beside a starting process slows its start: the spare starts once that launch's process is up (or it ended), or at once
    // when a launch or the hub (`apklayer app-spare`: wakeAppSpare) asks for it meanwhile
    pollfd wait[2] = {{afterFd, POLLIN, 0}, {server, POLLIN, 0}};
    while (afterFd >= 0 && poll(wait, 2, -1) < 0 && errno == EINTR) {}
    if (afterFd >= 0) close(afterFd);
    NativeEnv ne = nativeEnv(l, inst);
    for (const std::string& name : kLaunchEnv) ne.env.erase(name);  // the launch that started this holder: not the next one's
    std::vector<std::string> argv = windowJvmArgs(l, inst, ne);
    std::string key = appSpareKey(l, inst, argv, ne.env, sandboxPackage);
    int pair[2];
    if (socketpair(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0, pair) != 0) return 1;
    fcntl(pair[1], F_SETFD, 0);
    Spawn jvm;
    jvm.argv = argv;
    jvm.argv.push_back("--spare");
    jvm.env = ne.env;
    jvm.env["APKLAYER_SPARE_FD"] = std::to_string(pair[1]);
    jvm.cwd = l.appsDir;
    jvm.blockSigpipe = true;
    // under the sandbox: already that app's process (its uid and mount view; the binder driver keeps the uid it was opened with, and a JVM
    // cannot change mount namespace), so a spare is one app's, the one launched last (a launch of another makes it stale)
    if (!sandboxPackage.empty()) sandboxSpawn(l, inst, jvm, sandboxPackage);
    // no PR_SET_PDEATHSIG: the JVM's primordial thread keeps it (the Java main thread cannot clear it), so it would end the app born from the spare;
    // a spare whose holder ended exits instead once prepared (its socket's EOF: AppSpare), `apklayer stop` ends both (the record)
    int pid = spawn(jvm);
    close(pair[1]);
    writeFile(record, std::to_string(pid) + "\n" + std::to_string(getpid()) + "\n");
    printf("[apklayer] app spare pid=%d\n", pid);
    fflush(stdout);
    // before any answer: the socket and the lock are free for the next spare, which the launcher starts right after
    auto release = [&] { close(server); unlink(socketPath(inst).c_str()); unlink(record.c_str()); flock(lock, LOCK_UN); };
    int exited = pidfdOpen(pid), launch = -1;
    for (;;) {
        pollfd fds[2] = {{server, POLLIN, 0}, {exited, POLLIN, 0}};
        while (poll(fds, 2, -1) < 0 && errno == EINTR) {}
        if (fds[1].revents) { release(); waitpid(pid, nullptr, 0); return 0; }  // the spare ended before a launch took it (`apklayer stop`)
        int c = accept4(server, nullptr, nullptr, SOCK_CLOEXEC);
        if (c < 0) continue;
        int out[3], count = 0;
        std::string message = receive(c, out, &count);
        bool match = count == 3 && message.size() > key.size() && message.compare(0, key.size(), key) == 0 && message[key.size()] == '\0';
        if (match) {
            fflush(stdout);
            copyEarlyOutput(inst, out[0]);
        }
        if (match && sendWithFds(pair[0], message.substr(key.size() + 1), out, 3)) launch = c;
        for (int i = 0; i < count; i++) close(out[i]);
        if (launch >= 0) break;
        if (message.empty() || message == "now") { close(c); continue; }  // a launcher that gave up; the hub's wakeAppSpare
        // a launch of another build or environment: this spare is of no use to it (nor likely to the next); it starts one of its own
        release();
        std::string why = firstDifference(key, message);
        printf("[apklayer] app spare pid=%d: not for a launch of another runtime build or environment (%s), ends\n", pid, why.c_str());
        fflush(stdout);
        sendWithFds(c, "stale " + why, nullptr, 0);
        close(c);
        kill(pid, SIGKILL);
        waitpid(pid, nullptr, 0);
        return 0;
    }
    release();  // the launch records the JVM as its own from here on
    printf("[apklayer] app spare pid=%d taken by a launch\n", pid);
    fflush(stdout);
    sendWithFds(launch, "pid " + std::to_string(pid), nullptr, 0);
    int status = 0;
    while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
    sendWithFds(launch, "exit " + std::to_string(WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status)), nullptr, 0);
    return 0;
}

}  // namespace apklayer
