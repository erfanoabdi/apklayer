// A Halium device (Ubuntu Touch, docs/HALIUM.md): what apklayer detects and sets up by itself there. The device's HALs as hardware providers
// (graphics, camera, vibrator) are the automatic choice of cli/providers.cpp (cli/device_hals.cpp), audio's PulseAudio backend too.
#pragma once

#include "layout.h"

namespace apklayer {

// The machine runs its own Android (an LXC container or droid-hal: /android/system, /vendor) with binderfs.
bool isHalium();
// The default instance id: "ut" on a Halium device (the default instance's binder, /dev/binder, is the device's own), else "default".
std::string defaultInstanceId();
// Environment a Halium session needs that its login does not set: PULSE_SERVER (the session's socket) for the PulseAudio backend.
void haliumEnvDefaults(Env& env, const Layout& layout);

}  // namespace apklayer
