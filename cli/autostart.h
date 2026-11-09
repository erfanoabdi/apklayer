// Always-on instances: `apklayer instance autostart <id> [on|off]` (a systemd user unit that starts the stack and hub at login and keeps them up).
#pragma once

#include "layout.h"

namespace apklayer {

// the instance's marker file exists: its hub never exits when idle (HubDirectory.alwaysOn reads the same file)
bool autostartOn(const Instance& instance);
// <id> [on [--no-start]|off]: no state prints on|off; on writes the marker and the unit and enables it (--no-start: not now, only at login),
// off removes the marker and disables the unit
int autostartCommand(const Layout& layout, const std::vector<std::string>& args);
// the unit written again when the CLI it names is gone (another install replaced this one)
void refreshAutostart(const Layout& layout);

}  // namespace apklayer
