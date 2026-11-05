#include "proc.h"

#include <cctype>
#include <cerrno>
#include <fcntl.h>
#include <poll.h>
#include <sys/wait.h>
#include <unistd.h>

namespace manager {

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

std::string summary(const Result& r) {
    std::string text = r.status == 0 ? r.out : (r.err.empty() ? r.out : r.err), line;
    for (size_t end = text.size(); end > 0 && line.empty();) {
        size_t start = text.rfind('\n', end - 1);
        start = start == std::string::npos ? 0 : start + 1;
        line = text.substr(start, end - start);
        end = start > 0 ? start - 1 : 0;
    }
    if (line.rfind("apklayer: ", 0) == 0) line = line.substr(10);
    if (!line.empty()) line[0] = static_cast<char>(toupper(static_cast<unsigned char>(line[0])));
    return line.empty() ? (r.status == 0 ? "Done" : "Failed (status " + std::to_string(r.status) + ")") : line;
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
