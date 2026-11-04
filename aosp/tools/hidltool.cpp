// hidltool: HIDL through the host hwbinder context (docs/HALIUM.md).
//   device              plays the device in the two-instance test: becomes this instance's hwbinder context manager (a minimal
//                       hwservicemanager that serves one android.hardware.vibrator@1.0::IVibrator/default) and stays alive
//   vibrator [INSTANCE] IVibrator@1.0: supportsAmplitudeControl, a 50 ms on()
//   cameras [INSTANCE]  ICameraProvider@2.4 (default instance internal/0): the camera ids (getCameraIdList only, nothing opened)
//   codecs [INSTANCE]   media.c2@1.0 IComponentStore (default "default"): the component names (listComponents only)
// The client commands find the HAL like any HIDL client (IFoo::getService); libhidl asks the host for names in host_hals.conf.
#include <android/hardware/camera/provider/2.4/ICameraProvider.h>
#include <android/hardware/media/c2/1.0/IComponentStore.h>
#include <android/hardware/vibrator/1.0/IVibrator.h>
#include <android/hidl/manager/1.0/IServiceManager.h>
#include <hidl/HidlBinderSupport.h>
#include <hidl/HidlTransportSupport.h>
#include <hwbinder/Binder.h>
#include <hwbinder/IPCThreadState.h>
#include <hwbinder/ProcessState.h>
#include <stdio.h>
#include <string.h>

using android::sp;
using android::hardware::hidl_string;
using android::hardware::hidl_vec;
using android::hardware::Return;
using android::hardware::Void;
namespace V1_0 = android::hardware::vibrator::V1_0;
using android::hidl::base::V1_0::IBase;
using IServiceManager = android::hidl::manager::V1_0::IServiceManager;

namespace {
struct TestVibrator : V1_0::IVibrator {
    Return<V1_0::Status> on(uint32_t ms) override { printf("device: on(%u)\n", ms); fflush(stdout); return V1_0::Status::OK; }
    Return<V1_0::Status> off() override { return V1_0::Status::OK; }
    Return<bool> supportsAmplitudeControl() override { return true; }
    Return<V1_0::Status> setAmplitude(uint8_t) override { return V1_0::Status::OK; }
    Return<void> perform(V1_0::Effect, V1_0::EffectStrength, perform_cb cb) override { cb(V1_0::Status::UNSUPPORTED_OPERATION, 0); return Void(); }
};

// What a host's hwservicemanager does for a client's getService: get() of one registered service; the rest is not needed here
struct TestManager : IServiceManager {
    sp<IBase> vibrator = new TestVibrator();
    Return<sp<IBase>> get(const hidl_string& fqName, const hidl_string& name) override {
        bool ours = fqName == V1_0::IVibrator::descriptor && name == "default";
        printf("device: get %s/%s: %s\n", fqName.c_str(), name.c_str(), ours ? "served" : "none");
        fflush(stdout);
        return ours ? vibrator : nullptr;
    }
    Return<bool> add(const hidl_string&, const sp<IBase>&) override { return false; }
    Return<Transport> getTransport(const hidl_string&, const hidl_string&) override { return Transport::HWBINDER; }
    Return<void> list(list_cb cb) override { cb({}); return Void(); }
    Return<void> listByInterface(const hidl_string&, listByInterface_cb cb) override { cb({}); return Void(); }
    Return<bool> registerForNotifications(const hidl_string&, const hidl_string&, const sp<android::hidl::manager::V1_0::IServiceNotification>&) override { return false; }
    Return<void> debugDump(debugDump_cb cb) override { cb({}); return Void(); }
    Return<void> registerPassthroughClient(const hidl_string&, const hidl_string&) override { return Void(); }
};
}  // namespace

extern "C" int main(int argc, char** argv) {
    const char* cmd = argc > 1 ? argv[1] : "";
    if (!strcmp(cmd, "device")) {
        sp<TestManager> manager = new TestManager();
        sp<android::hardware::IBinder> binder = android::hardware::toBinder<IServiceManager>(manager);
        // as hwservicemanager's main: the local object first, then the kernel
        android::hardware::IPCThreadState::self()->setTheContextObject(static_cast<android::hardware::BHwBinder*>(binder.get()));
        android::hardware::ProcessState::self()->becomeContextManager();
        printf("device: ready\n");
        fflush(stdout);
        android::hardware::IPCThreadState::self()->joinThreadPool();
        return 0;
    }
    if (!strcmp(cmd, "vibrator")) {
        sp<V1_0::IVibrator> v = V1_0::IVibrator::getService(argc > 2 ? argv[2] : "default");
        if (v == nullptr) { printf("vibrator: not found\n"); return 1; }
        Return<bool> amplitude = v->supportsAmplitudeControl();
        Return<V1_0::Status> on = v->on(50);
        printf("vibrator: supportsAmplitudeControl %s, on(50) %s\n", amplitude.isOk() ? (amplitude ? "true" : "false") : "failed",
               on.isOk() ? V1_0::toString(static_cast<V1_0::Status>(on)).c_str() : "failed");
        return amplitude.isOk() && on.isOk() && on == V1_0::Status::OK ? 0 : 1;
    }
    if (!strcmp(cmd, "cameras")) {
        using android::hardware::camera::provider::V2_4::ICameraProvider;
        sp<ICameraProvider> p = ICameraProvider::getService(argc > 2 ? argv[2] : "internal/0");
        if (p == nullptr) { printf("cameras: no provider\n"); return 1; }
        bool ok = false;
        Return<void> r = p->getCameraIdList([&](auto status, const hidl_vec<hidl_string>& ids) {
            ok = status == android::hardware::camera::common::V1_0::Status::OK;
            printf("cameras: %zu:", ids.size());
            for (const auto& id : ids) printf(" %s", id.c_str());
            printf("\n");
        });
        return r.isOk() && ok ? 0 : 1;
    }
    if (!strcmp(cmd, "codecs")) {
        using android::hardware::media::c2::V1_0::IComponentStore;
        sp<IComponentStore> s = IComponentStore::getService(argc > 2 ? argv[2] : "default");
        if (s == nullptr) { printf("codecs: no store\n"); return 1; }
        size_t n = 0;
        Return<void> r = s->listComponents([&](auto, const auto& traits) {
            n = traits.size();
            for (const auto& t : traits) printf("codec: %s %s\n", t.name.c_str(), t.mediaType.c_str());
        });
        printf("codecs: %zu\n", n);
        return r.isOk() && n > 0 ? 0 : 1;
    }
    fprintf(stderr, "usage: hidltool device | vibrator [INSTANCE] | cameras [INSTANCE] | codecs [INSTANCE]\n");
    return 2;
}
