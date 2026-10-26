// The native_handle of an apklayer gralloc buffer, shared by the allocator and the mapper. fds: one per plane, in plane order
// (a plane-split buffer has a separate dmabuf per plane, see bridge/hals/graphics). ints: a HandleData, copied in and out.
#pragma once

#include <cutils/native_handle.h>
#include <string.h>

#include <algorithm>
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
    uint32_t planeFourcc[kMaxPlanes], planeWidth[kMaxPlanes], planeHeight[kMaxPlanes];
    uint64_t usage;     // aidl BufferUsage
    uint64_t modifier;  // DRM format modifier
    uint64_t id;        // unique per allocation (backing store id)
    uint64_t reservedSize;
};
static_assert(sizeof(HandleData) % sizeof(int) == 0, "HandleData is stored in native_handle ints");
constexpr int kNumInts = sizeof(HandleData) / sizeof(int);

inline bool decode(const native_handle_t* h, HandleData* out) {
    if (h == nullptr || h->numInts != kNumInts || h->numFds < 1 || h->numFds > kMaxPlanes) return false;
    memcpy(out, &h->data[h->numFds], sizeof(HandleData));
    return out->magic == kMagic && out->version == kVersion && static_cast<int>(out->numPlanes) == h->numFds;
}

inline void encode(native_handle_t* h, const HandleData& data) {
    memcpy(&h->data[h->numFds], &data, sizeof(HandleData));
}

}  // namespace apklayer::gralloc
