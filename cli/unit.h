// Transient systemd user units (docs/HALIUM.md "Out of Lomiri's lifecycle"): a session that freezes an app's whole unit when it loses focus (Lomiri
// SIGSTOPs every process of a `lomiri-app-launch--...` unit) must not freeze what that app started: the stack, the hub, the spare, the window shell
// and each app launch run in scopes of their own. Without a systemd user manager nothing changes.
#pragma once

#include "util.h"

namespace apklayer {

// The cgroup (v2) leaf this process runs in: "lomiri-app-launch--application-legacy--apklayer-manager--.service", "apklayer-ut-hub-42.scope"; "" without.
std::string currentUnit();
// Rewrites spec to run through `systemd-run --user --scope` in the new scope "<unit>-<pid>.scope" (systemd-run execs the command: the pid stays the
// calling process's). Call in the process that execs spec; no-op without the user's systemd manager.
void scopeSpawn(Spawn& spec, const std::string& unit);
// Re-executes this command (same pid, arguments, environment and inherited descriptors) in its own scope "<unit>-<pid>.scope", unless it already runs
// there, or alwaysOnToo and it runs in the instance's always-on unit (apklayer-instance@<id>.service: the stack and hub it starts are its own).
void enterOwnUnit(std::string unit, bool alwaysOnToo = false);

}  // namespace apklayer
