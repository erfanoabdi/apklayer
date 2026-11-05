// graltool: the device's gralloc through the host hwbinder context, shown through the shell (docs/HALIUM.md, plan step (e)).
//   show W H SECONDS [RRGGBB]   allocates one RGBA_8888 buffer from the device's allocator@4.0 (host_hals.conf lists
//                               android.hardware.graphics.allocator) with a descriptor from the device's own mapper (vendors
//                               encode descriptors their way: MediaTek's refuses AOSP's encoding), fills it through that mapper's lock,
//                               opens a display window of the shell and presents it as a gralloc frame (android_wlegl), then
//                               keeps the window SECONDS seconds and exits. Proves allocation and presentation without the GPU.
#include <android/hardware/graphics/allocator/4.0/IAllocator.h>
#include <android/hardware/graphics/mapper/4.0/IMapper.h>
#include <android/hidl/manager/1.0/BpHwServiceManager.h>
#include <android/hidl/manager/1.0/BnHwServiceManager.h>
#include <hidl/HidlBinderSupport.h>
#include <hwbinder/ProcessState.h>
#include <cutils/native_handle.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#include "apklayer/display.h"

using android::sp;
using android::hardware::hidl_handle;
using android::hardware::hidl_vec;
using android::hardware::graphics::allocator::V4_0::IAllocator;
using android::hardware::graphics::mapper::V4_0::Error;
using android::hardware::graphics::mapper::V4_0::IMapper;
using android::hardware::graphics::common::V1_2::BufferUsage;
using android::hardware::graphics::common::V1_2::PixelFormat;

namespace {
void configure(void*, int32_t w, int32_t h, int32_t scale) { printf("graltool: window %dx%d scale %d%%\n", w, h, scale); fflush(stdout); }
void vsync(void*, int64_t) {}
void refresh(void*, uint32_t mhz) { printf("graltool: refresh %u mHz\n", mhz); fflush(stdout); }
void released(void*, int slot) { printf("graltool: released %d\n", slot); fflush(stdout); }
}  // namespace

extern "C" int main(int argc, char** argv) {
    if (argc < 5 || strcmp(argv[1], "show")) { fprintf(stderr, "usage: graltool show W H SECONDS [RRGGBB]\n"); return 2; }
    uint32_t w = atoi(argv[2]), h = atoi(argv[3]), seconds = atoi(argv[4]);
    uint32_t rgb = argc > 5 ? strtoul(argv[5], nullptr, 16) : 0x2196f3;
    // GRALTOOL_FIRST: what happens before the allocator lookup. getservice: libui's order (Gralloc4Mapper before Gralloc4Allocator),
    // the mapper through libhidl (with both HALs in host_hals.conf: asked on the host, then loaded as passthrough), kept as libui keeps
    // it; hostmiss: only the host's hwservicemanager asked for the mapper (it has none), the proxy dropped; dlopen: only the vendor
    // mapper loaded (HIDL_FETCH_IMapper)
    const char* first = getenv("GRALTOOL_FIRST") ? getenv("GRALTOOL_FIRST") : "";
    sp<IMapper> earlyMapper;
    if (!strcmp(first, "getservice")) earlyMapper = IMapper::getService();
    if (!strcmp(first, "hostmiss")) {
        using android::hidl::manager::V1_0::IServiceManager;
        sp<IServiceManager> sm = android::hardware::fromBinder<IServiceManager, android::hidl::manager::V1_0::BpHwServiceManager,
                                                                android::hidl::manager::V1_0::BnHwServiceManager>(
                android::hardware::ProcessState::self(true)->getContextObject(nullptr));
        printf("graltool: host mapper %s\n", sm != nullptr && sm->get(IMapper::descriptor, "default").withDefault(nullptr) ? "found" : "missing");
    }
    if (!strcmp(first, "dlopen")) {
        void* lib = dlopen("/vendor/lib64/hw/android.hardware.graphics.mapper@4.0-impl-mediatek.so", RTLD_NOW);
        auto fetch = lib ? reinterpret_cast<IMapper* (*)(const char*)>(dlsym(lib, "HIDL_FETCH_IMapper")) : nullptr;
        earlyMapper = fetch ? fetch("default") : nullptr;
    }
    if (*first) { printf("graltool: first %s: mapper %s\n", first, earlyMapper ? "ok" : "null"); fflush(stdout); }
    sp<IAllocator> allocator = IAllocator::getService();
    if (allocator == nullptr) { printf("graltool: no allocator@4.0\n"); return 1; }
    uint64_t usage = static_cast<uint64_t>(BufferUsage::CPU_WRITE_OFTEN | BufferUsage::CPU_READ_OFTEN | BufferUsage::GPU_TEXTURE |
                                           BufferUsage::COMPOSER_OVERLAY);
    if (const char* u = getenv("GRALTOOL_USAGE")) usage = strtoull(u, nullptr, 16);  // to find what the device's allocator accepts
    IMapper::BufferDescriptorInfo info{"apklayer-graltool", w, h, 1, static_cast<PixelFormat>(PixelFormat::RGBA_8888), usage, 0};
    // the device's mapper is a passthrough HAL: loaded here from /vendor (HIDL_FETCH_IMapper, as libhidl's passthrough manager does)
    const char* mapperLib = getenv("GRALTOOL_MAPPER") ? getenv("GRALTOOL_MAPPER") : "/vendor/lib64/hw/android.hardware.graphics.mapper@4.0-impl-mediatek.so";
    void* lib = dlopen(mapperLib, RTLD_NOW);
    auto fetch = lib ? reinterpret_cast<IMapper* (*)(const char*)>(dlsym(lib, "HIDL_FETCH_IMapper")) : nullptr;
    sp<IMapper> mapper = fetch ? fetch("default") : nullptr;
    if (mapper == nullptr) { printf("graltool: no mapper from %s (%s)\n", mapperLib, dlerror()); return 1; }
    hidl_vec<uint8_t> descriptor;
    Error derr = Error::NO_RESOURCES;
    mapper->createDescriptor(info, [&](Error e, const hidl_vec<uint8_t>& d) { derr = e; descriptor = d; });
    if (derr != Error::NONE) { printf("graltool: createDescriptor: error %d\n", static_cast<int>(derr)); return 1; }
    native_handle_t* handle = nullptr;
    uint32_t stride = 0;
    auto ret = allocator->allocate(descriptor, 1, [&](Error error, uint32_t s, const hidl_vec<hidl_handle>& buffers) {
        if (error != Error::NONE || buffers.size() != 1) { printf("graltool: allocate: error %d\n", static_cast<int>(error)); return; }
        handle = native_handle_clone(buffers[0].getNativeHandle());
        stride = s;
    });
    if (!ret.isOk() || handle == nullptr) { printf("graltool: allocation failed (%s)\n", ret.description().c_str()); return 1; }
    printf("graltool: allocated %ux%u stride %u: %d fds, %d ints\n", w, h, stride, handle->numFds, handle->numInts);
    void* imported = nullptr;
    mapper->importBuffer(handle, [&](Error e, void* b) { if (e == Error::NONE) imported = b; });
    void* pixels = nullptr;
    IMapper::Rect all{0, 0, static_cast<int32_t>(w), static_cast<int32_t>(h)};
    if (imported) mapper->lock(imported, static_cast<uint64_t>(BufferUsage::CPU_WRITE_OFTEN), all, hidl_handle(),
                               [&](Error e, void* p) { if (e == Error::NONE) pixels = p; });
    if (pixels == nullptr) { printf("graltool: importBuffer/lock failed\n"); return 1; }
    uint32_t abgr = 0xff000000u | ((rgb & 0xff) << 16) | (rgb & 0xff00) | ((rgb >> 16) & 0xff);  // RGBA_8888 in memory: R, G, B, A
    for (uint32_t y = 0; y < h; y++)
        for (uint32_t x = 0; x < stride; x++)  // a lighter square in the middle: proves orientation and stride
            static_cast<uint32_t*>(pixels)[y * stride + x] = (x > w / 4 && x < 3 * w / 4 && y > h / 4 && y < 3 * h / 4) ? 0xffffffffu : abgr;
    mapper->unlock(imported, [](Error, const hidl_handle&) {});
    apklayer_display_callbacks cb{nullptr, vsync, refresh, released};
    apklayer_display* d = apklayer_display_open("apklayer:graltool", w, h, &cb);
    if (d == nullptr) { perror("graltool: display_open"); return 1; }
    apklayer_display_set_configure_callback(d, configure, nullptr);
    apklayer_native_buffer nb{w, h, stride, static_cast<uint32_t>(PixelFormat::RGBA_8888), usage, handle->numFds, handle->data,
                              handle->numInts, handle->data + handle->numFds};
    int32_t damage[4] = {0, 0, static_cast<int32_t>(w), static_cast<int32_t>(h)};
    int r = apklayer_display_present_native(d, 0, &nb, damage);
    printf("graltool: presented (%d)\n", r);
    fflush(stdout);
    sleep(seconds);
    apklayer_display_close(d);
    mapper->freeBuffer(imported);
    native_handle_close(handle);
    native_handle_delete(handle);
    return r == 0 ? 0 : 1;
}
