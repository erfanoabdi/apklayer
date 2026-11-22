/* bridge/shell/sharpen.c without a compositor: a raw RGBX picture goes into a dmabuf (a linear gbm buffer on the first render node), through the
 * upscale pass, and back as raw RGBX. tests/vm/sharpen.py compares the result with a bilinear stretch. Usage: sharpen-test in.raw w h out.raw ow oh [sharpness [accepted modifier, hex]] */
#define _GNU_SOURCE
#include <fcntl.h>
#include <gbm.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "../../bridge/shell/sharpen.h"

int main(int argc, char **argv)
{
    if (argc < 7) { fprintf(stderr, "usage: sharpen-test in.raw w h out.raw ow oh [sharpness]\n"); return 2; }
    int w = atoi(argv[2]), h = atoi(argv[3]), ow = atoi(argv[5]), oh = atoi(argv[6]);
    float sharp = argc > 7 ? (float)atof(argv[7]) : 0.5f;
    FILE *f = fopen(argv[1], "rb");
    uint8_t *in = malloc((size_t)w * h * 4);
    if (!f || fread(in, 4, (size_t)w * h, f) != (size_t)w * h) { fprintf(stderr, "cannot read %s\n", argv[1]); return 2; }
    fclose(f);
    /* the producer's buffer: every render node in turn until one allocates it */
    int fd = -1;
    struct gbm_device *gbm = NULL;
    struct gbm_bo *bo = NULL;
    for (int n = 128; n < 140 && !bo; n++) {
        char path[32];
        snprintf(path, sizeof path, "/dev/dri/renderD%d", n);
        fd = open(path, O_RDWR | O_CLOEXEC);
        if (fd < 0) continue;
        gbm = gbm_create_device(fd);
        if (gbm) bo = gbm_bo_create(gbm, (uint32_t)w, (uint32_t)h, GBM_FORMAT_XRGB8888, GBM_BO_USE_LINEAR | GBM_BO_USE_RENDERING);
        if (gbm && !bo) bo = gbm_bo_create(gbm, (uint32_t)w, (uint32_t)h, GBM_FORMAT_XRGB8888, GBM_BO_USE_LINEAR | GBM_BO_USE_WRITE);  /* NVIDIA's gbm has no linear render target */
        if (!bo) { if (gbm) gbm_device_destroy(gbm); close(fd); }
    }
    if (!bo) { fprintf(stderr, "no gbm buffer\n"); return 3; }
    uint32_t stride, mapstride;
    void *mapdata = NULL;
    uint8_t *map = gbm_bo_map(bo, 0, 0, (uint32_t)w, (uint32_t)h, GBM_BO_TRANSFER_WRITE, &mapstride, &mapdata);
    if (!map) { fprintf(stderr, "cannot map the buffer\n"); return 3; }
    for (int y = 0; y < h; y++)  /* RGBX bytes in, XRGB8888 (B, G, R, X in memory) out */
        for (int x = 0; x < w; x++) {
            const uint8_t *p = in + ((size_t)y * w + x) * 4;
            uint8_t *q = map + (size_t)y * mapstride + (size_t)x * 4;
            q[0] = p[2]; q[1] = p[1]; q[2] = p[0]; q[3] = 255;
        }
    gbm_bo_unmap(bo, mapdata);
    int dfd = gbm_bo_get_fd(bo);
    stride = gbm_bo_get_stride(bo);
    uint32_t off = gbm_bo_get_offset(bo, 0);
    struct sharpen *s = sharpen_open();
    if (argc > 8) {  /* the accepted modifiers: hex, comma separated */
        uint64_t mods[64];
        int n = 0;
        for (char *tok = strtok(argv[8], ","); tok && n < 64; tok = strtok(NULL, ",")) mods[n++] = strtoull(tok, NULL, 16);
        sharpen_accept(s, mods, n);
    }
    if (sharpen_import(s, 0, 0x34325258u, w, h, 1, &dfd, &stride, &off, gbm_bo_get_modifier(bo)) < 0) { fprintf(stderr, "import failed\n"); return 4; }
    int slot, fresh;
    if (getenv("SHARPEN_MODE")) sharpen_set_mode(s, atoi(getenv("SHARPEN_MODE")));
    if (sharpen_render(s, 0, ow, oh, sharp, &slot, &fresh) < 0) { fprintf(stderr, "render failed\n"); return 5; }
    uint8_t *out = malloc((size_t)ow * oh * 4);
    if (sharpen_readback(s, slot, out) < 0) { fprintf(stderr, "readback failed\n"); return 6; }
    struct sharpen_out info;
    if (sharpen_slot_info(s, slot, &info) < 0) { fprintf(stderr, "no slot info\n"); return 7; }
    fprintf(stderr, "ok: %dx%d -> %dx%d, output dmabuf stride %u modifier 0x%llx\n", w, h, ow, oh, info.stride, (unsigned long long)info.modifier);
    close(info.fd);
    f = fopen(argv[4], "wb");
    fwrite(out, 4, (size_t)ow * oh, f);
    fclose(f);
    sharpen_close(s);
    return 0;
}
