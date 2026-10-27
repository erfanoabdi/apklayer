// apklayer's "permission" service: Android's PermissionController (system_server, which apklayer does not run) answers
// libbinder's checkPermission() for every service that asks "may uid X do Y" (SurfaceFlinger, audio, ...). Without a service
// of that name libbinder waits for it ("Waiting to check permission ...") and then refuses. The instance is a single user's
// phone with the app's own permissions handled by the Java side, so everything is allowed.
#include <binder/Binder.h>
#include <binder/IPCThreadState.h>
#include <binder/Parcel.h>
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

// "package_native": the native side of PackageManager (IPackageManagerNative). MediaCodec asks it for system features when it creates an encoder
// (handheld or not) and waits for the service forever otherwise. A raw BBinder, so the module needs no AIDL-generated code: the transaction
// numbers are the order of the methods in IPackageManagerNative.aidl (hasSystemFeature 13, checkPermission 4, getTargetSdkVersionForPackage 9).
class PackageNative : public BBinder {
  public:
    const String16& getInterfaceDescriptor() const override {
        static const String16 descriptor("android.content.pm.IPackageManagerNative");
        return descriptor;
    }
    status_t onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags) override {
        const uint32_t base = FIRST_CALL_TRANSACTION;
        if (code == base + 13) {  // hasSystemFeature(String featureName, int version): a phone with a touchscreen
            if (!data.enforceInterface(getInterfaceDescriptor())) return PERMISSION_DENIED;
            reply->writeNoException();
            reply->writeBool(true);
            return OK;
        }
        if (code == base + 4) {  // checkPermission: granted
            if (!data.enforceInterface(getInterfaceDescriptor())) return PERMISSION_DENIED;
            reply->writeNoException();
            reply->writeInt32(0);
            return OK;
        }
        if (code == base + 9) {  // getTargetSdkVersionForPackage
            if (!data.enforceInterface(getInterfaceDescriptor())) return PERMISSION_DENIED;
            reply->writeNoException();
            reply->writeInt32(36);
            return OK;
        }
        return BBinder::onTransact(code, data, reply, flags);
    }
};

}  // namespace

extern "C" int main(int, char**) {
    ProcessState::self()->startThreadPool();
    sp<IServiceManager> sm = defaultServiceManager();
    if (sm == nullptr) { fprintf(stderr, "permission: no service manager\n"); return 1; }
    status_t st = sm->addService(String16("permission"), sp<AllowAll>::make());
    if (st != OK) { fprintf(stderr, "permission: addService failed (%d)\n", st); return 1; }
    st = sm->addService(String16("package_native"), sp<PackageNative>::make());
    if (st != OK) { fprintf(stderr, "permission: addService package_native failed (%d)\n", st); return 1; }
    IPCThreadState::self()->joinThreadPool();
    return 0;
}
