// Window surfaces of apklayer's libEGL (Egl.cpp holds the rest). The host's GBM platform has no pbuffers and cannot render into somebody
// else's buffer, so an Android window is a host window surface on a gbm_surface of the same size, and eglSwapBuffers copies the finished
// frame on the GPU (glBlitFramebuffer between two dmabuf EGLImages, a native fence handed to the BufferQueue: no CPU touch) into a buffer
// dequeued from the ANativeWindow. A resize (the window's default size changing) makes a new surface pair behind the same app-side EGLSurface.
// A pbuffer is the same pair without an Android window.
#define LOG_TAG "apklayer.egl"

#include <drm_fourcc.h>
#include <log/log.h>
#include <poll.h>
#include <system/window.h>
#include <unistd.h>

#include <mutex>
#include <set>
#include <vector>

#include "EglHost.h"

using namespace apklayer::egl;

namespace {

// The app's EGLSurface is the address of its Window: the host surface behind it is replaced when the Android window changes size.
struct Window {
    EGLDisplay dpy;
    EGLConfig cfg;
    ANativeWindow* native;
    void* gbmSurface;
    EGLSurface host;
    uint32_t width, height;
    EGLint visual;
};
std::recursive_mutex windowsLock;
std::set<Window*> windows;

Window* windowOf(EGLSurface s) {  // windowsLock held
    auto* w = static_cast<Window*>(s);
    return windows.count(w) ? w : nullptr;
}
EGLSurface appSurface(EGLSurface host) {  // reverse, for eglGetCurrentSurface
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    for (Window* w : windows) if (w->host == host) return w;
    return host;
}

constexpr int kApiEgl = 1;  // NATIVE_WINDOW_API_EGL
constexpr uint64_t kUsageRender = 0x200, kUsageTexture = 0x100;  // GRALLOC_USAGE_HW_RENDER | HW_TEXTURE

// The current context's framebuffer bindings, texture binding and scissor test around our copy.
struct GlState {
    int read = 0, draw = 0, tex = 0, scissor = 0;
    GlState() {
        auto get = gl<void (*)(unsigned, int*)>("glGetIntegerv");
        get(0x8CAA, &read);  // GL_READ_FRAMEBUFFER_BINDING
        get(0x8CA6, &draw);  // GL_DRAW_FRAMEBUFFER_BINDING
        get(0x8069, &tex);   // GL_TEXTURE_BINDING_2D
        scissor = gl<unsigned char (*)(unsigned)>("glIsEnabled")(0xC11);
        if (scissor) gl<void (*)(unsigned)>("glDisable")(0xC11);
    }
    ~GlState() {
        gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8CA8 /* GL_READ_FRAMEBUFFER */, read);
        gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8CA9 /* GL_DRAW_FRAMEBUFFER */, draw);
        gl<void (*)(unsigned, unsigned)>("glBindTexture")(0xDE1, tex);
        if (scissor) gl<void (*)(unsigned)>("glEnable")(0xC11);
    }
};

// A framebuffer with the image as its colour attachment; image and texture are the caller's to delete.
unsigned fboForImage(EGLImage image, unsigned* tex) {
    unsigned fbo = 0;
    gl<void (*)(int, unsigned*)>("glGenTextures")(1, tex);
    gl<void (*)(unsigned, unsigned)>("glBindTexture")(0xDE1, *tex);
    gl<void (*)(unsigned, void*)>("glEGLImageTargetTexture2DOES")(0xDE1, image);
    gl<void (*)(int, unsigned*)>("glGenFramebuffers")(1, &fbo);
    gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8D40, fbo);
    gl<void (*)(unsigned, unsigned, unsigned, unsigned, int)>("glFramebufferTexture2D")(0x8D40, 0x8CE0, 0xDE1, *tex, 0);
    return fbo;
}

// The frame just swapped into the gbm_surface, copied into a buffer of the Android window and queued.
void presentToWindow(EGLDisplay dpy, Window& w) {
    ANativeWindow* nw = w.native;
    void* bo = gbmFn<void* (*)(void*)>("gbm_surface_lock_front_buffer")(w.gbmSurface);
    if (!bo) { ALOGE("gbm_surface_lock_front_buffer failed"); return; }
    ANativeWindowBuffer* dst = nullptr;
    int fence = -1;
    if (nw->dequeueBuffer(nw, &dst, &fence) != 0 || !dst) {
        ALOGE("dequeueBuffer failed");
        gbmFn<void (*)(void*, void*)>("gbm_surface_release_buffer")(w.gbmSurface, bo);
        return;
    }
    if (fence >= 0) { pollfd p = {fence, POLLIN, 0}; poll(&p, 1, 1000); close(fence); }

    int fd = gbmFn<int (*)(void*)>("gbm_bo_get_fd")(bo);
    uint32_t stride = gbmFn<uint32_t (*)(void*)>("gbm_bo_get_stride")(bo);
    uint32_t format = gbmFn<uint32_t (*)(void*)>("gbm_bo_get_format")(bo);
    uint64_t modifier = gbmFn<uint64_t (*)(void*)>("gbm_bo_get_modifier")(bo);
    std::vector<EGLint> a = {EGL_WIDTH, (EGLint)w.width, EGL_HEIGHT, (EGLint)w.height, EGL_LINUX_DRM_FOURCC_EXT, (EGLint)format,
                             EGL_DMA_BUF_PLANE0_FD_EXT, fd, EGL_DMA_BUF_PLANE0_OFFSET_EXT, 0, EGL_DMA_BUF_PLANE0_PITCH_EXT, (EGLint)stride};
    if (modifier != DRM_FORMAT_MOD_INVALID)
        a.insert(a.end(), {EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT, (EGLint)(modifier & 0xffffffff), EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT, (EGLint)(modifier >> 32)});
    a.push_back(EGL_NONE);
    auto createImage = host<PFNEGLCREATEIMAGEKHRPROC>("eglCreateImageKHR");
    auto destroyImage = host<PFNEGLDESTROYIMAGEKHRPROC>("eglDestroyImageKHR");
    EGLImage src = createImage(dpy, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, a.data());
    EGLImage out = imageFromNativeBuffer(dpy, dst);
    int outFence = -1;
    if (src != EGL_NO_IMAGE && out != EGL_NO_IMAGE && host<EGLContext (*)()>("eglGetCurrentContext")() != EGL_NO_CONTEXT) {
        GlState saved;
        unsigned srcTex = 0, dstTex = 0;
        unsigned srcFbo = fboForImage(src, &srcTex), dstFbo = fboForImage(out, &dstTex);
        gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8CA8 /* GL_READ_FRAMEBUFFER */, srcFbo);
        gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8CA9 /* GL_DRAW_FRAMEBUFFER */, dstFbo);
        gl<void (*)(int, int, int, int, int, int, int, int, unsigned, unsigned)>("glBlitFramebuffer")(
                0, 0, w.width, w.height, 0, 0, dst->width, dst->height, 0x4000 /* GL_COLOR_BUFFER_BIT */, 0x2600 /* GL_NEAREST */);
        gl<void (*)()>("glFlush")();
        auto createSync = host<PFNEGLCREATESYNCKHRPROC>("eglCreateSyncKHR");
        auto dupFence = host<PFNEGLDUPNATIVEFENCEFDANDROIDPROC>("eglDupNativeFenceFDANDROID");
        EGLSyncKHR sync = createSync && dupFence ? createSync(dpy, EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr) : EGL_NO_SYNC_KHR;
        if (sync != EGL_NO_SYNC_KHR) {
            gl<void (*)()>("glFlush")();
            outFence = dupFence(dpy, sync);
            host<PFNEGLDESTROYSYNCKHRPROC>("eglDestroySyncKHR")(dpy, sync);
        }
        if (outFence < 0) gl<void (*)()>("glFinish")();  // no native fence: finish the work here
        gl<void (*)(int, unsigned*)>("glDeleteFramebuffers")(1, &srcFbo);
        gl<void (*)(int, unsigned*)>("glDeleteFramebuffers")(1, &dstFbo);
        gl<void (*)(int, unsigned*)>("glDeleteTextures")(1, &srcTex);
        gl<void (*)(int, unsigned*)>("glDeleteTextures")(1, &dstTex);
    } else {
        ALOGE("window present: images %p %p or no current context", src, out);
    }
    if (src != EGL_NO_IMAGE) destroyImage(dpy, src);
    if (out != EGL_NO_IMAGE) destroyImage(dpy, out);
    close(fd);
    gbmFn<void (*)(void*, void*)>("gbm_surface_release_buffer")(w.gbmSurface, bo);
    nw->queueBuffer(nw, dst, outFence);
}

// The Android window's current size: the user's if set, else the consumer's default (what a resize changes).
bool windowSize(ANativeWindow* nw, uint32_t* w, uint32_t* h) {
    int iw = 0, ih = 0;
    nw->query(nw, 6 /* NATIVE_WINDOW_DEFAULT_WIDTH */, &iw);
    nw->query(nw, 7 /* NATIVE_WINDOW_DEFAULT_HEIGHT */, &ih);
    if (iw <= 0 || ih <= 0) { nw->query(nw, 0 /* WIDTH */, &iw); nw->query(nw, 1 /* HEIGHT */, &ih); }
    if (iw <= 0 || ih <= 0) return false;
    *w = iw;
    *h = ih;
    return true;
}

// A gbm_surface and a host window surface of the Android window's size, stored in the Window (the old pair is the caller's).
bool attachHostSurface(Window* win) {
    uint32_t w, h;
    if (!windowSize(win->native, &w, &h)) return false;
    void* gs = gbmFn<void* (*)(void*, uint32_t, uint32_t, uint32_t, uint32_t)>("gbm_surface_create")(
            apklayer_gfx_gbm_device(gfx()), w, h, (uint32_t)win->visual, 1u << 2 /* GBM_BO_USE_RENDERING */);
    if (!gs) { ALOGE("gbm_surface_create %ux%u format 0x%x failed", w, h, win->visual); return false; }
    EGLSurface surface = host<PFNEGLCREATEPLATFORMWINDOWSURFACEPROC>("eglCreatePlatformWindowSurface")(win->dpy, win->cfg, gs, nullptr);
    if (surface == EGL_NO_SURFACE) {
        ALOGE("host window surface failed: 0x%x", host<EGLint (*)()>("eglGetError")());
        gbmFn<void (*)(void*)>("gbm_surface_destroy")(gs);
        return false;
    }
    win->gbmSurface = gs;
    win->host = surface;
    win->width = w;
    win->height = h;
    return true;
}

// The window changed size: a new surface pair, the context moved over if this surface was current, the old pair destroyed.
void resizeWindow(Window* win) {
    EGLSurface oldHost = win->host;
    void* oldGbm = win->gbmSurface;
    uint32_t oldW = win->width, oldH = win->height;
    EGLDisplay cur = host<EGLDisplay (*)()>("eglGetCurrentDisplay")();
    EGLContext ctx = host<EGLContext (*)()>("eglGetCurrentContext")();
    bool wasDraw = host<EGLSurface (*)(EGLint)>("eglGetCurrentSurface")(EGL_DRAW) == oldHost;
    bool wasRead = host<EGLSurface (*)(EGLint)>("eglGetCurrentSurface")(EGL_READ) == oldHost;
    if (!attachHostSurface(win)) { win->host = oldHost; win->gbmSurface = oldGbm; win->width = oldW; win->height = oldH; return; }
    if (wasDraw || wasRead)
        host<PFNEGLMAKECURRENTPROC>("eglMakeCurrent")(cur, wasDraw ? win->host : host<EGLSurface (*)(EGLint)>("eglGetCurrentSurface")(EGL_DRAW),
                                                      wasRead ? win->host : host<EGLSurface (*)(EGLint)>("eglGetCurrentSurface")(EGL_READ), ctx);
    host<EGLBoolean (*)(EGLDisplay, EGLSurface)>("eglDestroySurface")(win->dpy, oldHost);
    gbmFn<void (*)(void*)>("gbm_surface_destroy")(oldGbm);
    ALOGI("window %p resized %ux%u -> %ux%u", win, oldW, oldH, win->width, win->height);
}

EGLBoolean swapWindow(EGLDisplay dpy, EGLSurface surface) {
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    Window* win = windowOf(surface);
    if (!win) return host<EGLBoolean (*)(EGLDisplay, EGLSurface)>("eglSwapBuffers")(dpy, surface);
    if (!win->native) return EGL_TRUE;  // a pbuffer is never presented
    EGLBoolean ok = host<EGLBoolean (*)(EGLDisplay, EGLSurface)>("eglSwapBuffers")(dpy, win->host);
    if (!ok) return ok;
    presentToWindow(dpy, *win);
    uint32_t w, h;
    if (windowSize(win->native, &w, &h) && (w != win->width || h != win->height)) resizeWindow(win);
    return ok;
}

}  // namespace

EGLSurface apklayer::egl::hostSurface(EGLSurface s) {  // the host's surface behind one of ours (anything else, EGL_NO_SURFACE included, is the host's already)
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    Window* w = windowOf(s);
    return w ? w->host : s;
}

extern "C" {

EGLSurface eglCreateWindowSurface(EGLDisplay dpy, EGLConfig cfg, EGLNativeWindowType native, const EGLint*) {
    auto* nw = reinterpret_cast<ANativeWindow*>(native);
    if (nw == nullptr || nw->common.magic != ANDROID_NATIVE_WINDOW_MAGIC) { tError = EGL_BAD_NATIVE_WINDOW; return EGL_NO_SURFACE; }
    auto* win = new Window{dpy, cfg, nw, nullptr, EGL_NO_SURFACE, 0, 0, 0};
    host<EGLBoolean (*)(EGLDisplay, EGLConfig, EGLint, EGLint*)>("eglGetConfigAttrib")(dpy, cfg, EGL_NATIVE_VISUAL_ID, &win->visual);
    if (!attachHostSurface(win)) { delete win; tError = EGL_BAD_ALLOC; return EGL_NO_SURFACE; }
    // apklayer patch: EGL owns the window until eglDestroySurface. EGL10's
    // SurfaceTexture wrapper drops its temporary sp<Surface> after this call.
    nw->common.incRef(&nw->common);
    // the Android side: connect as an EGL producer and ask for buffers the GPU renders to and SurfaceFlinger samples
    native_window_api_connect(nw, kApiEgl);
    native_window_set_usage(nw, kUsageRender | kUsageTexture);
    native_window_set_buffers_format(nw, 1 /* HAL_PIXEL_FORMAT_RGBA_8888 */);
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    windows.insert(win);
    ALOGI("window surface %p: %ux%u, gbm format 0x%x", win, win->width, win->height, win->visual);
    return win;
}

EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) { return swapWindow(dpy, surface); }

EGLBoolean eglSwapBuffersWithDamageKHR(EGLDisplay dpy, EGLSurface surface, const EGLint*, EGLint) { return swapWindow(dpy, surface); }
EGLBoolean eglSwapBuffersWithDamageEXT(EGLDisplay dpy, EGLSurface surface, const EGLint* rects, EGLint n) { return eglSwapBuffersWithDamageKHR(dpy, surface, rects, n); }  // GeckoView asks for the EXT name
// HWUI announces the frame's damage with it before drawing; every swap here presents the whole buffer, so there is nothing to tell the host
// (forwarding failed with EGL_BAD_SURFACE: the handle is ours, not the host's, and HWUI aborts on the failure)
EGLBoolean eglSetDamageRegionKHR(EGLDisplay, EGLSurface, EGLint*, EGLint) { return EGL_TRUE; }

EGLBoolean eglDestroySurface(EGLDisplay dpy, EGLSurface surface) {
    {
        std::lock_guard<std::recursive_mutex> lock(windowsLock);
        if (Window* w = windowOf(surface)) {
            if (w->native) native_window_api_disconnect(w->native, kApiEgl);
            windows.erase(w);
            if (w->native) w->native->common.decRef(&w->native->common);
            EGLBoolean ok = host<EGLBoolean (*)(EGLDisplay, EGLSurface)>("eglDestroySurface")(dpy, w->host);
            gbmFn<void (*)(void*)>("gbm_surface_destroy")(w->gbmSurface);
            delete w;
            return ok;
        }
    }
    return host<EGLBoolean (*)(EGLDisplay, EGLSurface)>("eglDestroySurface")(dpy, surface);
}

EGLSurface eglGetCurrentSurface(EGLint which) {
    return appSurface(host<EGLSurface (*)(EGLint)>("eglGetCurrentSurface")(which));
}

EGLBoolean eglQuerySurface(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint* value) {
    {
        std::lock_guard<std::recursive_mutex> lock(windowsLock);
        if (Window* w = windowOf(surface)) {
            if (attribute == EGL_WIDTH) { *value = w->width; return EGL_TRUE; }
            if (attribute == EGL_HEIGHT) { *value = w->height; return EGL_TRUE; }
            surface = w->host;
        }
    }
    return host<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLint, EGLint*)>("eglQuerySurface")(dpy, surface, attribute, value);
}

EGLBoolean eglSurfaceAttrib(EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint value) {
    return host<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLint, EGLint)>("eglSurfaceAttrib")(dpy, hostSurface(surface), attribute, value);
}

EGLSurface eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig cfg, const EGLint* attribs) {
    uint32_t w = 1, h = 1;
    for (; attribs && *attribs != EGL_NONE; attribs += 2) {
        if (attribs[0] == EGL_WIDTH) w = attribs[1];
        if (attribs[0] == EGL_HEIGHT) h = attribs[1];
    }
    auto* win = new Window{dpy, cfg, nullptr, nullptr, EGL_NO_SURFACE, 0, 0, 0};
    host<EGLBoolean (*)(EGLDisplay, EGLConfig, EGLint, EGLint*)>("eglGetConfigAttrib")(dpy, cfg, EGL_NATIVE_VISUAL_ID, &win->visual);
    void* gs = gbmFn<void* (*)(void*, uint32_t, uint32_t, uint32_t, uint32_t)>("gbm_surface_create")(
            apklayer_gfx_gbm_device(gfx()), w, h, (uint32_t)win->visual, 1u << 2 /* GBM_BO_USE_RENDERING */);
    EGLSurface surface = gs ? host<PFNEGLCREATEPLATFORMWINDOWSURFACEPROC>("eglCreatePlatformWindowSurface")(dpy, cfg, gs, nullptr) : EGL_NO_SURFACE;
    if (surface == EGL_NO_SURFACE) {
        ALOGE("pbuffer %ux%u format 0x%x: host surface failed", w, h, win->visual);
        if (gs) gbmFn<void (*)(void*)>("gbm_surface_destroy")(gs);
        delete win;
        tError = EGL_BAD_ALLOC;
        return EGL_NO_SURFACE;
    }
    win->gbmSurface = gs; win->host = surface; win->width = w; win->height = h;
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    windows.insert(win);
    return win;
}

EGLSurface eglCreatePlatformWindowSurface(EGLDisplay d, EGLConfig c, void* w, const EGLAttrib*) {
    return eglCreateWindowSurface(d, c, reinterpret_cast<EGLNativeWindowType>(w), nullptr);
}

}  // extern "C"
