// android.hardware.graphics.allocator.IAllocator for apklayer: buffers come from the host graphics HAL (bridge/hals/graphics,
// libgbm dmabufs); the native_handle carries one dmabuf per plane and a HandleData the mapper decodes.
#define LOG_TAG "apklayer.allocator"

#include <aidl/android/hardware/graphics/allocator/AllocationError.h>
#include <aidl/android/hardware/graphics/allocator/BnAllocator.h>
#include <aidl/android/hardware/graphics/common/BlendMode.h>
#include <aidlcommonsupport/NativeHandle.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <log/log.h>

#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>

#include <atomic>

#include "gralloc_format.h"
#include "gralloc_handle.h"

using namespace aidl::android::hardware::graphics::allocator;
using aidl::android::hardware::graphics::common::BlendMode;
using aidl::android::hardware::graphics::common::PixelFormat;
using namespace apklayer::gralloc;

namespace {

std::atomic<uint64_t> gNextId{1};

class Allocator : public BnAllocator {
  public:
    explicit Allocator(apklayer_gfx* gfx) : mGfx(gfx) {}

    ndk::ScopedAStatus allocate(const std::vector<uint8_t>&, int32_t, AllocationResult*) override {
        return error(AllocationError::UNSUPPORTED);  // the legacy byte-array descriptor; allocate2 is used
    }

    ndk::ScopedAStatus allocate2(const BufferDescriptorInfo& d, int32_t count, AllocationResult* out) override {
        if (d.width <= 0 || d.height <= 0 || d.layerCount != 1 || count < 1) return error(AllocationError::BAD_DESCRIPTOR);
        out->stride = 0;
        for (int32_t i = 0; i < count; i++) {
            apklayer_gfx_buffer buf;
            int r = allocateOne(d, &buf);
            if (r != 0) {
                return error(r == -ENOTSUP ? AllocationError::UNSUPPORTED : AllocationError::NO_RESOURCES);
            }
            native_handle_t* h = toHandle(d, buf, &out->stride);
            apklayer_gfx_buffer_close(&buf);  // the handle holds its own dups
            if (h == nullptr) return error(AllocationError::NO_RESOURCES);
            out->buffers.push_back(android::dupToAidl(h));
            native_handle_close(h);
            native_handle_delete(h);
        }
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus isSupported(const BufferDescriptorInfo& d, bool* out) override {
        uint32_t fourcc = toFourcc(d.format, static_cast<uint64_t>(d.usage));
        *out = d.width > 0 && d.height > 0 && d.layerCount == 1 &&
               (d.format == PixelFormat::BLOB || (fourcc != 0 && apklayer_gfx_supports(mGfx, fourcc, toGfxUsage(static_cast<uint64_t>(d.usage)))));
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus getIMapperLibrarySuffix(std::string* out) override {
        *out = "apklayer";  // libui loads mapper.apklayer.so
        return ndk::ScopedAStatus::ok();
    }

  private:
    static ndk::ScopedAStatus error(AllocationError e) {
        return ndk::ScopedAStatus::fromServiceSpecificError(static_cast<int32_t>(e));
    }

    int allocateOne(const BufferDescriptorInfo& d, apklayer_gfx_buffer* buf) {
        uint64_t usage = static_cast<uint64_t>(d.usage);
        if (d.format == PixelFormat::BLOB) return apklayer_gfx_alloc_blob(mGfx, static_cast<uint32_t>(d.width), buf);
        uint32_t fourcc = toFourcc(d.format, usage);
        if (fourcc == 0) return -ENOTSUP;
        return apklayer_gfx_alloc(mGfx, d.width, d.height, fourcc, toGfxUsage(usage), buf);
    }

    // The handle's fds are dups of the buffer's (the caller closes buf); stride is in pixels for the first plane.
    native_handle_t* toHandle(const BufferDescriptorInfo& d, const apklayer_gfx_buffer& buf, int32_t* stride) {
        native_handle_t* h = native_handle_create(buf.num_planes + 1, kNumInts);  // plane fds + the metadata page
        if (h == nullptr) return nullptr;
        for (int i = 0; i < h->numFds; i++) h->data[i] = -1;
        HandleData data = {};
        data.magic = kMagic;
        data.version = kVersion;
        data.width = d.width;
        data.height = d.height;
        data.layerCount = d.layerCount;
        data.androidFormat = static_cast<uint32_t>(d.format);
        data.fourcc = buf.fourcc;
        data.numPlanes = buf.num_planes;
        data.usage = static_cast<uint64_t>(d.usage);
        data.modifier = buf.modifier;
        data.packed = buf.packed;
        data.allocFourcc = buf.alloc_fourcc;
        data.allocWidth = buf.alloc_width;
        data.allocHeight = buf.alloc_height;
        data.id = (static_cast<uint64_t>(getpid()) << 32) | gNextId++;
        data.reservedSize = d.reservedSize;
        for (int i = 0; i < buf.num_planes; i++) {
            h->data[i] = fcntl(buf.fd[i], F_DUPFD_CLOEXEC, 3);
            data.stride[i] = buf.stride[i];
            data.offset[i] = buf.offset[i];
            if (h->data[i] < 0) { native_handle_close(h); native_handle_delete(h); return nullptr; }
        }
        h->data[buf.num_planes] = memfd_create("apklayer-gralloc-metadata", MFD_CLOEXEC);
        if (h->data[buf.num_planes] < 0 || ftruncate(h->data[buf.num_planes], kMetadataSize) < 0) {
            native_handle_close(h);
            native_handle_delete(h);
            return nullptr;
        }
        if (SharedMetadata* meta = mapMetadata(h)) {
            *meta = {};
            meta->blendMode = static_cast<int32_t>(BlendMode::PREMULTIPLIED);
            memcpy(meta->name, d.name.data(), std::min(sizeof(meta->name) - 1, d.name.size()));
            unmapMetadata(meta);
        }
        encode(h, data);
        *stride = d.format == PixelFormat::BLOB ? d.width : buf.stride[0] / bytesPerPixel(buf.fourcc);
        return h;
    }

    apklayer_gfx* mGfx;
};

}  // namespace

// Exported so bionic_translation's launcher (scripts/btload.c --main) can run the service from the library.
extern "C" int main(int, char**) {
    apklayer_gfx* gfx = apklayer_gfx_open(nullptr);
    if (gfx == nullptr) {
        LOG(FATAL) << "no render node for the host graphics HAL";
    }
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();
    auto allocator = ndk::SharedRefBase::make<Allocator>(gfx);
    const std::string name = std::string(Allocator::descriptor) + "/default";
    binder_status_t st = AServiceManager_addService(allocator->asBinder().get(), name.c_str());
    LOG_ALWAYS_FATAL_IF(st != STATUS_OK, "cannot register %s: %d", name.c_str(), st);
    ABinderProcess_joinThreadPool();
    return 1;
}
