// The manager's own Slint platform for compositors with wl_shell only (Sailfish OS's Lipstick): src/wlshell.cpp
#pragma once

namespace manager {
// Connects to the Wayland display and registers the platform; false (and nothing registered) without a display or wl_shell
bool useWlShell();
}
