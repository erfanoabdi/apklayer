#include "logs.h"
#include "apps.h"
#include "logcap.h"

#include <cerrno>
#include <cstdio>
#include <fcntl.h>
#include <optional>
#include <signal.h>
#include <sys/stat.h>
#include <sys/utsname.h>
#include <unistd.h>

namespace apklayer {
namespace {
// An Android package name (and nothing a shell or a path could misread): the report names files after it.
bool packageName(const std::string& s) {
    if (s.empty() || s.size() > 255 || s[0] == '.' || s[0] == '-') return false;
    for (char c : s) if (!isalnum((unsigned char)c) && c != '.' && c != '_') return false;
    return true;
}

void writeAll(int fd, const char* p, ssize_t n) {
    while (n > 0) {
        ssize_t w = write(fd, p, n);
        if (w < 0 && errno == EINTR) continue;
        if (w <= 0) return;  // a closed terminal: the file still gets everything
        p += w;
        n -= w;
    }
}

int printFile(const std::string& path) {
    std::optional<std::string> text = readFile(path);
    if (!text) { fprintf(stderr, "apklayer: no log %s\n", path.c_str()); return 1; }
    fwrite(text->data(), 1, text->size(), stdout);
    return 0;
}
}  // namespace

std::string appLogPath(const Instance& inst, const std::string& package) { return join(inst.logs(), "app-" + package + ".log"); }

constexpr off_t kAppLogCap = 256 << 20;  // APKLAYER_APP_LOG_CAP (bytes) sets another, for the test

void teeLaunchOutput(const Instance& inst, const std::string& package) {
    std::string path = appLogPath(inst, package);
    makeDirs(inst.logs());
    rename(path.c_str(), (path + ".1").c_str());  // the previous launch's log stays one launch longer
    int file = open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_APPEND | O_CLOEXEC, 0600);
    int pipeFds[2];
    if (file < 0 || pipe2(pipeFds, O_CLOEXEC) != 0) { if (file >= 0) close(file); return; }  // no log, the launch still runs
    fflush(stdout);
    fflush(stderr);
    int terminal = fcntl(1, F_DUPFD_CLOEXEC, 3);
    // a pipe's reader may stop reading (the manager, frozen by its session while the app has the focus): it never stalls the app, what the pipe cannot
    // take at once is left out there (the file has everything). A terminal stays blocking (its description is the shell's too).
    struct stat st {};
    if (terminal >= 0 && fstat(terminal, &st) == 0 && S_ISFIFO(st.st_mode)) fcntl(terminal, F_SETFL, fcntl(terminal, F_GETFL) | O_NONBLOCK);
    pid_t tee = fork();
    if (tee == 0) {  // ends when every writer (this launcher, the JVM, its children) has closed the pipe
        signal(SIGPIPE, SIG_IGN);
        signal(SIGINT, SIG_IGN);  // Ctrl+C in the terminal ends the app; its last lines still reach the file
        close(pipeFds[1]);
        char buf[65536];
        const off_t cap = getenv("APKLAYER_APP_LOG_CAP") ? atoll(getenv("APKLAYER_APP_LOG_CAP")) : kAppLogCap;
        bool full = false;
        size_t skippedLines = 0, skippedBytes = 0;
        for (;;) {
            ssize_t n = read(pipeFds[0], buf, sizeof buf);
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) break;
            if (terminal >= 0) writeAll(terminal, buf, n);
            if (file < 0) continue;
            // a runaway app (a crash handler looping on its fault) never fills the disk: the file takes whole lines up to the cap, then only counts
            // what it leaves out (a note at the cap, another with the count at the end)
            size_t keep = n;
            if (!full) {
                struct stat fs {};
                size_t room = fstat(file, &fs) == 0 && fs.st_size < cap ? size_t(cap - fs.st_size) : 0;
                keep = capKeep(buf, n, room);
                if (keep < size_t(n)) {
                    char note[128];
                    int len = snprintf(note, sizeof note, "\n[apklayer] this log reached its size limit (%lld bytes): the rest of the launch's output is left out\n", (long long)cap);
                    writeAll(file, buf, keep);
                    writeAll(file, note, len);
                    full = true;
                }
            } else keep = 0;
            if (full) {
                skippedLines += capLines(buf + keep, n - keep);
                skippedBytes += n - keep;
            } else writeAll(file, buf, n);
        }
        if (file >= 0 && full) {
            char note[160];
            int len = snprintf(note, sizeof note, "[apklayer] %zu lines (%zu bytes) of the launch's output were left out at the size limit\n", skippedLines, skippedBytes);
            writeAll(file, note, len);
        }
        _exit(0);
    }
    close(pipeFds[0]);
    close(file);
    if (terminal >= 0) close(terminal);
    if (tee < 0) { close(pipeFds[1]); return; }
    dup2(pipeFds[1], 1);  // dup2 clears close-on-exec: children inherit the pipe as their stdout and stderr
    dup2(pipeFds[1], 2);
    close(pipeFds[1]);
}

int logsCommand(const Layout&, const Instance& inst, const std::vector<std::string>& args) {
    if (args.empty()) {  // which logs this instance has
        for (const std::string& name : listDir(inst.logs()))
            if (name.size() > 4 && (name.compare(name.size() - 4, 4, ".log") == 0 || name.compare(name.size() - 6, 6, ".log.1") == 0)) printf("%s\n", join(inst.logs(), name).c_str());
        return 0;
    }
    bool previous = args.size() == 2 && args[1] == "--previous";
    if (!packageName(args[0]) || (args.size() == 2 && !previous) || args.size() > 2) die("logs [<package> [--previous]]");
    return printFile(appLogPath(inst, args[0]) + (previous ? ".1" : ""));
}

int reportCommand(const Layout& l, const Instance& inst, const std::vector<std::string>& args) {
    if (args.empty() || args.size() > 2 || !packageName(args[0])) die("report <package> [out.tar.gz]");
    const std::string& package = args[0];
    std::string out = args.size() == 2 ? args[1] : "apklayer-report-" + package + ".tar.gz";
    if (out[0] != '/') out = join(realPath("."), out);
    char dirTemplate[] = "/tmp/apklayer-report-XXXXXX";
    if (!mkdtemp(dirTemplate)) die("report: no temporary directory");
    std::string dir = dirTemplate, base = join(dir, "apklayer-report-" + package);
    makeDirs(base);

    auto capture = [&](const std::vector<std::string>& argv) {
        Spawn s;
        s.argv = argv;
        s.env = currentEnv();
        int status = 0;
        std::string text = runCapture(s, &status);
        return text.empty() ? "(" + argv[0] + ": no output, status " + std::to_string(status) + ")\n" : text;
    };
    std::string cli = l.cliBinary(), id = inst.id;
    utsname u{};
    uname(&u);
    std::string about = "apklayer report for " + package + " (instance " + id + ")\n\n## apklayer --version\n" + capture({cli, "--version"}) +
                        "\n## host\n" + std::string(u.sysname) + " " + u.release + " " + u.version + " " + u.machine + "\n" + readFile("/etc/os-release").value_or("") +
                        "desktop: " + getenvOr("XDG_CURRENT_DESKTOP", "?") + ", session " + getenvOr("XDG_SESSION_TYPE", "?") + "\n" +
                        capture({"ldd", "--version"}).substr(0, 80) +
                        "\n\n## apklayer info " + package + "\n" + capture({cli, "--instance", id, "info", package}) +
                        "\n## apklayer status\n" + capture({cli, "--instance", id, "status"}) +
                        "\n## apklayer gpus\n" + capture({cli, "--instance", id, "gpus"}) +
                        "\n## apklayer permissions " + package + "\n" + capture({cli, "--instance", id, "permissions", package});
    writeFile(join(base, "about.txt"), about);
    // the app's own logs and every service log of the instance (the ones a crash or a black window is found in), the previous stack's too
    // (<name>.log.1, cli/stack.cpp: a service crash that ended it is there); other apps' logs stay out
    std::vector<std::string> names = {"app-" + package + ".log", "app-" + package + ".log.1", "app-spare.log", "app-spare.log.1"};
    for (const std::string& name : listDir(inst.logs())) {
        bool log = name.size() > 4 && (name.compare(name.size() - 4, 4, ".log") == 0 || (name.size() > 6 && name.compare(name.size() - 6, 6, ".log.1") == 0));
        if (name.rfind("component-" + package, 0) == 0 || (log && name.rfind("app-", 0) != 0 && name.rfind("component-", 0) != 0)) names.push_back(name);
    }
    int copied = 0;
    for (const std::string& name : names) {
        std::optional<std::string> text = readFile(join(inst.logs(), name));
        if (!text) continue;
        // shortcut: the last 8 MB of each log; upgrade to compression per file if reports need more
        if (text->size() > (8u << 20)) text = text->substr(text->size() - (8u << 20));
        writeFile(join(base, name), *text);
        copied++;
    }
    Spawn tar;
    tar.argv = {"tar", "czf", out, "-C", dir, baseName(base)};
    tar.env = currentEnv();
    int status = runAndWait(tar);
    removeTree(dir);
    if (status != 0) die("report: tar failed for " + out);
    printf("%s\n", out.c_str());
    fprintf(stderr, "apklayer: %d logs and about.txt in %s. Look through it before you attach it to an issue: logs can contain account names, URLs and file names.\n"
            "No log of the app? Every launch writes one (apklayer logs %s): open the app again, reproduce the problem, then run this again.\n", copied, out.c_str(), package.c_str());
    return 0;
}
}  // namespace apklayer
