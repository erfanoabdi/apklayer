#include "backend.h"

#include <algorithm>
#include <cerrno>
#include <climits>
#include <cctype>
#include <csignal>
#include <cstdlib>
#include <fcntl.h>
#include <filesystem>
#include <future>
#include <sys/wait.h>
#include <thread>
#include <unistd.h>

namespace manager {

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

Result Cli::run(const std::vector<std::string>& args) const {
    std::vector<std::string> argv = {exe_, "--instance", instance};
    argv.insert(argv.end(), args.begin(), args.end());
    return runProgram(argv);
}

namespace {
// the program's stdout and stderr, line by line, from a reader thread; then onExit with the status
void streamLines(std::vector<std::string> argv, bool ownSession, std::function<void(const std::string&)> onLine, std::function<void(int)> onExit) {
    int out[2];
    if (pipe2(out, O_CLOEXEC) != 0) { onExit(-1); return; }
    pid_t pid = fork();
    if (pid == 0) {
        if (ownSession) {
            setsid();                  // the app is not the manager's: closing the manager leaves it running
            signal(SIGPIPE, SIG_IGN);  // and its output then goes nowhere instead of ending it
            setenv("APKLAYER_RUNTIME_DEBUG", "1", 1);
        }
        dup2(out[1], 1);
        dup2(out[1], 2);
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
}  // namespace

void Cli::launch(const std::string& package, std::function<void(const std::string&)> onLine, std::function<void(int)> onExit) const {
    streamLines({exe_, "--instance", instance, "run", package}, true, std::move(onLine), std::move(onExit));
}

int Cli::runLines(const std::vector<std::string>& args, const std::function<void(const std::string&)>& onLine) const {
    std::vector<std::string> argv = {exe_, "--instance", instance};
    argv.insert(argv.end(), args.begin(), args.end());
    std::promise<int> done;
    streamLines(argv, false, onLine, [&done](int status) { done.set_value(status); });
    return done.get_future().get();
}

std::string chosenFile(const Result& chooser) {
    for (const auto& row : parseRows(chooser.out))
        if (!row.empty() && row[0].rfind("uri ", 0) == 0) return fileUriPath(row[0].substr(4));
    return "";
}

std::vector<std::string> findApks(size_t limit) {
    namespace fs = std::filesystem;
    const char* home = getenv("HOME");
    if (!home || !*home) return {};
    std::vector<std::pair<fs::file_time_type, std::string>> found;
    for (const char* sub : {"Downloads", "Documents", ""}) {
        std::error_code ec;
        for (fs::directory_iterator it(fs::path(home) / sub, ec), end; !ec && it != end; it.increment(ec)) {
            std::string name = it->path().filename().string();
            if (name.size() > 4 && name.compare(name.size() - 4, 4, ".apk") == 0 && it->is_regular_file(ec)) found.push_back({it->last_write_time(ec), it->path().string()});
        }
    }
    std::sort(found.begin(), found.end(), [](const auto& a, const auto& b) { return a.first > b.first; });
    std::vector<std::string> paths;
    for (const auto& f : found) if (paths.size() < limit) paths.push_back(f.second);
    return paths;
}

void dropImported(const std::string& path) {
    namespace fs = std::filesystem;
    const char* home = getenv("HOME");
    fs::path dir = fs::path(path).parent_path();
    if (!home || !*home || dir.parent_path().filename() != "HubIncoming" || path.rfind(std::string(home) + "/.cache/", 0) != 0) return;
    std::error_code ec;
    fs::remove_all(dir, ec);
}

std::vector<std::string> providerArgs(const std::string& domain, const std::string& backend, const std::vector<std::pair<std::string, std::string>>& options) {
    if (backend.empty()) return {"providers", "unset", domain};
    std::vector<std::string> args = {"providers", "set", domain, backend};
    for (const auto& [key, value] : options)
        if (!value.empty()) args.push_back(key + "=" + value);
    return args;
}

}  // namespace manager
