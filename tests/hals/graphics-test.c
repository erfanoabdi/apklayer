/* Host graphics HAL test (run on a machine with a render node): which formats the device allocates, and that a CPU-written
 * buffer reads back identical through a second mapping of the dmabuf (the zero-copy path's basic contract). */
#include "apklayer/graphics.h"

#include <drm_fourcc.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static int failures;
#define CHECK(cond, what) do { if (cond) printf("PASS  %s\n", what); else { printf("FAIL  %s\n", what); failures++; } } while (0)

static const struct { uint32_t fourcc; const char *name; } formats[] = {
    { DRM_FORMAT_ARGB8888, "ARGB8888" }, { DRM_FORMAT_ABGR8888, "ABGR8888" }, { DRM_FORMAT_XRGB8888, "XRGB8888" },
    { DRM_FORMAT_RGB565, "RGB565" }, { DRM_FORMAT_ABGR16161616F, "RGBA_F16" }, { DRM_FORMAT_ABGR2101010, "RGBA_1010102" },
    { DRM_FORMAT_NV12, "NV12" }, { DRM_FORMAT_YUV420, "I420" }, { DRM_FORMAT_YVU420, "YV12" }, { DRM_FORMAT_P010, "P010" },
};

int main(void)
{
    struct apklayer_gfx *gfx = apklayer_gfx_open(NULL);
    CHECK(gfx != NULL, "render node opens");
    if (!gfx) return 1;

    printf("format support (sample+render | sample+cpu):\n");
    for (size_t i = 0; i < sizeof formats / sizeof *formats; i++)
        printf("  %-13s %s | %s\n", formats[i].name,
               apklayer_gfx_supports(gfx, formats[i].fourcc, APKLAYER_GFX_SAMPLE | APKLAYER_GFX_RENDER) ? "yes" : "no ",
               apklayer_gfx_supports(gfx, formats[i].fourcc, APKLAYER_GFX_SAMPLE | APKLAYER_GFX_CPU_WRITE) ? "yes" : "no");

    struct apklayer_gfx_buffer b;
    int r = apklayer_gfx_alloc(gfx, 256, 128, DRM_FORMAT_ARGB8888, APKLAYER_GFX_SAMPLE | APKLAYER_GFX_CPU_WRITE | APKLAYER_GFX_CPU_READ, &b);
    CHECK(r == 0, "ARGB8888 CPU buffer allocates");
    if (r == 0) {
        printf("  planes=%d stride=%u modifier=0x%llx\n", b.num_planes, b.stride[0], (unsigned long long)b.modifier);
        uint32_t stride = 0, stride2 = 0;
        struct apklayer_gfx_mapping *m1 = NULL, *m2 = NULL;
        uint32_t *w = apklayer_gfx_map(gfx, &b, 0, true, &stride, &m1);
        CHECK(w != NULL, "buffer maps");
        if (!w) printf("  map errno: %s\n", strerror(errno));
        if (w) {
            for (uint32_t y = 0; y < b.height; y++)
                for (uint32_t x = 0; x < b.width; x++) w[y * (stride / 4) + x] = 0xff000000u | (y << 8) | x;
            apklayer_gfx_unmap(m1); /* the write reaches the buffer on unmap */

            struct apklayer_gfx_buffer copy = b;
            copy.fd[0] = dup(b.fd[0]); /* what another process holds after receiving the fd */
            uint32_t *rd = apklayer_gfx_map(gfx, &copy, 0, false, &stride2, &m2);
            int same = rd != NULL;
            for (uint32_t y = 0; rd && y < b.height && same; y++)
                for (uint32_t x = 0; x < b.width; x++)
                    if (rd[y * (stride2 / 4) + x] != (0xff000000u | (y << 8) | x)) { same = 0; break; }
            CHECK(same, "an imported copy of the dmabuf reads the same pixels");
            apklayer_gfx_unmap(m2);
            close(copy.fd[0]);
        }
        apklayer_gfx_buffer_close(&b);
    }

    /* YUV: native if the driver has it, else packed into one allocation. Each plane gets a marker; all must read back. */
    static const struct { uint32_t fourcc; const char *name; } yuv[] = {
        { DRM_FORMAT_NV12, "NV12" }, { DRM_FORMAT_NV21, "NV21" }, { DRM_FORMAT_NV16, "NV16" },
        { DRM_FORMAT_YUV420, "I420" }, { DRM_FORMAT_YVU420, "YV12" }, { DRM_FORMAT_P010, "P010" } };
    for (size_t i = 0; i < sizeof yuv / sizeof *yuv; i++) {
        int r2 = apklayer_gfx_alloc(gfx, 64, 64, yuv[i].fourcc, APKLAYER_GFX_SAMPLE | APKLAYER_GFX_CPU_WRITE | APKLAYER_GFX_CPU_READ, &b);
        char what[96];
        snprintf(what, sizeof what, "%s allocates (planes=%d, packed=%d)", yuv[i].name, r2 == 0 ? b.num_planes : 0, r2 == 0 ? b.packed : 0);
        CHECK(r2 == 0, what);
        if (r2 != 0) continue;
        uint32_t st[4]; struct apklayer_gfx_mapping *mm[4] = { 0 }; uint8_t *px[4];
        int mapped = 1;
        for (int p = 0; p < b.num_planes; p++) {
            px[p] = apklayer_gfx_map(gfx, &b, p, true, &st[p], &mm[p]);
            if (!px[p]) mapped = 0;
        }
        snprintf(what, sizeof what, "%s planes map (strides %u/%u/%u, offsets %u/%u/%u)", yuv[i].name, st[0], st[1], st[2],
                 b.offset[0], b.offset[1], b.offset[2]);
        CHECK(mapped, what);
        if (mapped) {
            for (int p = 0; p < b.num_planes; p++) { px[p][0] = (uint8_t)(0x10 + p); px[p][8] = (uint8_t)(0x20 + p); }
            int ok = 1;
            for (int p = 0; p < b.num_planes; p++) ok &= px[p][0] == 0x10 + p && px[p][8] == 0x20 + p;
            snprintf(what, sizeof what, "%s plane markers do not overlap", yuv[i].name);
            CHECK(ok, what);
        }
        for (int p = 0; p < b.num_planes; p++) apklayer_gfx_unmap(mm[p]);
        /* Mapper's one-base-pointer view must preserve every plane at unlock. */
        struct apklayer_gfx_mapping *linear = NULL;
        uint32_t linear_stride;
        uint8_t *base = apklayer_gfx_map_linear(gfx, &b, true, &linear_stride, &linear);
        CHECK(base != NULL, "contiguous YUV CPU view maps");
        if (base) {
            size_t offset = 0;
            int preserved = 1;
            for (int p = 0; p < b.num_planes; p++) {
                preserved &= base[offset] == 0x10 + p && base[offset + 8] == 0x20 + p;
                base[offset + 1] = 0x80 + p;
                offset += b.stride[p] * (p && b.fourcc != DRM_FORMAT_NV16 ? b.height / 2 : b.height);
            }
            CHECK(preserved, "plane markers survive unmap and contiguous remap");
            apklayer_gfx_unmap(linear);
            preserved = 1;
            for (int p = 0; p < b.num_planes; p++) {
                px[p] = apklayer_gfx_map(gfx, &b, p, false, &st[p], &mm[p]);
                preserved &= px[p] && px[p][0] == 0x10 + p && px[p][1] == 0x80 + p;
                apklayer_gfx_unmap(mm[p]);
            }
            CHECK(preserved, "contiguous YUV writes reach each GPU plane");
        }
        apklayer_gfx_buffer_close(&b);
    }

    r = apklayer_gfx_alloc_blob(gfx, 4096, &b);
    CHECK(r == 0, "BLOB (plain memory) allocates");
    if (r == 0) {
        uint32_t st; struct apklayer_gfx_mapping *mm = NULL;
        uint8_t *px = apklayer_gfx_map(gfx, &b, 0, true, &st, &mm);
        CHECK(px != NULL && st == 4096, "BLOB maps");
        if (px) { px[4095] = 7; apklayer_gfx_unmap(mm); }
        apklayer_gfx_buffer_close(&b);
    }

    CHECK(apklayer_gfx_alloc(gfx, 64, 64, 0x12345678, APKLAYER_GFX_SAMPLE, &b) == -ENOTSUP, "unknown format is refused explicitly (-ENOTSUP)");
    apklayer_gfx_close(gfx);
    printf(failures ? "%d FAILED\n" : "all passed\n", failures);
    return failures != 0;
}
