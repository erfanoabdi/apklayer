// android.hardware.graphics.mapper stable-C (IMapper v5) for apklayer: imports the allocator's buffers (aosp/hals/graphics/
// allocator) and maps them through the host graphics HAL. Structure after external/minigbm/cros_gralloc/mapper_stablec
// (Apache-2.0, docs/THIRD_PARTY.md); the buffer description comes from the native_handle (gralloc_handle.h), mutable metadata
// from the buffer's shared memory page.
#define LOG_TAG "apklayer.mapper"

#include <aidl/android/hardware/graphics/common/BlendMode.h>
#include <aidl/android/hardware/graphics/common/BufferUsage.h>
#include <aidl/android/hardware/graphics/common/Dataspace.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#include <aidl/android/hardware/graphics/common/PlaneLayout.h>
#include <aidl/android/hardware/graphics/common/StandardMetadataType.h>
#include <android-base/unique_fd.h>
#include <android/hardware/graphics/mapper/IMapper.h>
#include <android/hardware/graphics/mapper/utils/IMapperMetadataTypes.h>
#include <android/hardware/graphics/mapper/utils/IMapperProvider.h>
#include <cutils/native_handle.h>
#include <gralloctypes/Gralloc4.h>
#include <log/log.h>
#include <poll.h>

#include <memory>
#include <mutex>
#include <unordered_map>

#include "gralloc_format.h"
#include "gralloc_handle.h"

using namespace ::aidl::android::hardware::graphics::common;
using namespace ::android::hardware::graphics::mapper;
using ::android::base::unique_fd;
using namespace apklayer::gralloc;

namespace {

constexpr const char* kStandardName = "android.hardware.graphics.common.StandardMetadataType";

struct Locked {
    apklayer_gfx_mapping* mapping;
    void* base;
};

PlaneLayoutComponent comp(const ExtendableType& type, int64_t offsetBits, int64_t sizeBits) {
    PlaneLayoutComponent c;
    c.type = type;
    c.offsetInBits = offsetBits;
    c.sizeInBits = sizeBits;
    return c;
}

PlaneLayout plane(std::vector<PlaneLayoutComponent> comps, int64_t incrementBits, int64_t hsub, int64_t vsub) {
    PlaneLayout l;
    l.components = std::move(comps);
    l.sampleIncrementInBits = incrementBits;
    l.horizontalSubsampling = hsub;
    l.verticalSubsampling = vsub;
    return l;
}

// What the samples of each plane are, by DRM format (Android's component vocabulary).
std::vector<PlaneLayout> componentsFor(const HandleData& d) {
    using namespace ::android::gralloc4;
    switch (d.fourcc) {
        case DRM_FORMAT_ABGR8888:
            return {plane({comp(PlaneLayoutComponentType_R, 0, 8), comp(PlaneLayoutComponentType_G, 8, 8),
                           comp(PlaneLayoutComponentType_B, 16, 8), comp(PlaneLayoutComponentType_A, 24, 8)}, 32, 1, 1)};
        case DRM_FORMAT_XBGR8888:
            return {plane({comp(PlaneLayoutComponentType_R, 0, 8), comp(PlaneLayoutComponentType_G, 8, 8),
                           comp(PlaneLayoutComponentType_B, 16, 8)}, 32, 1, 1)};
        case DRM_FORMAT_ARGB8888:
            return {plane({comp(PlaneLayoutComponentType_B, 0, 8), comp(PlaneLayoutComponentType_G, 8, 8),
                           comp(PlaneLayoutComponentType_R, 16, 8), comp(PlaneLayoutComponentType_A, 24, 8)}, 32, 1, 1)};
        case DRM_FORMAT_BGR888:
            return {plane({comp(PlaneLayoutComponentType_R, 0, 8), comp(PlaneLayoutComponentType_G, 8, 8),
                           comp(PlaneLayoutComponentType_B, 16, 8)}, 24, 1, 1)};
        case DRM_FORMAT_RGB565:
            return {plane({comp(PlaneLayoutComponentType_B, 0, 5), comp(PlaneLayoutComponentType_G, 5, 6),
                           comp(PlaneLayoutComponentType_R, 11, 5)}, 16, 1, 1)};
        case DRM_FORMAT_ABGR16161616F:
            return {plane({comp(PlaneLayoutComponentType_R, 0, 16), comp(PlaneLayoutComponentType_G, 16, 16),
                           comp(PlaneLayoutComponentType_B, 32, 16), comp(PlaneLayoutComponentType_A, 48, 16)}, 64, 1, 1)};
        case DRM_FORMAT_ABGR2101010:
            return {plane({comp(PlaneLayoutComponentType_R, 0, 10), comp(PlaneLayoutComponentType_G, 10, 10),
                           comp(PlaneLayoutComponentType_B, 20, 10), comp(PlaneLayoutComponentType_A, 30, 2)}, 32, 1, 1)};
        case DRM_FORMAT_R8:
            return {plane({comp(d.androidFormat == static_cast<uint32_t>(PixelFormat::R_8) ? PlaneLayoutComponentType_R
                                                                                          : PlaneLayoutComponentType_Y, 0, 8)}, 8, 1, 1)};
        case DRM_FORMAT_R16:
            return {plane({comp(PlaneLayoutComponentType_Y, 0, 16)}, 16, 1, 1)};
        case DRM_FORMAT_NV12:
            return {plane({comp(PlaneLayoutComponentType_Y, 0, 8)}, 8, 1, 1),
                    plane({comp(PlaneLayoutComponentType_CB, 0, 8), comp(PlaneLayoutComponentType_CR, 8, 8)}, 16, 2, 2)};
        case DRM_FORMAT_NV21:
            return {plane({comp(PlaneLayoutComponentType_Y, 0, 8)}, 8, 1, 1),
                    plane({comp(PlaneLayoutComponentType_CR, 0, 8), comp(PlaneLayoutComponentType_CB, 8, 8)}, 16, 2, 2)};
        case DRM_FORMAT_NV16:
            return {plane({comp(PlaneLayoutComponentType_Y, 0, 8)}, 8, 1, 1),
                    plane({comp(PlaneLayoutComponentType_CB, 0, 8), comp(PlaneLayoutComponentType_CR, 8, 8)}, 16, 2, 1)};
        case DRM_FORMAT_YUV420:
            return {plane({comp(PlaneLayoutComponentType_Y, 0, 8)}, 8, 1, 1),
                    plane({comp(PlaneLayoutComponentType_CB, 0, 8)}, 8, 2, 2),
                    plane({comp(PlaneLayoutComponentType_CR, 0, 8)}, 8, 2, 2)};
        case DRM_FORMAT_YVU420:
            return {plane({comp(PlaneLayoutComponentType_Y, 0, 8)}, 8, 1, 1),
                    plane({comp(PlaneLayoutComponentType_CR, 0, 8)}, 8, 2, 2),
                    plane({comp(PlaneLayoutComponentType_CB, 0, 8)}, 8, 2, 2)};
        case DRM_FORMAT_P010:  // 10 significant bits in the top of each 16
            return {plane({comp(PlaneLayoutComponentType_Y, 6, 10)}, 16, 1, 1),
                    plane({comp(PlaneLayoutComponentType_CB, 6, 10), comp(PlaneLayoutComponentType_CR, 22, 10)}, 32, 2, 2)};
        default:
            return {};
    }
}

class ApklayerMapper final : public vendor::mapper::IMapperV5Impl {
  public:
    ApklayerMapper() : mGfx(apklayer_gfx_open(nullptr)) {
        if (mGfx == nullptr) ALOGE("no render node for the host graphics HAL");
    }

    AIMapper_Error importBuffer(const native_handle_t* handle, buffer_handle_t* out) override {
        HandleData d;
        if (!decode(handle, &d)) return AIMAPPER_ERROR_BAD_BUFFER;
        native_handle_t* clone = native_handle_clone(handle);  // dups the fds
        if (clone == nullptr) return AIMAPPER_ERROR_NO_RESOURCES;
        *out = clone;
        return AIMAPPER_ERROR_NONE;
    }

    AIMapper_Error freeBuffer(buffer_handle_t buffer) override {
        HandleData d;
        if (!decode(buffer, &d)) return AIMAPPER_ERROR_BAD_BUFFER;
        {
            std::lock_guard<std::mutex> l(mMutex);
            auto it = mLocked.find(buffer);
            if (it != mLocked.end()) { apklayer_gfx_unmap(it->second.mapping); mLocked.erase(it); }
        }
        native_handle_t* h = const_cast<native_handle_t*>(buffer);
        native_handle_close(h);
        native_handle_delete(h);
        return AIMAPPER_ERROR_NONE;
    }

    AIMapper_Error getTransportSize(buffer_handle_t buffer, uint32_t* numFds, uint32_t* numInts) override {
        HandleData d;
        if (!decode(buffer, &d)) return AIMAPPER_ERROR_BAD_BUFFER;
        *numFds = buffer->numFds;
        *numInts = buffer->numInts;
        return AIMAPPER_ERROR_NONE;
    }

    AIMapper_Error lock(buffer_handle_t buffer, uint64_t cpuUsage, ARect region, int acquireFence, void** outData) override {
        unique_fd fence(acquireFence);  // ours in every case
        HandleData d;
        if (!decode(buffer, &d) || mGfx == nullptr) return AIMAPPER_ERROR_BAD_BUFFER;
        if (cpuUsage == 0) return AIMAPPER_ERROR_BAD_VALUE;
        bool whole = region.left == 0 && region.top == 0 && region.right == 0 && region.bottom == 0;
        if (!whole && (region.left < 0 || region.top < 0 || region.right <= region.left || region.bottom <= region.top ||
                       region.right > static_cast<int32_t>(d.width) || region.bottom > static_cast<int32_t>(d.height)))
            return AIMAPPER_ERROR_BAD_VALUE;
        if (fence.ok()) {  // wait for the producer's GPU work
            pollfd p = {fence.get(), POLLIN, 0};
            poll(&p, 1, 3000);
        }
        apklayer_gfx_buffer b = toBuffer(buffer, d);
        uint32_t stride = 0;
        apklayer_gfx_mapping* mapping = nullptr;
        bool write = cpuUsage & bit(BufferUsage::CPU_WRITE_MASK);
        void* base = apklayer_gfx_map_linear(mGfx, &b, write, &stride, &mapping);
        if (base == nullptr) return AIMAPPER_ERROR_BAD_VALUE;
        {
            std::lock_guard<std::mutex> l(mMutex);
            auto old = mLocked.find(buffer);
            if (old != mLocked.end()) apklayer_gfx_unmap(old->second.mapping);
            mLocked[buffer] = {mapping, base};
        }
        *outData = base;
        return AIMAPPER_ERROR_NONE;
    }

    AIMapper_Error unlock(buffer_handle_t buffer, int* releaseFence) override {
        std::lock_guard<std::mutex> l(mMutex);
        auto it = mLocked.find(buffer);
        if (it == mLocked.end()) return AIMAPPER_ERROR_BAD_BUFFER;
        apklayer_gfx_unmap(it->second.mapping);  // CPU writes reach the buffer here
        mLocked.erase(it);
        *releaseFence = -1;
        return AIMAPPER_ERROR_NONE;
    }

    AIMapper_Error flushLockedBuffer(buffer_handle_t) override { return AIMAPPER_ERROR_NONE; }   // writes are published at unlock
    AIMapper_Error rereadLockedBuffer(buffer_handle_t) override { return AIMAPPER_ERROR_NONE; }

    int32_t getMetadata(buffer_handle_t buffer, AIMapper_MetadataType type, void* out, size_t outSize) override {
        if (strcmp(kStandardName, type.name) == 0) return getStandardMetadata(buffer, type.value, out, outSize);
        return -AIMAPPER_ERROR_UNSUPPORTED;
    }

    int32_t getStandardMetadata(buffer_handle_t buffer, int64_t type, void* out, size_t outSize) override {
        HandleData d;
        if (!decode(buffer, &d)) return -AIMAPPER_ERROR_BAD_BUFFER;
        SharedMetadata* meta = mapMetadata(buffer);
        if (meta == nullptr) return -AIMAPPER_ERROR_NO_RESOURCES;
        auto provider = [&]<StandardMetadataType T>(auto&& provide) -> int32_t { return get(d, *meta, provide, StandardMetadata<T>{}); };
        int32_t r = provideStandardMetadata(static_cast<StandardMetadataType>(type), out, outSize, provider);
        unmapMetadata(meta);
        return r;
    }

    AIMapper_Error setMetadata(buffer_handle_t buffer, AIMapper_MetadataType type, const void* data, size_t size) override {
        if (strcmp(kStandardName, type.name) == 0) return setStandardMetadata(buffer, type.value, data, size);
        return AIMAPPER_ERROR_UNSUPPORTED;
    }

    AIMapper_Error setStandardMetadata(buffer_handle_t buffer, int64_t typeRaw, const void* data, size_t size) override {
        HandleData d;
        if (!decode(buffer, &d)) return AIMAPPER_ERROR_BAD_BUFFER;
        auto type = static_cast<StandardMetadataType>(typeRaw);
        switch (type) {
            case StandardMetadataType::BUFFER_ID: case StandardMetadataType::NAME: case StandardMetadataType::WIDTH:
            case StandardMetadataType::HEIGHT: case StandardMetadataType::LAYER_COUNT:
            case StandardMetadataType::PIXEL_FORMAT_REQUESTED: case StandardMetadataType::USAGE:
                return AIMAPPER_ERROR_BAD_VALUE;  // read-only
            case StandardMetadataType::BLEND_MODE: case StandardMetadataType::DATASPACE: case StandardMetadataType::SMPTE2086:
            case StandardMetadataType::CTA861_3:
                break;
            default:
                return AIMAPPER_ERROR_UNSUPPORTED;
        }
        SharedMetadata* meta = mapMetadata(buffer);
        if (meta == nullptr) return AIMAPPER_ERROR_NO_RESOURCES;
        auto applier = [&]<StandardMetadataType T>(auto&& value) -> AIMapper_Error { return set<T>(*meta, value); };
        AIMapper_Error r = applyStandardMetadata(type, data, size, applier);
        unmapMetadata(meta);
        return r;
    }

    AIMapper_Error listSupportedMetadataTypes(const AIMapper_MetadataTypeDescription** list, size_t* count) override {
        static constexpr auto describe = [](StandardMetadataType t, bool settable) {
            return AIMapper_MetadataTypeDescription{{kStandardName, static_cast<int64_t>(t)}, nullptr, true, settable, {0}};
        };
        static constexpr std::array<AIMapper_MetadataTypeDescription, 20> kTypes{
                describe(StandardMetadataType::BUFFER_ID, false), describe(StandardMetadataType::NAME, false),
                describe(StandardMetadataType::WIDTH, false), describe(StandardMetadataType::HEIGHT, false),
                describe(StandardMetadataType::LAYER_COUNT, false), describe(StandardMetadataType::PIXEL_FORMAT_REQUESTED, false),
                describe(StandardMetadataType::PIXEL_FORMAT_FOURCC, false), describe(StandardMetadataType::PIXEL_FORMAT_MODIFIER, false),
                describe(StandardMetadataType::USAGE, false), describe(StandardMetadataType::ALLOCATION_SIZE, false),
                describe(StandardMetadataType::PROTECTED_CONTENT, false), describe(StandardMetadataType::COMPRESSION, false),
                describe(StandardMetadataType::INTERLACED, false), describe(StandardMetadataType::CHROMA_SITING, false),
                describe(StandardMetadataType::PLANE_LAYOUTS, false), describe(StandardMetadataType::CROP, false),
                describe(StandardMetadataType::DATASPACE, true), describe(StandardMetadataType::BLEND_MODE, true),
                describe(StandardMetadataType::SMPTE2086, true), describe(StandardMetadataType::CTA861_3, true)};
        *list = kTypes.data();
        *count = kTypes.size();
        return AIMAPPER_ERROR_NONE;
    }

    AIMapper_Error dumpBuffer(buffer_handle_t, AIMapper_DumpBufferCallback, void*) override { return AIMAPPER_ERROR_UNSUPPORTED; }
    AIMapper_Error dumpAllBuffers(AIMapper_BeginDumpBufferCallback, AIMapper_DumpBufferCallback, void*) override {
        return AIMAPPER_ERROR_NONE;
    }
    AIMapper_Error getReservedRegion(buffer_handle_t, void** region, uint64_t* size) override {
        *region = nullptr;
        *size = 0;
        return AIMAPPER_ERROR_UNSUPPORTED;
    }

  private:
    // The host HAL's view of an imported handle (fds are borrowed from the handle).
    static apklayer_gfx_buffer toBuffer(const native_handle_t* h, const HandleData& d) {
        apklayer_gfx_buffer b = {};
        b.width = d.width;
        b.height = d.height;
        b.fourcc = d.fourcc;
        b.modifier = d.modifier;
        b.num_planes = d.numPlanes;
        b.packed = d.packed;
        b.alloc_fourcc = d.allocFourcc;
        b.alloc_width = d.allocWidth;
        b.alloc_height = d.allocHeight;
        for (int i = 0; i < kMaxPlanes; i++) b.fd[i] = i < h->numFds - 1 ? h->data[i] : -1;
        for (uint32_t i = 0; i < d.numPlanes; i++) { b.stride[i] = d.stride[i]; b.offset[i] = d.offset[i]; }
        return b;
    }

    template <typename F, StandardMetadataType T>
    int32_t get(const HandleData& d, const SharedMetadata& m, F&& provide, StandardMetadata<T>) {
        if constexpr (T == StandardMetadataType::BUFFER_ID) return provide(d.id);
        if constexpr (T == StandardMetadataType::NAME) return provide(std::string(m.name));
        if constexpr (T == StandardMetadataType::WIDTH) return provide(static_cast<uint64_t>(d.width));
        if constexpr (T == StandardMetadataType::HEIGHT) return provide(static_cast<uint64_t>(d.height));
        if constexpr (T == StandardMetadataType::LAYER_COUNT) return provide(static_cast<uint64_t>(d.layerCount));
        if constexpr (T == StandardMetadataType::PIXEL_FORMAT_REQUESTED) return provide(static_cast<PixelFormat>(d.androidFormat));
        if constexpr (T == StandardMetadataType::PIXEL_FORMAT_FOURCC) return provide(static_cast<uint32_t>(d.fourcc));
        if constexpr (T == StandardMetadataType::PIXEL_FORMAT_MODIFIER) return provide(static_cast<uint64_t>(d.modifier));
        if constexpr (T == StandardMetadataType::USAGE) return provide(static_cast<BufferUsage>(d.usage));
        if constexpr (T == StandardMetadataType::ALLOCATION_SIZE) {
            uint64_t total = d.packed == 1 ? static_cast<uint64_t>(d.stride[0]) * d.allocHeight : 0;
            for (uint32_t i = 0; d.packed != 1 && i < d.numPlanes; i++) total += static_cast<uint64_t>(d.stride[i]) * d.height / (i && d.fourcc != DRM_FORMAT_NV16 ? 2 : 1);
            return provide(total);
        }
        if constexpr (T == StandardMetadataType::PROTECTED_CONTENT) return provide(static_cast<uint64_t>(d.usage & bit(BufferUsage::PROTECTED) ? 1 : 0));
        if constexpr (T == StandardMetadataType::COMPRESSION) return provide(::android::gralloc4::Compression_None);
        if constexpr (T == StandardMetadataType::INTERLACED) return provide(::android::gralloc4::Interlaced_None);
        if constexpr (T == StandardMetadataType::CHROMA_SITING) return provide(::android::gralloc4::ChromaSiting_None);
        if constexpr (T == StandardMetadataType::STRIDE) return provide(static_cast<uint32_t>(d.stride[0] / bytesPerPixel(d.fourcc)));
        if constexpr (T == StandardMetadataType::PLANE_LAYOUTS) {
            std::vector<PlaneLayout> layouts = componentsFor(d);
            if (layouts.size() != d.numPlanes) return -AIMAPPER_ERROR_UNSUPPORTED;
            for (size_t i = 0; i < layouts.size(); i++) {
                // apklayer patch: CPU lock stages separate GPU planes contiguously.
                layouts[i].offsetInBytes = d.packed == 2 && i ?
                    layouts[i - 1].offsetInBytes + layouts[i - 1].totalSizeInBytes : d.offset[i];
                layouts[i].strideInBytes = d.stride[i];
                layouts[i].widthInSamples = d.width / layouts[i].horizontalSubsampling;
                layouts[i].heightInSamples = d.height / layouts[i].verticalSubsampling;
                layouts[i].totalSizeInBytes = static_cast<int64_t>(d.stride[i]) * layouts[i].heightInSamples;
            }
            return provide(layouts);
        }
        if constexpr (T == StandardMetadataType::CROP) {
            std::vector<Rect> crops(d.numPlanes, Rect{0, 0, static_cast<int32_t>(d.width), static_cast<int32_t>(d.height)});
            return provide(crops);
        }
        if constexpr (T == StandardMetadataType::DATASPACE) return provide(static_cast<Dataspace>(m.dataspace));
        if constexpr (T == StandardMetadataType::BLEND_MODE) return provide(static_cast<BlendMode>(m.blendMode));
        if constexpr (T == StandardMetadataType::SMPTE2086) {
            if (!m.hasSmpte2086) return 0;
            Smpte2086 s;
            s.primaryRed = {m.primaryRed[0], m.primaryRed[1]};
            s.primaryGreen = {m.primaryGreen[0], m.primaryGreen[1]};
            s.primaryBlue = {m.primaryBlue[0], m.primaryBlue[1]};
            s.whitePoint = {m.whitePoint[0], m.whitePoint[1]};
            s.maxLuminance = m.maxLuminance;
            s.minLuminance = m.minLuminance;
            return provide(s);
        }
        if constexpr (T == StandardMetadataType::CTA861_3) {
            if (!m.hasCta861_3) return 0;
            Cta861_3 c;
            c.maxContentLightLevel = m.maxContentLightLevel;
            c.maxFrameAverageLightLevel = m.maxFrameAverageLightLevel;
            return provide(c);
        }
        return -AIMAPPER_ERROR_UNSUPPORTED;
    }

    template <StandardMetadataType T, typename V>
    AIMapper_Error set(SharedMetadata& m, V&& value) {
        if constexpr (T == StandardMetadataType::DATASPACE) m.dataspace = static_cast<int32_t>(value);
        if constexpr (T == StandardMetadataType::BLEND_MODE) m.blendMode = static_cast<int32_t>(value);
        if constexpr (T == StandardMetadataType::SMPTE2086) {
            if (!value) { m.hasSmpte2086 = 0; return AIMAPPER_ERROR_NONE; }
            m.hasSmpte2086 = 1;
            m.primaryRed[0] = value->primaryRed.x; m.primaryRed[1] = value->primaryRed.y;
            m.primaryGreen[0] = value->primaryGreen.x; m.primaryGreen[1] = value->primaryGreen.y;
            m.primaryBlue[0] = value->primaryBlue.x; m.primaryBlue[1] = value->primaryBlue.y;
            m.whitePoint[0] = value->whitePoint.x; m.whitePoint[1] = value->whitePoint.y;
            m.maxLuminance = value->maxLuminance;
            m.minLuminance = value->minLuminance;
        }
        if constexpr (T == StandardMetadataType::CTA861_3) {
            if (!value) { m.hasCta861_3 = 0; return AIMAPPER_ERROR_NONE; }
            m.hasCta861_3 = 1;
            m.maxContentLightLevel = value->maxContentLightLevel;
            m.maxFrameAverageLightLevel = value->maxFrameAverageLightLevel;
        }
        return AIMAPPER_ERROR_NONE;
    }

    apklayer_gfx* mGfx;
    std::mutex mMutex;
    std::unordered_map<buffer_handle_t, Locked> mLocked;
};

}  // namespace

extern "C" uint32_t ANDROID_HAL_MAPPER_VERSION = AIMAPPER_VERSION_5;

extern "C" AIMapper_Error AIMapper_loadIMapper(AIMapper** outImplementation) {
    static vendor::mapper::IMapperProvider<ApklayerMapper> provider;
    return provider.load(outImplementation);
}
