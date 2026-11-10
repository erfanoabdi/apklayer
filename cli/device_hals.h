// The "device" provider's probe (docs/history/PLAN-2026-11-10.md "Plan: hardware providers" 1, docs/HALIUM.md): does this machine's own Android (a Halium
// device) serve a domain's HAL that apklayer can reach through the host binder contexts, and does that path work end to end yet.
#pragma once

#include "layout.h"

namespace apklayer {

struct DeviceHal {
    int quality;       // <0 not here, 0 detected but not working yet (never the default), >0 works (preferred over every backend)
    std::string note;  // what was found, or why not
};

// domain: vibrator, camera, graphics or media (the domains with a device HAL in cli/providers.cpp)
DeviceHal probeDeviceHal(const Layout& layout, const std::string& domain);

// Asks the device's init to start the HIDL HALs of these packages that its manifests declare (ctl.interface_start, what Android's libhidl
// does for a lazy HAL hwservicemanager has not got yet): the instance's processes reach the host's hwservicemanager, not its init.
void startDeviceHals(const std::vector<std::string>& packages);

}  // namespace apklayer
