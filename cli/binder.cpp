#include "binder.h"

#include <fcntl.h>
#include <sys/file.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include <cstring>

#include "flatpak.h"
#include "halium.h"
#include "util.h"

namespace apklayer {

namespace {
// Waydroid's node names per family (tools/helpers/drivers.py): the first that exists is the machine's
const char* const kBinder[] = {"anbox-binder", "puddlejumper", "bonder", "binder", nullptr};
}  // namespace

bool haveBinderfs() { return exists("/dev/binderfs/binder-control"); }

std::string binderDevice(const std::string& id) {
    if (haveBinderfs()) return "/dev/binderfs/" + (id == "default" ? std::string("binder") : "binder-" + id);
    if (id != "default" || isHalium()) return "";  // fixed nodes: one instance, and not on a device whose Android owns them
    for (const char* const* n = kBinder; *n; n++)
        if (exists(std::string("/dev/") + *n)) return std::string("/dev/") + *n;
    return "";
}

std::string binderDeviceWhy(const std::string& id) {
    if (haveBinderfs()) return "its binder devices are missing: " + binderSetupCommand(id);
    if (inFlatpak()) return "no binderfs at /dev/binderfs: on the host run " + binderSetupCommand(id) + " (docs/INSTALL.md \"Flatpak\")";
    if (id != "default") return "this kernel has no binderfs, only fixed binder nodes: one instance (the default) can run";
    if (isHalium()) return "this Halium kernel has no binderfs: /dev/binder is the device's own";
    return "no binder device: load binder_linux (docs/INSTALL.md)";
}

std::string binderSetupCommand(const std::string& id) {
    if (isHalium() && exists("/etc/apklayer/instances"))
        return "echo " + id + " | sudo tee -a /etc/apklayer/instances && sudo systemctl restart apklayer-binder";
    // the Flatpak's copy as the host sees it: binderfs is mounted and its nodes made on the host, as root (docs/INSTALL.md "Flatpak")
    const std::string installed = inFlatpak() ? "/app/share/apklayer/tools/host-setup/binder.sh" : "/usr/share/apklayer/tools/host-setup/binder.sh";
    return "sudo sh " + (exists(installed) ? hostPath(installed) : std::string("tools/host-setup/binder.sh")) + " " + id;
}

bool userspaceBinder(const std::string& instanceRoot) {
    std::string mode = getenvOr("APKLAYER_BINDER", "");
    if (mode == "userspace" || mode == "kernel") return mode == "userspace";
    auto setting = readFile(join(instanceRoot, "binder"));
    return setting && setting->rfind("userspace", 0) == 0;
}

bool binderReady(const std::string& instanceRoot, const std::string& id) { return userspaceBinder(instanceRoot) || exists(binderDevice(id)); }

std::string binderSocketDir(const std::string& runtimeHome) { return join(runtimeHome, "apklayer-binder"); }

namespace {
int unixSocket(const std::string& path, bool listening) {
    sockaddr_un a{};
    a.sun_family = AF_UNIX;
    if (path.size() >= sizeof a.sun_path) die("binder socket path too long (" + std::to_string(path.size()) + " bytes): " + path);
    memcpy(a.sun_path, path.c_str(), path.size());
    int s = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (s < 0) return -1;
    bool ok = listening ? (unlink(path.c_str()), bind(s, (sockaddr*)&a, sizeof a) == 0 && listen(s, 64) == 0) : connect(s, (sockaddr*)&a, sizeof a) == 0;
    if (!ok) { close(s); return -1; }
    return s;
}
}  // namespace

void ensureBinderDaemon(const std::string& runtimeHome, const std::string& native, const std::string& id, const std::string& logFile) {
    std::string dir = binderSocketDir(runtimeHome), sock = join(dir, id + ".sock"), lock = join(dir, id + ".lock");
    makeDirs(dir);
    int lk = open(lock.c_str(), O_RDWR | O_CREAT | O_CLOEXEC, 0600);
    if (lk < 0 || flock(lk, LOCK_EX) != 0) die("cannot lock " + lock);
    if (int probe = unixSocket(sock, false); probe >= 0) {  // a daemon serves the instance (it cannot end while we hold the lock)
        close(probe);
        close(lk);
        return;
    }
    int s = unixSocket(sock, true);
    if (s < 0) die("cannot listen on " + sock);
    fcntl(s, F_SETFD, 0);  // the daemon's
    Spawn d;
    d.argv = {join(native, "bin/apklayer-binderd"), "--socket", sock, "--listen-fd", std::to_string(s), "--lock", lock};
    d.env = currentEnv();
    d.env.erase("LD_PRELOAD");  // the daemon is no Android process
    d.logFile = logFile;
    d.detach = true;
    int pid = spawn(d);
    close(s);
    writeFile(join(dir, id + ".pid"), std::to_string(pid) + "\n");
    close(lk);
}

std::string hostBinder(const std::string& family) {
    for (const std::string& dir : {std::string("/dev/binderfs/"), std::string("/dev/")})
        if (exists(dir + family)) return dir + family;
    return "";
}

}  // namespace apklayer
