// The per-app sandbox (docs/PLAN.md "per-app sandbox with apklayerd"), on with APKLAYER_SANDBOX=1: each instance's stack makes a user namespace
// (Android uid 1000 is the desktop user, 10000 and up the user's /etc/subuid range), every app process runs in it under its own uid and in a mount
// namespace of its own: its data, the shared runtime read-only, nothing of the user's home.
#pragma once

#include "layout.h"

namespace apklayer {

bool sandboxOn();
// The stack daemon, before it starts anything: unshares the instance's user namespace (newuidmap/newgidmap write its maps). Dies with the reason.
void enterInstanceUserNs();
// A process of the instance in the sandbox: `apklayer sandbox-exec <package|-> -- argv...` in front of spec's command ("-": only the user namespace, the hub).
void sandboxSpawn(const Layout& layout, const Instance& instance, Spawn& spec, const std::string& package);
// `apklayer sandbox-exec`: joins the instance's user namespace; for an app also its own mount view and uid; then execs argv.
int sandboxExec(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);
// Makes path (a file or folder the user picked) visible at the same path in process pid's mount view (an app's). False with the reason printed.
bool grantPath(const Instance& instance, int pid, const std::string& path);
// The user owns dir again (an app's data after the sandbox ran it as its own uid): before it is removed, or used without the sandbox.
void reclaim(const std::string& dir);

}  // namespace apklayer
