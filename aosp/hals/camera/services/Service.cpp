// frameworks/av/camera/cameraserver/main_cameraserver.cpp as a btload entry point: CameraService finds the camera providers (aosp/hals/camera)
// declared in the instance's VINTF manifests and serves "media.camera" to the apps' CameraManager and android.hardware.Camera.
// With it "media.camera.proxy", the part of system_server's CameraServiceProxy that CameraService asks (apklayer runs no system_server):
// without it every open is refused ("Camera disabled by device policy"), and it carries the apps' window shapes to open cameras; and "processinfo" (ActivityManager's process states, which a
// connect needs to rank camera clients).
#include <android/hardware/BnCameraServiceProxy.h>
#include <android/os/BnProcessInfoService.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/LazyServiceRegistrar.h>
#include <unistd.h>
#include <hidl/HidlTransportSupport.h>
#include <map>
#include <mutex>

#include "CameraService.h"

using namespace android;

namespace {
// The answers of CameraServiceProxy.java for an app without overrides: no policy disables the camera, no autoframing override; the
// notifications (session statistics, watchdog) have no listener here. Rotate-and-crop follows each app's window shape (WINDOW_SHAPE).
class CameraServiceProxy : public hardware::BnCameraServiceProxy, public IBinder::DeathRecipient {
  public:
    // apklayer's own call (not in ICameraServiceProxy.aidl), from an app process whose window turned between portrait and landscape
    // (HostCameraShadows.windowShape): package, rotate-and-crop rotation in degrees (0 or 90), the process's token. On a device WindowManager
    // tells system_server's CameraServiceProxy, which has cameraserver ask getRotateAndCropOverride again for each open camera
    // (notifyDisplayConfigurationChange): the open cameras' requests follow without a reopen.
    static constexpr uint32_t WINDOW_SHAPE = IBinder::FIRST_CALL_TRANSACTION + 0x1000;
    sp<hardware::ICameraService> service;

    status_t onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags) override {
        if (code != WINDOW_SHAPE) return BnCameraServiceProxy::onTransact(code, data, reply, flags);
        if (!data.enforceInterface(getInterfaceDescriptor())) return PERMISSION_DENIED;
        std::string package = String8(data.readString16()).c_str();
        int32_t degrees = data.readInt32();
        sp<IBinder> token = data.readStrongBinder();
        if (package.empty() || token == nullptr) return BAD_VALUE;
        {
            std::lock_guard<std::mutex> l(mLock);
            Window& entry = mWindows[package];
            if (entry.token != token && token->linkToDeath(sp<IBinder::DeathRecipient>::fromExisting(this)) != OK) {
                mWindows.erase(package);
                return DEAD_OBJECT;
            }
            entry = {degrees, token};
        }
        IPCThreadState::self()->clearCallingIdentity();  // cameraserver's own call (system_server's is allowed)
        if (service != nullptr) (void)service->notifyDisplayConfigurationChange();
        return OK;
    }
    void binderDied(const wp<IBinder>& who) override {  // the app process ended: a later process starts from its own window's shape
        std::lock_guard<std::mutex> l(mLock);
        for (auto it = mWindows.begin(); it != mWindows.end();) it = it->second.token.get() == who.unsafe_get() ? mWindows.erase(it) : std::next(it);
    }

    binder::Status pingForUserUpdate() override { return binder::Status::ok(); }
    binder::Status notifyCameraState(const hardware::CameraSessionStats&) override { return binder::Status::ok(); }
    binder::Status notifyFeatureCombinationStats(const hardware::CameraFeatureCombinationStats&) override { return binder::Status::ok(); }
    // CameraProviderManager's portrait rotation for a sensor at 90/270 (getCameraInfo): the rotation for a back camera, 360 minus it for a
    // front one (lensFacing is hardware::CAMERA_FACING_*, the client's facing). Asked at an open outside the camera compat info and after WINDOW_SHAPE.
    binder::Status getRotateAndCropOverride(const std::string& package, int32_t lensFacing, int32_t, int32_t* out) override {
        std::lock_guard<std::mutex> l(mLock);
        auto it = mWindows.find(package);
        int degrees = it == mWindows.end() ? 0 : it->second.degrees;
        if (lensFacing == hardware::CAMERA_FACING_FRONT) degrees = (360 - degrees) % 360;
        *out = degrees == 90 ? ANDROID_SCALER_ROTATE_AND_CROP_90 : degrees == 180 ? ANDROID_SCALER_ROTATE_AND_CROP_180
             : degrees == 270 ? ANDROID_SCALER_ROTATE_AND_CROP_270 : ANDROID_SCALER_ROTATE_AND_CROP_NONE;
        return binder::Status::ok();
    }
    binder::Status getAutoframingOverride(const std::string&, int32_t* out) override {
        *out = 0;  // AUTOFRAMING_OFF
        return binder::Status::ok();
    }
    binder::Status isCameraDisabled(int32_t, bool* out) override {
        *out = false;
        return binder::Status::ok();
    }
    binder::Status notifyWatchdog(int32_t, bool) override { return binder::Status::ok(); }
    binder::Status notifyCameraDistractionRestriction(hardware::camera2::ICameraDeviceUser::AudioRestriction) override { return binder::Status::ok(); }

  private:
    struct Window { int32_t degrees = 0; sp<IBinder> token; };
    std::mutex mLock;
    std::map<std::string, Window> mWindows;  // by package: the last shape its process reported
};

// Every client counts as a foreground app (PROCESS_STATE_TOP, oom score 0): the instance's apps are the user's open windows.
class ProcessInfo : public os::BnProcessInfoService {
  public:
    binder::Status getProcessStatesFromPids(const std::vector<int32_t>& pids, std::vector<int32_t>* states) override {
        states->assign(pids.size(), 2);
        return binder::Status::ok();
    }
    binder::Status getProcessStatesAndOomScoresFromPids(const std::vector<int32_t>& pids, std::vector<int32_t>* states,
                                                        std::vector<int32_t>* scores) override {
        states->assign(pids.size(), 2);
        scores->assign(pids.size(), 0);
        return binder::Status::ok();
    }
};
}  // namespace

extern "C" int main(int, char**) {
    signal(SIGPIPE, SIG_IGN);
    hardware::configureRpcThreadpool(5, /*willjoin*/ false);
    sp<ProcessState> proc(ProcessState::self());
    auto cameraProxy = sp<CameraServiceProxy>::make();
    defaultServiceManager()->addService(String16("media.camera.proxy"), cameraProxy);
    if (defaultServiceManager()->checkService(String16("processinfo")) == nullptr)
        defaultServiceManager()->addService(String16("processinfo"), sp<ProcessInfo>::make());
    CameraService::instantiate();
    // the foreground user, as CameraServiceProxy.java reports it at boot and on user switches: apps of other users are refused
    auto service = interface_cast<hardware::ICameraService>(defaultServiceManager()->checkService(String16("media.camera")));
    if (service == nullptr || !service->notifySystemEvent(hardware::ICameraService::EVENT_USER_SWITCHED, {0}).isOk()) return 1;
    cameraProxy->service = service;
    fprintf(stderr, "cameraserver: media.camera registered\n");
    // started on first use (cli/stack.cpp): registered again with client counting, the process exits once no client holds any of its services
    // (an AOSP lazy service) and the stack starts it again on the next lookup. _exit, not the registrar's exit(): static destructors under
    // btload abort while binder threads still run.
    if (getenv("APKLAYER_LAZY_SERVICE")) {
        auto& registrar = binder::LazyServiceRegistrar::getInstance();
        registrar.setActiveServicesCallback([&registrar](bool clients) {
            if (!clients && registrar.tryUnregister()) _exit(0);
            if (!clients) registrar.reRegister();
            return true;
        });
        for (const char* name : {"media.camera", "media.camera.proxy", "processinfo"})
            if (auto b = defaultServiceManager()->checkService(String16(name)); b != nullptr && b->localBinder() != nullptr) {
                (void)registrar.registerService(b, name);
                (void)defaultServiceManager()->checkService(String16(name));  // the client guarantee, as in MediaServices.cpp
            }
    }
    ProcessState::self()->startThreadPool();
    IPCThreadState::self()->joinThreadPool();
    return 1;
}
