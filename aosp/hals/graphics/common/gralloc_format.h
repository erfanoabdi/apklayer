// Android's gralloc vocabulary (aidl PixelFormat, BufferUsage) to the host HAL's (DRM fourcc, APKLAYER_GFX_* usage).
// One place for the translation, used by the allocator (what to allocate) and the mapper (what a buffer is).
#pragma once

#include <aidl/android/hardware/graphics/common/BufferUsage.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#include <drm_fourcc.h>

#include <cstdint>

#include "apklayer/graphics.h"

namespace apklayer::gralloc {

using aidl::android::hardware::graphics::common::BufferUsage;
using aidl::android::hardware::graphics::common::PixelFormat;

constexpr uint64_t bit(BufferUsage u) { return static_cast<uint64_t>(u); }

// 0 = not supported. BLOB-like formats (plain memory) are handled by the allocator before this.
inline uint32_t toFourcc(PixelFormat f, uint64_t usage) {
    switch (f) {
        case PixelFormat::RGBA_8888: return DRM_FORMAT_ABGR8888;  // R,G,B,A in memory order
        case PixelFormat::RGBX_8888: return DRM_FORMAT_XBGR8888;
        case PixelFormat::BGRA_8888: return DRM_FORMAT_ARGB8888;
        case PixelFormat::RGB_888: return DRM_FORMAT_BGR888;
        case PixelFormat::RGB_565: return DRM_FORMAT_RGB565;
        case PixelFormat::RGBA_FP16: return DRM_FORMAT_ABGR16161616F;
        case PixelFormat::RGBA_1010102: return DRM_FORMAT_ABGR2101010;
        case PixelFormat::YCBCR_422_SP: return DRM_FORMAT_NV16;
        case PixelFormat::YCRCB_420_SP: return DRM_FORMAT_NV21;
        case PixelFormat::YCBCR_420_888: return DRM_FORMAT_NV12;  // flexible: the layout is reported by the mapper
        case PixelFormat::YV12: return DRM_FORMAT_YVU420;
        case PixelFormat::YCBCR_P010: return DRM_FORMAT_P010;
        case PixelFormat::Y8: case PixelFormat::R_8: return DRM_FORMAT_R8;
        case PixelFormat::Y16: case PixelFormat::RAW16: return DRM_FORMAT_R16;
        case PixelFormat::IMPLEMENTATION_DEFINED:
            // the producer decides: video encoders and cameras want YUV, everything else is an RGB surface
            if (usage & (bit(BufferUsage::VIDEO_ENCODER) | bit(BufferUsage::CAMERA_OUTPUT))) return DRM_FORMAT_NV12;
            return DRM_FORMAT_ABGR8888;
        default: return 0;
    }
}

inline uint32_t toGfxUsage(uint64_t usage) {
    uint32_t u = APKLAYER_GFX_SAMPLE;  // SurfaceFlinger and apps sample anything
    if (usage & bit(BufferUsage::GPU_RENDER_TARGET)) u |= APKLAYER_GFX_RENDER;
    if (usage & bit(BufferUsage::CPU_READ_MASK)) u |= APKLAYER_GFX_CPU_READ;
    if (usage & bit(BufferUsage::CPU_WRITE_MASK)) u |= APKLAYER_GFX_CPU_WRITE;
    if (usage & (bit(BufferUsage::COMPOSER_OVERLAY) | bit(BufferUsage::COMPOSER_CLIENT_TARGET))) u |= APKLAYER_GFX_SCANOUT;
    return u;
}

// Bytes per pixel of the first plane (RGB formats), used for the stride in pixels that gralloc reports.
inline uint32_t bytesPerPixel(uint32_t fourcc) {
    switch (fourcc) {
        case DRM_FORMAT_ABGR16161616F: return 8;
        case DRM_FORMAT_ABGR8888: case DRM_FORMAT_XBGR8888: case DRM_FORMAT_ARGB8888: case DRM_FORMAT_ABGR2101010: return 4;
        case DRM_FORMAT_BGR888: return 3;
        case DRM_FORMAT_RGB565: case DRM_FORMAT_R16: case DRM_FORMAT_P010: return 2;
        default: return 1;  // R8 and the 8-bit planar formats' luma plane
    }
}

}  // namespace apklayer::gralloc
