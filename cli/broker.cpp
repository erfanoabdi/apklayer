#include "broker.h"
#include "apps.h"
#include "logs.h"
#include "sandbox.h"

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/xattr.h>
#include <vector>
#include <unistd.h>

namespace apklayer {

namespace {
bool under(const std::string& path, const std::string& dir) { return path == dir || path.rfind(dir + "/", 0) == 0; }

std::string fdPath(int fd) {
    char buf[4096];
    ssize_t n = readlink(("/proc/self/fd/" + std::to_string(fd)).c_str(), buf, sizeof buf - 1);
    return n > 0 ? std::string(buf, n) : "";
}

// The nodes a host GPU driver opens by path: DRM (Mesa, NVIDIA's GBM), NVIDIA's own (nvidiactl, nvidia0.., -modeset, -uvm, nvidia-caps/),
// AMD's compute (kfd), WSL's (dxg). Never a display's other devices (input, video capture, framebuffers).
bool gpuNode(const std::string& p) {
    return p.rfind("/dev/dri/", 0) == 0 || p.rfind("/dev/nvidia", 0) == 0 || p == "/dev/kfd" || p == "/dev/dxg";
}

// The app of a namespace uid in this layout (uids are per package, the same in every instance)
std::string packageOf(const Layout& l, uid_t uid) {
    for (const std::string& p : listDir(l.appsDir))
        if (atol(property(join(appDir(l, p), "info.properties"), "uid").c_str()) == (long)uid) return p;
    return "";
}

// The desktop's drives (udisks2's mount points, apklayer.host.HostVolumes.Removable) for an app with a storage permission it holds (granted, or a
// legacy app's not revoked, as cli/manage.cpp reads them) or all-files access it declares (MANAGE_EXTERNAL_STORAGE; MediaProvider's WRITE_MEDIA_STORAGE).
// shortcut: read and write for any of them, no per-file scoped-storage rules (as internal storage in the sandbox, cli/sandbox.cpp openStorageDir).
bool driveAccess(const Layout& l, const Instance& inst, const std::string& package) {
    std::string info = join(appDir(l, package), "info.properties");
    const bool legacy = atoi(property(info, "targetSdk").c_str()) < 23;
    std::vector<std::string> granted = split(readFile(join(inst.root, "permissions-granted")).value_or(""), '\n'),
                             revoked = split(readFile(join(inst.root, "permissions-revoked")).value_or(""), '\n');
    auto has = [](const std::vector<std::string>& lines, const std::string& line) { return std::find(lines.begin(), lines.end(), line) != lines.end(); };
    for (const std::string& p : split(property(info, "permissions"), ';')) {
        if (p == "android.permission.MANAGE_EXTERNAL_STORAGE" || p == "android.permission.WRITE_MEDIA_STORAGE") return true;
        if (p != "android.permission.READ_EXTERNAL_STORAGE" && p != "android.permission.WRITE_EXTERNAL_STORAGE" && p.rfind("android.permission.READ_MEDIA_", 0) != 0) continue;
        const std::string key = package + "\t" + p;
        if (has(granted, key) || (legacy && !has(revoked, key) && !has(revoked, key + "\tonce"))) return true;
    }
    return false;
}

// What of the user's files the app's view shows that it may reach as the user: the granted home without apklayer's own trees there (appView empties
// them), the drives' mount points
struct Home {
    std::vector<std::string> roots, closed;
    Home(const Layout& l, const Instance& inst, const std::string& package) :
        closed({realPath(join(l.dataHome, "apklayer")), realPath(join(l.stateHome, "apklayer")), realPath(l.runtimeHome)}) {
        if (homeAccess(l, inst, package)) roots.push_back(realPath(homeDir()));
        if (driveAccess(l, inst, package))
            for (const std::string& dir : driveRoots()) if (!realPath(dir).empty()) roots.push_back(realPath(dir));
    }
    bool root(const std::string& p) const { return std::find(roots.begin(), roots.end(), p) != roots.end(); }
    bool inside(const std::string& p) const {
        if (p.empty() || root(p) || std::none_of(roots.begin(), roots.end(), [&](const std::string& r) { return under(p, r); })) return false;
        for (const std::string& c : closed) if (!c.empty() && under(p, c)) return false;
        return true;
    }
    // An O_PATH fd of path's folder (the home or inside it) and the last name in *name, or -errno. follow: a link as the last name is followed as
    // the user would (a missing file: the path as given).
    int parent(const std::string& path, bool follow, std::string* name) const {
        std::string target = follow ? realPath(path) : path;
        size_t slash = target.find_last_of('/');
        *name = target.substr(slash + 1);
        int dir = open(slash == 0 ? "/" : target.substr(0, slash).c_str(), O_PATH | O_DIRECTORY | O_CLOEXEC);
        if (dir < 0) return -errno;
        std::string p = fdPath(dir);
        if ((!root(p) && !inside(p)) || name->empty() || *name == "." || *name == "..") { close(dir); return -EACCES; }
        return dir;
    }
};

// The package of a sandboxed peer, "" for any other
std::string peerPackage(const Layout& l, uid_t hostUid) {
    uid_t uid = appUidOf(hostUid);
    return uid == (uid_t)-1 ? "" : packageOf(l, uid);
}

// fd or -errno
int openFor(const Layout& l, const std::string& path, int flags, int mode, uid_t hostUid, const Instance& inst) {
    const int allowed = O_ACCMODE | O_CREAT | O_EXCL | O_TRUNC | O_APPEND | O_NONBLOCK | O_DIRECTORY | O_NOFOLLOW | O_PATH | O_CLOEXEC | O_LARGEFILE | O_DSYNC | O_SYNC;
    if (path.empty() || path[0] != '/' || (flags & ~allowed)) return -EINVAL;
    int base = flags | O_CLOEXEC | O_NOCTTY | O_NONBLOCK;  // never blocks the daemon (a FIFO): the caller's O_NONBLOCK is given back below
    int fd = -EACCES;
    struct stat st{};
    if (path.rfind("/dev/", 0) == 0) {
        fd = open(path.c_str(), base & ~(O_CREAT | O_EXCL | O_TRUNC));
        if (fd < 0) return -errno;
        if (fstat(fd, &st) != 0 || !S_ISCHR(st.st_mode) || !gpuNode(fdPath(fd))) { close(fd); return -EACCES; }
    } else {
        std::string package = peerPackage(l, hostUid);
        if (package.empty()) return -EACCES;
        std::string otherProcess = appLogPath(inst, package + ":");  // app-<package>:<process>.log: a window process of another android:process
        bool ownProcessLog = path.rfind(otherProcess.substr(0, otherProcess.size() - 4), 0) == 0 && path.find('/', otherProcess.size() - 4) == std::string::npos
                             && path.size() > otherProcess.size() && path.compare(path.size() - 4, 4, ".log") == 0;
        if (path == appLogPath(inst, package) || ownProcessLog) {  // its own app log (android.util.Log lines, AppEnvironment): appended, the user's file as before
            if ((flags & O_ACCMODE) != O_WRONLY || !(flags & O_APPEND)) return -EACCES;
            fd = open(path.c_str(), (base | O_NOFOLLOW) & ~(O_TRUNC | O_EXCL), 0600);
            return fd < 0 ? -errno : (fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) & ~O_NONBLOCK), fd);
        }
        Home home(l, inst, package);
        if (home.roots.empty()) return -EACCES;
        std::string name;
        int dir = home.parent(path, !(flags & O_NOFOLLOW), &name);
        if (dir < 0) return dir;
        fd = openat(dir, name.c_str(), base | O_NOFOLLOW, mode & 07777);
        int err = errno;
        close(dir);
        if (fd < 0) return -err;
        bool kind = fstat(fd, &st) == 0 && (S_ISREG(st.st_mode) || S_ISDIR(st.st_mode) || ((flags & O_PATH) && S_ISLNK(st.st_mode)));
        if (!kind || !home.inside(fdPath(fd))) { close(fd); return -EACCES; }
    }
    if (!(flags & (O_NONBLOCK | O_PATH))) fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) & ~O_NONBLOCK);
    return fd;
}

bool endsWith(const std::string& s, const char* tail) { size_t n = strlen(tail); return s.size() >= n && s.compare(s.size() - n, n, tail) == 0; }

// A value or list of an extended attribute as the user: op getxattr {path, name, nofollow, size}, listxattr {path, nofollow, size}, setxattr {path, name,
// nofollow, flags, hex value ("-": empty)}, removexattr {path, name, nofollow}. The O_PATH fd's /proc entry stands for the file. 0 or -errno; *ret, *data: what a read answers.
int xattrFor(int dir, const std::string& name, const std::vector<std::string>& r, long* ret, std::string* data) {
    int fd = openat(dir, name.c_str(), O_PATH | O_NOFOLLOW | O_CLOEXEC);
    if (fd < 0) return -errno;
    const std::string path = "/proc/self/fd/" + std::to_string(fd), &op = r[0];
    int rc = -EINVAL;
    if (op == "setxattr" && r.size() == 6) {
        std::string value;
        if (r[5] != "-" && r[5].size() % 2 == 0)
            for (size_t i = 0; i < r[5].size(); i += 2) value.push_back((char)strtol(r[5].substr(i, 2).c_str(), nullptr, 16));
        if (r[5] == "-" || r[5].size() % 2 == 0) rc = setxattr(path.c_str(), r[2].c_str(), value.data(), value.size(), atoi(r[4].c_str()));
    } else if (op == "removexattr" && r.size() == 4) rc = removexattr(path.c_str(), r[2].c_str());
    else if ((op == "getxattr" && r.size() == 5) || (op == "listxattr" && r.size() == 4)) {
        size_t want = std::min<unsigned long>(strtoul(r.back().c_str(), nullptr, 10), 65536);
        std::vector<char> buf(want ? want : 1);
        ssize_t n = op == "getxattr" ? getxattr(path.c_str(), r[2].c_str(), buf.data(), want) : listxattr(path.c_str(), buf.data(), want);
        if (n >= 0) { *ret = n; if (want) data->assign(buf.data(), n); }
        rc = n >= 0 ? 0 : -1;
    }
    int err = errno;
    close(fd);
    return rc == 0 ? 0 : rc == -EINVAL ? rc : -err;
}

// The granted home's and drives' other operations, as the user: mkdir, unlink, rmdir, rename (renameat2's flags), access, chmod, symlink, link, utimes,
// the xattr calls. 0 or -errno.
int changeFor(const Layout& l, const std::vector<std::string>& r, uid_t hostUid, const Instance& inst, long* ret, std::string* data) {
    std::string package = peerPackage(l, hostUid);
    if (package.empty()) return -EACCES;
    for (size_t i = 1; i < r.size(); i++) if (r[i].empty()) return -EINVAL;
    Home home(l, inst, package);
    if (home.roots.empty()) return -EACCES;
    std::string name, name2;
    const std::string& op = r[0];
    bool follow = op == "access" || op == "chmod";
    if (op == "utimes" && r.size() == 3) follow = r[2].back() == '0';
    else if (endsWith(op, "xattr")) follow = (op == "listxattr" ? r.size() > 2 && r[2] == "0" : r.size() > 3 && r[3] == "0");
    int dir = home.parent(r[1], follow, &name);  // never through a link as the last name: the link itself is what goes
    if (dir < 0) return dir;
    int rc = -EINVAL;
    if (op == "mkdir" && r.size() == 3) rc = mkdirat(dir, name.c_str(), atoi(r[2].c_str()) & 07777);
    else if (op == "unlink" && r.size() == 2) rc = unlinkat(dir, name.c_str(), 0);
    else if (op == "rmdir" && r.size() == 2) rc = unlinkat(dir, name.c_str(), AT_REMOVEDIR);
    else if (op == "access" && r.size() == 3) rc = faccessat(dir, name.c_str(), atoi(r[2].c_str()) & 7, 0);
    else if (op == "chmod" && r.size() == 3) rc = fchmodat(dir, name.c_str(), atoi(r[2].c_str()) & 07777, 0);
    else if (op == "utimes" && r.size() == 3) {
        long v[4]; int nofollow = 0;
        if (sscanf(r[2].c_str(), "%ld %ld %ld %ld %d", &v[0], &v[1], &v[2], &v[3], &nofollow) == 5) {
            timespec ts[2] = {{(time_t)v[0], v[1]}, {(time_t)v[2], v[3]}};
            rc = utimensat(dir, name.c_str(), ts, nofollow ? AT_SYMLINK_NOFOLLOW : 0);
        }
    } else if (endsWith(op, "xattr")) { int x = xattrFor(dir, name, r, ret, data); close(dir); return x; }
    else if (op == "symlink" && r.size() == 4) rc = symlinkat(r[2].c_str(), dir, name.c_str());
    else if ((op == "rename" || op == "link") && r.size() == 4) {
        int to = home.parent(r[2], false, &name2);
        if (to < 0) { close(dir); return to; }
        rc = op == "link" ? linkat(dir, name.c_str(), to, name2.c_str(), 0)
                          : (int)syscall(SYS_renameat2, dir, name.c_str(), to, name2.c_str(), (unsigned)atoi(r[3].c_str()));
        int err = errno;
        close(to);
        errno = err;
    }
    int err = errno;
    close(dir);
    return rc == 0 ? 0 : rc == -EINVAL ? rc : -err;
}

// data: an xattr's value or list, after "ok <ret>" and a NUL (bridge/native/broker.c ask_data)
void reply(int conn, int fd, int err, long ret = -1, const std::string& data = "") {
    std::string text = err == 0 ? "ok" : "err " + std::to_string(err);
    if (err == 0 && ret >= 0) text += " " + std::to_string(ret) + std::string(1, '\0') + data;
    iovec iov{text.data(), text.size()};
    msghdr msg{};
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;
    char control[CMSG_SPACE(sizeof(int))]{};
    if (fd >= 0) {
        msg.msg_control = control;
        msg.msg_controllen = sizeof control;
        cmsghdr* c = CMSG_FIRSTHDR(&msg);
        c->cmsg_level = SOL_SOCKET;
        c->cmsg_type = SCM_RIGHTS;
        c->cmsg_len = CMSG_LEN(sizeof(int));
        memcpy(CMSG_DATA(c), &fd, sizeof fd);
    }
    (void)!sendmsg(conn, &msg, MSG_NOSIGNAL);
}
}  // namespace

bool brokerRequest(const std::vector<std::string>& request) {
    static const char* ops[] = {"open", "mkdir", "unlink", "rmdir", "rename", "access", "chmod", "symlink", "link", "utimes", "setxattr", "getxattr", "listxattr", "removexattr"};
    for (const char* op : ops) if (!request.empty() && request[0] == op) return true;
    return false;
}

void brokerAnswer(const Layout& l, int conn, const std::vector<std::string>& request, const ucred& peer, const std::string& instance) {
    if (peer.uid == getuid() || instance.empty()) { reply(conn, -1, EPERM); return; }
    Instance inst = Instance::open(l, instance);
    if (request[0] != "open") {
        long ret = -1;
        std::string data;
        int rc = changeFor(l, request, peer.uid, inst, &ret, &data);
        reply(conn, -1, -rc, ret, data);
        return;
    }
    int fd = request.size() == 4 ? openFor(l, request[1], atoi(request[2].c_str()), atoi(request[3].c_str()), peer.uid, inst) : -EINVAL;
    if (request.size() == 4 && request[1].rfind("/dev/", 0) == 0)  // the GPU nodes a driver needed (apklayerd.log: what an NVIDIA check reads first)
        fprintf(stderr, "apklayerd: broker %s %s for uid %u: %s\n", fd >= 0 ? "opened" : "refused", request[1].c_str(), peer.uid, fd >= 0 ? "ok" : strerror(-fd));
    reply(conn, fd, fd >= 0 ? 0 : -fd);
    if (fd >= 0) close(fd);
}

}  // namespace apklayer
