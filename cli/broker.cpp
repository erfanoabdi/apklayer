#include "broker.h"
#include "apps.h"
#include "logs.h"
#include "sandbox.h"

#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <sys/stat.h>
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

// fd or -errno
int openFor(const Layout& l, const std::string& path, int flags, int mode, uid_t hostUid, const std::string& instance) {
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
        uid_t uid = appUidOf(hostUid);
        std::string package = uid == (uid_t)-1 ? "" : packageOf(l, uid);
        if (package.empty()) return -EACCES;
        Instance inst = Instance::open(l, instance);
        if (path == appLogPath(inst, package)) {  // its own app log (android.util.Log lines, AppEnvironment): appended, the user's file as before
            if ((flags & O_ACCMODE) != O_WRONLY || !(flags & O_APPEND)) return -EACCES;
            fd = open(path.c_str(), (base | O_NOFOLLOW) & ~(O_TRUNC | O_EXCL), 0600);
            return fd < 0 ? -errno : (fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) & ~O_NONBLOCK), fd);
        }
        if (!homeAccess(l, inst, package)) return -EACCES;
        std::string home = realPath(homeDir());
        std::vector<std::string> closed = {realPath(join(l.dataHome, "apklayer")), realPath(join(l.stateHome, "apklayer")), realPath(l.runtimeHome)};
        auto inHome = [&](const std::string& p) {  // what the app's view shows of the home: the apklayer trees there are emptied (appView)
            if (p.empty() || !under(p, home) || p == home) return false;
            for (const std::string& c : closed) if (!c.empty() && under(p, c)) return false;
            return true;
        };
        std::string target = (flags & O_NOFOLLOW) ? path : realPath(path);  // a link followed as the user would (a missing file: the path as given)
        size_t slash = target.find_last_of('/');
        std::string name = target.substr(slash + 1);
        int dir = open(slash == 0 ? "/" : target.substr(0, slash).c_str(), O_PATH | O_DIRECTORY | O_CLOEXEC);
        if (dir < 0) return -errno;
        std::string parent = fdPath(dir);
        if ((parent != home && !inHome(parent)) || name.empty() || name == "." || name == "..") { close(dir); return -EACCES; }
        fd = openat(dir, name.c_str(), base | O_NOFOLLOW, mode & 07777);
        int err = errno;
        close(dir);
        if (fd < 0) return -err;
        bool kind = fstat(fd, &st) == 0 && (S_ISREG(st.st_mode) || S_ISDIR(st.st_mode) || ((flags & O_PATH) && S_ISLNK(st.st_mode)));
        if (!kind || !inHome(fdPath(fd))) { close(fd); return -EACCES; }
    }
    if (!(flags & (O_NONBLOCK | O_PATH))) fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) & ~O_NONBLOCK);
    return fd;
}
}  // namespace

void brokerOpen(const Layout& l, int conn, const std::vector<std::string>& request, const ucred& peer, const std::string& instance) {
    int fd = request.size() == 4 && peer.uid != getuid() && !instance.empty()
        ? openFor(l, request[1], atoi(request[2].c_str()), atoi(request[3].c_str()), peer.uid, instance) : -EPERM;
    std::string reply = fd >= 0 ? "ok" : "err " + std::to_string(-fd);
    if (request.size() == 4 && request[1].rfind("/dev/", 0) == 0)  // the GPU nodes a driver needed (apklayerd.log: what an NVIDIA check reads first)
        fprintf(stderr, "apklayerd: broker %s %s for uid %u: %s\n", fd >= 0 ? "opened" : "refused", request[1].c_str(), peer.uid, fd >= 0 ? "ok" : strerror(-fd));
    iovec iov{reply.data(), reply.size()};
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
    if (fd >= 0) close(fd);
}

}  // namespace apklayer
