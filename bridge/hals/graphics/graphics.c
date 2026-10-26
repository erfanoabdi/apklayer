/* apklayer graphics HAL, host side: GPU buffers from libgbm (see include/apklayer/graphics.h). */
#define _GNU_SOURCE
#include "apklayer/graphics.h"

#include <dirent.h>
#include <drm_fourcc.h>
#include <errno.h>
#include <fcntl.h>
#include <gbm.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <string.h>
#include <unistd.h>

struct apklayer_gfx {
    int fd;
    struct gbm_device *gbm;
};

static int open_node(const char *path)
{
    return open(path, O_RDWR | O_CLOEXEC);
}

struct apklayer_gfx *apklayer_gfx_open(const char *render_node)
{
    if (!render_node) render_node = getenv("APKLAYER_RENDER_NODE");
    int fd = -1;
    if (render_node) {
        fd = open_node(render_node);
    } else {
        char path[64];
        for (int i = 128; i < 192 && fd < 0; i++) {
            snprintf(path, sizeof path, "/dev/dri/renderD%d", i);
            fd = open_node(path);
        }
    }
    if (fd < 0) return NULL;
    struct apklayer_gfx *gfx = calloc(1, sizeof *gfx);
    if (!gfx) { close(fd); errno = ENOMEM; return NULL; }
    gfx->fd = fd;
    gfx->gbm = gbm_create_device(fd);
    if (!gfx->gbm) { close(fd); free(gfx); errno = ENODEV; return NULL; }
    return gfx;
}

void apklayer_gfx_close(struct apklayer_gfx *gfx)
{
    if (!gfx) return;
    gbm_device_destroy(gfx->gbm);
    close(gfx->fd);
    free(gfx);
}

static uint32_t gbm_flags(uint32_t usage)
{
    uint32_t f = 0;
    /* APKLAYER_GFX_SAMPLE needs no flag: GBM has no texturing bit, any allocated buffer can be imported as a texture */
    if (usage & APKLAYER_GFX_RENDER) f |= GBM_BO_USE_RENDERING;
    if (usage & APKLAYER_GFX_SCANOUT) f |= GBM_BO_USE_SCANOUT;
    if (usage & (APKLAYER_GFX_CPU_READ | APKLAYER_GFX_CPU_WRITE)) f |= GBM_BO_USE_LINEAR; /* the CPU needs a linear layout */
    return f;
}

/* Multi-planar formats packed into one linear R8/R16 allocation, for drivers whose GBM has no YUV. chroma_rows: rows of the
 * allocation (in luma-stride units) per plane after the first, as {numerator, denominator} of the luma height. */
static const struct pack_spec {
    uint32_t fourcc, base;  /* base: DRM_FORMAT_R8 or DRM_FORMAT_R16 */
    int planes;
    struct { int num, den; int stride_div; } plane[3]; /* plane height = h * num / den; stride = luma stride / stride_div */
} packs[] = {
    { DRM_FORMAT_NV12, DRM_FORMAT_R8, 2, { { 1, 1, 1 }, { 1, 2, 1 } } },
    { DRM_FORMAT_NV21, DRM_FORMAT_R8, 2, { { 1, 1, 1 }, { 1, 2, 1 } } },
    { DRM_FORMAT_NV16, DRM_FORMAT_R8, 2, { { 1, 1, 1 }, { 1, 1, 1 } } },
    { DRM_FORMAT_YUV420, DRM_FORMAT_R8, 3, { { 1, 1, 1 }, { 1, 2, 2 }, { 1, 2, 2 } } },
    { DRM_FORMAT_YVU420, DRM_FORMAT_R8, 3, { { 1, 1, 1 }, { 1, 2, 2 }, { 1, 2, 2 } } },
    { DRM_FORMAT_P010, DRM_FORMAT_R16, 2, { { 1, 1, 1 }, { 1, 2, 1 } } },
};

static const struct pack_spec *find_pack(uint32_t fourcc)
{
    for (size_t i = 0; i < sizeof packs / sizeof *packs; i++) if (packs[i].fourcc == fourcc) return &packs[i];
    return NULL;
}

/* Rows of the packed allocation: each plane's bytes (stride/stride_div * height*num/den) expressed in luma-stride rows. */
static uint32_t packed_rows(const struct pack_spec *sp, uint32_t h)
{
    uint32_t rows = 0;
    for (int i = 0; i < sp->planes; i++) rows += (h * sp->plane[i].num / sp->plane[i].den) / sp->plane[i].stride_div;
    return rows;
}

bool apklayer_gfx_supports(struct apklayer_gfx *gfx, uint32_t fourcc, uint32_t usage)
{
    if (gbm_device_is_format_supported(gfx->gbm, fourcc, gbm_flags(usage)) != 0) return true;
    const struct pack_spec *sp = find_pack(fourcc);
    return sp && gbm_device_is_format_supported(gfx->gbm, sp->base, gbm_flags(usage)) != 0;
}

static void buffer_init(struct apklayer_gfx_buffer *out, uint32_t w, uint32_t h, uint32_t fourcc)
{
    memset(out, 0, sizeof *out);
    for (int i = 0; i < APKLAYER_GFX_MAX_PLANES; i++) out->fd[i] = -1;
    out->width = w;
    out->height = h;
    out->fourcc = fourcc;
    out->alloc_fourcc = fourcc;
    out->alloc_width = w;
    out->alloc_height = h;
}

static int alloc_packed(struct apklayer_gfx *gfx, struct apklayer_gfx_buffer *out, const struct pack_spec *sp, uint32_t usage)
{
    uint32_t w = out->width, h = out->height;
    uint32_t rows = packed_rows(sp, h);
    struct gbm_bo *bo = gbm_bo_create(gfx->gbm, w, rows, sp->base, gbm_flags(usage) | GBM_BO_USE_LINEAR);
    if (!bo) return errno ? -errno : -ENOMEM;
    uint32_t stride = gbm_bo_get_stride_for_plane(bo, 0);
    int fd = gbm_bo_get_fd_for_plane(bo, 0);
    out->modifier = gbm_bo_get_modifier(bo);
    out->alloc_fourcc = sp->base;
    out->alloc_width = w;
    out->alloc_height = rows;
    gbm_bo_destroy(bo); /* the dmabuf fd keeps the memory alive */
    if (fd < 0) return -EIO;
    out->packed = 1;
    out->num_planes = sp->planes;
    uint32_t off = 0;
    for (int i = 0; i < sp->planes; i++) {
        out->fd[i] = i == 0 ? fd : fcntl(fd, F_DUPFD_CLOEXEC, 3);
        out->stride[i] = stride / sp->plane[i].stride_div;
        out->offset[i] = off;
        off += out->stride[i] * (h * sp->plane[i].num / sp->plane[i].den);
        if (out->fd[i] < 0) return -errno;
    }
    return 0;
}

int apklayer_gfx_alloc(struct apklayer_gfx *gfx, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t usage,
                       struct apklayer_gfx_buffer *out)
{
    if (!apklayer_gfx_supports(gfx, fourcc, usage)) return -ENOTSUP;
    buffer_init(out, width, height, fourcc);
    int r = 0;
    if (gbm_device_is_format_supported(gfx->gbm, fourcc, gbm_flags(usage))) {
        struct gbm_bo *bo = gbm_bo_create(gfx->gbm, width, height, fourcc, gbm_flags(usage));
        if (!bo) return errno ? -errno : -ENOMEM;
        out->modifier = gbm_bo_get_modifier(bo);
        out->num_planes = gbm_bo_get_plane_count(bo);
        r = out->num_planes < 1 || out->num_planes > APKLAYER_GFX_MAX_PLANES ? -ENOTSUP : 0;
        for (int i = 0; !r && i < out->num_planes; i++) {
            out->fd[i] = gbm_bo_get_fd_for_plane(bo, i);
            out->stride[i] = gbm_bo_get_stride_for_plane(bo, i);
            out->offset[i] = gbm_bo_get_offset(bo, i);
            if (out->fd[i] < 0) r = errno ? -errno : -EIO;
        }
        gbm_bo_destroy(bo);
    } else {
        r = alloc_packed(gfx, out, find_pack(fourcc), usage);
    }
    if (r) apklayer_gfx_buffer_close(out);
    return r;
}

int apklayer_gfx_alloc_blob(struct apklayer_gfx *gfx, uint64_t size, struct apklayer_gfx_buffer *out)
{
    (void)gfx;
    buffer_init(out, (uint32_t)size, 1, 0);
    int fd = memfd_create("apklayer-blob", MFD_CLOEXEC);
    if (fd < 0) return -errno;
    if (ftruncate(fd, (off_t)size) < 0) { int e = errno; close(fd); return -e; }
    out->num_planes = 1;
    out->fd[0] = fd;
    out->stride[0] = (uint32_t)size;
    return 0;
}

void apklayer_gfx_buffer_close(struct apklayer_gfx_buffer *buf)
{
    for (int i = 0; i < APKLAYER_GFX_MAX_PLANES; i++) {
        if (buf->fd[i] < 0) continue;
        for (int j = i + 1; j < APKLAYER_GFX_MAX_PLANES; j++) if (buf->fd[j] == buf->fd[i]) buf->fd[j] = -1; /* a repeated fd */
        close(buf->fd[i]);
        buf->fd[i] = -1;
    }
}

struct apklayer_gfx_mapping {
    struct gbm_bo *bo;
    void *map_data;
    void *blob;      /* plain memory (fourcc 0): the mmap, or NULL */
    size_t blob_len;
};

void *apklayer_gfx_map(struct apklayer_gfx *gfx, const struct apklayer_gfx_buffer *buf, int plane, bool write,
                       uint32_t *stride, struct apklayer_gfx_mapping **mapping)
{
    if (plane < 0 || plane >= buf->num_planes) { errno = EINVAL; return NULL; }
    if (buf->fourcc != 0 && !buf->packed && buf->num_planes > 1) { errno = ENOTSUP; return NULL; }
    struct apklayer_gfx_mapping *m = calloc(1, sizeof *m);
    if (!m) { errno = ENOMEM; return NULL; }
    uint8_t *addr = NULL;
    if (buf->fourcc == 0) { /* plain memory */
        m->blob_len = buf->width;
        void *p = mmap(NULL, m->blob_len, PROT_READ | (write ? PROT_WRITE : 0), MAP_SHARED, buf->fd[0], 0);
        if (p != MAP_FAILED) { addr = p; m->blob = p; }
    } else {
        struct gbm_import_fd_data fd_data = {
            .fd = buf->fd[0], .width = buf->alloc_width, .height = buf->alloc_height,
            .stride = buf->packed ? buf->stride[0] : buf->stride[plane], .format = buf->alloc_fourcc,
        };
        m->bo = gbm_bo_import(gfx->gbm, GBM_BO_IMPORT_FD, &fd_data, GBM_BO_USE_LINEAR);
        uint32_t map_stride = 0;
        if (m->bo) {
            addr = gbm_bo_map(m->bo, 0, 0, fd_data.width, fd_data.height,
                              write ? GBM_BO_TRANSFER_READ_WRITE : GBM_BO_TRANSFER_READ, &map_stride, &m->map_data);
            if (!addr) gbm_bo_destroy(m->bo);
        }
        (void)map_stride; /* the buffer's own strides apply: for a packed buffer the allocation and the map agree */
    }
    if (!addr) { free(m); return NULL; }
    *stride = buf->stride[plane];
    *mapping = m;
    return addr + (buf->fourcc == 0 ? 0 : buf->offset[plane]);
}

void apklayer_gfx_unmap(struct apklayer_gfx_mapping *m)
{
    if (!m) return;
    if (m->blob) munmap(m->blob, m->blob_len);
    if (m->bo) { gbm_bo_unmap(m->bo, m->map_data); gbm_bo_destroy(m->bo); }
    free(m);
}
