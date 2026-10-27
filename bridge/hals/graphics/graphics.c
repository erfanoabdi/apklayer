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

struct gbm_device *apklayer_gfx_gbm_device(struct apklayer_gfx *gfx) { return gfx->gbm; }

static uint32_t gbm_flags(uint32_t usage)
{
    uint32_t f = 0;
    /* APKLAYER_GFX_SAMPLE needs no flag: GBM has no texturing bit, any allocated buffer can be imported as a texture */
    if (usage & APKLAYER_GFX_RENDER) f |= GBM_BO_USE_RENDERING;
    if (usage & APKLAYER_GFX_SCANOUT) f |= GBM_BO_USE_SCANOUT;
    if (usage & (APKLAYER_GFX_CPU_READ | APKLAYER_GFX_CPU_WRITE)) f |= GBM_BO_USE_LINEAR; /* the CPU needs a linear layout */
    return f;
}

/* YUV plane geometry when GBM lacks native multi-planar allocation. Each
 * plane has its own correctly typed R/RG resource; no format reinterpretation. */
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

bool apklayer_gfx_supports(struct apklayer_gfx *gfx, uint32_t fourcc, uint32_t usage)
{
    if (gbm_device_is_format_supported(gfx->gbm, fourcc, gbm_flags(usage)) != 0) return true;
    const struct pack_spec *sp = find_pack(fourcc);
    if (!sp) return false;
    for (int p = 0; p < sp->planes; p++) {
        uint32_t fmt = !p || sp->plane[p].stride_div == 2 ? sp->base :
            sp->base == DRM_FORMAT_R16 ? DRM_FORMAT_GR1616 : DRM_FORMAT_GR88;
        if (!gbm_device_is_format_supported(gfx->gbm, fmt, gbm_flags(usage) | GBM_BO_USE_LINEAR)) return false;
    }
    return true;
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

/* apklayer patch: keep GPU plane resources typed correctly. Reinterpreting a
 * tall R8 resource as NV12 imports on virgl but samples the wrong texels. */
static uint32_t plane_format(const struct pack_spec *sp, int p)
{
    if (!p || sp->plane[p].stride_div == 2) return sp->base;
    return sp->base == DRM_FORMAT_R16 ? DRM_FORMAT_GR1616 : DRM_FORMAT_GR88;
}

static int alloc_packed(struct apklayer_gfx *gfx, struct apklayer_gfx_buffer *out, const struct pack_spec *sp, uint32_t usage)
{
    out->packed = 2; /* separate GPU plane resources; CPU linear mapping stages YUV */
    out->num_planes = sp->planes;
    out->modifier = DRM_FORMAT_MOD_LINEAR;
    for (int p = 0; p < sp->planes; p++) {
        uint32_t width = out->width / (p ? 2 : 1);
        uint32_t height = out->height * sp->plane[p].num / sp->plane[p].den;
        struct gbm_bo *bo = gbm_bo_create(gfx->gbm, width, height, plane_format(sp, p), gbm_flags(usage) | GBM_BO_USE_LINEAR);
        if (!bo) return errno ? -errno : -ENOMEM;
        out->fd[p] = gbm_bo_get_fd(bo);
        out->stride[p] = gbm_bo_get_stride(bo);
        uint64_t modifier = gbm_bo_get_modifier(bo);
        gbm_bo_destroy(bo);
        if (out->fd[p] < 0) return -EIO;
        if (modifier != DRM_FORMAT_MOD_LINEAR) return -ENOTSUP;
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
    void *stage;
    size_t stage_offset[4], plane_size[4];
    void *plane_addr[4];
    struct apklayer_gfx_mapping *plane_map[4];
    int planes;
    bool write;
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
        if (buf->packed == 2) {
            const struct pack_spec *sp = find_pack(buf->fourcc);
            if (!sp) { free(m); errno = ENOTSUP; return NULL; }
            fd_data.fd = buf->fd[plane];
            fd_data.width = buf->width / (plane ? 2 : 1);
            fd_data.height = buf->height * sp->plane[plane].num / sp->plane[plane].den;
            fd_data.stride = buf->stride[plane];
            fd_data.format = plane_format(sp, plane);
        }
        /* a buffer made for rendering (framebuffers) can refuse the linear usage on import; the same dmabuf imports with the usage it was made for */
        static const uint32_t import_usage[] = { GBM_BO_USE_LINEAR, GBM_BO_USE_RENDERING, GBM_BO_USE_RENDERING | GBM_BO_USE_SCANOUT, 0 };
        for (size_t u = 0; u < sizeof import_usage / sizeof *import_usage && !m->bo; u++)
            m->bo = gbm_bo_import(gfx->gbm, GBM_BO_IMPORT_FD, &fd_data, import_usage[u]);
        if (!m->bo && getenv("APKLAYER_RUNTIME_DEBUG")) { char link[64] = "?", path[40]; snprintf(path, sizeof path, "/proc/self/fd/%d", fd_data.fd); ssize_t n = readlink(path, link, sizeof link - 1); if (n > 0) link[n] = 0; fprintf(stderr, "[gfx] gbm_bo_import fd %d (%s) %ux%u stride %u format 0x%x: %s\n", fd_data.fd, link, fd_data.width, fd_data.height, fd_data.stride, fd_data.format, strerror(errno)); }
        uint32_t map_stride = 0;
        if (m->bo) {
            addr = gbm_bo_map(m->bo, 0, 0, fd_data.width, fd_data.height,
                              write ? GBM_BO_TRANSFER_READ_WRITE : GBM_BO_TRANSFER_READ, &map_stride, &m->map_data);
            if (!addr && getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[gfx] gbm_bo_map %ux%u: %s\n", fd_data.width, fd_data.height, strerror(errno));
            if (!addr) gbm_bo_destroy(m->bo);
        }
        (void)map_stride; /* the buffer's own strides apply: for a packed buffer the allocation and the map agree */
    }
    if (!addr) { free(m); return NULL; }
    *stride = buf->stride[plane];
    *mapping = m;
    return addr + (buf->fourcc == 0 ? 0 : buf->offset[plane]);
}

/* Android's mapper lock API exposes one base pointer. Separate GPU resources
 * need a temporary contiguous YUV view; presentation still imports the planes
 * directly, with no RGB conversion or CPU readback. */
void *apklayer_gfx_map_linear(struct apklayer_gfx *gfx, const struct apklayer_gfx_buffer *buf, bool write,
                             uint32_t *stride, struct apklayer_gfx_mapping **mapping)
{
    if (buf->packed != 2) return apklayer_gfx_map(gfx, buf, 0, write, stride, mapping);
    const struct pack_spec *sp = find_pack(buf->fourcc);
    if (!sp) { errno = ENOTSUP; return NULL; }
    struct apklayer_gfx_mapping *m = calloc(1, sizeof *m);
    if (!m) return NULL;
    size_t size = 0;
    for (int p = 0; p < buf->num_planes; p++) {
        m->stage_offset[p] = size;
        m->plane_size[p] = (size_t)buf->stride[p] * (buf->height * sp->plane[p].num / sp->plane[p].den);
        size += m->plane_size[p];
    }
    m->stage = malloc(size);
    if (!m->stage) { free(m); return NULL; }
    m->planes = buf->num_planes;
    for (int p = 0; p < m->planes; p++) {
        uint32_t mapped_stride;
        m->plane_addr[p] = apklayer_gfx_map(gfx, buf, p, write, &mapped_stride, &m->plane_map[p]);
        if (!m->plane_addr[p] || mapped_stride != buf->stride[p]) { apklayer_gfx_unmap(m); errno = EIO; return NULL; }
        memcpy((char *)m->stage + m->stage_offset[p], m->plane_addr[p], m->plane_size[p]);
    }
    m->write = write;
    *stride = buf->stride[0];
    *mapping = m;
    return m->stage;
}

void apklayer_gfx_unmap(struct apklayer_gfx_mapping *m)
{
    if (!m) return;
    if (m->stage) {
        for (int p = 0; p < m->planes; p++) {
            if (m->write) memcpy(m->plane_addr[p], (char *)m->stage + m->stage_offset[p], m->plane_size[p]);
            apklayer_gfx_unmap(m->plane_map[p]);
        }
        free(m->stage);
    }
    if (m->blob) munmap(m->blob, m->blob_len);
    if (m->bo) { gbm_bo_unmap(m->bo, m->map_data); gbm_bo_destroy(m->bo); }
    free(m);
}
