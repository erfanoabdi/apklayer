#include "binder.h"

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
    if (id != "default") return "this kernel has no binderfs, only fixed binder nodes: one instance (the default) can run";
    if (isHalium()) return "this Halium kernel has no binderfs: /dev/binder is the device's own";
    return "no binder device: load binder_linux (docs/INSTALL.md)";
}

std::string binderSetupCommand(const std::string& id) {
    if (isHalium() && exists("/etc/apklayer/instances"))
        return "echo " + id + " | sudo tee -a /etc/apklayer/instances && sudo systemctl restart apklayer-binder";
    const std::string installed = "/usr/share/apklayer/tools/host-setup/binder.sh";
    return "sudo sh " + std::string(exists(installed) ? installed : "tools/host-setup/binder.sh") + " " + id;
}

std::string hostBinder(const std::string& family) {
    for (const std::string& dir : {std::string("/dev/binderfs/"), std::string("/dev/")})
        if (exists(dir + family)) return dir + family;
    return "";
}

}  // namespace apklayer
