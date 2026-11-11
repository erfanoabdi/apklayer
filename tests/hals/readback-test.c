/* The GPU copy of buffers the CPU cannot map (bridge/hals/graphics/readback.c, NVIDIA's path for tiled buffers), on this machine's GPU:
 * for each format it moves (RGBA/BGRA/RGBX/BGRX 8888, RGB888, RGB565), a pattern written into a tiled and a LINEAR buffer through the GPU
 * (apklayer_gfx_upload) reads back identical (apklayer_gfx_readback); where the driver maps the LINEAR buffer, its bytes are the
 * pattern's too, so the CPU conversion matches the format's real layout (a swizzle both ways wrong would still round-trip).
 * Then the HAL's own path for a software-rendered window (apklayer_gfx_alloc with the composer's usage, a CPU map written, unmapped, mapped
 * again and read): on a GPU that samples LINEAR only externally (NVIDIA) that buffer is tiled and both maps go through the GPU.
 * Usage: readback-test [render node]. Exit 0 when every format the device allocates passes. */
#include "../../bridge/hals/graphics/readback.h"
#include "apklayer/graphics.h"

#include <drm_fourcc.h>
#include <fcntl.h>
#include <gbm.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static const struct { uint32_t fourcc; unsigned bpp; int x_byte; const char *name; } formats[] = {
    { DRM_FORMAT_ABGR8888, 4, -1, "ABGR8888 (RGBA_8888)" }, { DRM_FORMAT_XBGR8888, 4, 3, "XBGR8888 (RGBX_8888)" },
    { DRM_FORMAT_ARGB8888, 4, -1, "ARGB8888 (BGRA_8888)" }, { DRM_FORMAT_XRGB8888, 4, 3, "XRGB8888" },
    { DRM_FORMAT_BGR888, 3, -1, "BGR888 (RGB_888)" }, { DRM_FORMAT_RGB565, 2, -1, "RGB565 (RGB_565)" },
};

static int same(const uint8_t *a, uint32_t a_stride, const uint8_t *b, uint32_t b_stride, uint32_t w, uint32_t h, unsigned bpp, int x_byte)
{
    for (uint32_t y = 0; y < h; y++)
        for (uint32_t x = 0; x < w * bpp; x++) {
            if (x_byte >= 0 && (int)(x % bpp) == x_byte) continue;  /* the X byte is undefined */
            if (a[(size_t)y * a_stride + x] != b[(size_t)y * b_stride + x]) {
                fprintf(stderr, "    differs at pixel %u,%u byte %u: 0x%02x vs 0x%02x\n", x / bpp, y, x % bpp, a[(size_t)y * a_stride + x], b[(size_t)y * b_stride + x]);
                return 0;
            }
        }
    return 1;
}

int main(int argc, char **argv)
{
    const char *node = argc > 1 ? argv[1] : "/dev/dri/renderD128";
    int dev = open(node, O_RDWR | O_CLOEXEC);
    struct gbm_device *gbm = dev >= 0 ? gbm_create_device(dev) : NULL;
    if (!gbm) { printf("FAIL  %s opens as a GBM device\n", node); return 1; }
    const uint32_t w = 67, h = 33;  /* odd sizes: row padding and strides other than width * bpp */
    int failures = 0, passes = 0;
    for (size_t f = 0; f < sizeof formats / sizeof *formats; f++) {
        unsigned bpp = formats[f].bpp;
        uint32_t src_stride = w * bpp + 8;
        uint8_t *src = malloc((size_t)src_stride * h), *dst = malloc((size_t)src_stride * h);
        for (size_t i = 0; i < (size_t)src_stride * h; i++) src[i] = (uint8_t)(i * 131 + 7 + i / src_stride * 17);
        for (int linear = 0; linear < 2; linear++) {
            uint32_t flags = GBM_BO_USE_RENDERING | (linear ? GBM_BO_USE_LINEAR : 0);
            struct gbm_bo *bo = gbm_device_is_format_supported(gbm, formats[f].fourcc, flags) ? gbm_bo_create(gbm, w, h, formats[f].fourcc, flags) : NULL;
            if (!bo) { printf("SKIP  %s %s: the device does not allocate it\n", formats[f].name, linear ? "LINEAR" : "tiled"); continue; }
            int fd = gbm_bo_get_fd(bo);
            uint32_t stride = gbm_bo_get_stride(bo), offset = gbm_bo_get_offset(bo, 0);
            uint64_t mod = gbm_bo_get_modifier(bo);
            memset(dst, 0, (size_t)src_stride * h);
            int up = apklayer_gfx_upload(gbm, fd, w, h, formats[f].fourcc, stride, offset, mod, src, src_stride);
            int rd = up ? up : apklayer_gfx_readback(gbm, fd, w, h, formats[f].fourcc, stride, offset, mod, dst, src_stride);
            int ok = !up && !rd && same(src, src_stride, dst, src_stride, w, h, bpp, formats[f].x_byte);
            printf("%s  %s %s (modifier 0x%llx): upload %d, readback %d, round trip %s\n", ok ? "PASS" : "FAIL", formats[f].name,
                   linear ? "LINEAR" : "tiled", (unsigned long long)mod, up, rd, ok ? "identical" : "differs");
            ok ? passes++ : failures++;
            if (linear && !up) {
                uint32_t map_stride = 0;
                void *map_data = NULL;
                uint8_t *p = gbm_bo_map(bo, 0, 0, w, h, GBM_BO_TRANSFER_READ, &map_stride, &map_data);
                if (!p) printf("SKIP  %s LINEAR bytes: the driver does not map it\n", formats[f].name);
                else {
                    int raw = same(src, src_stride, p, map_stride, w, h, bpp, formats[f].x_byte);
                    printf("%s  %s LINEAR bytes as the GPU wrote them match the CPU layout\n", raw ? "PASS" : "FAIL", formats[f].name);
                    raw ? passes++ : failures++;
                    gbm_bo_unmap(bo, map_data);
                }
            }
            close(fd);
            gbm_bo_destroy(bo);
        }
        free(src);
        free(dst);
    }
    struct apklayer_gfx *gfx = apklayer_gfx_open(node);
    if (!gfx) { printf("FAIL  %s opens as the graphics HAL\n", node); failures++; }
    const uint32_t window = APKLAYER_GFX_SAMPLE | APKLAYER_GFX_SCANOUT | APKLAYER_GFX_CPU_READ | APKLAYER_GFX_CPU_WRITE;  /* Surface.lockCanvas */
    for (size_t f = 0; gfx && f < sizeof formats / sizeof *formats; f++) {
        struct apklayer_gfx_buffer b;
        if (!apklayer_gfx_supports(gfx, formats[f].fourcc, window) || apklayer_gfx_alloc(gfx, w, h, formats[f].fourcc, window, &b)) {
            printf("SKIP  %s window buffer: not allocated\n", formats[f].name);
            continue;
        }
        unsigned bpp = formats[f].bpp;
        uint32_t stride = 0;
        struct apklayer_gfx_mapping *m = NULL;
        uint8_t *p = apklayer_gfx_map(gfx, &b, 0, true, &stride, &m);
        uint8_t *pattern = malloc((size_t)w * bpp * h);
        for (size_t i = 0; i < (size_t)w * bpp * h; i++) pattern[i] = (uint8_t)(i * 29 + 3);
        int ok = p != NULL;
        if (p) {
            for (uint32_t y = 0; y < h; y++) memcpy(p + (size_t)y * stride, pattern + (size_t)y * w * bpp, (size_t)w * bpp);
            apklayer_gfx_unmap(m);
            p = apklayer_gfx_map(gfx, &b, 0, false, &stride, &m);
            ok = p && same(pattern, w * bpp, p, stride, w, h, bpp, formats[f].x_byte);
            if (p) apklayer_gfx_unmap(m);
        }
        printf("%s  %s window buffer (modifier 0x%llx%s): CPU write, then CPU read %s\n", ok ? "PASS" : "FAIL", formats[f].name,
               (unsigned long long)b.modifier, b.modifier == DRM_FORMAT_MOD_LINEAR ? ", LINEAR" : ", tiled", ok ? "identical" : "differs");
        ok ? passes++ : failures++;
        free(pattern);
        apklayer_gfx_buffer_close(&b);
    }
    if (gfx) apklayer_gfx_close(gfx);
    printf("%d passed, %d failed\n", passes, failures);
    gbm_device_destroy(gbm);
    close(dev);
    return failures || !passes;
}
