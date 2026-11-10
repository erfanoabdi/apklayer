/* apklayer graphics HAL, host side: a CPU read or write of a buffer the driver cannot map. NVIDIA's GBM maps no tiled buffer and makes no
 * linear render target, so SurfaceFlinger's screenshot buffer (rendered by the GPU, read by the CPU) is tiled: the GPU copies it out with
 * glReadPixels from the dmabuf imported as an EGLImage; and NVIDIA samples a LINEAR buffer only as an external texture, so a buffer the CPU
 * draws and SurfaceFlinger composes (a software-rendered dialog) is tiled too, the CPU's pixels written in with glTexSubImage2D.
 * The host's EGL and GLES are loaded on first use (no link dependency). */
#define _GNU_SOURCE
#include "readback.h"

#include <dlfcn.h>
#include <drm_fourcc.h>
#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

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
#define GL_COLOR_ATTACHMENT0 0x8CE0
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#define GL_PACK_ALIGNMENT 0x0D05
#define GL_PACK_ROW_LENGTH 0x0D02
#define GL_UNPACK_ALIGNMENT 0x0CF5
#define GL_UNPACK_ROW_LENGTH 0x0CF2
#define GL_RGBA 0x1908
#define GL_UNSIGNED_BYTE 0x1401
#define GL_NO_ERROR 0

static struct {
    void *(*getProcAddress)(const char *);
    EGLp (*getPlatformDisplay)(unsigned, void *, const EGLAttrib *);
    unsigned (*initialize)(EGLp, int *, int *);
    unsigned (*bindAPI)(unsigned);
    EGLp (*createContext)(EGLp, EGLp, EGLp, const int *);
    unsigned (*destroyContext)(EGLp, EGLp);
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
    void (*texSubImage2D)(unsigned, int, int, int, int, int, unsigned, unsigned, const void *);
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
        F(createContext, "eglCreateContext"), F(destroyContext, "eglDestroyContext"), F(makeCurrent, "eglMakeCurrent"),
        F(getCurrentContext, "eglGetCurrentContext"), F(getCurrentDisplay, "eglGetCurrentDisplay"), F(getCurrentSurface, "eglGetCurrentSurface"),
        F(createImage, "eglCreateImage"), F(destroyImage, "eglDestroyImage"), F(imageTargetTexture2D, "glEGLImageTargetTexture2DOES"),
        F(genTextures, "glGenTextures"), F(deleteTextures, "glDeleteTextures"), F(bindTexture, "glBindTexture"),
        F(genFramebuffers, "glGenFramebuffers"), F(deleteFramebuffers, "glDeleteFramebuffers"), F(bindFramebuffer, "glBindFramebuffer"),
        F(framebufferTexture2D, "glFramebufferTexture2D"), F(checkFramebufferStatus, "glCheckFramebufferStatus"),
        F(pixelStorei, "glPixelStorei"), F(getError, "glGetError"), F(readPixels, "glReadPixels"),
        F(texSubImage2D, "glTexSubImage2D"), F(finish, "glFinish"),
#undef F
    };
    for (size_t i = 0; i < sizeof fns / sizeof *fns; i++)
        if (!(*fns[i].slot = gl.getProcAddress(fns[i].name))) { gl.readPixels = NULL; return false; }
    return true;
}

/* The GPU copy between the dmabuf and mem (at mem_stride): out of the buffer (glReadPixels) or into it (upload: glTexSubImage2D). */
static int transfer(struct gbm_device *gbm, int fd, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t stride,
                    uint32_t offset, uint64_t modifier, void *dst, uint32_t dst_stride, bool upload)
{
    /* shortcut: RGBA byte order only (glReadPixels GL_RGBA is DRM ABGR/XBGR8888, SurfaceFlinger's capture format); others want a swizzle */
    if ((fourcc != DRM_FORMAT_ABGR8888 && fourcc != DRM_FORMAT_XBGR8888) || dst_stride % 4) return -ENOTSUP;
    if (!load()) return -ENOSYS;
    EGLp dpy = gl.getPlatformDisplay(EGL_PLATFORM_GBM_KHR, gbm, NULL);
    int major, minor;
    if (!dpy || !gl.initialize(dpy, &major, &minor) || !gl.bindAPI(EGL_OPENGL_ES_API)) return -ENODEV;
    /* the caller's thread may have its own context current (libapklayer_egl uses the same host EGL): put it back afterwards */
    EGLp old_dpy = gl.getCurrentDisplay(), old_ctx = gl.getCurrentContext(), old_draw = gl.getCurrentSurface(EGL_DRAW), old_read = gl.getCurrentSurface(EGL_READ);
    static const int ctx_attrs[] = { EGL_CONTEXT_MAJOR_VERSION, 3, EGL_NONE };
    EGLp ctx = gl.createContext(dpy, NULL /* EGL_NO_CONFIG_KHR */, NULL, ctx_attrs);
    if (!ctx) return -ENODEV;
    int r = -EIO;
    if (gl.makeCurrent(dpy, NULL, NULL, ctx)) {  /* surfaceless */
        EGLAttrib attrs[] = { EGL_WIDTH, width, EGL_HEIGHT, height, EGL_LINUX_DRM_FOURCC_EXT, fourcc, EGL_DMA_BUF_PLANE0_FD_EXT, fd,
            EGL_DMA_BUF_PLANE0_OFFSET_EXT, offset, EGL_DMA_BUF_PLANE0_PITCH_EXT, stride,
            EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT, (EGLAttrib)(modifier & 0xffffffff), EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT, (EGLAttrib)(modifier >> 32), EGL_NONE };
        if (modifier == DRM_FORMAT_MOD_INVALID) attrs[12] = EGL_NONE;  /* no modifier: the driver's implicit layout */
        EGLp image = gl.createImage(dpy, NULL, EGL_LINUX_DMA_BUF_EXT, NULL, attrs);
        unsigned tex = 0, fb = 0;
        if (image) {
            gl.genTextures(1, &tex);
            gl.bindTexture(GL_TEXTURE_2D, tex);
            gl.imageTargetTexture2D(GL_TEXTURE_2D, image);
            gl.genFramebuffers(1, &fb);
            gl.bindFramebuffer(GL_FRAMEBUFFER, fb);
            gl.framebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
            if (gl.checkFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
                if (upload) {
                    gl.pixelStorei(GL_UNPACK_ALIGNMENT, 4);
                    gl.pixelStorei(GL_UNPACK_ROW_LENGTH, (int)(dst_stride / 4));
                    gl.texSubImage2D(GL_TEXTURE_2D, 0, 0, 0, (int)width, (int)height, GL_RGBA, GL_UNSIGNED_BYTE, dst);
                    gl.pixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                    gl.finish();  /* the pixels are in the buffer before the consumer gets it */
                } else {
                    gl.pixelStorei(GL_PACK_ALIGNMENT, 4);
                    gl.pixelStorei(GL_PACK_ROW_LENGTH, (int)(dst_stride / 4));
                    gl.readPixels(0, 0, (int)width, (int)height, GL_RGBA, GL_UNSIGNED_BYTE, dst);  /* texel row 0 is the buffer's first row */
                }
                if (gl.getError() == GL_NO_ERROR) r = 0;
            }
            gl.deleteFramebuffers(1, &fb);
            gl.deleteTextures(1, &tex);
            gl.destroyImage(dpy, image);
        }
        if (r && getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, upload ? "[gfx] upload %ux%u fourcc 0x%x modifier 0x%llx: %s\n" : "[gfx] readback %ux%u fourcc 0x%x modifier 0x%llx: %s\n", width, height, fourcc,
                                                           (unsigned long long)modifier, image ? "no complete framebuffer" : "no EGLImage");
    }
    gl.makeCurrent(old_ctx ? old_dpy : dpy, old_draw, old_read, old_ctx);
    gl.destroyContext(dpy, ctx);
    return r;
}

int apklayer_gfx_readback(struct gbm_device *gbm, int fd, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t stride,
                          uint32_t offset, uint64_t modifier, void *dst, uint32_t dst_stride)
{
    return transfer(gbm, fd, width, height, fourcc, stride, offset, modifier, dst, dst_stride, false);
}

int apklayer_gfx_upload(struct gbm_device *gbm, int fd, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t stride,
                        uint32_t offset, uint64_t modifier, const void *src, uint32_t src_stride)
{
    return transfer(gbm, fd, width, height, fourcc, stride, offset, modifier, (void *)src, src_stride, true);
}

bool apklayer_gfx_linear_external_only(struct gbm_device *gbm, uint32_t fourcc)
{
    static int answer = -1;  /* one GPU per process (the allocator's) */
    if (answer >= 0) return answer;
    answer = 0;
    if (!load()) return false;
    EGLp dpy = gl.getPlatformDisplay(EGL_PLATFORM_GBM_KHR, gbm, NULL);
    int major, minor;
    unsigned (*query)(EGLp, int, int, uint64_t *, unsigned *, int *) =
        (unsigned (*)(EGLp, int, int, uint64_t *, unsigned *, int *))gl.getProcAddress("eglQueryDmaBufModifiersEXT");
    if (!dpy || !query || !gl.initialize(dpy, &major, &minor)) return false;
    int n = 0;
    if (!query(dpy, (int)fourcc, 0, NULL, NULL, &n) || n <= 0 || n > 256) return false;
    uint64_t mods[256];
    unsigned ext[256];
    if (!query(dpy, (int)fourcc, n, mods, ext, &n)) return false;
    for (int i = 0; i < n; i++) if (mods[i] == DRM_FORMAT_MOD_LINEAR) answer = ext[i] != 0;
    if (answer) fprintf(stderr, "apklayer-gfx: the GPU samples LINEAR only as an external texture: CPU-drawn buffers it composes are tiled\n");
    return answer;
}
