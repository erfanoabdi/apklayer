// The native_handle of an apklayer gralloc buffer, shared by the allocator and the mapper. fds: one per plane, in plane order
// (packed formats repeat the same dmabuf, see bridge/hals/graphics), then one more fd: a shared memory page with the buffer's
// mutable metadata (SharedMetadata: dataspace, blend mode, HDR info), visible to every process that imports the buffer.
// ints: a HandleData, copied in and out.
#pragma once

#include <cutils/native_handle.h>
#include <string.h>

#include <algorithm>
#include <sys/mman.h>

#include <cstdint>

namespace apklayer::gralloc {

constexpr uint32_t kMagic = 0x4c475041;  // "APGL"
constexpr uint32_t kVersion = 1;
constexpr int kMaxPlanes = 4;

struct HandleData {
    uint32_t magic, version;
    uint32_t width, height, layerCount;
    uint32_t androidFormat;  // aidl PixelFormat the client asked for
    uint32_t fourcc;         // DRM format; 0 for BLOB (plain memory)
    uint32_t numPlanes;
    uint32_t stride[kMaxPlanes];  // bytes
    uint32_t offset[kMaxPlanes];  // bytes
    uint32_t packed;  // 0 native, 1 legacy shared R8/R16 allocation, 2 separate R/RG plane resources
    uint32_t allocFourcc, allocWidth, allocHeight;  // the underlying allocation (R8/R16 and total rows when packed)
    uint64_t usage;     // aidl BufferUsage
    uint64_t modifier;  // DRM format modifier
    uint64_t id;        // unique per allocation (backing store id)
    uint64_t reservedSize;
};
static_assert(sizeof(HandleData) % sizeof(int) == 0, "HandleData is stored in native_handle ints");
constexpr int kNumInts = sizeof(HandleData) / sizeof(int);

inline bool decode(const native_handle_t* h, HandleData* out) {
    if (h == nullptr || h->numInts != kNumInts || h->numFds < 2 || h->numFds > kMaxPlanes + 1) return false;
    memcpy(out, &h->data[h->numFds], sizeof(HandleData));
    return out->magic == kMagic && out->version == kVersion && static_cast<int>(out->numPlanes) + 1 == h->numFds;
}

inline void encode(native_handle_t* h, const HandleData& data) {
    memcpy(&h->data[h->numFds], &data, sizeof(HandleData));
}

constexpr size_t kMetadataSize = 4096;

// Mutable metadata, one page per buffer in shared memory.
struct SharedMetadata {
    int32_t dataspace;  // aidl Dataspace
    int32_t blendMode;  // aidl BlendMode
    uint32_t hasSmpte2086, hasCta861_3;
    float primaryRed[2], primaryGreen[2], primaryBlue[2], whitePoint[2], maxLuminance, minLuminance;  // SMPTE 2086
    float maxContentLightLevel, maxFrameAverageLightLevel;                                             // CTA 861.3
    char name[128];
};
static_assert(sizeof(SharedMetadata) <= kMetadataSize);

inline int metadataFd(const native_handle_t* h) { return h->data[h->numFds - 1]; }

inline SharedMetadata* mapMetadata(const native_handle_t* h) {
    void* p = mmap(nullptr, kMetadataSize, PROT_READ | PROT_WRITE, MAP_SHARED, metadataFd(h), 0);
    return p == MAP_FAILED ? nullptr : static_cast<SharedMetadata*>(p);
}

inline void unmapMetadata(SharedMetadata* m) { munmap(m, kMetadataSize); }

}  // namespace apklayer::gralloc
