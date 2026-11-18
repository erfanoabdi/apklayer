/* apklayer graphics HAL, host side: a CPU read or write of a buffer the driver cannot map. NVIDIA's GBM maps no tiled buffer and makes no
 * linear render target, so SurfaceFlinger's screenshot buffer (rendered by the GPU, read by the CPU) is tiled: the GPU copies it out with
 * glReadPixels from the dmabuf imported as an EGLImage; and NVIDIA samples a LINEAR buffer only as an external texture, so a buffer the CPU
 * draws and SurfaceFlinger composes (a software-rendered window) is tiled too, the CPU's pixels going in through the GPU.
 * Readback uses RGBA8 and converts to the buffer's layout where needed. Uploads use the corresponding GLES pixel format. ANGLE instances use
 * a Linux ANGLE EGL build for GBM imports; native instances use host EGL on GBM. One context per process, made on first
 * use and used under a lock. */
#define _GNU_SOURCE
#include "readback.h"

#include <dlfcn.h>
#include <drm_fourcc.h>
#include <errno.h>
#include <limits.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

typedef void *EGLp;
typedef intptr_t EGLAttrib;
#define EGL_NONE 0x3038
#define EGL_WIDTH 0x3057
#define EGL_HEIGHT 0x3056
#define EGL_OPENGL_ES_API 0x30A0
#define EGL_CONTEXT_MAJOR_VERSION 0x3098
#define EGL_PLATFORM_GBM_KHR 0x31D7
#define EGL_EXTENSIONS 0x3055
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
#define GL_TEXTURE0 0x84C0
#define GL_TEXTURE_MIN_FILTER 0x2801
#define GL_TEXTURE_MAG_FILTER 0x2800
#define GL_TEXTURE_WRAP_S 0x2802
#define GL_TEXTURE_WRAP_T 0x2803
#define GL_CLAMP_TO_EDGE 0x812F
#define GL_FRAMEBUFFER 0x8D40
#define GL_COLOR_ATTACHMENT0 0x8CE0
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#define GL_VERTEX_SHADER 0x8B31
#define GL_FRAGMENT_SHADER 0x8B30
#define GL_COMPILE_STATUS 0x8B81
#define GL_LINK_STATUS 0x8B82
#define GL_TRIANGLES 0x0004
#define GL_NEAREST 0x2600
#define GL_PACK_ALIGNMENT 0x0D05
#define GL_PACK_ROW_LENGTH 0x0D02
#define GL_UNPACK_ALIGNMENT 0x0CF5
#define GL_UNPACK_ROW_LENGTH 0x0CF2
#define GL_RGBA 0x1908
#define GL_RGB 0x1907
#define GL_BGRA_EXT 0x80E1
#define GL_RGBA8 0x8058
#define GL_UNSIGNED_BYTE 0x1401
#define GL_UNSIGNED_SHORT_5_6_5 0x8363
#define GL_NO_ERROR 0

static struct {
    void *(*getProcAddress)(const char *);
    EGLp (*getPlatformDisplay)(unsigned, void *, const EGLAttrib *);
    EGLp (*getDisplay)(EGLp);
    unsigned (*initialize)(EGLp, int *, int *);
    unsigned (*bindAPI)(unsigned);
    EGLp (*createContext)(EGLp, EGLp, EGLp, const int *);
    unsigned (*destroyContext)(EGLp, EGLp);
    unsigned (*makeCurrent)(EGLp, EGLp, EGLp, EGLp);
    const char *(*queryString)(EGLp, int);
    EGLp (*getCurrentContext)(void), (*getCurrentDisplay)(void), (*getCurrentSurface)(int);
    EGLp (*createImage)(EGLp, EGLp, unsigned, void *, const EGLAttrib *);
    unsigned (*destroyImage)(EGLp, EGLp);
    void (*imageTargetTexture2D)(unsigned, void *);
    void (*genTextures)(int, unsigned *), (*deleteTextures)(int, const unsigned *), (*bindTexture)(unsigned, unsigned);
    void (*texParameteri)(unsigned, unsigned, int);
    void (*genFramebuffers)(int, unsigned *), (*deleteFramebuffers)(int, const unsigned *), (*bindFramebuffer)(unsigned, unsigned);
    void (*framebufferTexture2D)(unsigned, unsigned, unsigned, unsigned, int);
    unsigned (*checkFramebufferStatus)(unsigned);
    void (*pixelStorei)(unsigned, int);
    void (*readPixels)(int, int, int, int, unsigned, unsigned, void *);
    void (*texImage2D)(unsigned, int, int, int, int, int, unsigned, unsigned, const void *);
    void (*texSubImage2D)(unsigned, int, int, int, int, int, unsigned, unsigned, const void *);
    unsigned (*createShader)(unsigned), (*createProgram)(void);
    void (*shaderSource)(unsigned, int, const char *const *, const int *), (*compileShader)(unsigned);
    void (*getShaderiv)(unsigned, unsigned, int *), (*deleteShader)(unsigned);
    void (*attachShader)(unsigned, unsigned), (*linkProgram)(unsigned), (*getProgramiv)(unsigned, unsigned, int *);
    void (*deleteProgram)(unsigned), (*useProgram)(unsigned), (*activeTexture)(unsigned), (*uniform1i)(int, int);
    int (*getUniformLocation)(unsigned, const char *);
    void (*viewport)(int, int, int, int), (*drawArrays)(unsigned, int, int), (*finish)(void);
    unsigned (*getError)(void);
} gl;

static void *egl_handle;
static bool angle_backend;
static bool angle_host_backend;
static pthread_once_t load_once = PTHREAD_ONCE_INIT;

static void *symbol(const char *name)
{
    return dlsym(egl_handle, name);
}

static bool gfx_debug(void)
{
    const char *value = getenv("APKLAYER_GFX_DEBUG");
    return value && *value && strcmp(value, "0");
}

#define GFX_DEBUG(...) do { if (gfx_debug()) fprintf(stderr, "[gfx] " __VA_ARGS__); } while (0)

static void load_symbols(void)
{
    const char *active = getenv("APKLAYER_ANGLE_ACTIVE");
    angle_backend = active && *active;
    if (angle_backend) {
        const char *host_angle = getenv("APKLAYER_ANGLE_HOST_EGL");
        if (!host_angle || !*host_angle) {
            fprintf(stderr, "apklayer-gfx: ANGLE buffer transfer needs APKLAYER_ANGLE_HOST_EGL\n");
            return;
        }
        /* Android-target ANGLE advertises DMA-BUF import but its Android Vulkan
         * display does not implement Linux's external-image sibling. */
        angle_host_backend = true;
        egl_handle = dlopen(host_angle, RTLD_NOW);
    } else {
        egl_handle = dlopen("libEGL.so.1", RTLD_NOW);
    }
    if (!egl_handle) {
        fprintf(stderr, "apklayer-gfx: could not load %s for buffer transfers\n",
                angle_host_backend ? "host ANGLE EGL" : angle_backend ? "Android ANGLE EGL" : "host EGL");
        return;
    }
    /* EGL core entry points have versioned signatures (notably eglCreateImage's EGLAttrib list).
     * Resolve them from libEGL itself; eglGetProcAddress is reserved for GLES entry points. */
    gl.getProcAddress = (void *(*)(const char *))symbol("eglGetProcAddress");
    if (angle_backend && !angle_host_backend) gl.getDisplay = (EGLp (*)(EGLp))symbol("eglGetDisplay");
    gl.initialize = (unsigned (*)(EGLp, int *, int *))symbol("eglInitialize");
    gl.bindAPI = (unsigned (*)(unsigned))symbol("eglBindAPI");
    gl.createContext = (EGLp (*)(EGLp, EGLp, EGLp, const int *))symbol("eglCreateContext");
    gl.destroyContext = (unsigned (*)(EGLp, EGLp))symbol("eglDestroyContext");
    gl.makeCurrent = (unsigned (*)(EGLp, EGLp, EGLp, EGLp))symbol("eglMakeCurrent");
    gl.queryString = (const char *(*)(EGLp, int))symbol("eglQueryString");
    gl.getCurrentContext = (EGLp (*)(void))symbol("eglGetCurrentContext");
    gl.getCurrentDisplay = (EGLp (*)(void))symbol("eglGetCurrentDisplay");
    gl.getCurrentSurface = (EGLp (*)(int))symbol("eglGetCurrentSurface");
    gl.createImage = (EGLp (*)(EGLp, EGLp, unsigned, void *, const EGLAttrib *))symbol("eglCreateImage");
    gl.destroyImage = (unsigned (*)(EGLp, EGLp))symbol("eglDestroyImage");
    if (!angle_backend || angle_host_backend)
        gl.getPlatformDisplay = (EGLp (*)(unsigned, void *, const EGLAttrib *))symbol("eglGetPlatformDisplay");
    GFX_DEBUG("loaded %s EGL core symbols (eglCreateImage=%p)\n",
              angle_host_backend ? "host ANGLE" : angle_backend ? "Android ANGLE" : "host", (void *)gl.createImage);
    if (!gl.getProcAddress || !gl.initialize || !gl.bindAPI || !gl.createContext || !gl.destroyContext || !gl.makeCurrent || !gl.createImage || !gl.destroyImage
        || !gl.getCurrentContext || !gl.getCurrentDisplay || !gl.getCurrentSurface
        || (angle_backend && !gl.queryString) || (angle_backend && !angle_host_backend && !gl.getDisplay)
        || ((!angle_backend || angle_host_backend) && !gl.getPlatformDisplay)) return;
    struct { void **slot; const char *name; } fns[] = {
#define F(field, name) { (void **)&gl.field, name }
        F(imageTargetTexture2D, "glEGLImageTargetTexture2DOES"),
        F(genTextures, "glGenTextures"), F(deleteTextures, "glDeleteTextures"), F(bindTexture, "glBindTexture"),
        F(texParameteri, "glTexParameteri"), F(texSubImage2D, "glTexSubImage2D"),
        F(genFramebuffers, "glGenFramebuffers"), F(deleteFramebuffers, "glDeleteFramebuffers"), F(bindFramebuffer, "glBindFramebuffer"),
        F(framebufferTexture2D, "glFramebufferTexture2D"), F(checkFramebufferStatus, "glCheckFramebufferStatus"),
        F(pixelStorei, "glPixelStorei"), F(getError, "glGetError"), F(texImage2D, "glTexImage2D"),
        F(createShader, "glCreateShader"), F(shaderSource, "glShaderSource"), F(compileShader, "glCompileShader"),
        F(getShaderiv, "glGetShaderiv"), F(deleteShader, "glDeleteShader"), F(createProgram, "glCreateProgram"),
        F(attachShader, "glAttachShader"), F(linkProgram, "glLinkProgram"), F(getProgramiv, "glGetProgramiv"),
        F(deleteProgram, "glDeleteProgram"), F(useProgram, "glUseProgram"), F(getUniformLocation, "glGetUniformLocation"),
        F(activeTexture, "glActiveTexture"), F(uniform1i, "glUniform1i"), F(viewport, "glViewport"),
        F(drawArrays, "glDrawArrays"), F(finish, "glFinish"), F(readPixels, "glReadPixels"),  /* readPixels last: the "loaded" mark */
#undef F
    };
    for (size_t i = 0; i < sizeof fns / sizeof *fns; i++)
        if (!(*fns[i].slot = gl.getProcAddress(fns[i].name))) return;
}

static bool load(void)
{
    pthread_once(&load_once, load_symbols);
    return gl.readPixels != NULL;
}

static bool has_extension(const char *extensions, const char *name)
{
    size_t n = strlen(name);
    for (const char *p = extensions; p && *p;) {
        while (*p == ' ') p++;
        const char *end = strchr(p, ' ');
        size_t len = end ? (size_t)(end - p) : strlen(p);
        if (len == n && !memcmp(p, name, n)) return true;
        p = end ? end + 1 : p + len;
    }
    return false;
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

struct pixel_upload {
    const uint8_t *pixels;
    unsigned format, type;
    int row_length, alignment;
};

/* RGBX uploads use RGB texels. BGRX uploads use opaque BGRA texels. */
static void pack_upload_row(uint32_t fourcc, uint32_t width, unsigned bpp, const uint8_t *src, uint8_t *dst)
{
    if (fourcc == DRM_FORMAT_XBGR8888) {
        for (uint32_t x = 0; x < width; ++x) memcpy(dst + (size_t)x * 3, src + (size_t)x * 4, 3);
    } else {
        memcpy(dst, src, (size_t)width * bpp);
        if (fourcc == DRM_FORMAT_XRGB8888)
            for (uint32_t x = 0; x < width; ++x) dst[(size_t)x * 4 + 3] = 0xff;
    }
}

/* ---- the GPU side ---- */

static pthread_mutex_t ctx_lock = PTHREAD_MUTEX_INITIALIZER;
static struct gbm_device *ctx_gbm;
static EGLp ctx_dpy, ctx;
static unsigned copy_program;
static int copy_sampler;
static struct {
    unsigned fb, tex;
    uint32_t width, height;
} copy_target;

struct imported_texture {
    dev_t dev;
    ino_t ino;
    uint64_t modifier, used;
    uint32_t width, height, fourcc, stride, offset;
    unsigned tex;
};

static struct imported_texture imports[4];
static uint64_t import_clock;

static void destroy_context(void)
{
    if (ctx) gl.destroyContext(ctx_dpy, ctx);
    ctx = NULL;
    ctx_gbm = NULL;
    copy_program = 0;
    memset(&copy_target, 0, sizeof copy_target);
    memset(imports, 0, sizeof imports);
    import_clock = 0;
}

void apklayer_gfx_transfer_close(struct gbm_device *gbm)
{
    pthread_mutex_lock(&ctx_lock);
    if (ctx_gbm == gbm) destroy_context();
    pthread_mutex_unlock(&ctx_lock);
}

/* The process's copy context on gbm's display (made once; ctx_lock held). */
static bool context(struct gbm_device *gbm)
{
    if (ctx && ctx_gbm == gbm) return true;
    EGLp dpy = angle_backend && !angle_host_backend ? gl.getDisplay(NULL) : gl.getPlatformDisplay(EGL_PLATFORM_GBM_KHR, gbm, NULL);
    int major, minor;
    if (!dpy) { GFX_DEBUG("eglGetDisplay returned no display\n"); return false; }
    if (!gl.initialize(dpy, &major, &minor)) { GFX_DEBUG("eglInitialize failed\n"); return false; }
    GFX_DEBUG("EGL initialized %d.%d\n", major, minor);
    if (!gl.bindAPI(EGL_OPENGL_ES_API)) { GFX_DEBUG("eglBindAPI(EGL_OPENGL_ES_API) failed\n"); return false; }
    if (angle_backend) {
        const char *ext = gl.queryString(dpy, EGL_EXTENSIONS);
        if (!has_extension(ext, "EGL_EXT_image_dma_buf_import") || !has_extension(ext, "EGL_EXT_image_dma_buf_import_modifiers")
            || !has_extension(ext, "EGL_KHR_surfaceless_context")) {
            fprintf(stderr, "apklayer-gfx: ANGLE does not expose dma-buf import and surfaceless-context support\n");
            return false;
        }
    }
    static const int attrs[] = { EGL_CONTEXT_MAJOR_VERSION, 3, EGL_NONE };  /* ES 3: glBlitFramebuffer */
    EGLp c = gl.createContext(dpy, NULL /* EGL_NO_CONFIG_KHR */, NULL, attrs);
    if (!c) { GFX_DEBUG("eglCreateContext failed\n"); return false; }
    destroy_context();
    ctx_gbm = gbm, ctx_dpy = dpy, ctx = c;
    GFX_DEBUG("EGL ES3 context created\n");
    return true;
}

/* Imported dma-bufs on NVIDIA are often sampleable but not framebuffer-renderable. Sample them
 * into an ordinary RGBA8 framebuffer for readback; uploads use the import's transfer-dst usage. */
static bool ensure_copy_program(void)
{
    if (copy_program) return true;
    static const char *vertex =
        "#version 300 es\n"
        "out vec2 uv;\n"
        "void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));"
        "uv=p;gl_Position=vec4(p*2.0-1.0,0.0,1.0);}\n";
    static const char *fragment =
        "#version 300 es\n"
        "precision highp float;in vec2 uv;uniform sampler2D src;out vec4 color;"
        "void main(){color=texture(src,uv);}\n";
    unsigned vs = gl.createShader(GL_VERTEX_SHADER), fs = gl.createShader(GL_FRAGMENT_SHADER);
    int ok = 0;
    if (!vs || !fs) goto fail;
    gl.shaderSource(vs, 1, &vertex, NULL); gl.compileShader(vs); gl.getShaderiv(vs, GL_COMPILE_STATUS, &ok);
    if (!ok) goto fail;
    gl.shaderSource(fs, 1, &fragment, NULL); gl.compileShader(fs); gl.getShaderiv(fs, GL_COMPILE_STATUS, &ok);
    if (!ok) goto fail;
    copy_program = gl.createProgram();
    if (!copy_program) goto fail;
    gl.attachShader(copy_program, vs); gl.attachShader(copy_program, fs); gl.linkProgram(copy_program);
    gl.getProgramiv(copy_program, GL_LINK_STATUS, &ok);
    if (!ok) { gl.deleteProgram(copy_program); copy_program = 0; goto fail; }
    copy_sampler = gl.getUniformLocation(copy_program, "src");
    gl.deleteShader(vs); gl.deleteShader(fs);
    GFX_DEBUG("compiled ANGLE dma-buf sampling program\n");
    return copy_sampler >= 0;
fail:
    if (vs) gl.deleteShader(vs);
    if (fs) gl.deleteShader(fs);
    GFX_DEBUG("failed to compile ANGLE dma-buf sampling program\n");
    return false;
}

static unsigned read_pixels(uint32_t width, uint32_t height, int row_length, uint8_t *dst)
{
    gl.pixelStorei(GL_PACK_ALIGNMENT, 4);
    gl.pixelStorei(GL_PACK_ROW_LENGTH, row_length);
    gl.readPixels(0, 0, (int)width, (int)height, GL_RGBA, GL_UNSIGNED_BYTE, dst);
    return gl.getError();
}

static void delete_copy_target(void)
{
    gl.deleteFramebuffers(1, &copy_target.fb);
    gl.deleteTextures(1, &copy_target.tex);
    memset(&copy_target, 0, sizeof copy_target);
}

/* Keep one private RGBA target for repeated reads from non-renderable imports. */
static bool bind_copy_target(uint32_t width, uint32_t height)
{
    if (!copy_target.fb) gl.genFramebuffers(1, &copy_target.fb);
    gl.bindFramebuffer(GL_FRAMEBUFFER, copy_target.fb);
    if (copy_target.width == width && copy_target.height == height) return true;
    if (!copy_target.tex) gl.genTextures(1, &copy_target.tex);
    gl.bindTexture(GL_TEXTURE_2D, copy_target.tex);
    gl.texImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, (int)width, (int)height, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    gl.framebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, copy_target.tex, 0);
    unsigned status = gl.checkFramebufferStatus(GL_FRAMEBUFFER);
    if (gl.getError() != GL_NO_ERROR || status != GL_FRAMEBUFFER_COMPLETE) {
        delete_copy_target();
        return false;
    }
    copy_target.width = width;
    copy_target.height = height;
    return true;
}

/* Four small tiled imports cover a typical swapchain and keep at most 8 MiB of DMA-BUFs alive.
 * A texture keeps the underlying allocation alive after eglDestroyImage, so its inode cannot be
 * recycled while cached. Use the allocation identity and its full layout, since fd numbers can be reused. */
static unsigned import_texture(int fd, uint32_t width, uint32_t height, uint32_t fourcc,
                               uint32_t stride, uint32_t offset, uint64_t modifier,
                               struct imported_texture **retained)
{
    struct imported_texture *slot = NULL;
    struct stat st;
    if (modifier != DRM_FORMAT_MOD_LINEAR && modifier != DRM_FORMAT_MOD_INVALID &&
        fstat(fd, &st) == 0 && st.st_size > 0 && st.st_size <= 2 * 1024 * 1024) {
        for (size_t i = 0; i < sizeof imports / sizeof *imports; ++i) {
            struct imported_texture *entry = &imports[i];
            if (entry->tex && entry->dev == st.st_dev && entry->ino == st.st_ino &&
                entry->width == width && entry->height == height && entry->fourcc == fourcc &&
                entry->stride == stride && entry->offset == offset && entry->modifier == modifier) {
                entry->used = ++import_clock;
                *retained = entry;
                gl.bindTexture(GL_TEXTURE_2D, entry->tex);
                return entry->tex;
            }
            if (!slot || entry->used < slot->used) slot = entry;
        }
    }

    EGLAttrib attrs[] = { EGL_WIDTH, width, EGL_HEIGHT, height, EGL_LINUX_DRM_FOURCC_EXT, fourcc, EGL_DMA_BUF_PLANE0_FD_EXT, fd,
        EGL_DMA_BUF_PLANE0_OFFSET_EXT, offset, EGL_DMA_BUF_PLANE0_PITCH_EXT, stride,
        EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT, (EGLAttrib)(modifier & 0xffffffff), EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT, (EGLAttrib)(modifier >> 32), EGL_NONE };
    if (modifier == DRM_FORMAT_MOD_INVALID) attrs[12] = EGL_NONE;
    EGLp image = gl.createImage(ctx_dpy, NULL, EGL_LINUX_DMA_BUF_EXT, NULL, attrs);
    if (!image) return 0;
    unsigned tex = 0;
    gl.genTextures(1, &tex);
    gl.bindTexture(GL_TEXTURE_2D, tex);
    gl.imageTargetTexture2D(GL_TEXTURE_2D, image);
    gl.destroyImage(ctx_dpy, image);
    gl.texParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    gl.texParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    gl.texParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    gl.texParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (gl.getError() != GL_NO_ERROR) { gl.deleteTextures(1, &tex); return 0; }
    if (slot) {
        gl.deleteTextures(1, &slot->tex);
        *slot = (struct imported_texture){
            st.st_dev, st.st_ino, modifier, ++import_clock, width, height, fourcc, stride, offset, tex,
        };
        *retained = slot;
    }
    return tex;
}

/* The GPU copy between the dmabuf and a CPU view in RGBA8. */
static int gpu_copy(struct gbm_device *gbm, int fd, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t stride,
                    uint32_t offset, uint64_t modifier, const struct pixel_upload *upload, uint8_t *dst, uint32_t rgba_stride)
{
    pthread_mutex_lock(&ctx_lock);
    if (!context(gbm)) { pthread_mutex_unlock(&ctx_lock); return -ENODEV; }
    /* the caller's thread may have its own context current (libapklayer_egl uses the same host EGL): put it back afterwards */
    EGLp old_dpy = gl.getCurrentDisplay(), old_ctx = gl.getCurrentContext(), old_draw = gl.getCurrentSurface(EGL_DRAW), old_read = gl.getCurrentSurface(EGL_READ);
    int row_length = rgba_stride == width * 4 ? 0 : (int)(rgba_stride / 4);
    int r = -EIO;
    const char *why = "no context";
    if (gl.makeCurrent(ctx_dpy, NULL, NULL, ctx)) {  /* surfaceless */
        GFX_DEBUG("eglMakeCurrent succeeded\n");
        while (gl.getError() != GL_NO_ERROR) {}
        struct imported_texture *retained = NULL;
        unsigned tex = import_texture(fd, width, height, fourcc, stride, offset, modifier, &retained);
        unsigned fb = 0;
        why = "could not import the dma-buf as a texture";
        if (tex) {
            if (upload) {
                why = "a GL error while uploading into dma-buf";
                gl.pixelStorei(GL_UNPACK_ALIGNMENT, upload->alignment);
                gl.pixelStorei(GL_UNPACK_ROW_LENGTH, upload->row_length);
                gl.texSubImage2D(GL_TEXTURE_2D, 0, 0, 0, (int)width, (int)height, upload->format, upload->type, upload->pixels);
                unsigned error = gl.getError();
                gl.finish();  /* the pixels are in the buffer before the consumer gets it */
                unsigned finish_error = gl.getError();
                GFX_DEBUG("dma-buf TexSubImage format 0x%x type 0x%x error 0x%x, finish error 0x%x\n",
                          upload->format, upload->type, error, finish_error);
                if (error == GL_NO_ERROR && finish_error == GL_NO_ERROR) r = 0;
            } else {
                if (fourcc == DRM_FORMAT_ABGR8888) {
                    /* Read renderable RGBA images without a temporary target or a draw. */
                    gl.genFramebuffers(1, &fb);
                    gl.bindFramebuffer(GL_FRAMEBUFFER, fb);
                    gl.framebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
                    unsigned status = gl.checkFramebufferStatus(GL_FRAMEBUFFER);
                    if (gl.getError() == GL_NO_ERROR && status == GL_FRAMEBUFFER_COMPLETE &&
                        read_pixels(width, height, row_length, dst) == GL_NO_ERROR) {
                        r = 0;
                        goto copy_cleanup;
                    }
                }
                why = "the RGBA readback framebuffer is incomplete";
                if (!bind_copy_target(width, height)) goto copy_cleanup;
                why = "could not create dma-buf sampling program";
                if (ensure_copy_program()) {
                    gl.viewport(0, 0, (int)width, (int)height);
                    gl.activeTexture(GL_TEXTURE0);
                    gl.bindTexture(GL_TEXTURE_2D, tex);
                    gl.useProgram(copy_program);
                    gl.uniform1i(copy_sampler, 0);
                    gl.drawArrays(GL_TRIANGLES, 0, 3);
                    /* Native NVIDIA readback is faster with the draw submitted separately. */
                    if (!angle_backend) gl.finish();
                    unsigned error = read_pixels(width, height, row_length, dst);
                    GFX_DEBUG("dma-buf sample GL error 0x%x\n", error);
                    why = "a GL error while sampling dma-buf";
                    if (error == GL_NO_ERROR) r = 0;
                }
            }
copy_cleanup:
            gl.bindFramebuffer(GL_FRAMEBUFFER, 0);
            gl.useProgram(0);
            /* Large captures are infrequent. Cap retained target storage at 8 MiB. */
            if ((uint64_t)copy_target.width * copy_target.height > 2 * 1024 * 1024) delete_copy_target();
            if (fb) gl.deleteFramebuffers(1, &fb);
            if (!retained || r != 0) {
                gl.deleteTextures(1, &tex);
                if (retained) memset(retained, 0, sizeof *retained);
            }
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
    if (!bpp || !dst || !width || !height || width > dst_stride / bpp || width > UINT32_MAX / 4 ||
        height > INT_MAX || (size_t)width * 4 > SIZE_MAX / height) return -ENOTSUP;
    if (!load()) return -ENOSYS;
    // ANGLE handles padded RGBA pitch efficiently. Keep the tight GL pack on native drivers.
    bool direct = dst_stride == width * 4 || (angle_backend && dst_stride % 4 == 0);
    if ((fourcc == DRM_FORMAT_ABGR8888 || fourcc == DRM_FORMAT_XBGR8888) && direct)
        return gpu_copy(gbm, fd, width, height, fourcc, stride, offset, modifier, NULL, dst, dst_stride);
    uint8_t *rgba = malloc((size_t)width * 4 * height);
    if (!rgba) return -ENOMEM;
    int r = gpu_copy(gbm, fd, width, height, fourcc, stride, offset, modifier, NULL, rgba, width * 4);
    for (uint32_t y = 0; !r && y < height; y++) from_rgba(fourcc, rgba + (size_t)y * width * 4, (uint8_t *)dst + (size_t)y * dst_stride, width);
    free(rgba);
    return r;
}

int apklayer_gfx_upload(struct gbm_device *gbm, int fd, uint32_t width, uint32_t height, uint32_t fourcc, uint32_t stride,
                        uint32_t offset, uint64_t modifier, const void *src, uint32_t src_stride)
{
    unsigned bpp = pixel_bytes(fourcc);
    if (!bpp || !src || !width || !height || width > src_stride / bpp || height > INT_MAX) return -ENOTSUP;
    if (!load()) return -ENOSYS;
    struct pixel_upload upload = {
        .pixels = src, .format = GL_RGBA, .type = GL_UNSIGNED_BYTE, .alignment = 4,
    };
    unsigned upload_bpp = bpp;
    switch (fourcc) {
    case DRM_FORMAT_ARGB8888: case DRM_FORMAT_XRGB8888:
        upload.format = GL_BGRA_EXT;
        break;
    case DRM_FORMAT_XBGR8888: case DRM_FORMAT_BGR888:
        upload.format = GL_RGB;
        upload.alignment = 1;
        upload_bpp = 3;
        break;
    case DRM_FORMAT_RGB565:
        upload.format = GL_RGB;
        upload.type = GL_UNSIGNED_SHORT_5_6_5;
        upload.alignment = 2;
        break;
    }
    bool repack = fourcc == DRM_FORMAT_XBGR8888 || fourcc == DRM_FORMAT_XRGB8888 ||
                  src_stride % upload_bpp != 0 ||
                  (fourcc == DRM_FORMAT_ABGR8888 && !angle_backend && src_stride != width * 4);
    uint8_t *packed = NULL;
    if (repack) {
        size_t row_bytes = (size_t)width * upload_bpp;
        if (row_bytes > SIZE_MAX / height) return -ENOMEM;
        packed = malloc(row_bytes * height);
        if (!packed) return -ENOMEM;
        for (uint32_t y = 0; y < height; ++y)
            pack_upload_row(fourcc, width, upload_bpp, (const uint8_t *)src + (size_t)y * src_stride, packed + (size_t)y * row_bytes);
        upload.pixels = packed;
    } else if (src_stride / upload_bpp != width) {
        upload.row_length = (int)(src_stride / upload_bpp);
    }
    int r = gpu_copy(gbm, fd, width, height, fourcc, stride, offset, modifier, &upload, NULL, 0);
    free(packed);
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
        // ANGLE's Vulkan modifier query can return the list without filling external_only.
        // Start with the same ordinary texture import assumption as the no-query path.
        unsigned ext[256] = {0};
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
