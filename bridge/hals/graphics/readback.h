/* apklayer graphics HAL, host side: the GPU copy of a buffer the CPU cannot map (readback.c). */
#include <stdbool.h>
#pragma once
#include <gbm.h>
#include <stdint.h>

/* Copies the single-plane dmabuf fd (width x height, fourcc, stride, offset, modifier) into dst at dst_stride. 0 or -errno. */
int apklayer_gfx_readback(struct gbm_device *gbm, int fd, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t stride,
                          uint32_t offset, uint64_t modifier, void *dst, uint32_t dst_stride);
/* The other way: src (at src_stride) into the dmabuf, through the GPU. 0 or -errno. */
int apklayer_gfx_upload(struct gbm_device *gbm, int fd, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t stride,
                        uint32_t offset, uint64_t modifier, const void *src, uint32_t src_stride);
/* Whether the GPU imports a LINEAR buffer of fourcc only as an external texture (NVIDIA): GL_TEXTURE_2D, which SurfaceFlinger binds, refuses it. */
bool apklayer_gfx_linear_external_only(struct gbm_device *gbm, uint32_t fourcc);
