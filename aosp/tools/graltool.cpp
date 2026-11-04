// graltool: the device's gralloc through the host hwbinder context, shown through the shell (docs/HALIUM.md, plan step (e)).
//   show W H SECONDS [RRGGBB]   allocates one RGBA_8888 buffer from the device's allocator@4.0 (host_hals.conf lists
//                               android.hardware.graphics.allocator), fills it through the CPU (mmap of its first fd: a dma-buf),
//                               opens a display window of the shell and presents it as a gralloc frame (android_wlegl), then
//                               keeps the window SECONDS seconds and exits. Proves allocation and presentation without the GPU.
#include <android/hardware/graphics/allocator/4.0/IAllocator.h>
#include <android/hardware/graphics/mapper/4.0/IMapper.h>
#include <cutils/native_handle.h>
#include <gralloctypes/Gralloc4.h>
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
    sp<IAllocator> allocator = IAllocator::getService();
    if (allocator == nullptr) { printf("graltool: no allocator@4.0\n"); return 1; }
    uint64_t usage = static_cast<uint64_t>(BufferUsage::CPU_WRITE_OFTEN | BufferUsage::CPU_READ_OFTEN | BufferUsage::GPU_TEXTURE |
                                           BufferUsage::COMPOSER_OVERLAY);
    if (const char* u = getenv("GRALTOOL_USAGE")) usage = strtoull(u, nullptr, 16);  // to find what the device's allocator accepts
    IMapper::BufferDescriptorInfo info{"apklayer-graltool", w, h, 1, static_cast<PixelFormat>(PixelFormat::RGBA_8888), usage, 0};
    hidl_vec<uint8_t> descriptor;
    if (android::gralloc4::encodeBufferDescriptorInfo(info, &descriptor) != android::OK) { printf("graltool: descriptor\n"); return 1; }
    native_handle_t* handle = nullptr;
    uint32_t stride = 0;
    auto ret = allocator->allocate(descriptor, 1, [&](Error error, uint32_t s, const hidl_vec<hidl_handle>& buffers) {
        if (error != Error::NONE || buffers.size() != 1) { printf("graltool: allocate: error %d\n", static_cast<int>(error)); return; }
        handle = native_handle_clone(buffers[0].getNativeHandle());
        stride = s;
    });
    if (!ret.isOk() || handle == nullptr) { printf("graltool: allocation failed (%s)\n", ret.description().c_str()); return 1; }
    printf("graltool: allocated %ux%u stride %u: %d fds, %d ints\n", w, h, stride, handle->numFds, handle->numInts);
    size_t size = static_cast<size_t>(stride) * h * 4;
    void* pixels = mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_SHARED, handle->data[0], 0);
    if (pixels == MAP_FAILED) { perror("graltool: mmap of the buffer's first fd"); return 1; }
    uint32_t abgr = 0xff000000u | ((rgb & 0xff) << 16) | (rgb & 0xff00) | ((rgb >> 16) & 0xff);  // RGBA_8888 in memory: R, G, B, A
    for (uint32_t y = 0; y < h; y++)
        for (uint32_t x = 0; x < stride; x++)  // a lighter square in the middle: proves orientation and stride
            static_cast<uint32_t*>(pixels)[y * stride + x] = (x > w / 4 && x < 3 * w / 4 && y > h / 4 && y < 3 * h / 4) ? 0xffffffffu : abgr;
    munmap(pixels, size);
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
    native_handle_close(handle);
    native_handle_delete(handle);
    return r == 0 ? 0 : 1;
}
