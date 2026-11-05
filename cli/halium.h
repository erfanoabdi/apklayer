// A Halium device (Ubuntu Touch, docs/HALIUM.md): what apklayer detects and sets up by itself there (`apklayer setup` runs the seeding).
#pragma once

#include "layout.h"

namespace apklayer {

// The machine runs its own Android (an LXC container or droid-hal: /android/system, /vendor) with binderfs.
bool isHalium();
// The default instance id: "ut" on a Halium device (the default instance's binder, /dev/binder, is the device's own), else "default".
std::string defaultInstanceId();
// The audio HAL's host library: the PulseAudio build when the session has no PipeWire daemon and the package has both, else the standard one.
std::string audioLibrary(const Layout& layout);
// Environment a Halium session needs that its login does not set: PULSE_SERVER (the session's socket) for the PulseAudio HAL.
void haliumEnvDefaults(Env& env, const Layout& layout);
// A new instance's providers.toml on a Halium device: graphics (and camera, when the device has a camera HAL) = device. Nothing when the file exists.
void seedHaliumProviders(const Instance& instance);

}  // namespace apklayer
