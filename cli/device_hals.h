// The "device" provider's probe (docs/PLAN.md "Plan: hardware providers" 1, docs/HALIUM.md): does this machine's own Android (a Halium
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

}  // namespace apklayer
