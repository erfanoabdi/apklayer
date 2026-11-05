// apklayer's camera provider: Goldfish's AIDL ICameraProvider (goldfish/, registered as internal/1 by its service_entry.cpp) with one
// BackendCamera per camera of the instance's backend (bridge/hals/camera: libapklayer-camera-<backend>.so, loaded by btload through
// APKLAYER_HOST_LIBS). Arguments: the backend's options (key=value). Replaces Goldfish's main.cpp, which lists QEMU and GL-rendered cameras.
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>

#include <apklayer/camera.h>
#include <log/log.h>

#include "BackendCamera.h"
#include "goldfish/service_entry.h"

// weak: bionic's linker binds every symbol at load, and the backend is a host library loaded beside this one
#pragma weak apklayer_camera_init
#pragma weak apklayer_camera_list
#pragma weak apklayer_camera_open
#pragma weak apklayer_camera_close
#pragma weak apklayer_camera_start
#pragma weak apklayer_camera_stop

namespace {
using namespace android::hardware::camera::provider::implementation;
using hw::BackendCamera;

// JPEG thumbnail sizes for a frame size: Goldfish's list_qemu_cameras.cpp rule (about 19500 pixels, a multiple of 16 high, even width)
Rect<uint16_t> thumbnail(Rect<uint16_t> res) {
    const double aspect = double(res.width) / res.height;
    const uint16_t height = ((uint16_t(std::sqrt(19500 / aspect)) + 7) >> 4) << 4;
    return {uint16_t((uint16_t(height * aspect + 1) >> 1) << 1), height};
}

bool byArea(Rect<uint16_t> a, Rect<uint16_t> b) { return a.area() != b.area() ? a.area() < b.area() : a.width < b.width; }

// The sizes the camera itself streams (the backend's list), smallest first.
std::vector<Rect<uint16_t>> nativeSizes(const apklayer_camera_info& info) {
    std::vector<Rect<uint16_t>> sizes;
    for (int i = 0; i < info.num_sizes && i < APKLAYER_CAMERA_MAX_SIZES; i++) sizes.push_back({uint16_t(info.sizes[i][0]), uint16_t(info.sizes[i][1])});
    std::sort(sizes.begin(), sizes.end(), byArea);
    return sizes;
}

hw::BaseQemuCamera::Parameters parameters(const apklayer_camera_info& info) {
    hw::BaseQemuCamera::Parameters p;
    p.name = info.name;
    const std::vector<Rect<uint16_t>> native = nativeSizes(info);
    // the sensor covers every stream: the largest width and the largest height (Goldfish's list_qemu_cameras.cpp rule)
    p.sensorSize = native.front();
    for (Rect<uint16_t> r : native) p.sensorSize = {std::max(p.sensorSize.width, r.width), std::max(p.sensorSize.height, r.height)};
    // Besides the camera's own sizes, the common smaller ones (BackendCamera scales every stream from a native frame), as a phone HAL lists
    // them: Camera1 takes its video sizes from the encoders' range (MediaProfiles' defaults: 176x144 to 352x288) and refuses a camera with
    // none of them ("generated preview size list is empty"); apps look for 4:3 and 16:9 sizes.
    static const Rect<uint16_t> common[] = {{176, 144}, {320, 240}, {352, 288}, {640, 360}, {640, 480}, {1280, 720}, {1920, 1080}};
    p.supportedResolutions = native;
    for (Rect<uint16_t> r : common)
        if (r.width <= p.sensorSize.width && r.height <= p.sensorSize.height) p.supportedResolutions.push_back(r);
    std::sort(p.supportedResolutions.begin(), p.supportedResolutions.end(), byArea);
    p.supportedResolutions.erase(std::unique(p.supportedResolutions.begin(), p.supportedResolutions.end()), p.supportedResolutions.end());
    p.availableThumbnailResolutions.push_back({0, 0});
    for (Rect<uint16_t> r : p.supportedResolutions)
        if (thumbnail(r).area() < r.area() / 4) p.availableThumbnailResolutions.push_back(thumbnail(r));
    std::sort(p.availableThumbnailResolutions.begin(), p.availableThumbnailResolutions.end(), byArea);
    p.availableThumbnailResolutions.erase(std::unique(p.availableThumbnailResolutions.begin(), p.availableThumbnailResolutions.end()),
                                          p.availableThumbnailResolutions.end());
    p.isBackFacing = info.facing != APKLAYER_CAM_FRONT;
    p.sensorOrientation = info.orientation;
    return p;
}
}  // namespace

extern "C" int main(int argc, char** argv) {
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IOLBF, 0);
    if (!apklayer_camera_init) { fprintf(stderr, "camera: no backend library (APKLAYER_HOST_LIBS)\n"); return 1; }
    if (int error = apklayer_camera_init(argc - 1, argv + 1)) { fprintf(stderr, "camera backend: %s\n", strerror(-error)); return 1; }
    apklayer_camera_info infos[8];
    const int n = apklayer_camera_list(infos, 8);
    std::vector<hw::HwCameraFactory> cameras;
    for (int i = 0; i < n; i++) {
        if (infos[i].num_sizes < 1) continue;
        fprintf(stderr, "camera %zu: %s (%s), %d sizes up to %ux%u\n", cameras.size(), infos[i].name, infos[i].id, infos[i].num_sizes,
                infos[i].sizes[infos[i].num_sizes - 1][0], infos[i].sizes[infos[i].num_sizes - 1][1]);
        cameras.push_back([params = parameters(infos[i]), id = std::string(infos[i].id), native = nativeSizes(infos[i])]() {
            return std::make_unique<BackendCamera>(params, id, native);
        });
    }
    if (n < 0) fprintf(stderr, "camera backend: no cameras: %s\n", strerror(-n));
    // camera ids from 0, as on a phone (Camera1 apps open camera 0)
    return serviceEntry(0, Span<const hw::HwCameraFactory>(cameras.data(), cameras.data() + cameras.size()), 4);
}
