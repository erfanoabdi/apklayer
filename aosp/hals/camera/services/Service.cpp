// frameworks/av/camera/cameraserver/main_cameraserver.cpp as a btload entry point: CameraService finds the camera providers (aosp/hals/camera)
// declared in the instance's VINTF manifests and serves "media.camera" to the apps' CameraManager and android.hardware.Camera.
#include "CameraService.h"
#include <hidl/HidlTransportSupport.h>

using namespace android;

extern "C" int main(int, char**) {
    signal(SIGPIPE, SIG_IGN);
    hardware::configureRpcThreadpool(5, /*willjoin*/ false);
    sp<ProcessState> proc(ProcessState::self());
    CameraService::instantiate();
    fprintf(stderr, "cameraserver: media.camera registered\n");
    ProcessState::self()->startThreadPool();
    IPCThreadState::self()->joinThreadPool();
    return 1;
}
