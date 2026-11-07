#include "fetch.h"

#include <cctype>
#include <cerrno>
#include <climits>
#include <cstdlib>
#include <dirent.h>
#include <fcntl.h>
#include <fstream>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

namespace manager {

namespace {
std::string home(const char* xdg, const char* fallback) {
    const char* v = getenv(xdg);
    return v && *v ? v : std::string(getenv("HOME") ? getenv("HOME") : "") + "/" + fallback;
}

bool exists(const std::string& p) { struct stat st{}; return lstat(p.c_str(), &st) == 0; }
void removeTree(const std::string& p) { if (!p.empty()) runProgram({"rm", "-rf", "--", p}); }
std::string mkdirs(const std::string& dir) { return runProgram({"mkdir", "-p", "--", dir}).status == 0 ? "" : "cannot create " + dir; }

// a release's runtime version names a directory: nothing that could leave root or hide in it
bool safeName(const std::string& v) {
    if (v.empty() || v[0] == '.' || v == "current") return false;
    for (char c : v) if (!isalnum(static_cast<unsigned char>(c)) && c != '.' && c != '-' && c != '_' && c != '+') return false;
    return true;
}

// wget with its progress bar on stderr ("  45%[=====>   ]"): the last percentage seen goes to progress
std::string wget(const std::string& url, const std::string& out, const Progress& progress) {
    int err[2];
    if (pipe2(err, O_CLOEXEC) != 0) return "no pipe";
    pid_t pid = fork();
    if (pid == 0) {
        dup2(err[1], 2);
        int devnull = open("/dev/null", O_RDWR);
        dup2(devnull, 0);
        dup2(devnull, 1);
        // a stalled server fails in minutes, not wget's default of 20 tries of 15 minutes each
        execlp("wget", "wget", "-q", "--tries=3", "--timeout=30", "--show-progress", "--progress=bar:force:noscroll", "--compression=auto", "-O", out.c_str(), "--", url.c_str(), nullptr);
        _exit(127);
    }
    close(err[1]);
    if (pid < 0) { close(err[0]); return "cannot start wget"; }
    std::string tail;
    char buf[4096];
    for (ssize_t got; (got = read(err[0], buf, sizeof buf)) != 0;) {
        if (got < 0) { if (errno == EINTR) continue; break; }
        tail.append(buf, got);
        size_t pct = tail.rfind('%');
        if (pct != std::string::npos && progress) {
            size_t start = pct;
            while (start > 0 && isdigit(static_cast<unsigned char>(tail[start - 1]))) start--;
            progress(start < pct ? atoi(tail.substr(start, pct - start).c_str()) / 100.0 : -1);
        } else if (progress) progress(-1);
        if (tail.size() > 8192) tail.erase(0, tail.size() - 512);
    }
    close(err[0]);
    int st = 0;
    while (waitpid(pid, &st, 0) < 0 && errno == EINTR) {}
    if (WIFEXITED(st) && WEXITSTATUS(st) == 0) return "";
    if (WIFEXITED(st) && WEXITSTATUS(st) == 127) return "wget is not installed";
    return "download failed (wget status " + std::to_string(WIFEXITED(st) ? WEXITSTATUS(st) : 128 + WTERMSIG(st)) + "): " + url;
}

std::string copyFile(const std::string& from, const std::string& to, const Progress& progress) {
    std::ifstream in(from, std::ios::binary);
    if (!in) return "cannot read " + from;
    in.seekg(0, std::ios::end);
    double size = static_cast<double>(in.tellg());
    in.seekg(0);
    std::ofstream out(to, std::ios::binary | std::ios::trunc);
    std::vector<char> buf(1 << 20);
    double done = 0;
    while (in.read(buf.data(), buf.size()) || in.gcount()) {
        out.write(buf.data(), in.gcount());
        done += in.gcount();
        if (progress) progress(size > 0 ? done / size : -1);
    }
    return out ? "" : "cannot write " + to;
}
}  // namespace

std::string downloadsDir() { return home("XDG_CACHE_HOME", ".cache") + "/apklayer/downloads"; }
std::string runtimeRoot() { return home("XDG_DATA_HOME", ".local/share") + "/apklayer/runtime"; }

std::string sha256File(const std::string& path) {
    Result r = runProgram({"sha256sum", "--", path});
    return r.status == 0 && r.out.size() >= 64 ? r.out.substr(0, 64) : "";
}

namespace {
std::string get(const std::string& url, const std::string& part, const Progress& progress) {
    if (std::string e = mkdirs(part.substr(0, part.rfind('/'))); !e.empty()) return e;
    return url.rfind("file://", 0) == 0 ? copyFile(fileUriPath(url), part, progress)
         : url.rfind("https://", 0) == 0 || url.rfind("http://", 0) == 0 ? wget(url, part, progress) : "not a download URL: " + url;
}
}  // namespace

std::string download(const std::string& url, const std::string& dest, const Progress& progress) {
    std::string part = dest + ".part", e = get(url, part, progress);
    if (e.empty() && rename(part.c_str(), dest.c_str()) != 0) e = "cannot rename " + part;
    if (!e.empty()) unlink(part.c_str());
    return e;
}

std::string fetch(const std::string& url, const std::string& sha256, const std::string& dest, const Progress& progress) {
    if (sha256.size() != 64) return "no SHA256 for " + url;  // nothing unverified is used
    std::string part = dest + ".part", e = get(url, part, progress);
    if (e.empty()) {
        std::string got = sha256File(part), want = sha256;
        for (char& c : want) c = static_cast<char>(tolower(static_cast<unsigned char>(c)));
        if (got != want) e = "checksum mismatch for " + url.substr(url.rfind('/') + 1) + " (expected " + want.substr(0, 12) + "…, got " + (got.empty() ? "nothing" : got.substr(0, 12) + "…") + ")";
    }
    if (e.empty() && rename(part.c_str(), dest.c_str()) != 0) e = "cannot rename " + part;
    if (!e.empty()) unlink(part.c_str());
    return e;
}

// an unpacked bundle with its parts as real directories (a bundle made from a prebuilts checkout of links held links out of itself:
// tools/release/runtime-bundle.sh copies through them now); "" when complete, else what is wrong
static std::string runtimeProblem(const std::string& dir) {
    if (!exists(dir + "/runtime.json")) return "no runtime.json";
    for (const char* part : {"android-root/system", "android-libs", "aosp/android-all"}) {
        struct stat st{};
        if (lstat((dir + "/" + part).c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) return std::string("no ") + part;
    }
    return "";
}

std::string installedRuntime(const std::string& root) {
    char target[PATH_MAX];
    ssize_t n = readlink((root + "/current").c_str(), target, sizeof target - 1);
    if (n <= 0) return "";
    std::string v(target, n);
    return runtimeProblem(root + "/" + v).empty() ? v : "";  // a broken one counts as none: setup unpacks it again
}

std::string unpackRuntime(const std::string& bundle, const std::string& root, const std::string& version) {
    if (!safeName(version)) return "not a runtime version: " + version;
    if (std::string e = mkdirs(root); !e.empty()) return e;
    std::string pattern = root + "/.unpack.XXXXXX";
    if (!mkdtemp(pattern.data())) return "cannot create a directory in " + root;
    const std::string tmp = pattern;
    // zstd apart from tar: BusyBox's tar (Sailfish OS) has no --zstd; -o: owner not restored (GNU and BusyBox); a broken stream fails tar too
    Result tar = runProgram({"sh", "-c", "zstd -dcq -- \"$0\" | tar -xof - -C \"$1\"", bundle, tmp});
    if (tar.status != 0) { removeTree(tmp); return "cannot unpack " + bundle + ": " + tar.err.substr(0, 200); }
    std::string dir = tmp;
    if (!exists(dir + "/runtime.json")) {  // the bundle's one top directory
        std::vector<std::string> names;
        if (DIR* d = opendir(tmp.c_str())) { while (dirent* e = readdir(d)) if (e->d_name[0] != '.') names.push_back(e->d_name); closedir(d); }
        if (names.size() == 1) dir = tmp + "/" + names[0];
    }
    if (std::string p = runtimeProblem(dir); !p.empty()) { removeTree(tmp); return "not a complete runtime bundle (" + p + "): " + bundle; }
    std::string target = root + "/" + version, previous = installedRuntime(root), old = tmp + ".old";
    if (exists(target) && rename(target.c_str(), old.c_str()) != 0) { removeTree(tmp); return "cannot replace " + target; }
    if (rename(dir.c_str(), target.c_str()) != 0) { rename(old.c_str(), target.c_str()); removeTree(tmp); return "cannot move the runtime to " + target; }
    // current switches in one rename: a CLI starting now sees the old runtime or the new one, never a half
    std::string link = root + "/.current.tmp";
    unlink(link.c_str());
    if (symlink(version.c_str(), link.c_str()) != 0 || rename(link.c_str(), (root + "/current").c_str()) != 0) { removeTree(tmp); removeTree(old); return "cannot switch " + root + "/current"; }
    removeTree(tmp);
    removeTree(old);
    if (DIR* d = previous == version ? nullptr : opendir(root.c_str())) {  // keep the new one and the one before it (a reinstall keeps all)
        std::vector<std::string> stale;
        while (dirent* e = readdir(d)) {
            std::string n = e->d_name;
            if (n != "." && n != ".." && n != "current" && n != version && n != previous && n.rfind(".unpack.", 0) != 0) stale.push_back(n);
        }
        closedir(d);
        for (const std::string& n : stale) removeTree(root + "/" + n);
    }
    return "";
}

Result installDeb(const std::string& path) {
    if (runProgram({"sh", "-c", "command -v pkcon"}).status == 0) {
        Result r = runProgram({"pkcon", "-y", "--allow-untrusted", "install-local", path});  // a downloaded deb is no repository's: untrusted to PackageKit
        if (r.status == 0) return r;
    }
    // shortcut: a person who declined PackageKit's prompt is asked again by pkexec (the status cannot tell a refusal from a backend failure); untested: no pkcon on the VMs
    return runProgram({"pkexec", "apt-get", "install", "-y", path});
}

}  // namespace manager
