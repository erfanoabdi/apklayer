#include "util.h"

#include <dirent.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <pwd.h>
#include <signal.h>
#include <sys/inotify.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <sstream>

extern char** environ;

namespace apklayer {

void die(const std::string& message) {
    std::cerr << "apklayer: " << message << std::endl;
    std::exit(1);
}

std::string join(const std::string& dir, const std::string& name) {
    if (dir.empty()) return name;
    return dir.back() == '/' ? dir + name : dir + "/" + name;
}

std::string homeDir() {
    if (const char* home = std::getenv("HOME")) return home;
    if (passwd* pw = getpwuid(getuid())) return pw->pw_dir;
    return "/";
}

std::string getenvOr(const char* name, const std::string& fallback) {
    const char* value = std::getenv(name);
    return value && *value ? value : fallback;
}

bool exists(const std::string& path) {
    struct stat st;
    return stat(path.c_str(), &st) == 0;
}

bool isDir(const std::string& path) {
    struct stat st;
    return stat(path.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

bool isExecutable(const std::string& path) { return access(path.c_str(), X_OK) == 0 && !isDir(path); }

std::string realPath(const std::string& path) {
    char buffer[PATH_MAX];
    return realpath(path.c_str(), buffer) ? std::string(buffer) : path;
}

std::string dirName(const std::string& path) {
    size_t slash = path.find_last_of('/');
    if (slash == std::string::npos) return ".";
    return slash == 0 ? "/" : path.substr(0, slash);
}

std::string baseName(const std::string& path) {
    size_t slash = path.find_last_of('/');
    return slash == std::string::npos ? path : path.substr(slash + 1);
}

void makeDirs(const std::string& path) {
    if (path.empty() || isDir(path)) return;
    makeDirs(dirName(path));
    if (mkdir(path.c_str(), 0777) != 0 && errno != EEXIST) die("cannot create " + path + ": " + std::strerror(errno));
}

void removeTree(const std::string& path) {
    struct stat st;
    if (lstat(path.c_str(), &st) != 0) return;
    if (S_ISDIR(st.st_mode)) {
        for (const std::string& name : listDir(path)) removeTree(join(path, name));
        rmdir(path.c_str());
    } else {
        unlink(path.c_str());
    }
}

std::optional<std::string> readFile(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) return std::nullopt;
    std::ostringstream out;
    out << in.rdbuf();
    return out.str();
}

bool writeFile(const std::string& path, const std::string& content) {
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    out << content;
    return static_cast<bool>(out);
}

std::vector<std::string> listDir(const std::string& path) {
    std::vector<std::string> names;
    if (DIR* dir = opendir(path.c_str())) {
        while (dirent* entry = readdir(dir)) {
            std::string name = entry->d_name;
            if (name != "." && name != "..") names.push_back(name);
        }
        closedir(dir);
    }
    return names;
}

std::vector<std::string> split(const std::string& text, char separator) {
    std::vector<std::string> parts;
    std::string item;
    std::istringstream in(text);
    while (std::getline(in, item, separator)) parts.push_back(item);
    return parts;
}

std::string trim(const std::string& text) {
    size_t begin = text.find_first_not_of(" \t\r\n"), end = text.find_last_not_of(" \t\r\n");
    return begin == std::string::npos ? "" : text.substr(begin, end - begin + 1);
}

std::string property(const std::string& file, const std::string& key) {
    auto content = readFile(file);
    if (!content) return "";
    std::istringstream in(*content);
    std::string line, prefix = key + "=";
    while (std::getline(in, line))
        if (line.compare(0, prefix.size(), prefix) == 0) return line.substr(prefix.size());
    return "";
}

// ---- SHA-256 ----
namespace {
struct Sha256 {
    uint32_t h[8] = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
    uint64_t length = 0;
    unsigned char block[64];
    size_t used = 0;
    static uint32_t rotr(uint32_t x, int n) { return (x >> n) | (x << (32 - n)); }
    void process(const unsigned char* p) {
        static const uint32_t k[64] = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be,
            0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa,
            0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85,
            0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
            0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f,
            0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};
        uint32_t w[64];
        for (int i = 0; i < 16; i++) w[i] = uint32_t(p[i * 4]) << 24 | uint32_t(p[i * 4 + 1]) << 16 | uint32_t(p[i * 4 + 2]) << 8 | p[i * 4 + 3];
        for (int i = 16; i < 64; i++) {
            uint32_t s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3), s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16] + s0 + w[i - 7] + s1;
        }
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
        for (int i = 0; i < 64; i++) {
            uint32_t t1 = hh + (rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)) + ((e & f) ^ (~e & g)) + k[i] + w[i];
            uint32_t t2 = (rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)) + ((a & b) ^ (a & c) ^ (b & c));
            hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
    }
    void update(const unsigned char* data, size_t n) {
        length += n;
        while (n) {
            size_t take = std::min(n, sizeof block - used);
            std::memcpy(block + used, data, take);
            used += take; data += take; n -= take;
            if (used == sizeof block) { process(block); used = 0; }
        }
    }
    std::string finish() {
        uint64_t bits = length * 8;
        unsigned char pad = 0x80;
        update(&pad, 1);
        pad = 0;
        while (used != 56) update(&pad, 1);
        unsigned char tail[8];
        for (int i = 0; i < 8; i++) tail[i] = static_cast<unsigned char>(bits >> (56 - 8 * i));
        update(tail, 8);
        char hex[65];
        for (int i = 0; i < 8; i++) std::snprintf(hex + i * 8, 9, "%08x", h[i]);
        return hex;
    }
};
}  // namespace

std::string sha256File(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    Sha256 sha;
    char buffer[1 << 16];
    while (in.read(buffer, sizeof buffer) || in.gcount()) sha.update(reinterpret_cast<unsigned char*>(buffer), static_cast<size_t>(in.gcount()));
    return sha.finish();
}

std::string sha256Text(const std::string& text) {
    Sha256 sha;
    sha.update(reinterpret_cast<const unsigned char*>(text.data()), text.size());
    return sha.finish();
}

// ---- processes ----
Env currentEnv() {
    Env env;
    for (char** e = environ; e && *e; e++) {
        const char* eq = std::strchr(*e, '=');
        if (eq) env[std::string(*e, eq - *e)] = eq + 1;
    }
    return env;
}

char** toEnvp(const Env& env, std::vector<std::string>& storage, std::vector<char*>& pointers) {
    storage.clear();
    pointers.clear();
    for (const auto& [key, value] : env) storage.push_back(key + "=" + value);
    for (std::string& s : storage) pointers.push_back(s.data());
    pointers.push_back(nullptr);
    return pointers.data();
}

namespace {
[[noreturn]] void execChild(const Spawn& spec) {
    if (spec.newSession) setsid();
    if (!spec.cwd.empty() && chdir(spec.cwd.c_str()) != 0) _exit(126);
    if (!spec.logFile.empty()) {
        int fd = open(spec.logFile.c_str(), O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
        if (fd >= 0) { dup2(fd, 1); dup2(fd, 2); }
    }
    int devnull = open("/dev/null", O_RDONLY);
    if (devnull >= 0) dup2(devnull, 0);
    std::vector<std::string> storage;
    std::vector<char*> envp, argv;
    toEnvp(spec.env, storage, envp);
    std::vector<std::string> args = spec.argv;
    for (std::string& a : args) argv.push_back(a.data());
    argv.push_back(nullptr);
    execvpe(argv[0], argv.data(), envp.data());
    std::fprintf(stderr, "apklayer: cannot run %s: %s\n", argv[0], std::strerror(errno));
    _exit(127);
}
}  // namespace

int spawn(const Spawn& spec) {
    if (spec.detach) {
        pid_t first = fork();
        if (first < 0) die("fork failed");
        if (first == 0) {
            pid_t second = fork();
            if (second != 0) _exit(0);
            Spawn detached = spec;
            detached.newSession = true;
            execChild(detached);
        }
        waitpid(first, nullptr, 0);
        return 0;
    }
    pid_t pid = fork();
    if (pid < 0) die("fork failed");
    if (pid == 0) execChild(spec);
    return pid;
}

int runAndWait(const Spawn& spec) {
    int pid = spawn(spec), status = 0;
    while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}

std::string runCapture(const Spawn& spec, int* statusOut) {
    int fds[2];
    if (pipe2(fds, O_CLOEXEC) != 0) die("pipe failed");
    pid_t pid = fork();
    if (pid < 0) die("fork failed");
    if (pid == 0) {
        dup2(fds[1], 1);
        execChild(spec);
    }
    close(fds[1]);
    std::string out;
    char buffer[4096];
    for (ssize_t n; (n = read(fds[0], buffer, sizeof buffer)) != 0;) {
        if (n < 0) { if (errno == EINTR) continue; break; }
        out.append(buffer, static_cast<size_t>(n));
    }
    close(fds[0]);
    int status = 0;
    while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
    if (statusOut) *statusOut = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
    return out;
}

int pidfdOpen(int pid) { return static_cast<int>(syscall(SYS_pidfd_open, pid, 0)); }

bool waitForFile(const std::string& path, int deathPidfd) {
    int notify = inotify_init1(IN_CLOEXEC | IN_NONBLOCK);
    if (notify < 0) die("inotify failed");
    inotify_add_watch(notify, dirName(path).c_str(), IN_CREATE | IN_MOVED_TO);
    bool found = exists(path);  // after the watch exists: nothing can slip between the check and the wait
    while (!found) {
        pollfd fds[2] = {{notify, POLLIN, 0}, {deathPidfd, POLLIN, 0}};
        if (poll(fds, deathPidfd >= 0 ? 2 : 1, -1) < 0 && errno != EINTR) break;
        if (fds[1].revents & POLLIN) break;
        if (fds[0].revents & POLLIN) {
            char buffer[4096];
            while (read(notify, buffer, sizeof buffer) > 0) {}
            found = exists(path);
        }
    }
    close(notify);
    return found;
}

}  // namespace apklayer
