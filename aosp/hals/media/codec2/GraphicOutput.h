/* Shared software-YUV to mapper-described gralloc copy, published before callbacks. */
#pragma once
#include "DecoderParams.h"
#include <apklayer/codec.h>
#include <C2PlatformSupport.h>
#include <cstdio>
#include <chrono>
#include <cstring>
#include <vector>
namespace apklayer {
struct DecodedGraphic {
    std::shared_ptr<C2GraphicBlock> block;
    uint32_t width, height;
    bool changed;
    int64_t fetchUs = 0;  // time waiting for a free output buffer (the consumer releasing one)
};
inline c2_status_t copyGraphic(apklayer_codec_frame *raw, const std::shared_ptr<C2BlockPool>& pool,
        const std::shared_ptr<DecoderParams>& params, DecodedGraphic *out) {
    apklayer_codec_frame_info info;
    apklayer_codec_frame_describe(raw, &info);
    const void *planes[4];
    c2_status_t status = C2_OK;
    if (strcmp(info.format, "I420") || apklayer_codec_frame_map(raw, planes)) {
        status = C2_CANNOT_DO; return status;
    }
    auto& block = out->block;
    auto t0 = std::chrono::steady_clock::now();
    status = pool->fetchGraphicBlock((info.width + 15) & ~15u, (info.height + 1) & ~1u,
        HAL_PIXEL_FORMAT_YCBCR_420_888, {C2MemoryUsage::CPU_READ, C2MemoryUsage::CPU_WRITE}, &block);
    out->fetchUs = std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now() - t0).count();
    if (status != C2_OK) { fprintf(stderr, "codec2: fetch graphic block %d\n", status); return status; }
    { // Publish CPU plane writes before notifying any cloned output work.
        C2GraphicView target = block->map().get();
        if (target.error()) { fprintf(stderr, "codec2: output map %d\n", target.error()); status = target.error(); return status; }
        auto layout = target.layout();
        // The block is write-combined dmabuf memory: whole rows are written at once (memcpy, or one interleaved row for semi-planar
        // chroma). Byte stores at a stride cost ~85 ms per 720p frame here.
        uint8_t *const *dst = target.data();
        const unsigned cw = (info.width + 1) / 2, ch = (info.height + 1) / 2;
        bool semiPlanar = layout.planes[1].colInc == 2 && layout.planes[2].colInc == 2 && layout.planes[1].rowInc == layout.planes[2].rowInc &&
                (dst[1] - dst[2] == 1 || dst[2] - dst[1] == 1);
        for (unsigned y = 0; y < info.height; y++)
            memcpy(dst[0] + y * layout.planes[0].rowInc, static_cast<const uint8_t *>(planes[0]) + y * info.stride[0], info.width);
        if (semiPlanar) {
            bool uFirst = dst[1] < dst[2];
            uint8_t *base = uFirst ? dst[1] : dst[2];
            std::vector<uint8_t> row(cw * 2);
            for (unsigned y = 0; y < ch; y++) {
                auto *u = static_cast<const uint8_t *>(planes[1]) + y * info.stride[1];
                auto *v = static_cast<const uint8_t *>(planes[2]) + y * info.stride[2];
                for (unsigned x = 0; x < cw; x++) { row[2 * x + (uFirst ? 0 : 1)] = u[x]; row[2 * x + (uFirst ? 1 : 0)] = v[x]; }
                memcpy(base + y * layout.planes[1].rowInc, row.data(), cw * 2);
            }
        } else {
            for (unsigned p = 1; p < 3; p++) {
                const auto& plane = layout.planes[p];
                for (unsigned y = 0; y < ch; y++) {
                    auto *d = dst[p] + y * plane.rowInc;
                    auto *src = static_cast<const uint8_t *>(planes[p]) + y * info.stride[p];
                    if (plane.colInc == 1) memcpy(d, src, cw);
                    else for (unsigned x = 0; x < cw; x++) d[x * plane.colInc] = src[x];
                }
            }
        }
    }
    C2StreamPictureSizeInfo::output size(0u, info.width, info.height);
    bool changed = info.width != params->width() || info.height != params->height();
    if (changed) {
        std::vector<std::unique_ptr<C2SettingResult>> failures;
        status = params->config({&size}, C2_MAY_BLOCK, &failures);
        if (status != C2_OK) return status;
    }
    out->width = info.width; out->height = info.height; out->changed = changed;
    return C2_OK;
}
}
