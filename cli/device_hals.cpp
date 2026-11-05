#include "device_hals.h"

#include "binder.h"
#include "halium.h"

namespace apklayer {

namespace {
// The HALs the device's VINTF manifests declare (its servicemanager and hwservicemanager serve what these name): the "<name>" of every
// <hal> in the vendor's and ODM's manifest and fragments. Read once per process. The live listing would need a binder client in the CLI
// (glibc; libbinder is Android code): the manifests are what the device's own service managers check registrations against.
const std::string& declared() {
    static const std::string names = [] {
        std::string all;
        for (const std::string& dir : {std::string("/vendor/etc/vintf"), std::string("/odm/etc/vintf")}) {
            std::vector<std::string> files = {join(dir, "manifest.xml")};
            for (const std::string& f : listDir(join(dir, "manifest"))) files.push_back(join(dir, "manifest/" + f));
            for (const std::string& f : files)
                if (auto text = readFile(f)) all += *text;
        }
        if (auto legacy = readFile("/vendor/manifest.xml")) all += *legacy;
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
        if (!isDir("/vendor/lib64/egl")) return {-1, "the device has no GPU driver in /vendor/lib64/egl"};
        return {30, "the device's gralloc and GPU driver"};
    }
    if (domain == "media") {
        if (!declares("android.hardware.media.c2") && !declares("android.hardware.media.omx")) return {-1, "the device declares no Codec2 or OMX store"};
        // docs/PLAN.md "Plan: hardware providers" (e), docs/HALIUM.md: the codec list holds the device's codecs, creating one fails
        return {0, "the device's codecs are found, but not working yet (creating a decoder fails)"};
    }
    return {-1, "no device HAL for this domain"};
}

}  // namespace apklayer
