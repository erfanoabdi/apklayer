/* apklayer graphics HAL, host side (docs/ARCHITECTURE.md "Android side and host side").
 * Plain C: the Android-side HAL services (aosp/hals/graphics) call this to get GPU buffers from the host. Buffers are dmabufs;
 * the format is a DRM fourcc (Android's formats are translated on the Android side). Vendor-neutral: libgbm on a render node
 * (Mesa, NVIDIA via nvidia-drm, AMD, Intel...). One context per instance (PLAN P9): nothing here is global. */
#ifndef APKLAYER_GRAPHICS_H
#define APKLAYER_GRAPHICS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

enum apklayer_gfx_usage {
    APKLAYER_GFX_SAMPLE = 1 << 0,     /* sampled by the GPU (textures, composition) */
    APKLAYER_GFX_RENDER = 1 << 1,     /* rendered to by the GPU (framebuffer) */
    APKLAYER_GFX_CPU_READ = 1 << 2,   /* mapped for reading: forces a linear layout */
    APKLAYER_GFX_CPU_WRITE = 1 << 3,  /* mapped for writing (software decoders, camera): forces a linear layout */
    APKLAYER_GFX_SCANOUT = 1 << 4,    /* may go to the compositor's overlay/scanout plane */
};

#define APKLAYER_GFX_MAX_PLANES 4

struct apklayer_gfx_buffer {
    uint32_t width, height;
    uint32_t fourcc;                              /* DRM_FORMAT_* */
    uint64_t modifier;                            /* DRM_FORMAT_MOD_*, DRM_FORMAT_MOD_INVALID if the driver did not say */
    int num_planes;
    int fd[APKLAYER_GFX_MAX_PLANES];              /* dmabuf per plane (the same fd may repeat); owned by the caller, close() them */
    /* A multi-planar format the driver cannot allocate natively (YUV on virtio-gpu, llvmpipe) is one linear allocation holding
     * all planes back to back (what a real NV12 buffer is): every fd[] is the same dmabuf and offset[] says where each plane
     * starts, so it maps as one region and imports to EGL as a multi-plane dmabuf with per-plane offsets. packed != 0 then. */
    int packed;
    uint32_t alloc_fourcc, alloc_width, alloc_height; /* the underlying allocation (for a packed buffer: R8/R16 and the total rows) */
    uint32_t stride[APKLAYER_GFX_MAX_PLANES];     /* bytes */
    uint32_t offset[APKLAYER_GFX_MAX_PLANES];     /* bytes into the plane's dmabuf */
};

struct apklayer_gfx;

/* Opens the render node (NULL: $APKLAYER_RENDER_NODE, else the first /dev/dri/renderD* that opens). NULL on failure (errno). */
struct apklayer_gfx *apklayer_gfx_open(const char *render_node);
void apklayer_gfx_close(struct apklayer_gfx *gfx);

/* Can buffers of this format be allocated for these usages on this device? */
bool apklayer_gfx_supports(struct apklayer_gfx *gfx, uint32_t fourcc, uint32_t usage);

/* Allocates a buffer. Returns 0 or -errno (-ENOTSUP: format/usage not available, the caller falls back or fails explicitly). */
int apklayer_gfx_alloc(struct apklayer_gfx *gfx, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t usage,
                       struct apklayer_gfx_buffer *out);

/* Plain CPU memory shared by fd (Android's BLOB buffers: JPEG, raw sensor data); not GPU-importable. fourcc is 0. */
int apklayer_gfx_alloc_blob(struct apklayer_gfx *gfx, uint64_t size, struct apklayer_gfx_buffer *out);

void apklayer_gfx_buffer_close(struct apklayer_gfx_buffer *buf); /* closes its fds */

/* CPU access to a plane (this process's mapping of any dmabuf, ours or imported): the buffer is imported into the driver and
 * mapped through it, which is coherent and works on every driver (mmap of the dmabuf itself is not guaranteed: virtio-gpu
 * refuses it). For a packed buffer every plane is a pointer into the same region. A real multi-plane buffer from the driver
 * is not mappable through GBM (-ENOTSUP). */
struct apklayer_gfx_mapping;
void *apklayer_gfx_map(struct apklayer_gfx *gfx, const struct apklayer_gfx_buffer *buf, int plane, bool write,
                       uint32_t *stride, struct apklayer_gfx_mapping **mapping);
void apklayer_gfx_unmap(struct apklayer_gfx_mapping *mapping);

#ifdef __cplusplus
}
#endif
#endif
