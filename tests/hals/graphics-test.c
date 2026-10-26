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

    int nv12 = apklayer_gfx_alloc(gfx, 64, 64, DRM_FORMAT_NV12, APKLAYER_GFX_SAMPLE, &b);
    printf("NV12 sample-only alloc: %s", nv12 == 0 ? "ok" : strerror(-nv12));
    if (nv12 == 0) { printf(" (planes=%d, strides %u/%u, modifier 0x%llx)", b.num_planes, b.stride[0], b.stride[1], (unsigned long long)b.modifier); apklayer_gfx_buffer_close(&b); }
    printf("\n");

    CHECK(apklayer_gfx_alloc(gfx, 64, 64, 0x12345678, APKLAYER_GFX_SAMPLE, &b) == -ENOTSUP, "unknown format is refused explicitly (-ENOTSUP)");
    apklayer_gfx_close(gfx);
    printf(failures ? "%d FAILED\n" : "all passed\n", failures);
    return failures != 0;
}
