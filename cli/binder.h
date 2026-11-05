// Binder nodes, in one place (docs/HALIUM.md "Binder nodes"). An instance's own stack needs a context of its own: with binderfs
// /dev/binderfs/binder[-<id>] (tools/host-setup/binder.sh makes them); a kernel without binderfs, with the fixed /dev/binder nodes only,
// serves one instance (the default) until a userspace binder exists (docs/PLAN.md), and never a Halium device's: those nodes are the device's.
// The host context (the device's own binder and hwbinder, to reach its HALs) is a different thing: hostBinder().
#pragma once

#include <string>

namespace apklayer {

// binderfs is mounted with its control node.
bool haveBinderfs();
// The binder node of the instance's stack, or "" when this machine cannot give that instance one (binderDeviceWhy says why).
std::string binderDevice(const std::string& id);
std::string binderDeviceWhy(const std::string& id);
// The machine's own binder ("binder", "hwbinder" or "vndbinder"): binderfs's, else /dev's; "" when there is none.
std::string hostBinder(const std::string& family);

}  // namespace apklayer
