/* apklayer graphics HAL, host side: the modifiers a shared buffer may use (readback.h apklayer_gfx_shared_modifiers).
 * A gralloc buffer goes to every API of every process: EGL, the app's Vulkan (ANGLE's AHardwareBuffer images, Skia's) and the compositor.
 * NVIDIA's GBM gives a buffer the GPU only renders and samples a compressed block-linear kind (0x300000000e08014 for ABGR8888), which its
 * EGL imports but its Vulkan driver does not list (only the uncompressed kinds): a hardware bitmap's vkCreateImage had no modifier to use
 * and HWUI's EGLImage failed (YouTube's thumbnails under ANGLE). The shared list is EGL's NVIDIA block-linear modifiers without the
 * compressed ones (in an ANGLE instance EGL is Linux ANGLE, whose list is already Vulkan's: GBM still picks a compressed kind by itself);
 * on other GPUs there are none, and GBM keeps choosing. */
#include "readback.h"

#include <pthread.h>
#include <stdio.h>
#include <string.h>

#define MOD_VENDOR_NVIDIA 0x03ull
/* drm_fourcc.h DRM_FORMAT_MOD_NVIDIA_BLOCK_LINEAR_2D(c, s, g, k, h): bit 4 marks the 2D block-linear layout, c (bits 23-25) its compression */
static bool nvidia_block_linear(uint64_t mod) { return (mod >> 56) == MOD_VENDOR_NVIDIA && (mod & 0x10); }
static bool nvidia_compressed(uint64_t mod) { return nvidia_block_linear(mod) && ((mod >> 23) & 0x7); }

int apklayer_gfx_shared_modifiers(struct gbm_device *gbm, uint32_t fourcc, uint64_t *out, int max)
{
    enum { kMax = 64 };
    static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
    static struct { uint32_t fourcc; int count; uint64_t mods[kMax]; } seen[16];  /* one GPU per process (the allocator's) */
    static int nseen;
    pthread_mutex_lock(&lock);
    int found = -1;
    for (int i = 0; i < nseen; i++) if (seen[i].fourcc == fourcc) found = i;
    if (found < 0) {
        uint64_t mods[256];
        unsigned ext[256];
        int n = apklayer_gfx_egl_modifiers(gbm, fourcc, mods, ext, 256), count = 0, compressed = 0;
        uint64_t keep[kMax];
        for (int i = 0; i < n; i++) {
            if (nvidia_compressed(mods[i])) { compressed++; continue; }
            if (ext[i] || !nvidia_block_linear(mods[i]) || count == kMax) continue;  /* tiled, texture-importable: what a GPU buffer wants */
            keep[count++] = mods[i];
        }
        if (count) fprintf(stderr, "apklayer-gfx: fourcc 0x%x: shared buffers use NVIDIA's %d uncompressed block-linear modifiers (%d compressed ones listed, not importable everywhere)\n",
                           fourcc, count, compressed);
        if (nseen < (int)(sizeof seen / sizeof *seen)) {
            found = nseen++;
            seen[found].fourcc = fourcc;
            seen[found].count = count;
            memcpy(seen[found].mods, keep, sizeof(uint64_t) * (size_t)count);
        } else {
            int copied = count < max ? count : max;
            memcpy(out, keep, sizeof(uint64_t) * (size_t)copied);
            pthread_mutex_unlock(&lock);
            return copied;
        }
    }
    int copied = seen[found].count < max ? seen[found].count : max;
    memcpy(out, seen[found].mods, sizeof(uint64_t) * (size_t)copied);
    pthread_mutex_unlock(&lock);
    return copied;
}
