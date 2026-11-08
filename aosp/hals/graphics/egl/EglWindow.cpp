// Window surfaces of apklayer's libEGL (Egl.cpp holds the rest). The host's GBM platform has no pbuffers and cannot render into somebody
// else's buffer, so an Android window is a host window surface on a gbm_surface of the same size, and eglSwapBuffers copies the finished
// frame on the GPU (glBlitFramebuffer between two dmabuf EGLImages, a native fence handed to the BufferQueue: no CPU touch) into a buffer
// dequeued from the ANativeWindow. A resize (the window's default size changing) makes a new surface pair behind the same app-side EGLSurface.
// A pbuffer is the same pair without an Android window.
#define LOG_TAG "apklayer.egl"

#include <drm_fourcc.h>
#include <log/log.h>
#include <errno.h>
#include <poll.h>
#include <system/window.h>
#include <sys/stat.h>
#include <unistd.h>

#include <mutex>
#include <optional>
#include <set>
#include <vector>

#include "EglHost.h"

using namespace apklayer::egl;

namespace {

// The app's EGLSurface is the address of its Window: the host surface behind it is replaced when the Android window changes size.
// An EGLImage of one buffer with the texture and framebuffer made of it, kept across swaps (a swap used to import both buffers and build
// and delete two FBOs every frame). The key of a gbm buffer is its dmabuf's inode (the image keeps the dmabuf alive, so it is not reused
// while cached), of an Android buffer its address (held by a reference while cached).
struct CachedImage {
    uint64_t key;
    ANativeWindowBuffer* ref;  // the Android buffer, referenced; null for a gbm buffer
    EGLImage image;
    unsigned tex, fbo;
    uint64_t used;
};
constexpr size_t kCacheMax = 8;

struct Window {
    EGLDisplay dpy;
    EGLConfig cfg;
    ANativeWindow* native;
    void* gbmSurface;
    EGLSurface host;
    uint32_t width, height;
    EGLint visual;
    // the swap's cached images: the GL objects belong to cacheCtx's share group
    EGLContext cacheCtx = EGL_NO_CONTEXT;
    std::vector<CachedImage> srcCache, dstCache;
    uint64_t useCount = 0;
    int64_t presentationTime = 0;  // eglPresentationTimeANDROID for the next frame (0 = none)
};
std::recursive_mutex windowsLock;
std::set<Window*> windows;

Window* windowOf(EGLSurface s) {  // windowsLock held
    auto* w = static_cast<Window*>(s);
    return windows.count(w) ? w : nullptr;
}
ANativeWindow* nativeOf(EGLSurface s) {  // the Android window of one of our window surfaces, else null
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    Window* w = windowOf(s);
    return w ? w->native : nullptr;
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

void dropCached(EGLDisplay dpy, EGLContext cacheCtx, CachedImage& c) {
    // the GL objects only while their context is current; otherwise they go with their context (or are leaked: a few names)
    if (cacheCtx != EGL_NO_CONTEXT && host<EGLContext (*)()>("eglGetCurrentContext")() == cacheCtx) {
        gl<void (*)(int, unsigned*)>("glDeleteFramebuffers")(1, &c.fbo);
        gl<void (*)(int, unsigned*)>("glDeleteTextures")(1, &c.tex);
    }
    host<PFNEGLDESTROYIMAGEKHRPROC>("eglDestroyImageKHR")(dpy, c.image);
    if (c.ref) c.ref->common.decRef(&c.ref->common);
}

void dropCache(Window& w) {
    for (auto* v : {&w.srcCache, &w.dstCache}) {
        for (CachedImage& c : *v) dropCached(w.dpy, w.cacheCtx, c);
        v->clear();
    }
}

// The cached image and framebuffer for `key`, made by `make` (an EGLImage) when there is none yet. The framebuffer is left bound to GL_FRAMEBUFFER.
template <typename Make>
CachedImage* cachedImage(Window& w, std::vector<CachedImage>& cache, uint64_t key, ANativeWindowBuffer* ref, Make&& make) {
    for (CachedImage& c : cache) if (c.key == key) { c.used = ++w.useCount; return &c; }
    EGLImage image = make();
    if (image == EGL_NO_IMAGE) return nullptr;
    if (cache.size() >= kCacheMax) {
        auto oldest = cache.begin();
        for (auto it = cache.begin(); it != cache.end(); ++it) if (it->used < oldest->used) oldest = it;
        dropCached(w.dpy, w.cacheCtx, *oldest);
        cache.erase(oldest);
    }
    if (ref) ref->common.incRef(&ref->common);
    CachedImage c = {key, ref, image, 0, 0, ++w.useCount};
    c.fbo = fboForImage(image, &c.tex);
    cache.push_back(c);
    return &cache.back();
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

    EGLContext ctx = host<EGLContext (*)()>("eglGetCurrentContext")();
    if (ctx != w.cacheCtx) { dropCache(w); w.cacheCtx = ctx; }  // other context: other GL objects
    int fd = gbmFn<int (*)(void*)>("gbm_bo_get_fd")(bo);
    struct stat st = {};
    fstat(fd, &st);
    auto createImage = host<PFNEGLCREATEIMAGEKHRPROC>("eglCreateImageKHR");
    CachedImage *src = nullptr, *out = nullptr;
    std::optional<GlState> saved;  // restored when the function ends (creating a cache entry binds a texture and a framebuffer)
    if (ctx != EGL_NO_CONTEXT) {
        saved.emplace();
        src = cachedImage(w, w.srcCache, (uint64_t)st.st_ino, nullptr, [&]() -> EGLImage {
            uint32_t stride = gbmFn<uint32_t (*)(void*)>("gbm_bo_get_stride")(bo);
            uint32_t format = gbmFn<uint32_t (*)(void*)>("gbm_bo_get_format")(bo);
            uint64_t modifier = gbmFn<uint64_t (*)(void*)>("gbm_bo_get_modifier")(bo);
            std::vector<EGLint> a = {EGL_WIDTH, (EGLint)w.width, EGL_HEIGHT, (EGLint)w.height, EGL_LINUX_DRM_FOURCC_EXT, (EGLint)format,
                                     EGL_DMA_BUF_PLANE0_FD_EXT, fd, EGL_DMA_BUF_PLANE0_OFFSET_EXT, 0, EGL_DMA_BUF_PLANE0_PITCH_EXT, (EGLint)stride};
            if (modifier != DRM_FORMAT_MOD_INVALID)
                a.insert(a.end(), {EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT, (EGLint)(modifier & 0xffffffff), EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT, (EGLint)(modifier >> 32)});
            a.push_back(EGL_NONE);
            return createImage(dpy, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, a.data());
        });
        out = cachedImage(w, w.dstCache, (uint64_t)(uintptr_t)dst, dst, [&]() { return imageFromNativeBuffer(dpy, dst); });
    }
    int outFence = -1;
    if (src && out) {
        gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8CA8 /* GL_READ_FRAMEBUFFER */, src->fbo);
        gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8CA9 /* GL_DRAW_FRAMEBUFFER */, out->fbo);
        // an incomplete side would leave GL_INVALID_FRAMEBUFFER_OPERATION in the app's context (Meet's video renderer checks glGetError and
        // gave up on every frame): checked first, the window named once
        auto status = gl<unsigned (*)(unsigned)>("glCheckFramebufferStatus");
        unsigned rs = status(0x8CA8), ds = status(0x8CA9);
        if (rs == 0x8CD5 /* GL_FRAMEBUFFER_COMPLETE */ && ds == 0x8CD5) {
            gl<void (*)(int, int, int, int, int, int, int, int, unsigned, unsigned)>("glBlitFramebuffer")(
                    0, 0, w.width, w.height, 0, 0, dst->width, dst->height, 0x4000 /* GL_COLOR_BUFFER_BIT */, 0x2600 /* GL_NEAREST */);
        } else {
            static std::set<ANativeWindow*> named;
            if (named.insert(nw).second)
                ALOGE("window present %ux%u: framebuffer incomplete (read 0x%x; draw 0x%x: buffer %dx%d format %d usage 0x%llx)",
                      w.width, w.height, rs, ds, dst->width, dst->height, dst->format, (unsigned long long)dst->usage);
        }
        auto createSync = host<PFNEGLCREATESYNCKHRPROC>("eglCreateSyncKHR");
        auto dupFence = host<PFNEGLDUPNATIVEFENCEFDANDROIDPROC>("eglDupNativeFenceFDANDROID");
        EGLSyncKHR sync = createSync && dupFence ? createSync(dpy, EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr) : EGL_NO_SYNC_KHR;
        if (sync != EGL_NO_SYNC_KHR) {
            gl<void (*)()>("glFlush")();  // the fence fd exists once the sync is flushed
            outFence = dupFence(dpy, sync);
            host<PFNEGLDESTROYSYNCKHRPROC>("eglDestroySyncKHR")(dpy, sync);
        }
        if (outFence < 0) gl<void (*)()>("glFinish")();  // no native fence: finish the work here
    } else {
        ALOGE("window present: images %p %p or no current context", src, out);
    }
    close(fd);
    gbmFn<void (*)(void*, void*)>("gbm_surface_release_buffer")(w.gbmSurface, bo);
    if (w.presentationTime) { native_window_set_buffers_timestamp(nw, w.presentationTime); w.presentationTime = 0; }
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

// A gbm_surface of that size and the host's window surface on it. Mesa takes RENDERING alone; NVIDIA's GBM backend refuses that surface
// (EGL_BAD_ALLOC) unless SCANOUT is asked as well, so that is the second try. A 10-bit config without alpha gets the alpha variant's buffer
// first: the present cannot read an XR30/XB30 image back (virgl: an incomplete read framebuffer, RGB10 is not colour-renderable in GLES), and
// Mesa takes an A format for an X config (its GBM config check compares the RGB channels only).
EGLSurface hostSurfaceOnGbm(EGLDisplay dpy, EGLConfig cfg, EGLint visual, uint32_t w, uint32_t h, void** gbmOut) {
    auto create = gbmFn<void* (*)(void*, uint32_t, uint32_t, uint32_t, uint32_t)>("gbm_surface_create");
    auto destroy = gbmFn<void (*)(void*)>("gbm_surface_destroy");
    auto platformSurface = host<PFNEGLCREATEPLATFORMWINDOWSURFACEPROC>("eglCreatePlatformWindowSurface");
    std::vector<uint32_t> formats = {(uint32_t)visual};
    if (visual == DRM_FORMAT_XRGB2101010) formats.insert(formats.begin(), DRM_FORMAT_ARGB2101010);
    if (visual == DRM_FORMAT_XBGR2101010) formats.insert(formats.begin(), DRM_FORMAT_ABGR2101010);
    for (uint32_t format : formats)
        for (uint32_t usage : {1u << 2 /* GBM_BO_USE_RENDERING */, (1u << 2) | 1u /* | GBM_BO_USE_SCANOUT */}) {
            void* gs = create(apklayer_gfx_gbm_device(gfx()), w, h, format, usage);
            if (!gs) continue;
            EGLSurface surface = platformSurface(dpy, cfg, gs, nullptr);
            if (surface != EGL_NO_SURFACE) { *gbmOut = gs; return surface; }
            destroy(gs);
        }
    ALOGE("host window surface %ux%u format 0x%x failed: 0x%x", w, h, visual, host<EGLint (*)()>("eglGetError")());
    return EGL_NO_SURFACE;
}

// A gbm_surface and a host window surface of the Android window's size, stored in the Window (the old pair is the caller's).
bool attachHostSurface(Window* win) {
    uint32_t w, h;
    if (!windowSize(win->native, &w, &h)) return false;
    void* gs = nullptr;
    EGLSurface surface = hostSurfaceOnGbm(win->dpy, win->cfg, win->visual, w, h, &gs);
    if (surface == EGL_NO_SURFACE) return false;
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
    dropCache(*win);  // the old gbm buffers go with their surface
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
    // the Android side: connect as an EGL producer and ask for buffers the GPU renders to and SurfaceFlinger samples. Connected before the
    // size is read: Surface keeps its own copy of the window's default size, refreshed by connect and dequeueBuffer only, so a surface created
    // after a resize (a game's new window in surfaceCreated) read the old size until its first swap
    bool connected = native_window_api_connect(nw, kApiEgl) == 0;
    if (!attachHostSurface(win)) {
        if (connected) native_window_api_disconnect(nw, kApiEgl);
        delete win; tError = EGL_BAD_ALLOC; return EGL_NO_SURFACE;
    }
    // apklayer patch: EGL owns the window until eglDestroySurface. EGL10's
    // SurfaceTexture wrapper drops its temporary sp<Surface> after this call.
    nw->common.incRef(&nw->common);
    native_window_set_usage(nw, kUsageRender | kUsageTexture);
    native_window_set_buffers_format(nw, 1 /* HAL_PIXEL_FORMAT_RGBA_8888 */);
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    windows.insert(win);
    ALOGI("window surface %p: %ux%u, gbm format 0x%x", win, win->width, win->height, win->visual);
    return win;
}

EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) { return swapWindow(dpy, surface); }

// The swap interval of the current draw surface is the Android window's (interval 0 = the BufferQueue's async mode: frames are not held back
// for SurfaceFlinger's vsync, as with Android's own libEGL); the host's gbm surface has nothing to throttle.
EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval) {
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    Window* w = windowOf(appSurface(host<EGLSurface (*)(EGLint)>("eglGetCurrentSurface")(EGL_DRAW)));
    if (!w) return host<EGLBoolean (*)(EGLDisplay, EGLint)>("eglSwapInterval")(dpy, interval);
    if (w->native && w->native->setSwapInterval) w->native->setSwapInterval(w->native, interval < 0 ? 0 : interval);
    return EGL_TRUE;
}

EGLBoolean eglPresentationTimeANDROID(EGLDisplay, EGLSurface surface, EGLnsecsANDROID time) {
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    Window* w = windowOf(surface);
    if (!w) return fail(EGL_BAD_SURFACE);
    w->presentationTime = time;
    return EGL_TRUE;
}

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
            dropCache(*w);
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
    if (attribute == EGL_TIMESTAMPS_ANDROID) {  // SurfaceFlinger's frame events for this window (no effect on a pbuffer, as the spec says)
        ANativeWindow* nw = nativeOf(surface);
        return !nw || native_window_enable_frame_timestamps(nw, value != 0) == 0 ? EGL_TRUE : fail(EGL_BAD_SURFACE);
    }
    return host<EGLBoolean (*)(EGLDisplay, EGLSurface, EGLint, EGLint)>("eglSurfaceAttrib")(dpy, hostSurface(surface), attribute, value);
}

// EGL_ANDROID_get_frame_timestamps as Android's libEGL does it (egl_platform_entries.cpp): libgui's Surface keeps SurfaceFlinger's frame events.
// The display present time is offered only when SurfaceFlinger trusts the present fences (ours are PRESENT_FENCE_IS_NOT_RELIABLE: it says no).
EGLBoolean eglGetNextFrameIdANDROID(EGLDisplay, EGLSurface surface, EGLuint64KHR* frameId) {
    ANativeWindow* nw = nativeOf(surface);
    if (!nw) return fail(EGL_BAD_SURFACE);
    uint64_t id = 0;
    if (native_window_get_next_frame_id(nw, &id) != 0) return fail(EGL_NOT_INITIALIZED);
    *frameId = id;
    return EGL_TRUE;
}

EGLBoolean eglGetCompositorTimingANDROID(EGLDisplay, EGLSurface surface, EGLint n, const EGLint* names, EGLnsecsANDROID* values) {
    ANativeWindow* nw = nativeOf(surface);
    if (!nw) return fail(EGL_BAD_SURFACE);
    int64_t *deadline = nullptr, *interval = nullptr, *latency = nullptr;
    for (EGLint i = 0; i < n; i++) {
        switch (names[i]) {
            case EGL_COMPOSITE_DEADLINE_ANDROID: deadline = &values[i]; break;
            case EGL_COMPOSITE_INTERVAL_ANDROID: interval = &values[i]; break;
            case EGL_COMPOSITE_TO_PRESENT_LATENCY_ANDROID: latency = &values[i]; break;
            default: return fail(EGL_BAD_PARAMETER);
        }
    }
    int ret = native_window_get_compositor_timing(nw, deadline, interval, latency);
    return ret == 0 ? EGL_TRUE : fail(ret == -ENOSYS ? EGL_BAD_SURFACE : EGL_NOT_INITIALIZED);
}

EGLBoolean eglGetCompositorTimingSupportedANDROID(EGLDisplay, EGLSurface surface, EGLint name) {
    if (!nativeOf(surface)) return fail(EGL_BAD_SURFACE);
    return name == EGL_COMPOSITE_DEADLINE_ANDROID || name == EGL_COMPOSITE_INTERVAL_ANDROID || name == EGL_COMPOSITE_TO_PRESENT_LATENCY_ANDROID;
}

EGLBoolean eglGetFrameTimestampsANDROID(EGLDisplay, EGLSurface surface, EGLuint64KHR frameId, EGLint n, const EGLint* names, EGLnsecsANDROID* values) {
    ANativeWindow* nw = nativeOf(surface);
    if (!nw) return fail(EGL_BAD_SURFACE);
    int64_t *requested = nullptr, *acquire = nullptr, *latch = nullptr, *firstRefresh = nullptr, *lastRefresh = nullptr, *gpuDone = nullptr,
            *present = nullptr, *dequeueReady = nullptr, *release = nullptr;
    for (EGLint i = 0; i < n; i++) {
        switch (names[i]) {
            case EGL_REQUESTED_PRESENT_TIME_ANDROID: requested = &values[i]; break;
            case EGL_RENDERING_COMPLETE_TIME_ANDROID: acquire = &values[i]; break;
            case EGL_COMPOSITION_LATCH_TIME_ANDROID: latch = &values[i]; break;
            case EGL_FIRST_COMPOSITION_START_TIME_ANDROID: firstRefresh = &values[i]; break;
            case EGL_LAST_COMPOSITION_START_TIME_ANDROID: lastRefresh = &values[i]; break;
            case EGL_FIRST_COMPOSITION_GPU_FINISHED_TIME_ANDROID: gpuDone = &values[i]; break;
            case EGL_DISPLAY_PRESENT_TIME_ANDROID: present = &values[i]; break;
            case EGL_DEQUEUE_READY_TIME_ANDROID: dequeueReady = &values[i]; break;
            case EGL_READS_DONE_TIME_ANDROID: release = &values[i]; break;
            default: return fail(EGL_BAD_PARAMETER);
        }
    }
    int ret = native_window_get_frame_timestamps(nw, frameId, requested, acquire, latch, firstRefresh, lastRefresh, gpuDone, present,
                                                 dequeueReady, release);
    switch (ret) {
        case 0: return EGL_TRUE;
        case -ENOENT: return fail(EGL_BAD_ACCESS);  // not (or no longer) in the history
        case -ENOSYS: return fail(EGL_BAD_SURFACE);
        case -EINVAL: return fail(EGL_BAD_PARAMETER);
        default: return fail(EGL_NOT_INITIALIZED);
    }
}

EGLBoolean eglGetFrameTimestampSupportedANDROID(EGLDisplay, EGLSurface surface, EGLint name) {
    ANativeWindow* nw = nativeOf(surface);
    if (!nw) return fail(EGL_BAD_SURFACE);
    switch (name) {
        case EGL_COMPOSITE_DEADLINE_ANDROID: case EGL_COMPOSITE_INTERVAL_ANDROID: case EGL_COMPOSITE_TO_PRESENT_LATENCY_ANDROID:
        case EGL_REQUESTED_PRESENT_TIME_ANDROID: case EGL_RENDERING_COMPLETE_TIME_ANDROID: case EGL_COMPOSITION_LATCH_TIME_ANDROID:
        case EGL_FIRST_COMPOSITION_START_TIME_ANDROID: case EGL_LAST_COMPOSITION_START_TIME_ANDROID:
        case EGL_FIRST_COMPOSITION_GPU_FINISHED_TIME_ANDROID: case EGL_DEQUEUE_READY_TIME_ANDROID: case EGL_READS_DONE_TIME_ANDROID:
            return EGL_TRUE;
        case EGL_DISPLAY_PRESENT_TIME_ANDROID: {
            int value = 0;
            nw->query(nw, NATIVE_WINDOW_FRAME_TIMESTAMPS_SUPPORTS_PRESENT, &value);
            return value != 0;
        }
        default: return EGL_FALSE;
    }
}

EGLSurface eglCreatePbufferSurface(EGLDisplay dpy, EGLConfig cfg, const EGLint* attribs) {
    uint32_t w = 1, h = 1;
    for (; attribs && *attribs != EGL_NONE; attribs += 2) {
        if (attribs[0] == EGL_WIDTH) w = attribs[1];
        if (attribs[0] == EGL_HEIGHT) h = attribs[1];
    }
    auto* win = new Window{dpy, cfg, nullptr, nullptr, EGL_NO_SURFACE, 0, 0, 0};
    host<EGLBoolean (*)(EGLDisplay, EGLConfig, EGLint, EGLint*)>("eglGetConfigAttrib")(dpy, cfg, EGL_NATIVE_VISUAL_ID, &win->visual);
    void* gs = nullptr;
    EGLSurface surface = hostSurfaceOnGbm(dpy, cfg, win->visual, w, h, &gs);
    if (surface == EGL_NO_SURFACE) {
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
