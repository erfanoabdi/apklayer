// A Halium device (Ubuntu Touch, docs/HALIUM.md): what apklayer detects and sets up by itself there. The device's HALs as hardware providers
// (graphics, camera, vibrator) are the automatic choice of cli/providers.cpp (cli/device_hals.cpp), audio's PulseAudio backend too.
#pragma once

#include "layout.h"

namespace apklayer {

// What marks a Halium device: its Android's system and vendor trees and a binder of the device's own, binderfs or (a kernel without it) /dev/binder.
inline bool haliumLayout(bool androidSystem, bool binderfs, bool devBinder, bool vendorLib) { return androidSystem && (binderfs || devBinder) && vendorLib; }
// The machine runs its own Android (an LXC container or droid-hal: /android/system, /vendor) with binderfs or /dev/binder.
bool isHalium();
// The default instance id: "ut" on a Halium device (the default instance's binder, /dev/binder, is the device's own), else "default".
std::string defaultInstanceId();
// Environment a Halium session needs that its login does not set: PULSE_SERVER (the session's socket) for the PulseAudio backend.
void haliumEnvDefaults(Env& env, const Layout& layout);
// A launch from Lomiri's drawer (lomiri-app-launch: APP_ID, its unit): a window shell of its own, started in that unit before the launch leaves it, so
// Lomiri gives its Wayland client (the app's window) to the app (card, name, icon) and drops it when the launch ends. Sets APKLAYER_APP_SHELL (its socket).
void startLomiriAppShell(const Layout& layout, const std::string& instanceId);

}  // namespace apklayer
