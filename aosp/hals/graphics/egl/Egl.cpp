// libEGL for apklayer's Android userspace (PLAN P7.1 step 3): replaces Android's EGL loader (cfg.d maps libEGL.so here). It
// forwards to the host's EGL on the GBM platform of the host graphics HAL's device (vendor-neutral: Mesa, NVIDIA...) and adds
// what Android expects of an EGL driver: EGL_ANDROID_image_native_buffer (an ANativeWindowBuffer from our gralloc becomes a
// dmabuf EGLImage), eglGetNativeClientBufferANDROID, filtering of attributes the host does not know. Window surfaces are a host
// gbm_surface plus a GPU copy into the ANativeWindow's buffer; everything not listed in IMPLEMENTED below is a straight tail-call into the host library.
#define LOG_TAG "apklayer.egl"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/hardware_buffer.h>
#include <dlfcn.h>
#include <drm_fourcc.h>
#include <log/log.h>
#include <nativebase/nativebase.h>
#include <poll.h>
#include <system/window.h>
#include <unistd.h>

#include <cstring>
#include <initializer_list>
#include <mutex>
#include <set>
#include <string>
#include <vector>

#include "gralloc_handle.h"
#include "apklayer/graphics.h"

using namespace apklayer::gralloc;

namespace android {
// libnativewindow's platform-visible conversion (C++ linkage in this tree)
ANativeWindowBuffer* AHardwareBuffer_to_ANativeWindowBuffer(AHardwareBuffer*);
}  // namespace android

#define EGL_NATIVE_BUFFER_ANDROID 0x3140
#define EGL_PLATFORM_ANDROID_KHR 0x3141
#define EGL_CONTEXT_PRIORITY_LEVEL_IMG 0x3100
#define EGL_RECORDABLE_ANDROID 0x3142
#define EGL_PROTECTED_CONTENT_EXT 0x32C0

namespace {

void* hostLib() {
    static void* h = dlopen("libEGL.so.1", RTLD_NOW | RTLD_GLOBAL);  // the host's glvnd EGL (bionic_dlopen falls back to glibc)
    LOG_ALWAYS_FATAL_IF(h == nullptr, "no host libEGL.so.1");
    return h;
}

// A function of the host's libEGL. glvnd exports only the core entry points by name; extension functions (eglCreateImageKHR,
// eglGetPlatformDisplayEXT, the *SyncKHR ones...) come from its eglGetProcAddress, so that is the fallback for every lookup.
template <typename F>
F host(const char* name) {
    void* p = dlsym(hostLib(), name);
    if (!p && strcmp(name, "eglGetProcAddress") != 0) {
        auto proc = reinterpret_cast<void* (*)(const char*)>(dlsym(hostLib(), "eglGetProcAddress"));
        if (proc) p = proc(name);
    }
    return reinterpret_cast<F>(p);
}

thread_local EGLint tError = EGL_SUCCESS;  // errors of our own (the host keeps its own)
EGLBoolean fail(EGLint e) {
    tError = e;
    return EGL_FALSE;
}

apklayer_gfx* gfx() {
    static apklayer_gfx* g = apklayer_gfx_open(nullptr);
    LOG_ALWAYS_FATAL_IF(g == nullptr, "no render node for the host graphics HAL");
    return g;
}

EGLDisplay gbmDisplay() {
    static EGLDisplay d = [] {
        EGLDisplay display = EGL_NO_DISPLAY;
        void* gbm = apklayer_gfx_gbm_device(gfx());
        if (auto get = host<PFNEGLGETPLATFORMDISPLAYPROC>("eglGetPlatformDisplay"))  // EGL 1.5 core
            display = get(EGL_PLATFORM_GBM_KHR, gbm, nullptr);
        if (display == EGL_NO_DISPLAY) {  // the extension, which glvnd only hands out through eglGetProcAddress
            auto proc = host<__eglMustCastToProperFunctionPointerType (*)(const char*)>("eglGetProcAddress");
            auto ext = proc ? reinterpret_cast<PFNEGLGETPLATFORMDISPLAYEXTPROC>(proc("eglGetPlatformDisplayEXT")) : nullptr;
            if (ext) display = ext(EGL_PLATFORM_GBM_KHR, gbm, nullptr);
        }
        ALOGI("host EGL display on the GBM platform: %p", display);
        return display;
    }();
    return d;
}

// Copy an attribute list without the keys the host EGL does not know.
std::vector<EGLint> filtered(const EGLint* attribs, std::initializer_list<EGLint> drop) {
    std::vector<EGLint> out;
    for (; attribs && *attribs != EGL_NONE; attribs += 2) {
        bool skip = false;
        for (EGLint k : drop) skip |= (*attribs == k);
        if (!skip) { out.push_back(attribs[0]); out.push_back(attribs[1]); }
    }
    out.push_back(EGL_NONE);
    return out;
}

const char* kAndroidExtensions =
        " EGL_ANDROID_image_native_buffer EGL_ANDROID_get_native_client_buffer EGL_ANDROID_blob_cache EGL_IMG_context_priority"
        " EGL_ANDROID_recordable";

// An ANativeWindowBuffer of our gralloc as a dmabuf EGLImage on the host.
EGLImage imageFromNativeBuffer(EGLDisplay dpy, ANativeWindowBuffer* buf) {
    HandleData d;
    if (buf == nullptr || !decode(buf->handle, &d)) {
        ALOGE("eglCreateImageKHR(native buffer): not one of our gralloc handles (buffer %p, handle %p, fds %d, ints %d)", buf,
              buf ? buf->handle : nullptr, buf && buf->handle ? buf->handle->numFds : -1, buf && buf->handle ? buf->handle->numInts : -1);
        tError = EGL_BAD_PARAMETER;
        return EGL_NO_IMAGE;
    }
    SharedMetadata* meta = mapMetadata(buf->handle);
    int32_t dataspace = meta ? meta->dataspace : 0;
    if (meta) unmapMetadata(meta);

    std::vector<EGLint> a = {EGL_WIDTH, (EGLint)d.width, EGL_HEIGHT, (EGLint)d.height, EGL_LINUX_DRM_FOURCC_EXT, (EGLint)d.fourcc};
    static const EGLint fd[] = {EGL_DMA_BUF_PLANE0_FD_EXT, EGL_DMA_BUF_PLANE1_FD_EXT, EGL_DMA_BUF_PLANE2_FD_EXT, EGL_DMA_BUF_PLANE3_FD_EXT};
    static const EGLint off[] = {EGL_DMA_BUF_PLANE0_OFFSET_EXT, EGL_DMA_BUF_PLANE1_OFFSET_EXT, EGL_DMA_BUF_PLANE2_OFFSET_EXT, EGL_DMA_BUF_PLANE3_OFFSET_EXT};
    static const EGLint pitch[] = {EGL_DMA_BUF_PLANE0_PITCH_EXT, EGL_DMA_BUF_PLANE1_PITCH_EXT, EGL_DMA_BUF_PLANE2_PITCH_EXT, EGL_DMA_BUF_PLANE3_PITCH_EXT};
    static const EGLint modLo[] = {EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT, EGL_DMA_BUF_PLANE1_MODIFIER_LO_EXT, EGL_DMA_BUF_PLANE2_MODIFIER_LO_EXT, EGL_DMA_BUF_PLANE3_MODIFIER_LO_EXT};
    static const EGLint modHi[] = {EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT, EGL_DMA_BUF_PLANE1_MODIFIER_HI_EXT, EGL_DMA_BUF_PLANE2_MODIFIER_HI_EXT, EGL_DMA_BUF_PLANE3_MODIFIER_HI_EXT};
    for (uint32_t i = 0; i < d.numPlanes && i < 4; i++) {
        a.insert(a.end(), {fd[i], buf->handle->data[i], off[i], (EGLint)d.offset[i], pitch[i], (EGLint)d.stride[i]});
        if (d.modifier != DRM_FORMAT_MOD_INVALID)
            a.insert(a.end(), {modLo[i], (EGLint)(d.modifier & 0xffffffff), modHi[i], (EGLint)(d.modifier >> 32)});
    }
    if (d.fourcc == DRM_FORMAT_NV12 || d.fourcc == DRM_FORMAT_NV21 || d.fourcc == DRM_FORMAT_YUV420 || d.fourcc == DRM_FORMAT_YVU420 ||
        d.fourcc == DRM_FORMAT_NV16 || d.fourcc == DRM_FORMAT_P010) {
        // colour hints from the buffer's dataspace (standard and range bits); unknown means BT.601 limited, Android's default
        int32_t standard = dataspace & 0x003F0000, range = dataspace & 0x38000000;
        EGLint space = standard == (1 << 16) ? EGL_ITU_REC709_EXT : standard == (6 << 16) ? EGL_ITU_REC2020_EXT : EGL_ITU_REC601_EXT;
        a.insert(a.end(), {EGL_YUV_COLOR_SPACE_HINT_EXT, space,
                           EGL_SAMPLE_RANGE_HINT_EXT, range == (1 << 27) ? EGL_YUV_FULL_RANGE_EXT : EGL_YUV_NARROW_RANGE_EXT});
    }
    a.push_back(EGL_NONE);
    auto create = host<PFNEGLCREATEIMAGEKHRPROC>("eglCreateImageKHR");
    EGLImage image = create(dpy, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, a.data());
    if (image == EGL_NO_IMAGE)
        ALOGE("host eglCreateImageKHR(dmabuf %ux%u fourcc 0x%x planes %u modifier 0x%llx) failed: EGL error 0x%x", d.width, d.height, d.fourcc,
              d.numPlanes, (unsigned long long)d.modifier, host<EGLint (*)()>("eglGetError")());
    else
        ALOGI("imported buffer id %llx: %ux%u fourcc 0x%x android format %u usage 0x%llx planes %u stride %u modifier 0x%llx", (unsigned long long)d.id,
              d.width, d.height, d.fourcc, d.androidFormat, (unsigned long long)d.usage, d.numPlanes, d.stride[0], (unsigned long long)d.modifier);
    return image;
}


// ---- window surfaces (PLAN P7.1 step 3b) ----
// The host's GBM platform has no pbuffers and cannot render into somebody else's buffer, so an Android window is a host window
// surface on a gbm_surface of the same size, and eglSwapBuffers copies the finished frame on the GPU (glBlitFramebuffer between two
// dmabuf EGLImages, a native fence handed to the BufferQueue: no CPU touch) into a buffer dequeued from the ANativeWindow.
// A resize (the window's default size changing) makes a new surface pair behind the same app-side EGLSurface.
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
// the host's surface for one of ours (anything else, EGL_NO_SURFACE or a pbuffer, is the host's already)
EGLSurface hostSurface(EGLSurface s) {
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    Window* w = windowOf(s);
    return w ? w->host : s;
}
EGLSurface appSurface(EGLSurface host) {  // reverse, for eglGetCurrentSurface
    std::lock_guard<std::recursive_mutex> lock(windowsLock);
    for (Window* w : windows) if (w->host == host) return w;
    return host;
}

constexpr int kApiEgl = 1;  // NATIVE_WINDOW_API_EGL
constexpr uint64_t kUsageRender = 0x200, kUsageTexture = 0x100;  // GRALLOC_USAGE_HW_RENDER | HW_TEXTURE

template <typename F>
F gbmFn(const char* name) {
    static void* lib = dlopen("libgbm.so.1", RTLD_NOW | RTLD_GLOBAL);
    return reinterpret_cast<F>(lib ? dlsym(lib, name) : nullptr);
}

// GL entry points of the current context (through the host's eglGetProcAddress: glvnd exports no names)
template <typename F>
F gl(const char* name) {
    return host<F>(name);
}

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
        gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8CAA, read);
        gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8CA6, draw);
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
        gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8CAA, srcFbo);
        gl<void (*)(unsigned, unsigned)>("glBindFramebuffer")(0x8CA6, dstFbo);
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
        if (outFence < 0) gl<void (*)()>("glFinish")();
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
    EGLBoolean ok = host<EGLBoolean (*)(EGLDisplay, EGLSurface)>("eglSwapBuffers")(dpy, win->host);
    if (!ok) return ok;
    presentToWindow(dpy, *win);
    uint32_t w, h;
    if (windowSize(win->native, &w, &h) && (w != win->width || h != win->height)) resizeWindow(win);
    return ok;
}

}  // namespace

extern "C" {

// ---- implemented here ----

EGLDisplay eglGetDisplay(EGLNativeDisplayType) { return gbmDisplay(); }

EGLDisplay eglGetPlatformDisplay(EGLenum, void*, const EGLAttrib*) { return gbmDisplay(); }

EGLint eglGetError() {
    if (tError != EGL_SUCCESS) { EGLint e = tError; tError = EGL_SUCCESS; return e; }
    return host<EGLint (*)()>("eglGetError")();
}

const char* eglQueryString(EGLDisplay dpy, EGLint name) {
    static std::string extensions, clientExtensions;
    const char* hostString = host<const char* (*)(EGLDisplay, EGLint)>("eglQueryString")(dpy, name);
    if (name == EGL_EXTENSIONS && dpy != EGL_NO_DISPLAY) {
        if (extensions.empty()) extensions = std::string(hostString ? hostString : "") + kAndroidExtensions;
        return extensions.c_str();
    }
    if (name == EGL_EXTENSIONS) {
        if (clientExtensions.empty()) clientExtensions = std::string(hostString ? hostString : "") + " EGL_KHR_platform_android";
        return clientExtensions.c_str();
    }
    return hostString;
}

const char* eglQueryStringImplementationANDROID(EGLDisplay dpy, EGLint name) { return eglQueryString(dpy, name); }

EGLClientBuffer eglGetNativeClientBufferANDROID(const AHardwareBuffer* buffer) {
    return buffer ? android::AHardwareBuffer_to_ANativeWindowBuffer(const_cast<AHardwareBuffer*>(buffer)) : nullptr;
}

EGLImageKHR eglCreateImageKHR(EGLDisplay dpy, EGLContext ctx, EGLenum target, EGLClientBuffer buffer, const EGLint* attribs) {
    if (target == EGL_NATIVE_BUFFER_ANDROID) return imageFromNativeBuffer(dpy, static_cast<ANativeWindowBuffer*>(buffer));
    return host<PFNEGLCREATEIMAGEKHRPROC>("eglCreateImageKHR")(dpy, ctx, target, buffer, attribs);
}

EGLImage eglCreateImage(EGLDisplay dpy, EGLContext ctx, EGLenum target, EGLClientBuffer buffer, const EGLAttrib* attribs) {
    if (target == EGL_NATIVE_BUFFER_ANDROID) return imageFromNativeBuffer(dpy, static_cast<ANativeWindowBuffer*>(buffer));
    return host<PFNEGLCREATEIMAGEPROC>("eglCreateImage")(dpy, ctx, target, buffer, attribs);
}

EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig cfg, EGLContext share, const EGLint* attribs) {
    // priority and protected content are Android-only context attributes
    auto a = filtered(attribs, {EGL_CONTEXT_PRIORITY_LEVEL_IMG, EGL_PROTECTED_CONTENT_EXT});
    return host<PFNEGLCREATECONTEXTPROC>("eglCreateContext")(dpy, cfg, share, a.data());
}

EGLBoolean eglChooseConfig(EGLDisplay dpy, const EGLint* attribs, EGLConfig* cfgs, EGLint size, EGLint* num) {
    auto a = filtered(attribs, {EGL_RECORDABLE_ANDROID, EGL_PROTECTED_CONTENT_EXT});
    return host<PFNEGLCHOOSECONFIGPROC>("eglChooseConfig")(dpy, a.data(), cfgs, size, num);
}

EGLSurface eglCreateWindowSurface(EGLDisplay dpy, EGLConfig cfg, EGLNativeWindowType native, const EGLint*) {
    auto* nw = reinterpret_cast<ANativeWindow*>(native);
    if (nw == nullptr || nw->common.magic != ANDROID_NATIVE_WINDOW_MAGIC) { tError = EGL_BAD_NATIVE_WINDOW; return EGL_NO_SURFACE; }
    auto* win = new Window{dpy, cfg, nw, nullptr, EGL_NO_SURFACE, 0, 0, 0};
    host<EGLBoolean (*)(EGLDisplay, EGLConfig, EGLint, EGLint*)>("eglGetConfigAttrib")(dpy, cfg, EGL_NATIVE_VISUAL_ID, &win->visual);
    if (!attachHostSurface(win)) { delete win; tError = EGL_BAD_ALLOC; return EGL_NO_SURFACE; }
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

EGLBoolean eglDestroySurface(EGLDisplay dpy, EGLSurface surface) {
    {
        std::lock_guard<std::recursive_mutex> lock(windowsLock);
        if (Window* w = windowOf(surface)) {
            native_window_api_disconnect(w->native, kApiEgl);
            windows.erase(w);
            EGLBoolean ok = host<EGLBoolean (*)(EGLDisplay, EGLSurface)>("eglDestroySurface")(dpy, w->host);
            gbmFn<void (*)(void*)>("gbm_surface_destroy")(w->gbmSurface);
            delete w;
            return ok;
        }
    }
    return host<EGLBoolean (*)(EGLDisplay, EGLSurface)>("eglDestroySurface")(dpy, surface);
}

EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    EGLBoolean ok = host<PFNEGLMAKECURRENTPROC>("eglMakeCurrent")(dpy, hostSurface(draw), hostSurface(read), ctx);
    static bool logged;
    if (ok && ctx != EGL_NO_CONTEXT && !logged) {  // apklayer patch (diagnostic): the GL the context really gives (Skia's renderability caps depend on it)
        logged = true;
        auto gs = host<const unsigned char* (*)(unsigned)>("glGetString");
        if (gs) ALOGI("GL version '%s' renderer '%s' extensions '%s'", gs(0x1F02), gs(0x1F01), gs(0x1F03));
    }
    return ok;
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

EGLSurface eglCreatePlatformWindowSurface(EGLDisplay d, EGLConfig c, void* w, const EGLAttrib*) {
    return eglCreateWindowSurface(d, c, reinterpret_cast<EGLNativeWindowType>(w), nullptr);
}

// Android's EGL extensions the framework asks for that need no real work here.
void eglSetBlobCacheFuncsANDROID(EGLDisplay, EGLSetBlobFuncANDROID, EGLGetBlobFuncANDROID) {}
EGLBoolean eglGetFrameTimestampSupportedANDROID(EGLDisplay, EGLSurface, EGLint) { return EGL_FALSE; }
EGLBoolean eglGetCompositorTimingSupportedANDROID(EGLDisplay, EGLSurface, EGLint) { return EGL_FALSE; }
EGLBoolean eglGetCompositorTimingANDROID(EGLDisplay, EGLSurface, EGLint, const EGLint*, EGLnsecsANDROID*) { return fail(EGL_BAD_ACCESS); }
EGLBoolean eglGetNextFrameIdANDROID(EGLDisplay, EGLSurface, EGLuint64KHR*) { return fail(EGL_BAD_ACCESS); }
EGLBoolean eglGetFrameTimestampsANDROID(EGLDisplay, EGLSurface, EGLuint64KHR, EGLint, const EGLint*, EGLnsecsANDROID*) { return fail(EGL_BAD_ACCESS); }
EGLBoolean eglPresentationTimeANDROID(EGLDisplay, EGLSurface, EGLnsecsANDROID) { return EGL_TRUE; }

// ---- forwarded to the host, untouched ----
// A tail call through a pointer resolved at load: no signature needed.
#define FORWARDED(X) \
    X(eglBindAPI) X(eglBindTexImage) X(eglClientWaitSync) X(eglClientWaitSyncKHR) X(eglCopyBuffers) \
    X(eglCreatePbufferFromClientBuffer) X(eglCreatePbufferSurface) X(eglCreatePixmapSurface) X(eglCreatePlatformPixmapSurface) \
    X(eglCreateSync) X(eglCreateSyncKHR) X(eglDestroyContext) X(eglDestroyImage) X(eglDestroyImageKHR) \
    X(eglDestroySync) X(eglDestroySyncKHR) X(eglDupNativeFenceFDANDROID) X(eglGetConfigAttrib) X(eglGetConfigs) \
    X(eglGetCurrentContext) X(eglGetCurrentDisplay) X(eglGetSyncAttrib) X(eglGetSyncAttribKHR) \
    X(eglInitialize) X(eglQueryAPI) X(eglQueryContext) X(eglReleaseTexImage) \
    X(eglReleaseThread) X(eglSetDamageRegionKHR) X(eglSignalSyncKHR) \
    X(eglSwapInterval) X(eglTerminate) X(eglWaitClient) X(eglWaitGL) X(eglWaitNative) \
    X(eglWaitSync) X(eglWaitSyncKHR) X(eglLockSurfaceKHR) X(eglUnlockSurfaceKHR)

#define DECLARE_SLOT(name) __attribute__((used, visibility("hidden"))) void* host_slot_##name;
FORWARDED(DECLARE_SLOT)

// file-scope assembly: no C declaration to clash with the EGL headers' signatures
#define DEFINE_STUB(name) \
    __asm__(".text\n.globl " #name "\n.type " #name ",%function\n" #name ":\n\tadrp x16, host_slot_" #name \
            "\n\tldr x16, [x16, :lo12:host_slot_" #name "]\n\tbr x16\n");
FORWARDED(DEFINE_STUB)

// extensions an app or library may ask eglGetProcAddress for: ours where we implement them, the host's otherwise
__eglMustCastToProperFunctionPointerType eglGetProcAddress(const char* name) {
    struct { const char* n; void* f; } ours[] = {
        {"eglCreateImageKHR", (void*)eglCreateImageKHR}, {"eglCreateImage", (void*)eglCreateImage},
        {"eglGetNativeClientBufferANDROID", (void*)eglGetNativeClientBufferANDROID},
        {"eglSwapBuffersWithDamageKHR", (void*)eglSwapBuffersWithDamageKHR}, {"eglSwapBuffers", (void*)eglSwapBuffers},
        {"eglQueryStringImplementationANDROID", (void*)eglQueryStringImplementationANDROID},
        {"eglSetBlobCacheFuncsANDROID", (void*)eglSetBlobCacheFuncsANDROID},
        {"eglGetFrameTimestampsANDROID", (void*)eglGetFrameTimestampsANDROID},
        {"eglGetNextFrameIdANDROID", (void*)eglGetNextFrameIdANDROID},
        {"eglGetCompositorTimingANDROID", (void*)eglGetCompositorTimingANDROID},
        {"eglGetCompositorTimingSupportedANDROID", (void*)eglGetCompositorTimingSupportedANDROID},
        {"eglGetFrameTimestampSupportedANDROID", (void*)eglGetFrameTimestampSupportedANDROID},
        {"eglPresentationTimeANDROID", (void*)eglPresentationTimeANDROID},
        {"eglCreatePlatformWindowSurface", (void*)eglCreatePlatformWindowSurface},
    };
    for (auto& o : ours) if (!strcmp(name, o.n)) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(o.f);
    auto proc = host<__eglMustCastToProperFunctionPointerType (*)(const char*)>("eglGetProcAddress");
    return host<__eglMustCastToProperFunctionPointerType (*)(const char*)>("eglGetProcAddress")(name);
}

// A forwarded function the host does not have: fails instead of crashing on a null call.
static long unsupportedForwarded() {
    tError = EGL_BAD_ACCESS;
    return 0;
}

__attribute__((constructor)) static void resolveForwarded() {
    // glvnd's libEGL exports only the core entry points by name; extension functions (eglDestroyImageKHR, the *SyncKHR and
    // *ANDROID ones...) come from its eglGetProcAddress
    auto proc = host<__eglMustCastToProperFunctionPointerType (*)(const char*)>("eglGetProcAddress");
#define RESOLVE(name)                                                                        \
    host_slot_##name = dlsym(hostLib(), #name);                                              \
    if (!host_slot_##name && proc) host_slot_##name = reinterpret_cast<void*>(proc(#name));  \
    if (!host_slot_##name) { ALOGW("host EGL has no " #name); host_slot_##name = reinterpret_cast<void*>(&unsupportedForwarded); }
    FORWARDED(RESOLVE)
}

}  // extern "C"

// Android's libEGL exports this one with C++ linkage (_Z13eglBeginFramePvS_); callers in the framework expect that symbol.
void eglBeginFrame(void*, void*) {}

