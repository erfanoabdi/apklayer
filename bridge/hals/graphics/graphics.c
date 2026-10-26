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

/* Multi-planar formats built from single-plane allocations, for drivers whose GBM has no YUV (virtio-gpu, llvmpipe). */
static const struct split_spec {
    uint32_t fourcc;
    int planes;
    struct { uint32_t fourcc; int sub_x, sub_y; } plane[3]; /* sub: width / sub_x, height / sub_y */
} splits[] = {
    { DRM_FORMAT_NV12, 2, { { DRM_FORMAT_R8, 1, 1 }, { DRM_FORMAT_GR88, 2, 2 } } },
    { DRM_FORMAT_NV21, 2, { { DRM_FORMAT_R8, 1, 1 }, { DRM_FORMAT_GR88, 2, 2 } } },
    { DRM_FORMAT_NV16, 2, { { DRM_FORMAT_R8, 1, 1 }, { DRM_FORMAT_GR88, 2, 1 } } },
    { DRM_FORMAT_YUV420, 3, { { DRM_FORMAT_R8, 1, 1 }, { DRM_FORMAT_R8, 2, 2 }, { DRM_FORMAT_R8, 2, 2 } } },
    { DRM_FORMAT_YVU420, 3, { { DRM_FORMAT_R8, 1, 1 }, { DRM_FORMAT_R8, 2, 2 }, { DRM_FORMAT_R8, 2, 2 } } },
    { DRM_FORMAT_P010, 2, { { DRM_FORMAT_R16, 1, 1 }, { DRM_FORMAT_GR1616, 2, 2 } } },
};

static const struct split_spec *find_split(uint32_t fourcc)
{
    for (size_t i = 0; i < sizeof splits / sizeof *splits; i++) if (splits[i].fourcc == fourcc) return &splits[i];
    return NULL;
}

bool apklayer_gfx_supports(struct apklayer_gfx *gfx, uint32_t fourcc, uint32_t usage)
{
    if (gbm_device_is_format_supported(gfx->gbm, fourcc, gbm_flags(usage)) != 0) return true;
    const struct split_spec *sp = find_split(fourcc);
    for (int i = 0; sp && i < sp->planes; i++)
        if (!gbm_device_is_format_supported(gfx->gbm, sp->plane[i].fourcc, gbm_flags(usage))) return false;
    return sp != NULL;
}

/* One GBM allocation as plane `plane` of out (a dmabuf fd, stride, offset). */
static int alloc_plane(struct apklayer_gfx *gfx, struct apklayer_gfx_buffer *out, int plane, uint32_t w, uint32_t h,
                       uint32_t fourcc, uint32_t usage)
{
    struct gbm_bo *bo = gbm_bo_create(gfx->gbm, w, h, fourcc, gbm_flags(usage));
    if (!bo) return errno ? -errno : -ENOMEM;
    out->fd[plane] = gbm_bo_get_fd_for_plane(bo, 0);
    out->stride[plane] = gbm_bo_get_stride_for_plane(bo, 0);
    out->offset[plane] = gbm_bo_get_offset(bo, 0);
    out->plane_fourcc[plane] = fourcc;
    out->plane_width[plane] = w;
    out->plane_height[plane] = h;
    if (plane == 0) out->modifier = gbm_bo_get_modifier(bo);
    gbm_bo_destroy(bo); /* the dmabuf fd keeps the memory alive */
    return out->fd[plane] < 0 ? -EIO : 0;
}

static void buffer_init(struct apklayer_gfx_buffer *out, uint32_t w, uint32_t h, uint32_t fourcc)
{
    memset(out, 0, sizeof *out);
    for (int i = 0; i < APKLAYER_GFX_MAX_PLANES; i++) out->fd[i] = -1;
    out->width = w;
    out->height = h;
    out->fourcc = fourcc;
}

int apklayer_gfx_alloc(struct apklayer_gfx *gfx, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t usage,
                       struct apklayer_gfx_buffer *out)
{
    if (!apklayer_gfx_supports(gfx, fourcc, usage)) return -ENOTSUP;
    buffer_init(out, width, height, fourcc);
    int r;
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
            out->plane_fourcc[i] = out->num_planes == 1 ? fourcc : 0; /* a real multi-plane buffer is mapped as a whole by the driver */
            out->plane_width[i] = width;
            out->plane_height[i] = height;
            if (out->fd[i] < 0) r = errno ? -errno : -EIO;
        }
        gbm_bo_destroy(bo);
    } else {
        const struct split_spec *sp = find_split(fourcc);
        out->num_planes = sp->planes;
        r = 0;
        for (int i = 0; !r && i < sp->planes; i++)
            r = alloc_plane(gfx, out, i, width / sp->plane[i].sub_x, height / sp->plane[i].sub_y, sp->plane[i].fourcc, usage);
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
    out->plane_width[0] = (uint32_t)size;
    out->plane_height[0] = 1;
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
    struct apklayer_gfx_mapping *m = calloc(1, sizeof *m);
    if (!m) { errno = ENOMEM; return NULL; }
    void *addr = NULL;
    if (buf->plane_fourcc[plane] == 0 && buf->fourcc == 0) { /* plain memory */
        m->blob_len = buf->plane_width[plane];
        addr = mmap(NULL, m->blob_len, PROT_READ | (write ? PROT_WRITE : 0), MAP_SHARED, buf->fd[plane], 0);
        if (addr == MAP_FAILED) addr = NULL; else m->blob = addr;
        *stride = buf->stride[plane];
    } else if (buf->plane_fourcc[plane] == 0) { /* a real multi-plane buffer: not mappable through GBM */
        errno = ENOTSUP;
    } else {
        struct gbm_import_fd_data fd_data = {
            .fd = buf->fd[plane], .width = buf->plane_width[plane], .height = buf->plane_height[plane],
            .stride = buf->stride[plane], .format = buf->plane_fourcc[plane],
        };
        m->bo = gbm_bo_import(gfx->gbm, GBM_BO_IMPORT_FD, &fd_data, GBM_BO_USE_LINEAR);
        if (m->bo) {
            addr = gbm_bo_map(m->bo, 0, 0, fd_data.width, fd_data.height,
                              write ? GBM_BO_TRANSFER_READ_WRITE : GBM_BO_TRANSFER_READ, stride, &m->map_data);
            if (!addr) gbm_bo_destroy(m->bo);
        }
    }
    if (!addr) { free(m); return NULL; }
    *mapping = m;
    return addr;
}

void apklayer_gfx_unmap(struct apklayer_gfx_mapping *m)
{
    if (!m) return;
    if (m->blob) munmap(m->blob, m->blob_len);
    if (m->bo) { gbm_bo_unmap(m->bo, m->map_data); gbm_bo_destroy(m->bo); }
    free(m);
}
