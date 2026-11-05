#include "halium.h"

#include <cstdio>
#include <unistd.h>

#include "binder.h"
#include "providers.h"

namespace apklayer {

bool isHalium() { return isDir("/android/system") && exists("/dev/binderfs/binder-control") && isDir("/vendor/lib64"); }

std::string defaultInstanceId() { return isHalium() ? "ut" : "default"; }

std::string audioLibrary(const Layout& layout) {
    const std::string pw = join(layout.native, "libapklayer-hal-audio.so"), pulse = join(layout.native, "libapklayer-hal-audio-pulse.so");
    if (!exists(pulse)) return pw;
    return !exists(pw) || !exists(join(layout.runtimeHome, "pipewire-0")) ? pulse : pw;
}

void haliumEnvDefaults(Env& env, const Layout& layout) {
    if (!isHalium() || env.count("PULSE_SERVER")) return;
    std::string socket = join(layout.runtimeHome, "pulse/native");
    if (exists(socket)) env["PULSE_SERVER"] = "unix:" + socket;
}

void seedHaliumProviders(const Instance& instance) {
    if (!isHalium() || exists(providersFile(instance))) return;
    std::vector<ProviderConfig> domains;
    if (isDir("/vendor/lib64/egl")) domains.push_back({"graphics", "device", "", {}});
    auto manifest = readFile("/vendor/etc/vintf/manifest.xml");
    if (manifest && manifest->find("android.hardware.camera.provider") != std::string::npos) domains.push_back({"camera", "device", "", {}});
    if (domains.empty()) return;
    saveProviders(providersFile(instance), domains);
    writeHostHals(instance);
    fprintf(stderr, "apklayer: Halium device: instance %s uses the device's graphics%s\n", instance.id.c_str(), domains.size() > 1 ? " and camera" : "");
}

}  // namespace apklayer
