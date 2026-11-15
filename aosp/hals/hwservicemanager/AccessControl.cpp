// hwservicemanager's access policy for apklayer: allow everything, as aosp/hals/servicemanager/Access.cpp does for the AIDL service manager
// (SELinux, which Android uses to decide who may add and find a HAL, does not exist on a desktop host). The tree's ServiceManager logic is used
// unchanged; same interface as system/hwservicemanager/AccessControl.h (its SELinux members stay unused).
#include "AccessControl.h"

namespace android {

AccessControl::AccessControl() : mSeContext(nullptr), mSeHandle(nullptr), mSeCallbacks() {}

bool AccessControl::canAdd(const std::string&, const CallingContext&) { return true; }
bool AccessControl::canGet(const std::string&, const CallingContext&) { return true; }
bool AccessControl::canList(const CallingContext&) { return true; }

AccessControl::CallingContext AccessControl::getCallingContext(pid_t sourcePid) { return {true, "", sourcePid}; }

}  // namespace android
