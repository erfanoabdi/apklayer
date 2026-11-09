// The service manager's access policy for apklayer (PLAN P7.1): allow everything. Android decides who may add and find services with
// SELinux and the kernel's per-transaction security context; neither exists on a desktop host (the kernel's security module is
// AppArmor, which cannot supply an SELinux context), and the host's own isolation (bwrap, P6) is the boundary. The tree's
// ServiceManager logic is used unchanged; only this policy layer is ours. Same interface as cmds/servicemanager/Access.h.
#include "Access.h"

#include <binder/IPCThreadState.h>

#include <cstdlib>
#include <fcntl.h>
#include <set>
#include <sstream>
#include <unistd.h>

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

// Services the stack starts on use (cli/stack.cpp, APKLAYER_LAZY_NAMES): each lookup of their names is written to the stack's FIFO, whatever the
// call (get, check, wait, isDeclared); the stack starts the service unless it runs, and the client waits as on a device (waitForService and
// getService's polling look again, which also covers a lazy service that was just ending). Single-threaded: the service manager's looper makes every call.
static void announce(const std::string& name) {
    static const char* fifo = getenv("APKLAYER_LAZY_FIFO");
    static std::set<std::string> names = [] {
        std::set<std::string> out;
        std::istringstream list(getenv("APKLAYER_LAZY_NAMES") ? getenv("APKLAYER_LAZY_NAMES") : "");
        for (std::string n; list >> n;) out.insert(n);
        return out;
    }();
    static int fd = -1;
    if (!fifo || !names.count(name)) return;
    if (fd < 0) fd = open(fifo, O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    std::string line = name + "\n";
    if (fd >= 0) (void)!write(fd, line.data(), line.size());
}

bool Access::canFind(const CallingContext&, const std::string& name) { announce(name); return true; }
bool Access::canAdd(const CallingContext&, const std::string&) { return true; }
bool Access::canList(const CallingContext&) { return true; }

bool Access::actionAllowed(const CallingContext&, const char*, const char*, const std::string&) { return true; }
bool Access::actionAllowedFromLookup(const CallingContext&, const std::string&, const char*) { return true; }

}  // namespace android
