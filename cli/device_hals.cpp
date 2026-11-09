#include "device_hals.h"

#include "binder.h"
#include "env.h"
#include "halium.h"

namespace apklayer {

namespace {
// the host's own files: the CLI the hub's processes start runs under the path layer, which serves /vendor and /odm from the Android root
const std::string kHost = "/proc/self/root";

// The HALs the device's VINTF manifests declare (its servicemanager and hwservicemanager serve what these name): the "<name>" of every
// <hal> in the vendor's and ODM's manifest and fragments. Read once per process. The live listing would need a binder client in the CLI
// (glibc; libbinder is Android code): the manifests are what the device's own service managers check registrations against.
const std::string& declared() {
    static const std::string names = [] {
        std::string all;
        for (const std::string& dir : {kHost + "/vendor/etc/vintf", kHost + "/odm/etc/vintf"}) {
            std::vector<std::string> files = {join(dir, "manifest.xml")};
            for (const std::string& f : listDir(join(dir, "manifest"))) files.push_back(join(dir, "manifest/" + f));
            for (const std::string& f : files)
                if (auto text = readFile(f)) all += *text;
        }
        if (auto legacy = readFile(kHost + "/vendor/manifest.xml")) all += *legacy;
        return all;
    }();
    return names;
}

bool declares(const std::string& hal) { return declared().find("<name>" + hal + "</name>") != std::string::npos; }
}  // namespace

DeviceHal probeDeviceHal(const Layout& l, const std::string& domain) {
    if (!isHalium()) return {-1, "not a Halium device (no /android/system, binderfs and /vendor/lib64)"};
    if (hostBinder("hwbinder").empty() && hostBinder("binder").empty()) return {-1, "the device's binder nodes are missing"};
    if (domain == "vibrator")
        return declares("android.hardware.vibrator") ? DeviceHal{30, "the device's vibrator HAL"} : DeviceHal{-1, "the device declares no vibrator HAL"};
    if (domain == "camera") {
        if (!declares("android.hardware.camera.provider")) return {-1, "the device declares no camera provider"};
        // cameraserver runs in the stack and finds the device's HIDL provider through the host hwservicemanager (libhidl 0003)
        if (!exists(join(l.androidLibs, "libapklayer_cameraserver.so"))) return {-1, "this runtime has no cameraserver"};
        return {30, "the device's camera provider, through cameraserver"};
    }
    if (domain == "graphics") {
        if (!declares("android.hardware.graphics.allocator")) return {-1, "the device declares no gralloc allocator"};
        if (!isDir(kHost + "/vendor/lib64/egl")) return {-1, "the device has no GPU driver in /vendor/lib64/egl"};
        return {30, "the device's gralloc and GPU driver"};
    }
    if (domain == "media") {
        if (!declares("android.hardware.media.c2") && !declares("android.hardware.media.omx")) return {-1, "the device declares no Codec2 or OMX store"};
        // docs/HALIUM.md "Hardware providers": the default on Halium (owner, 2026-11-09), hardware H.264 decode and encode on the Volla X23;
        // it takes its output buffers from the device's gralloc, so automatic only with graphics = device (cli/providers.cpp deviceDomain)
        return {30, "the device's Codec2 store (with graphics = device)"};
    }
    return {-1, "no device HAL for this domain"};
}

void startDeviceHals(const std::vector<std::string>& packages) {
    if (!isHalium() || !exists("/usr/bin/setprop")) return;
    const std::string& text = declared();
    for (const std::string& package : packages)
        for (size_t at = text.find("<name>" + package + "</name>"); at != std::string::npos; at = text.find("<name>" + package + "</name>", at + 1)) {
            size_t end = text.find("</hal>", at);
            std::string hal = text.substr(at, end == std::string::npos ? std::string::npos : end - at);
            if (hal.find("<transport>hwbinder</transport>") == std::string::npos) continue;
            // shortcut: only the <fqname> form (MediaTek's manifests); the older <version>/<interface> form is not started, add it when a device needs it
            for (size_t f = hal.find("<fqname>"); f != std::string::npos; f = hal.find("<fqname>", f + 1)) {
                size_t e = hal.find("</fqname>", f);
                if (e == std::string::npos) break;
                Spawn set;  // a running HAL stays as it is; a disabled (lazy) one starts, e.g. MediaTek's mms@1.6 on the Volla Quintus
                set.argv = {"/usr/bin/setprop", "ctl.interface_start", package + trim(hal.substr(f + 8, e - f - 8))};
                set.env = hostToolEnv();
                set.logFile = "/dev/null";  // init refuses an interface no service of its own declares (setprop: "could not set property")
                runAndWait(set);
            }
        }
}

}  // namespace apklayer
