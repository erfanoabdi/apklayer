/* apklayer graphics HAL, host side: a CPU read or write of a buffer the driver cannot map. NVIDIA's GBM maps no tiled buffer and makes no
 * linear render target, so SurfaceFlinger's screenshot buffer (rendered by the GPU, read by the CPU) is tiled: the GPU copies it out with
 * glReadPixels from the dmabuf imported as an EGLImage; and NVIDIA samples a LINEAR buffer only as an external texture, so a buffer the CPU
 * draws and SurfaceFlinger composes (a software-rendered window) is tiled too, the CPU's pixels going in through the GPU.
 * The GPU always moves RGBA8 (glReadPixels GL_RGBA/GL_UNSIGNED_BYTE, the one read every ES driver takes; a blit from an RGBA8 texture, which
 * converts into the buffer's own format); the CPU converts that to and from the buffer's layout (to_rgba/from_rgba). One context per process,
 * made on first use and used under a lock. The host's EGL and GLES are loaded on first use (no link dependency). */
#define _GNU_SOURCE
#include "readback.h"

#include <dlfcn.h>
#include <drm_fourcc.h>
#include <errno.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef void *EGLp;
typedef intptr_t EGLAttrib;
#define EGL_NONE 0x3038
#define EGL_WIDTH 0x3057
#define EGL_HEIGHT 0x3056
#define EGL_OPENGL_ES_API 0x30A0
#define EGL_CONTEXT_MAJOR_VERSION 0x3098
#define EGL_PLATFORM_GBM_KHR 0x31D7
#define EGL_LINUX_DMA_BUF_EXT 0x3270
#define EGL_LINUX_DRM_FOURCC_EXT 0x3271
#define EGL_DMA_BUF_PLANE0_FD_EXT 0x3272
#define EGL_DMA_BUF_PLANE0_OFFSET_EXT 0x3273
#define EGL_DMA_BUF_PLANE0_PITCH_EXT 0x3274
#define EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT 0x3443
#define EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT 0x3444
#define EGL_DRAW 0x3059
#define EGL_READ 0x305A
#define GL_TEXTURE_2D 0x0DE1
#define GL_FRAMEBUFFER 0x8D40
#define GL_READ_FRAMEBUFFER 0x8CA8
#define GL_DRAW_FRAMEBUFFER 0x8CA9
#define GL_COLOR_ATTACHMENT0 0x8CE0
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#define GL_COLOR_BUFFER_BIT 0x4000
#define GL_NEAREST 0x2600
#define GL_PACK_ALIGNMENT 0x0D05
#define GL_PACK_ROW_LENGTH 0x0D02
#define GL_UNPACK_ALIGNMENT 0x0CF5
#define GL_UNPACK_ROW_LENGTH 0x0CF2
#define GL_RGBA 0x1908
#define GL_RGBA8 0x8058
#define GL_UNSIGNED_BYTE 0x1401
#define GL_NO_ERROR 0

static struct {
    void *(*getProcAddress)(const char *);
    EGLp (*getPlatformDisplay)(unsigned, void *, const EGLAttrib *);
    unsigned (*initialize)(EGLp, int *, int *);
    unsigned (*bindAPI)(unsigned);
    EGLp (*createContext)(EGLp, EGLp, EGLp, const int *);
    unsigned (*makeCurrent)(EGLp, EGLp, EGLp, EGLp);
    EGLp (*getCurrentContext)(void), (*getCurrentDisplay)(void), (*getCurrentSurface)(int);
    EGLp (*createImage)(EGLp, EGLp, unsigned, void *, const EGLAttrib *);
    unsigned (*destroyImage)(EGLp, EGLp);
    void (*imageTargetTexture2D)(unsigned, void *);
    void (*genTextures)(int, unsigned *), (*deleteTextures)(int, const unsigned *), (*bindTexture)(unsigned, unsigned);
    void (*genFramebuffers)(int, unsigned *), (*deleteFramebuffers)(int, const unsigned *), (*bindFramebuffer)(unsigned, unsigned);
    void (*framebufferTexture2D)(unsigned, unsigned, unsigned, unsigned, int);
    unsigned (*checkFramebufferStatus)(unsigned);
    void (*pixelStorei)(unsigned, int);
    void (*readPixels)(int, int, int, int, unsigned, unsigned, void *);
    void (*texImage2D)(unsigned, int, int, int, int, int, unsigned, unsigned, const void *);
    void (*blitFramebuffer)(int, int, int, int, int, int, int, int, unsigned, unsigned);
    void (*finish)(void);
    unsigned (*getError)(void);
} gl;

static bool load(void)
{
    if (gl.readPixels) return true;
    void *egl = dlopen("libEGL.so.1", RTLD_NOW);
    if (!egl || !(gl.getProcAddress = (void *(*)(const char *))dlsym(egl, "eglGetProcAddress"))) return false;
    struct { void **slot; const char *name; } fns[] = {
#define F(field, name) { (void **)&gl.field, name }
        F(getPlatformDisplay, "eglGetPlatformDisplay"), F(initialize, "eglInitialize"), F(bindAPI, "eglBindAPI"),
        F(createContext, "eglCreateContext"), F(makeCurrent, "eglMakeCurrent"),
        F(getCurrentContext, "eglGetCurrentContext"), F(getCurrentDisplay, "eglGetCurrentDisplay"), F(getCurrentSurface, "eglGetCurrentSurface"),
        F(createImage, "eglCreateImage"), F(destroyImage, "eglDestroyImage"), F(imageTargetTexture2D, "glEGLImageTargetTexture2DOES"),
        F(genTextures, "glGenTextures"), F(deleteTextures, "glDeleteTextures"), F(bindTexture, "glBindTexture"),
        F(genFramebuffers, "glGenFramebuffers"), F(deleteFramebuffers, "glDeleteFramebuffers"), F(bindFramebuffer, "glBindFramebuffer"),
        F(framebufferTexture2D, "glFramebufferTexture2D"), F(checkFramebufferStatus, "glCheckFramebufferStatus"),
        F(pixelStorei, "glPixelStorei"), F(getError, "glGetError"), F(texImage2D, "glTexImage2D"),
        F(blitFramebuffer, "glBlitFramebuffer"), F(finish, "glFinish"), F(readPixels, "glReadPixels"),  /* readPixels last: the "loaded" mark */
#undef F
    };
    for (size_t i = 0; i < sizeof fns / sizeof *fns; i++)
        if (!(*fns[i].slot = gl.getProcAddress(fns[i].name))) { gl.readPixels = NULL; return false; }
    return true;
}

/* ---- the CPU side: the buffer's layout <-> RGBA8 (R, G, B, A bytes) ---- */

/* Bytes per pixel of the formats moved through RGBA8 without loss, 0 for the others (10-bit, float, YUV: their CPU-drawn windows do
 * not occur; they stay LINEAR). */
static unsigned pixel_bytes(uint32_t fourcc)
{
    switch (fourcc) {
    case DRM_FORMAT_ABGR8888: case DRM_FORMAT_XBGR8888: case DRM_FORMAT_ARGB8888: case DRM_FORMAT_XRGB8888: return 4;
    case DRM_FORMAT_BGR888: return 3;
    case DRM_FORMAT_RGB565: return 2;
    default: return 0;
    }
}

bool apklayer_gfx_transfer_supported(uint32_t fourcc) { return pixel_bytes(fourcc) != 0; }

static void to_rgba(uint32_t fourcc, const uint8_t *s, uint8_t *d, uint32_t n)  /* one row: buffer -> RGBA8 */
{
    switch (fourcc) {
    case DRM_FORMAT_ABGR8888: memcpy(d, s, (size_t)n * 4); break;  /* R, G, B, A in memory */
    case DRM_FORMAT_XBGR8888: for (uint32_t i = 0; i < n; i++, s += 4, d += 4) { d[0] = s[0]; d[1] = s[1]; d[2] = s[2]; d[3] = 0xff; } break;
    case DRM_FORMAT_ARGB8888: for (uint32_t i = 0; i < n; i++, s += 4, d += 4) { d[0] = s[2]; d[1] = s[1]; d[2] = s[0]; d[3] = s[3]; } break;
    case DRM_FORMAT_XRGB8888: for (uint32_t i = 0; i < n; i++, s += 4, d += 4) { d[0] = s[2]; d[1] = s[1]; d[2] = s[0]; d[3] = 0xff; } break;
    case DRM_FORMAT_BGR888: for (uint32_t i = 0; i < n; i++, s += 3, d += 4) { d[0] = s[0]; d[1] = s[1]; d[2] = s[2]; d[3] = 0xff; } break;
    case DRM_FORMAT_RGB565:  /* little endian [15:0] R:G:B 5:6:5 */
        for (uint32_t i = 0; i < n; i++, s += 2, d += 4) {
            unsigned v = s[0] | s[1] << 8, r = v >> 11, g = v >> 5 & 63, b = v & 31;
            d[0] = (uint8_t)(r << 3 | r >> 2); d[1] = (uint8_t)(g << 2 | g >> 4); d[2] = (uint8_t)(b << 3 | b >> 2); d[3] = 0xff;
        }
        break;
    }
}

static void from_rgba(uint32_t fourcc, const uint8_t *s, uint8_t *d, uint32_t n)  /* one row: RGBA8 -> buffer */
{
    switch (fourcc) {
    case DRM_FORMAT_ABGR8888: case DRM_FORMAT_XBGR8888: memcpy(d, s, (size_t)n * 4); break;
    case DRM_FORMAT_ARGB8888: case DRM_FORMAT_XRGB8888: for (uint32_t i = 0; i < n; i++, s += 4, d += 4) { d[0] = s[2]; d[1] = s[1]; d[2] = s[0]; d[3] = s[3]; } break;
    case DRM_FORMAT_BGR888: for (uint32_t i = 0; i < n; i++, s += 4, d += 3) { d[0] = s[0]; d[1] = s[1]; d[2] = s[2]; } break;
    case DRM_FORMAT_RGB565:  /* GL's 5/6-bit to 8-bit expansion is v * 255 / max, rounded: this rounding takes it back exactly */
        for (uint32_t i = 0; i < n; i++, s += 4, d += 2) {
            unsigned v = (s[0] * 31u + 127) / 255 << 11 | (s[1] * 63u + 127) / 255 << 5 | (s[2] * 31u + 127) / 255;
            d[0] = (uint8_t)v; d[1] = (uint8_t)(v >> 8);
        }
        break;
    }
}

/* ---- the GPU side ---- */

static pthread_mutex_t ctx_lock = PTHREAD_MUTEX_INITIALIZER;
static struct gbm_device *ctx_gbm;
static EGLp ctx_dpy, ctx;

/* The process's copy context on gbm's display (made once; ctx_lock held). */
static bool context(struct gbm_device *gbm)
{
    if (ctx && ctx_gbm == gbm) return true;
    EGLp dpy = gl.getPlatformDisplay(EGL_PLATFORM_GBM_KHR, gbm, NULL);
    int major, minor;
    if (!dpy || !gl.initialize(dpy, &major, &minor) || !gl.bindAPI(EGL_OPENGL_ES_API)) return false;
    static const int attrs[] = { EGL_CONTEXT_MAJOR_VERSION, 3, EGL_NONE };  /* ES 3: glBlitFramebuffer */
    EGLp c = gl.createContext(dpy, NULL /* EGL_NO_CONFIG_KHR */, NULL, attrs);
    if (!c) return false;
    ctx_gbm = gbm, ctx_dpy = dpy, ctx = c;  /* a second GBM device in one process (none today) leaves the first context behind */
    return true;
}

/* The GPU copy between the dmabuf and rgba (RGBA8, width * 4 per row): out of the buffer (glReadPixels) or into it (a blit from an RGBA8
 * texture, which converts into the buffer's own format). */
static int gpu_copy(struct gbm_device *gbm, int fd, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t stride,
                    uint32_t offset, uint64_t modifier, uint8_t *rgba, bool upload)
{
    if (!load()) return -ENOSYS;
    pthread_mutex_lock(&ctx_lock);
    if (!context(gbm)) { pthread_mutex_unlock(&ctx_lock); return -ENODEV; }
    /* the caller's thread may have its own context current (libapklayer_egl uses the same host EGL): put it back afterwards */
    EGLp old_dpy = gl.getCurrentDisplay(), old_ctx = gl.getCurrentContext(), old_draw = gl.getCurrentSurface(EGL_DRAW), old_read = gl.getCurrentSurface(EGL_READ);
    int r = -EIO;
    const char *why = "no context";
    if (gl.makeCurrent(ctx_dpy, NULL, NULL, ctx)) {  /* surfaceless */
        while (gl.getError() != GL_NO_ERROR) {}
        EGLAttrib attrs[] = { EGL_WIDTH, width, EGL_HEIGHT, height, EGL_LINUX_DRM_FOURCC_EXT, fourcc, EGL_DMA_BUF_PLANE0_FD_EXT, fd,
            EGL_DMA_BUF_PLANE0_OFFSET_EXT, offset, EGL_DMA_BUF_PLANE0_PITCH_EXT, stride,
            EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT, (EGLAttrib)(modifier & 0xffffffff), EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT, (EGLAttrib)(modifier >> 32), EGL_NONE };
        if (modifier == DRM_FORMAT_MOD_INVALID) attrs[12] = EGL_NONE;  /* no modifier: the driver's implicit layout */
        EGLp image = gl.createImage(ctx_dpy, NULL, EGL_LINUX_DMA_BUF_EXT, NULL, attrs);
        unsigned tex = 0, fb = 0, src_tex = 0, src_fb = 0;
        why = "no EGLImage";
        if (image) {
            gl.genTextures(1, &tex);
            gl.bindTexture(GL_TEXTURE_2D, tex);
            gl.imageTargetTexture2D(GL_TEXTURE_2D, image);
            gl.genFramebuffers(1, &fb);
            gl.bindFramebuffer(GL_FRAMEBUFFER, fb);
            gl.framebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
            why = "the buffer is no complete framebuffer";
            if (gl.checkFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
                why = "a GL error";
                if (upload) {
                    gl.genTextures(1, &src_tex);
                    gl.bindTexture(GL_TEXTURE_2D, src_tex);
                    gl.pixelStorei(GL_UNPACK_ALIGNMENT, 4);
                    gl.pixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                    gl.texImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, (int)width, (int)height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
                    gl.genFramebuffers(1, &src_fb);
                    gl.bindFramebuffer(GL_READ_FRAMEBUFFER, src_fb);
                    gl.framebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, src_tex, 0);
                    gl.bindFramebuffer(GL_DRAW_FRAMEBUFFER, fb);
                    if (gl.checkFramebufferStatus(GL_READ_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE)  /* row 0 to row 0: no flip */
                        gl.blitFramebuffer(0, 0, (int)width, (int)height, 0, 0, (int)width, (int)height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
                    else why = "the upload texture is no complete framebuffer";
                    gl.finish();  /* the pixels are in the buffer before the consumer gets it */
                } else {
                    gl.pixelStorei(GL_PACK_ALIGNMENT, 4);
                    gl.pixelStorei(GL_PACK_ROW_LENGTH, 0);
                    gl.readPixels(0, 0, (int)width, (int)height, GL_RGBA, GL_UNSIGNED_BYTE, rgba);  /* texel row 0 is the buffer's first row */
                }
                if (gl.getError() == GL_NO_ERROR && (!upload || src_fb)) r = 0;
            }
            gl.bindFramebuffer(GL_FRAMEBUFFER, 0);
            if (src_fb) gl.deleteFramebuffers(1, &src_fb);
            if (src_tex) gl.deleteTextures(1, &src_tex);
            gl.deleteFramebuffers(1, &fb);
            gl.deleteTextures(1, &tex);
            gl.destroyImage(ctx_dpy, image);
        }
    }
    if (r) fprintf(stderr, "apklayer-gfx: %s of a %ux%u buffer (fourcc 0x%x, modifier 0x%llx) through the GPU failed: %s\n",
                   upload ? "writing" : "reading", width, height, fourcc, (unsigned long long)modifier, why);
    if (old_ctx) gl.makeCurrent(old_dpy, old_draw, old_read, old_ctx);
    else gl.makeCurrent(ctx_dpy, NULL, NULL, NULL);  /* release ours: another thread may take it next */
    pthread_mutex_unlock(&ctx_lock);
    return r;
}

int apklayer_gfx_readback(struct gbm_device *gbm, int fd, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t stride,
                          uint32_t offset, uint64_t modifier, void *dst, uint32_t dst_stride)
{
    unsigned bpp = pixel_bytes(fourcc);
    if (!bpp || dst_stride < width * bpp) return -ENOTSUP;
    uint8_t *rgba = malloc((size_t)width * 4 * height);
    if (!rgba) return -ENOMEM;
    int r = gpu_copy(gbm, fd, width, height, fourcc, stride, offset, modifier, rgba, false);
    for (uint32_t y = 0; !r && y < height; y++) from_rgba(fourcc, rgba + (size_t)y * width * 4, (uint8_t *)dst + (size_t)y * dst_stride, width);
    free(rgba);
    return r;
}

int apklayer_gfx_upload(struct gbm_device *gbm, int fd, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t stride,
                        uint32_t offset, uint64_t modifier, const void *src, uint32_t src_stride)
{
    unsigned bpp = pixel_bytes(fourcc);
    if (!bpp || src_stride < width * bpp) return -ENOTSUP;
    uint8_t *rgba = malloc((size_t)width * 4 * height);
    if (!rgba) return -ENOMEM;
    for (uint32_t y = 0; y < height; y++) to_rgba(fourcc, (const uint8_t *)src + (size_t)y * src_stride, rgba + (size_t)y * width * 4, width);
    int r = gpu_copy(gbm, fd, width, height, fourcc, stride, offset, modifier, rgba, true);
    free(rgba);
    return r;
}

/* Whether a LINEAR buffer of fourcc is unusable as GL_TEXTURE_2D: listed only as external-only (NVIDIA), or not listed at all by a driver
 * that lists modifiers for the format. A driver that lists none answers false (Mesa without modifiers imports LINEAR as it always did). */
bool apklayer_gfx_linear_external_only(struct gbm_device *gbm, uint32_t fourcc)
{
    static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
    static struct { uint32_t fourcc; bool answer; } seen[16];  /* one GPU per process (the allocator's) */
    static int nseen;
    pthread_mutex_lock(&lock);
    for (int i = 0; i < nseen; i++) if (seen[i].fourcc == fourcc) { bool a = seen[i].answer; pthread_mutex_unlock(&lock); return a; }
    bool answer = false;
    unsigned (*query)(EGLp, int, int, uint64_t *, unsigned *, int *) = NULL;
    EGLp dpy = NULL;
    int major, minor, n = 0;
    if (load()) {
        dpy = gl.getPlatformDisplay(EGL_PLATFORM_GBM_KHR, gbm, NULL);
        query = (unsigned (*)(EGLp, int, int, uint64_t *, unsigned *, int *))gl.getProcAddress("eglQueryDmaBufModifiersEXT");
    }
    if (dpy && query && gl.initialize(dpy, &major, &minor) && query(dpy, (int)fourcc, 0, NULL, NULL, &n) && n > 0 && n <= 256) {
        uint64_t mods[256];
        unsigned ext[256];
        if (query(dpy, (int)fourcc, n, mods, ext, &n)) {
            answer = true;
            for (int i = 0; i < n; i++) if (mods[i] == DRM_FORMAT_MOD_LINEAR && !ext[i]) answer = false;
        }
    }
    if (answer) fprintf(stderr, "apklayer-gfx: the GPU samples LINEAR fourcc 0x%x only as an external texture: CPU-drawn buffers it composes are tiled\n", fourcc);
    if (nseen < (int)(sizeof seen / sizeof *seen)) seen[nseen].fourcc = fourcc, seen[nseen].answer = answer, nseen++;
    pthread_mutex_unlock(&lock);
    return answer;
}
