#include "binder.h"

#include <fcntl.h>
#include <sys/file.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

#include <cstring>

#include "flatpak.h"
#include "halium.h"
#include "layout.h"
#include "stack.h"
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
    // binder.sh loads binder_linux and mounts binderfs itself, so a desktop without either needs only that command (isHalium() implies binderfs)
    return "its kernel binder devices are missing: " + binderSetupCommand(id) + " (or apklayer binder userspace, the default)";
}

std::string binderSetupCommand(const std::string& id) {
    if (isHalium() && exists("/etc/apklayer/instances"))
        return "echo " + id + " | sudo tee -a /etc/apklayer/instances && sudo systemctl enable apklayer-binder && sudo systemctl restart apklayer-binder";
    // the Flatpak's copy as the host sees it: binderfs is mounted and its nodes made on the host, as root (docs/INSTALL.md "Flatpak")
    const std::string installed = inFlatpak() ? "/app/share/apklayer/tools/host-setup/binder.sh" : "/usr/share/apklayer/tools/host-setup/binder.sh";
    return "sudo sh " + (exists(installed) ? hostPath(installed) : std::string("tools/host-setup/binder.sh")) + " " + id;
}

bool userspaceBinder(const std::string& instanceRoot) {
    std::string mode = getenvOr("APKLAYER_BINDER", "");
    if (mode == "userspace" || mode == "kernel") return mode == "userspace";
    auto setting = readFile(binderChoiceFile(instanceRoot));
    return !(setting && setting->rfind("kernel", 0) == 0);  // the default since 0.4.9 (docs/BINDER.md); kernel binder only when chosen
}

std::string binderChoiceFile(const std::string& instanceRoot) { return join(instanceRoot, "binder"); }

bool stackUserspaceBinder(const Instance& inst) {
    auto pid = readFile(inst.pidFile());
    if (!(pid && alive(atoi(pid->c_str())))) return userspaceBinder(inst.root);
    auto running = readFile(join(inst.runtimeDir, "binder"));
    return running && running->rfind("userspace", 0) == 0;  // none: a stack an apklayer before 0.4.9 started, on the kernel's binder (its default)
}

void recordStackBinder(const Instance& inst) { writeFile(join(inst.runtimeDir, "binder"), userspaceBinder(inst.root) ? "userspace\n" : "kernel\n"); }

int binderCommand(const Instance& inst, const std::vector<std::string>& args) {
    if (args.size() == 1 && (args[0] == "userspace" || args[0] == "kernel")) {
        writeFile(binderChoiceFile(inst.root), args[0] + "\n");
        printf("apklayer: instance %s uses the %s binder from its next start (apklayer stop)\n", inst.id.c_str(), args[0].c_str());
        if (getenv("APKLAYER_BINDER") && getenvOr("APKLAYER_BINDER", "") != args[0]) printf("apklayer: APKLAYER_BINDER=%s in this environment overrides it\n", getenv("APKLAYER_BINDER"));
        if (args[0] == "kernel" && !exists(binderDevice(inst.id))) printf("apklayer: %s\n", binderDeviceWhy(inst.id).c_str());
        return 0;
    }
    if (!args.empty()) die("binder [userspace|kernel]");
    // rows: the instance's binder and where that comes from; whether its kernel devices exist, else what makes them
    std::string from = getenv("APKLAYER_BINDER") ? "environment" : exists(binderChoiceFile(inst.root)) ? "instance" : "default";
    printf("binder\t%s\t%s\n", userspaceBinder(inst.root) ? "userspace" : "kernel", from.c_str());
    if (exists(binderDevice(inst.id))) puts("kernel\tready");
    else printf("kernel\tmissing\t%s\n", binderSetupCommand(inst.id).c_str());
    return 0;
}

bool binderReady(const std::string& instanceRoot, const std::string& id) { return userspaceBinder(instanceRoot) || exists(binderDevice(id)); }

std::string binderSocketDir(const std::string& runtimeHome) { return join(runtimeHome, "apklayer-binder"); }

namespace {
int unixSocket(const std::string& path, bool listening) {
    sockaddr_un a{};
    a.sun_family = AF_UNIX;
    if (path.size() >= sizeof a.sun_path) die("binder socket path too long (" + std::to_string(path.size()) + " bytes): " + path);
    memcpy(a.sun_path, path.c_str(), path.size());
    int s = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);  // bridge/binderd/protocol.h
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
    chmod(sock.c_str(), 0666);  // the sandbox's app uids connect too (cli/sandbox.cpp); the runtime directory (0700) keeps other users out
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
