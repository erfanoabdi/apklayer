/* Host EGL probe: what the host's EGL offers on the GBM platform of our graphics HAL's device (the platform SurfaceFlinger's
 * RenderEngine and apps will run on): extensions, a surfaceless GLES3 context, dmabuf import of our buffers (RGBA and the packed
 * YUV layouts), and native fence sync. */
#define _GNU_SOURCE
#include "apklayer/graphics.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <drm_fourcc.h>
#include <gbm.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static int failures;
#define CHECK(cond, what) do { if (cond) printf("PASS  %s\n", what); else { printf("FAIL  %s\n", what); failures++; } } while (0)
static int has(const char *list, const char *ext) { return list && strstr(list, ext) != NULL; }

static EGLImage import(EGLDisplay dpy, const struct apklayer_gfx_buffer *b, PFNEGLCREATEIMAGEKHRPROC create)
{
    EGLint a[64]; int n = 0;
    a[n++] = EGL_WIDTH; a[n++] = b->width; a[n++] = EGL_HEIGHT; a[n++] = b->height;
    a[n++] = EGL_LINUX_DRM_FOURCC_EXT; a[n++] = b->fourcc;
    static const EGLint fdk[] = { EGL_DMA_BUF_PLANE0_FD_EXT, EGL_DMA_BUF_PLANE1_FD_EXT, EGL_DMA_BUF_PLANE2_FD_EXT };
    static const EGLint offk[] = { EGL_DMA_BUF_PLANE0_OFFSET_EXT, EGL_DMA_BUF_PLANE1_OFFSET_EXT, EGL_DMA_BUF_PLANE2_OFFSET_EXT };
    static const EGLint pitchk[] = { EGL_DMA_BUF_PLANE0_PITCH_EXT, EGL_DMA_BUF_PLANE1_PITCH_EXT, EGL_DMA_BUF_PLANE2_PITCH_EXT };
    for (int i = 0; i < b->num_planes && i < 3; i++) {
        a[n++] = fdk[i]; a[n++] = b->fd[i];
        a[n++] = offk[i]; a[n++] = b->offset[i];
        a[n++] = pitchk[i]; a[n++] = b->stride[i];
        if (getenv("PROBE_MODIFIERS") && b->modifier != DRM_FORMAT_MOD_INVALID) { /* what the Android-side EGL layer passes */
            static const EGLint lo[] = { EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT, EGL_DMA_BUF_PLANE1_MODIFIER_LO_EXT, EGL_DMA_BUF_PLANE2_MODIFIER_LO_EXT };
            static const EGLint hi[] = { EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT, EGL_DMA_BUF_PLANE1_MODIFIER_HI_EXT, EGL_DMA_BUF_PLANE2_MODIFIER_HI_EXT };
            a[n++] = lo[i]; a[n++] = (EGLint)(b->modifier & 0xffffffff); a[n++] = hi[i]; a[n++] = (EGLint)(b->modifier >> 32);
        }
    }
    a[n++] = EGL_NONE;
    return create(dpy, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, NULL, a);
}

int main(void)
{
    struct apklayer_gfx *gfx = apklayer_gfx_open(NULL);
    CHECK(gfx != NULL, "graphics HAL opens");
    if (!gfx) return 1;
    PFNEGLGETPLATFORMDISPLAYEXTPROC getpd = (void *)eglGetProcAddress("eglGetPlatformDisplayEXT");
    EGLDisplay dpy = getpd ? getpd(EGL_PLATFORM_GBM_KHR, apklayer_gfx_gbm_device(gfx), NULL) : EGL_NO_DISPLAY;
    CHECK(dpy != EGL_NO_DISPLAY, "EGL display on the GBM platform");
    EGLint major, minor;
    CHECK(eglInitialize(dpy, &major, &minor), "eglInitialize");
    const char *ext = eglQueryString(dpy, EGL_EXTENSIONS);
    printf("  EGL %d.%d, vendor %s\n", major, minor, eglQueryString(dpy, EGL_VENDOR));
    static const char *want[] = { "EGL_KHR_image_base", "EGL_EXT_image_dma_buf_import", "EGL_EXT_image_dma_buf_import_modifiers",
        "EGL_KHR_fence_sync", "EGL_KHR_wait_sync", "EGL_ANDROID_native_fence_sync", "EGL_KHR_surfaceless_context",
        "EGL_KHR_no_config_context", "EGL_IMG_context_priority", "EGL_EXT_buffer_age", "EGL_KHR_partial_update",
        "EGL_ANDROID_image_native_buffer" };
    for (size_t i = 0; i < sizeof want / sizeof *want; i++) printf("  %-42s %s\n", want[i], has(ext, want[i]) ? "yes" : "no");

    eglBindAPI(EGL_OPENGL_ES_API);
    EGLint cfg_attr[] = { EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_SURFACE_TYPE, 0, EGL_NONE };
    EGLConfig cfg; EGLint ncfg = 0;
    eglChooseConfig(dpy, cfg_attr, &cfg, 1, &ncfg);
    CHECK(ncfg > 0, "a GLES3 config (no surface type)");
    EGLint ctx_attr[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctx_attr);
    CHECK(ctx != EGL_NO_CONTEXT, "GLES3 context");
    CHECK(eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx), "surfaceless eglMakeCurrent");
    printf("  GL_RENDERER %s | %s\n", glGetString(GL_RENDERER), glGetString(GL_VERSION));

    PFNEGLCREATEIMAGEKHRPROC create = (void *)eglGetProcAddress("eglCreateImageKHR");
    PFNGLEGLIMAGETARGETTEXTURE2DOESPROC target = (void *)eglGetProcAddress("glEGLImageTargetTexture2DOES");
    static const struct { uint32_t fourcc; const char *name; } fmts[] = {
        { DRM_FORMAT_ABGR8888, "ABGR8888" }, { DRM_FORMAT_NV12, "NV12 (packed)" }, { DRM_FORMAT_YVU420, "YV12 (packed)" } };
    for (size_t i = 0; i < sizeof fmts / sizeof *fmts; i++) {
        struct apklayer_gfx_buffer b;
        int r = apklayer_gfx_alloc(gfx, 128, 64, fmts[i].fourcc, APKLAYER_GFX_SAMPLE | APKLAYER_GFX_CPU_WRITE, &b);
        char what[96];
        snprintf(what, sizeof what, "%s: allocates", fmts[i].name);
        CHECK(r == 0, what);
        if (r) continue;
        EGLImage img = import(dpy, &b, create);
        snprintf(what, sizeof what, "%s: imports as an EGLImage (eglError 0x%x)", fmts[i].name, img ? 0 : eglGetError());
        CHECK(img != EGL_NO_IMAGE, what);
        if (img) {
            GLuint tex;
            glGenTextures(1, &tex);
            GLenum tgt = fmts[i].fourcc == DRM_FORMAT_ABGR8888 ? GL_TEXTURE_2D : GL_TEXTURE_EXTERNAL_OES;
            glBindTexture(tgt, tex);
            target(tgt, img);
            snprintf(what, sizeof what, "%s: binds as a texture (glError 0x%x)", fmts[i].name, glGetError());
            CHECK(glGetError() == GL_NO_ERROR, what);
            glDeleteTextures(1, &tex);
            ((PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR"))(dpy, img);
        }
        apklayer_gfx_buffer_close(&b);
    }

    /* Render into an imported dmabuf: SurfaceFlinger's framebuffer target. The EGLImage becomes a texture, the texture a framebuffer
     * attachment; try the allocation usages a gralloc buffer for GPU_RENDER_TARGET can get. */
    static const struct { uint32_t usage; const char *name; } usages[] = {
        { APKLAYER_GFX_SAMPLE | APKLAYER_GFX_RENDER, "sample+render" },
        { APKLAYER_GFX_SAMPLE | APKLAYER_GFX_RENDER | APKLAYER_GFX_SCANOUT, "sample+render+scanout" },
        { APKLAYER_GFX_SAMPLE | APKLAYER_GFX_RENDER | APKLAYER_GFX_CPU_WRITE, "sample+render+cpu(linear)" },
    };
    for (size_t i = 0; i < sizeof usages / sizeof *usages; i++) {
        struct apklayer_gfx_buffer b;
        char what[128];
        if (apklayer_gfx_alloc(gfx, 256, 128, DRM_FORMAT_ABGR8888, usages[i].usage, &b) != 0) { printf("SKIP  %s: no allocation\n", usages[i].name); continue; }
        EGLImage img = import(dpy, &b, create);
        GLuint tex = 0, fbo = 0;
        GLenum status = 0;
        if (img) {
            glGenTextures(1, &tex);
            glBindTexture(GL_TEXTURE_2D, tex);
            target(GL_TEXTURE_2D, img);
            glGenFramebuffers(1, &fbo);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
            status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        }
        snprintf(what, sizeof what, "dmabuf (%s, modifier 0x%llx) is a complete framebuffer (status 0x%x, glError 0x%x)", usages[i].name, (unsigned long long)b.modifier, status, glGetError());
        CHECK(img && status == GL_FRAMEBUFFER_COMPLETE, what);
        if (img && status == GL_FRAMEBUFFER_COMPLETE) {
            glClearColor(0.f, 1.f, 0.f, 1.f);
            glClear(GL_COLOR_BUFFER_BIT);
            uint8_t px[4] = { 0 };
            glReadPixels(10, 10, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
            snprintf(what, sizeof what, "  cleared pixel reads back as %u,%u,%u,%u", px[0], px[1], px[2], px[3]);
            CHECK(px[1] == 255 && px[0] == 0, what);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (fbo) glDeleteFramebuffers(1, &fbo);
        if (tex) glDeleteTextures(1, &tex);
        apklayer_gfx_buffer_close(&b);
    }

    PFNEGLCREATESYNCKHRPROC create_sync = (void *)eglGetProcAddress("eglCreateSyncKHR");
    PFNEGLDUPNATIVEFENCEFDANDROIDPROC dup_fence = (void *)eglGetProcAddress("eglDupNativeFenceFDANDROID");
    if (create_sync && dup_fence && has(ext, "EGL_ANDROID_native_fence_sync")) {
        glClearColor(1, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        EGLSync sync = create_sync(dpy, EGL_SYNC_NATIVE_FENCE_ANDROID, NULL);
        glFlush();
        int fd = sync ? dup_fence(dpy, sync) : -1;
        CHECK(fd >= 0, "native fence sync yields a sync_file fd");
        if (fd >= 0) close(fd);
    } else {
        printf("SKIP  native fence sync is not offered by this driver\n");
    }
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglTerminate(dpy);
    apklayer_gfx_close(gfx);
    printf(failures ? "%d FAILED\n" : "all passed\n", failures);
    return failures != 0;
}
