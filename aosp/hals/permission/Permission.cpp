// apklayer's "permission" service: Android's PermissionController (system_server, which apklayer does not run) answers
// libbinder's checkPermission() for every service that asks "may uid X do Y" (SurfaceFlinger, audio, ...). Without a service
// of that name libbinder waits for it ("Waiting to check permission ...") and then refuses. The instance is a single user's
// phone with the app's own permissions handled by the Java side, so everything is allowed.
#include <binder/IPCThreadState.h>
#include <binder/IPermissionController.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <utils/String16.h>
#include <utils/Vector.h>

#include <stdio.h>

using namespace android;

namespace {

class AllowAll : public BnPermissionController {
  public:
    bool checkPermission(const String16&, int32_t, int32_t) override { return true; }
    int32_t noteOp(const String16&, int32_t, const String16&) override { return 0; }  // AppOpsManager.MODE_ALLOWED
    void getPackagesForUid(const uid_t, Vector<String16>& packages) override { packages.clear(); }
    bool isRuntimePermission(const String16&) override { return false; }
    int getPackageUid(const String16&, int) override { return -1; }
};

}  // namespace

extern "C" int main(int, char**) {
    ProcessState::self()->startThreadPool();
    sp<IServiceManager> sm = defaultServiceManager();
    if (sm == nullptr) { fprintf(stderr, "permission: no service manager\n"); return 1; }
    status_t st = sm->addService(String16("permission"), sp<AllowAll>::make());
    if (st != OK) { fprintf(stderr, "permission: addService failed (%d)\n", st); return 1; }
    IPCThreadState::self()->joinThreadPool();
    return 0;
}
