#include "backend.h"

#include <cerrno>
#include <climits>
#include <cctype>
#include <csignal>
#include <cstdlib>
#include <fcntl.h>
#include <poll.h>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>

namespace manager {

namespace {
std::vector<char*> cArgs(const std::vector<std::string>& argv) {
    std::vector<char*> c;
    for (const std::string& a : argv) c.push_back(const_cast<char*>(a.c_str()));
    c.push_back(nullptr);
    return c;
}

int exitStatus(pid_t pid) {
    int st = 0;
    while (waitpid(pid, &st, 0) < 0)
        if (errno != EINTR) return -1;
    return WIFEXITED(st) ? WEXITSTATUS(st) : 128 + WTERMSIG(st);
}
}  // namespace

Rows parseRows(const std::string& tsv) {
    Rows rows;
    size_t start = 0;
    while (start < tsv.size()) {
        size_t end = tsv.find('\n', start);
        if (end == std::string::npos) end = tsv.size();
        std::vector<std::string> fields;
        for (size_t f = start;;) {
            size_t tab = tsv.find('\t', f);
            if (tab == std::string::npos || tab > end) { fields.push_back(tsv.substr(f, end - f)); break; }
            fields.push_back(tsv.substr(f, tab - f));
            f = tab + 1;
        }
        if (end > start) rows.push_back(std::move(fields));
        start = end + 1;
    }
    return rows;
}

std::string Cli::locate() {
    if (const char* cli = getenv("APKLAYER_CLI"); cli && *cli) return cli;
    char exe[PATH_MAX];
    ssize_t n = readlink("/proc/self/exe", exe, sizeof exe - 1);
    if (n > 0) {
        std::string dir(exe, n);
        for (size_t slash; (slash = dir.rfind('/')) != std::string::npos && slash > 0;) {
            dir.resize(slash);
            if (access((dir + "/bin/apklayer").c_str(), X_OK) == 0) return dir + "/bin/apklayer";
        }
    }
    return "apklayer";
}

Result runProgram(const std::vector<std::string>& argv) {
    Result r;
    int out[2], err[2];
    if (pipe2(out, O_CLOEXEC) != 0) return r;
    if (pipe2(err, O_CLOEXEC) != 0) { close(out[0]); close(out[1]); return r; }
    pid_t pid = fork();
    if (pid == 0) {
        dup2(out[1], 1);
        dup2(err[1], 2);
        int devnull = open("/dev/null", O_RDONLY);
        dup2(devnull, 0);
        std::vector<char*> c = cArgs(argv);
        execvp(c[0], c.data());
        _exit(127);
    }
    close(out[1]);
    close(err[1]);
    pollfd fds[2] = {{out[0], POLLIN, 0}, {err[0], POLLIN, 0}};
    std::string* sinks[2] = {&r.out, &r.err};
    for (int open = 2; open > 0;) {
        if (poll(fds, 2, -1) < 0) { if (errno == EINTR) continue; break; }
        for (int i = 0; i < 2; i++) {
            if (fds[i].fd < 0 || !(fds[i].revents & (POLLIN | POLLHUP | POLLERR))) continue;
            char buf[4096];
            ssize_t got = read(fds[i].fd, buf, sizeof buf);
            if (got > 0) { sinks[i]->append(buf, got); continue; }
            if (got < 0 && errno == EINTR) continue;
            close(fds[i].fd);
            fds[i].fd = -1;
            open--;
        }
    }
    r.status = pid > 0 ? exitStatus(pid) : -1;
    return r;
}

Result Cli::run(const std::vector<std::string>& args) const {
    std::vector<std::string> argv = {exe_, "--instance", instance};
    argv.insert(argv.end(), args.begin(), args.end());
    return runProgram(argv);
}

void Cli::launch(const std::string& package, std::function<void(const std::string&)> onLine, std::function<void(int)> onExit) const {
    std::vector<std::string> argv = {exe_, "--instance", instance, "run", package};
    int out[2];
    if (pipe2(out, O_CLOEXEC) != 0) { onExit(-1); return; }
    pid_t pid = fork();
    if (pid == 0) {
        setsid();                  // the app is not the manager's: closing the manager leaves it running
        signal(SIGPIPE, SIG_IGN);  // and its output then goes nowhere instead of ending it
        dup2(out[1], 1);
        dup2(out[1], 2);
        setenv("APKLAYER_RUNTIME_DEBUG", "1", 1);
        std::vector<char*> c = cArgs(argv);
        execvp(c[0], c.data());
        _exit(127);
    }
    close(out[1]);
    if (pid < 0) { close(out[0]); onExit(-1); return; }
    std::thread([fd = out[0], pid, onLine = std::move(onLine), onExit = std::move(onExit)] {
        std::string pending;
        char buf[4096];
        for (ssize_t got; (got = read(fd, buf, sizeof buf)) != 0;) {
            if (got < 0) { if (errno == EINTR) continue; break; }
            pending.append(buf, got);
            for (size_t nl; (nl = pending.find('\n')) != std::string::npos; pending.erase(0, nl + 1)) onLine(pending.substr(0, nl));
        }
        close(fd);
        if (!pending.empty()) onLine(pending);
        onExit(exitStatus(pid));
    }).detach();
}

std::string fileUriPath(const std::string& uri) {
    const std::string scheme = "file://";
    std::string s = uri.rfind(scheme, 0) == 0 ? uri.substr(scheme.size()) : uri, path;
    for (size_t i = 0; i < s.size(); i++) {
        if (s[i] == '%' && i + 2 < s.size() && isxdigit(static_cast<unsigned char>(s[i + 1])) && isxdigit(static_cast<unsigned char>(s[i + 2]))) {
            path += static_cast<char>(std::stoi(s.substr(i + 1, 2), nullptr, 16));
            i += 2;
        } else path += s[i];
    }
    return path;
}

}  // namespace manager
