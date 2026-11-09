// Binder nodes, in one place (docs/HALIUM.md "Binder nodes"). An instance's own stack needs a context of its own: with binderfs
// /dev/binderfs/binder[-<id>] (tools/host-setup/binder.sh makes them); a kernel without binderfs, with the fixed /dev/binder nodes only,
// serves one instance (the default); the userspace binder (the default) needs none of them, and never a Halium device's: those nodes are the device's.
// The host context (the device's own binder and hwbinder, to reach its HALs) is a different thing: hostBinder().
#pragma once

#include <string>
#include <vector>

namespace apklayer {

// binderfs is mounted with its control node.
bool haveBinderfs();
// The binder node of the instance's stack, or "" when this machine cannot give that instance one (binderDeviceWhy says why).
std::string binderDevice(const std::string& id);
std::string binderDeviceWhy(const std::string& id);
// The administrator's command that makes the instance's binderfs nodes: on a Halium device the id joins /etc/apklayer/instances, which
// apklayer-binder.service (enabled by it) reads at every boot; elsewhere the package's (or the tree's) tools/host-setup/binder.sh, once per boot.
std::string binderSetupCommand(const std::string& id);
// The userspace binder (docs/BINDER.md, bridge/binderd) instead of the kernel's for the instance whose data root this is: APKLAYER_BINDER=userspace
// (=kernel: never), else the instance setting, a file <root>/binder that says kernel (or anything else: userspace), else userspace, the default
// since 0.4.9. No binder devices are needed then.
bool userspaceBinder(const std::string& instanceRoot);
std::string binderChoiceFile(const std::string& instanceRoot);
// apklayer binder [userspace|kernel]: the instance's choice (from its next start), or rows: binder <kind> <environment|instance|default>,
// kernel ready | kernel missing <the command that makes the devices>
struct Instance;
// The binder of the instance's running stack (what it recorded at its start, recordStackBinder: a choice changed since applies from its next
// start, and a stack an older apklayer started is on the kernel's), else userspaceBinder: what its processes must use
bool stackUserspaceBinder(const Instance& inst);
void recordStackBinder(const Instance& inst);
int binderCommand(const Instance& inst, const std::vector<std::string>& args);
// The instance can run a stack: its userspace binder, or its kernel binder device exists.
bool binderReady(const std::string& instanceRoot, const std::string& id);
// The directory of the instances' userspace binder sockets (<id>.sock): $XDG_RUNTIME_DIR/apklayer-binder, which the route (bridge/native/binder_route.c)
// gets as APKLAYER_BINDER_DIR.
std::string binderSocketDir(const std::string& runtimeHome);
// Starts the instance's binder daemon unless one listens: the socket is bound here under <id>.lock and handed over (the daemon ends a few
// seconds after its last client, under the same lock).
void ensureBinderDaemon(const std::string& runtimeHome, const std::string& native, const std::string& id, const std::string& logFile);
// The machine's own binder ("binder", "hwbinder" or "vndbinder"): binderfs's, else /dev's; "" when there is none.
std::string hostBinder(const std::string& family);

}  // namespace apklayer
