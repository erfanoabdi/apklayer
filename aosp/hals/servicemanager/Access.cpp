// The service manager's access policy for apklayer (PLAN P7.1): allow everything. Android decides who may add and find services with
// SELinux and the kernel's per-transaction security context; neither exists on a desktop host (the kernel's security module is
// AppArmor, which cannot supply an SELinux context), and the host's own isolation (bwrap, P6) is the boundary. The tree's
// ServiceManager logic is used unchanged; only this policy layer is ours. Same interface as cmds/servicemanager/Access.h.
#include "Access.h"

#include <binder/IPCThreadState.h>

#include <sstream>

namespace android {

Access::Access() {}
Access::~Access() {}

std::string Access::CallingContext::toDebugString() const {
    std::stringstream ss;
    ss << "Caller(pid=" << debugPid << ",uid=" << uid << ")";
    return ss.str();
}

Access::CallingContext Access::getCallingContext() {
    IPCThreadState* ipc = IPCThreadState::self();
    return CallingContext{.debugPid = ipc->getCallingPid(), .uid = ipc->getCallingUid(), .sid = ""};
}

bool Access::canFind(const CallingContext&, const std::string&) { return true; }
bool Access::canAdd(const CallingContext&, const std::string&) { return true; }
bool Access::canList(const CallingContext&) { return true; }

bool Access::actionAllowed(const CallingContext&, const char*, const char*, const std::string&) { return true; }
bool Access::actionAllowedFromLookup(const CallingContext&, const std::string&, const char*) { return true; }

}  // namespace android
