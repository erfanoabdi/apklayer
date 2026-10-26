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

bool apklayer_gfx_supports(struct apklayer_gfx *gfx, uint32_t fourcc, uint32_t usage)
{
    return gbm_device_is_format_supported(gfx->gbm, fourcc, gbm_flags(usage)) != 0;
}

int apklayer_gfx_alloc(struct apklayer_gfx *gfx, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t usage,
                       struct apklayer_gfx_buffer *out)
{
    if (!apklayer_gfx_supports(gfx, fourcc, usage)) return -ENOTSUP;
    struct gbm_bo *bo = gbm_bo_create(gfx->gbm, width, height, fourcc, gbm_flags(usage));
    if (!bo) return errno ? -errno : -ENOMEM;
    memset(out, 0, sizeof *out);
    for (int i = 0; i < APKLAYER_GFX_MAX_PLANES; i++) out->fd[i] = -1;
    out->width = width;
    out->height = height;
    out->fourcc = fourcc;
    out->modifier = gbm_bo_get_modifier(bo);
    out->num_planes = gbm_bo_get_plane_count(bo);
    if (out->num_planes < 1 || out->num_planes > APKLAYER_GFX_MAX_PLANES) { gbm_bo_destroy(bo); return -ENOTSUP; }
    for (int i = 0; i < out->num_planes; i++) {
        out->fd[i] = gbm_bo_get_fd_for_plane(bo, i);
        out->stride[i] = gbm_bo_get_stride_for_plane(bo, i);
        out->offset[i] = gbm_bo_get_offset(bo, i);
        if (out->fd[i] < 0) { int e = errno ? errno : EIO; apklayer_gfx_buffer_close(out); gbm_bo_destroy(bo); return -e; }
    }
    gbm_bo_destroy(bo); /* the dmabuf fds keep the memory alive */
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
};

void *apklayer_gfx_map(struct apklayer_gfx *gfx, const struct apklayer_gfx_buffer *buf, int plane, bool write,
                       uint32_t *stride, struct apklayer_gfx_mapping **mapping)
{
    if (plane < 0 || plane >= buf->num_planes) { errno = EINVAL; return NULL; }
    struct gbm_import_fd_data fd_data = {
        .fd = buf->fd[plane], .width = buf->width, .height = buf->height, .stride = buf->stride[plane], .format = buf->fourcc,
    };
    struct gbm_bo *bo = gbm_bo_import(gfx->gbm, GBM_BO_IMPORT_FD, &fd_data, GBM_BO_USE_LINEAR);
    if (!bo) return NULL;
    void *map_data = NULL;
    void *addr = gbm_bo_map(bo, 0, 0, buf->width, buf->height, write ? GBM_BO_TRANSFER_READ_WRITE : GBM_BO_TRANSFER_READ,
                            stride, &map_data);
    if (!addr) { gbm_bo_destroy(bo); return NULL; }
    struct apklayer_gfx_mapping *m = malloc(sizeof *m);
    if (!m) { gbm_bo_unmap(bo, map_data); gbm_bo_destroy(bo); errno = ENOMEM; return NULL; }
    m->bo = bo;
    m->map_data = map_data;
    *mapping = m;
    return addr;
}

void apklayer_gfx_unmap(struct apklayer_gfx_mapping *m)
{
    if (!m) return;
    gbm_bo_unmap(m->bo, m->map_data);
    gbm_bo_destroy(m->bo);
    free(m);
}
