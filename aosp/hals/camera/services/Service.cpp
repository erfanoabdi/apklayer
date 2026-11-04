// frameworks/av/camera/cameraserver/main_cameraserver.cpp as a btload entry point: CameraService finds the camera providers (aosp/hals/camera)
// declared in the instance's VINTF manifests and serves "media.camera" to the apps' CameraManager and android.hardware.Camera.
// With it "media.camera.proxy", the part of system_server's CameraServiceProxy that CameraService asks (apklayer runs no system_server):
// without it every open is refused ("Camera disabled by device policy").
#include <android/hardware/BnCameraServiceProxy.h>
#include <binder/IServiceManager.h>
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
}  // namespace

extern "C" int main(int, char**) {
    signal(SIGPIPE, SIG_IGN);
    hardware::configureRpcThreadpool(5, /*willjoin*/ false);
    sp<ProcessState> proc(ProcessState::self());
    defaultServiceManager()->addService(String16("media.camera.proxy"), sp<CameraServiceProxy>::make());
    CameraService::instantiate();
    fprintf(stderr, "cameraserver: media.camera registered\n");
    ProcessState::self()->startThreadPool();
    IPCThreadState::self()->joinThreadPool();
    return 1;
}
