// frameworks/av/camera/cameraserver/main_cameraserver.cpp as a btload entry point: CameraService finds the camera providers (aosp/hals/camera)
// declared in the instance's VINTF manifests and serves "media.camera" to the apps' CameraManager and android.hardware.Camera.
// With it "media.camera.proxy", the part of system_server's CameraServiceProxy that CameraService asks (apklayer runs no system_server):
// without it every open is refused ("Camera disabled by device policy"); and "processinfo" (ActivityManager's process states, which a
// connect needs to rank camera clients).
#include <android/hardware/BnCameraServiceProxy.h>
#include <android/os/BnProcessInfoService.h>
#include <binder/IServiceManager.h>
#include <binder/LazyServiceRegistrar.h>
#include <unistd.h>
#include <hidl/HidlTransportSupport.h>

#include "CameraService.h"

using namespace android;

namespace {
// The answers of CameraServiceProxy.java for an app without overrides: no policy disables the camera, no rotate-and-crop or autoframing
// override; the notifications (session statistics, watchdog) have no listener here.
class CameraServiceProxy : public hardware::BnCameraServiceProxy {
  public:
    binder::Status pingForUserUpdate() override { return binder::Status::ok(); }
    binder::Status notifyCameraState(const hardware::CameraSessionStats&) override { return binder::Status::ok(); }
    binder::Status notifyFeatureCombinationStats(const hardware::CameraFeatureCombinationStats&) override { return binder::Status::ok(); }
    binder::Status getRotateAndCropOverride(const std::string&, int32_t, int32_t, int32_t* out) override {
        *out = 0;  // ROTATE_AND_CROP_NONE
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
    defaultServiceManager()->addService(String16("media.camera.proxy"), sp<CameraServiceProxy>::make());
    if (defaultServiceManager()->checkService(String16("processinfo")) == nullptr)
        defaultServiceManager()->addService(String16("processinfo"), sp<ProcessInfo>::make());
    CameraService::instantiate();
    // the foreground user, as CameraServiceProxy.java reports it at boot and on user switches: apps of other users are refused
    auto service = interface_cast<hardware::ICameraService>(defaultServiceManager()->checkService(String16("media.camera")));
    if (service == nullptr || !service->notifySystemEvent(hardware::ICameraService::EVENT_USER_SWITCHED, {0}).isOk()) return 1;
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
