// libEGL for apklayer's Android userspace (PLAN P7.1 step 3): replaces Android's EGL loader (cfg.d maps libEGL.so here). It
// forwards to the host's EGL on the GBM platform of the host graphics HAL's device (vendor-neutral: Mesa, NVIDIA...) and adds
// what Android expects of an EGL driver: EGL_ANDROID_image_native_buffer (an ANativeWindowBuffer from our gralloc becomes a
// dmabuf EGLImage), eglGetNativeClientBufferANDROID, filtering of attributes the host does not know. Window surfaces (EglWindow.cpp) are a host
// gbm_surface plus a GPU copy into the ANativeWindow's buffer; everything not implemented in these two files is a straight tail-call into the host library.
#define LOG_TAG "apklayer.egl"

#include <EGL/egl.h>
#include <android/hardware_buffer.h>
#include <drm_fourcc.h>
#include <log/log.h>
#include <nativebase/nativebase.h>

#include <cstring>
#include <initializer_list>
#include <mutex>
#include <algorithm>
#include <string>
#include <vector>

#include "EglHost.h"
#include "gralloc_handle.h"
#include "Gles1.h"

using namespace apklayer::gralloc;
using namespace apklayer::egl;

namespace android {
// libnativewindow's platform-visible conversion (C++ linkage in this tree)
ANativeWindowBuffer* AHardwareBuffer_to_ANativeWindowBuffer(AHardwareBuffer*);
}  // namespace android

#define EGL_NATIVE_BUFFER_ANDROID 0x3140
#define EGL_PLATFORM_ANDROID_KHR 0x3141
#define EGL_CONTEXT_PRIORITY_LEVEL_IMG 0x3100
#define EGL_RECORDABLE_ANDROID 0x3142
#define EGL_PROTECTED_CONTENT_EXT 0x32C0
#ifndef EGL_TELEMETRY_HINT_ANDROID
#define EGL_TELEMETRY_HINT_ANDROID 0x3570
#endif

void* apklayer::egl::hostLib() {
    static void* h = dlopen("libEGL.so.1", RTLD_NOW | RTLD_GLOBAL);  // the host's glvnd EGL (bionic_dlopen falls back to glibc)
    LOG_ALWAYS_FATAL_IF(h == nullptr, "no host libEGL.so.1");
    return h;
}

thread_local EGLint apklayer::egl::tError = EGL_SUCCESS;  // errors of our own (the host keeps its own)
EGLBoolean apklayer::egl::fail(EGLint e) {
    tError = e;
    return EGL_FALSE;
}

apklayer_gfx* apklayer::egl::gfx() {
    static apklayer_gfx* g = apklayer_gfx_open(nullptr);
    LOG_ALWAYS_FATAL_IF(g == nullptr, "no render node for the host graphics HAL");
    return g;
}

EGLDisplay apklayer::egl::gbmDisplay() {
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

namespace {

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
        " EGL_ANDROID_recordable EGL_KHR_swap_buffers_with_damage";  // the last: implemented here (HWUI refuses to start without it)

static unsigned displayRefs;  // initialize/terminate count, below

}  // namespace

// An ANativeWindowBuffer of our gralloc as a dmabuf EGLImage on the host.
EGLImage apklayer::egl::imageFromNativeBuffer(EGLDisplay dpy, ANativeWindowBuffer* buf) {
    // apklayer patch: Skia asks eglGetCurrentDisplay() on threads with no context (Bitmap.copy of a hardware bitmap on the main thread):
    // EGL_NO_DISPLAY, which the host rejects with EGL_BAD_DISPLAY. There is one display.
    if (dpy == EGL_NO_DISPLAY) dpy = gbmDisplay();
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
        // unspecified standard: what Android's ColorUtils assumes, BT.709 for HD (taller than 576 or wider than 1024), else BT.601;
        // always reading 601 desaturated HD video
        bool hd = d.height > 576 || d.width > 1024;
        EGLint space = standard == (1 << 16) ? EGL_ITU_REC709_EXT : standard == (6 << 16) ? EGL_ITU_REC2020_EXT
                     : standard == 0 && hd ? EGL_ITU_REC709_EXT : EGL_ITU_REC601_EXT;
        a.insert(a.end(), {EGL_YUV_COLOR_SPACE_HINT_EXT, space,
                           EGL_SAMPLE_RANGE_HINT_EXT, range == (1 << 27) ? EGL_YUV_FULL_RANGE_EXT : EGL_YUV_NARROW_RANGE_EXT});
    }
    a.push_back(EGL_NONE);
    auto create = host<PFNEGLCREATEIMAGEKHRPROC>("eglCreateImageKHR");
    EGLImage image = create(dpy, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, a.data());
    if (image == EGL_NO_IMAGE)
        ALOGE("host eglCreateImageKHR(dmabuf %ux%u fourcc 0x%x planes %u modifier 0x%llx) failed: EGL error 0x%x (display refs %u)", d.width, d.height,
              d.fourcc, d.numPlanes, (unsigned long long)d.modifier, host<EGLint (*)()>("eglGetError")(), displayRefs);
    else
        ALOGI("imported buffer id %llx: %ux%u fourcc 0x%x android format %u usage 0x%llx planes %u stride %u modifier 0x%llx", (unsigned long long)d.id,
              d.width, d.height, d.fourcc, d.androidFormat, (unsigned long long)d.usage, d.numPlanes, d.stride[0], (unsigned long long)d.modifier);
    return image;
}

extern "C" {

// ---- implemented here ----

EGLDisplay eglGetDisplay(EGLNativeDisplayType) { return gbmDisplay(); }

EGLDisplay eglGetPlatformDisplay(EGLenum, void*, const EGLAttrib*) { return gbmDisplay(); }

// apklayer patch: Android's EGL loader reference-counts display initialization
// (egl_display_t). libgui's SyncFeatures temporarily borrows the same display.
// A direct host eglTerminate would destroy RenderEngine's still-live context.
static std::mutex displayLock;
static EGLint displayMajor, displayMinor;
EGLBoolean eglInitialize(EGLDisplay dpy, EGLint* major, EGLint* minor) {
    if (dpy != gbmDisplay()) return fail(EGL_BAD_DISPLAY);
    std::lock_guard<std::mutex> lock(displayLock);
    if (displayRefs == 0 && !host<PFNEGLINITIALIZEPROC>("eglInitialize")(dpy, &displayMajor, &displayMinor)) return EGL_FALSE;
    ++displayRefs;
    if (major) *major = displayMajor;
    if (minor) *minor = displayMinor;
    return EGL_TRUE;
}
EGLBoolean eglTerminate(EGLDisplay dpy) {
    if (dpy != gbmDisplay()) return fail(EGL_BAD_DISPLAY);
    std::lock_guard<std::mutex> lock(displayLock);
    if (displayRefs == 0 || --displayRefs != 0) return EGL_TRUE;
    // apklayer patch: the host display stays initialized for the process. Skia/HWUI readbacks (Bitmap.copy of a hardware bitmap,
    // thumbnails) end their last reference and start again; a real terminate made every later dmabuf import fail with EGL_BAD_DISPLAY.
    return EGL_TRUE;
}

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
    // priority, protected content and the telemetry hint are Android-only context attributes
    auto a = filtered(attribs, {EGL_CONTEXT_PRIORITY_LEVEL_IMG, EGL_PROTECTED_CONTENT_EXT, EGL_TELEMETRY_HINT_ANDROID});
    auto create = host<PFNEGLCREATECONTEXTPROC>("eglCreateContext");
    // no client version (or 1) is OpenGL ES 1: the host's own, or the translator over a host GLES 3 context (Gles1.cpp)
    EGLContext ctx = apklayer::egl::wantsGles1(a) ? apklayer::egl::gles1CreateContext(dpy, cfg, share, a, create) : create(dpy, cfg, share, a.data());
    if (ctx == EGL_NO_CONTEXT) {  // diagnosis: engines (bgfx) probe several versions, the last failure is the one they report
        EGLint err = host<EGLint (*)()>("eglGetError")();
        std::string list;
        for (size_t i = 0; i + 1 < a.size(); i += 2) list += " " + std::to_string(a[i]) + "=" + std::to_string(a[i + 1]);
        ALOGW("eglCreateContext failed (error 0x%x), attributes:%s", err, list.c_str());
        tError = err;
    }
    return ctx;
}

EGLBoolean eglChooseConfig(EGLDisplay dpy, const EGLint* attribs, EGLConfig* cfgs, EGLint size, EGLint* num) {
    auto a = filtered(attribs, {EGL_RECORDABLE_ANDROID, EGL_PROTECTED_CONTENT_EXT});
    // The host's configs are for window surfaces (its display is a GBM device): a request for pbuffer-capable configs (Chromium's offscreen GL) is matched with the
    // window ones, and eglCreatePbufferSurface makes the pbuffer from a GBM surface of the asked size.
    for (size_t i = 0; i + 1 < a.size(); i += 2)
        if (a[i] == EGL_SURFACE_TYPE && (a[i + 1] & EGL_PBUFFER_BIT)) a[i + 1] = (a[i + 1] & ~EGL_PBUFFER_BIT) | EGL_WINDOW_BIT;
    EGLBoolean ok = host<PFNEGLCHOOSECONFIGPROC>("eglChooseConfig")(dpy, a.data(), cfgs, size, num);
    if (!ok || (num && *num == 0)) {  // diagnosis: what was asked for when the host has nothing that matches
        std::string list;
        for (size_t i = 0; i + 1 < a.size(); i += 2) list += " 0x" + std::to_string(a[i]) + "=" + std::to_string(a[i + 1]);
        ALOGW("eglChooseConfig found no config for:%s", list.c_str());
    }
    // EGL sorts deeper colour first, so a plain RGB8 request gets a 10-bit (XR30) or float config at the top; Android's drivers give the 8-bit
    // ones, and the window present cannot read 10-bit/float buffers back (Meet's video renderer: an incomplete framebuffer on every frame).
    // Unless more than 8 bits were asked for, the configs of at most 8 bits per channel come first, the deeper ones after them.
    bool deep = false;
    for (size_t i = 0; i + 1 < a.size(); i += 2)
        if ((a[i] == EGL_RED_SIZE || a[i] == EGL_GREEN_SIZE || a[i] == EGL_BLUE_SIZE || a[i] == EGL_ALPHA_SIZE) && a[i + 1] > 8) deep = true;
    if (ok && !deep && num && *num > 0) {
        auto choose = host<PFNEGLCHOOSECONFIGPROC>("eglChooseConfig");
        auto attrib = host<PFNEGLGETCONFIGATTRIBPROC>("eglGetConfigAttrib");
        EGLint all = 0;
        choose(dpy, a.data(), nullptr, 0, &all);
        std::vector<EGLConfig> found(all > 0 ? all : 0), ordered;
        if (all > 0 && choose(dpy, a.data(), found.data(), all, &all)) {
            found.resize(all);
            auto deepConfig = [&](EGLConfig c) { EGLint r = 0; attrib(dpy, c, EGL_RED_SIZE, &r); return r > 8; };
            for (EGLConfig c : found) if (!deepConfig(c)) ordered.push_back(c);
            for (EGLConfig c : found) if (deepConfig(c)) ordered.push_back(c);
            *num = cfgs ? std::min<EGLint>(size, (EGLint)ordered.size()) : (EGLint)ordered.size();
            if (cfgs) std::copy(ordered.begin(), ordered.begin() + *num, cfgs);
        }
    }
    return ok;
}

EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    EGLBoolean ok = host<PFNEGLMAKECURRENTPROC>("eglMakeCurrent")(dpy, hostSurface(draw), hostSurface(read), ctx);
    if (ok) apklayer::egl::gles1MadeCurrent(ctx);
    static bool logged;
    if (ok && ctx != EGL_NO_CONTEXT && !logged) {  // apklayer patch (diagnostic): the GL the context really gives (Skia's renderability caps depend on it)
        logged = true;
        auto gs = host<const unsigned char* (*)(unsigned)>("glGetString");
        if (gs) ALOGI("GL version '%s' renderer '%s' extensions '%s'", gs(0x1F02), gs(0x1F01), gs(0x1F03));
    }
    return ok;
}

EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx) {
    apklayer::egl::gles1DestroyContext(dpy, ctx);
    return host<EGLBoolean (*)(EGLDisplay, EGLContext)>("eglDestroyContext")(dpy, ctx);
}

EGLBoolean eglQueryContext(EGLDisplay dpy, EGLContext ctx, EGLint attribute, EGLint* value) {
    if (apklayer::egl::gles1QueryContext(ctx, attribute, value)) return EGL_TRUE;
    return host<EGLBoolean (*)(EGLDisplay, EGLContext, EGLint, EGLint*)>("eglQueryContext")(dpy, ctx, attribute, value);
}

EGLBoolean eglReleaseThread() {
    EGLBoolean ok = host<EGLBoolean (*)()>("eglReleaseThread")();
    apklayer::egl::gles1MadeCurrent(EGL_NO_CONTEXT);  // no context on the thread now: the no-context GL hooks, as Android's eglReleaseThread sets
    return ok;
}

EGLBoolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig cfg, EGLint attribute, EGLint* value) {
    EGLBoolean ok = host<EGLBoolean (*)(EGLDisplay, EGLConfig, EGLint, EGLint*)>("eglGetConfigAttrib")(dpy, cfg, attribute, value);
    if (ok && attribute == EGL_SURFACE_TYPE && value) *value |= EGL_PBUFFER_BIT;  // eglCreatePbufferSurface (EglWindow.cpp) works on every config
    // Android's native visual id is a HAL pixel format (engines hand it to ANativeWindow_setBuffersGeometry: bgfx, SDL, Unity); the host's is a DRM fourcc
    if (ok && attribute == EGL_NATIVE_VISUAL_ID && value) {
        switch (static_cast<uint32_t>(*value)) {
            case DRM_FORMAT_ARGB8888: case DRM_FORMAT_ABGR8888: *value = 1; break;      // HAL_PIXEL_FORMAT_RGBA_8888 (every window buffer is allocated as it)
            case DRM_FORMAT_XRGB8888: case DRM_FORMAT_XBGR8888: *value = 2; break;      // RGBX_8888
            case DRM_FORMAT_RGB565: *value = 4; break;                                  // RGB_565
            case DRM_FORMAT_ARGB2101010: case DRM_FORMAT_ABGR2101010: *value = 0x2b; break;  // RGBA_1010102
            case DRM_FORMAT_ABGR16161616F: *value = 0x16; break;                        // RGBA_FP16
            default: break;
        }
    }
    return ok;
}

// Android's EGL extensions the framework asks for that need no real work here.
void eglSetBlobCacheFuncsANDROID(EGLDisplay, EGLSetBlobFuncANDROID, EGLGetBlobFuncANDROID) {}
EGLBoolean eglGetFrameTimestampSupportedANDROID(EGLDisplay, EGLSurface, EGLint) { return EGL_FALSE; }
EGLBoolean eglGetCompositorTimingSupportedANDROID(EGLDisplay, EGLSurface, EGLint) { return EGL_FALSE; }
EGLBoolean eglGetCompositorTimingANDROID(EGLDisplay, EGLSurface, EGLint, const EGLint*, EGLnsecsANDROID*) { return fail(EGL_BAD_ACCESS); }
EGLBoolean eglGetNextFrameIdANDROID(EGLDisplay, EGLSurface, EGLuint64KHR*) { return fail(EGL_BAD_ACCESS); }
EGLBoolean eglGetFrameTimestampsANDROID(EGLDisplay, EGLSurface, EGLuint64KHR, EGLint, const EGLint*, EGLnsecsANDROID*) { return fail(EGL_BAD_ACCESS); }

// ---- forwarded to the host, untouched ----
// A tail call through a pointer resolved at load: no signature needed.
#define FORWARDED(X) \
    X(eglBindAPI) X(eglBindTexImage) X(eglClientWaitSync) X(eglClientWaitSyncKHR) X(eglCopyBuffers) \
    X(eglCreatePbufferFromClientBuffer) X(eglCreatePixmapSurface) X(eglCreatePlatformPixmapSurface) \
    X(eglCreateSync) X(eglCreateSyncKHR) X(eglDestroyImage) X(eglDestroyImageKHR) \
    X(eglDestroySync) X(eglDestroySyncKHR) X(eglDupNativeFenceFDANDROID) X(eglGetConfigs) \
    X(eglGetCurrentContext) X(eglGetCurrentDisplay) X(eglGetSyncAttrib) X(eglGetSyncAttribKHR) \
    X(eglQueryAPI) X(eglReleaseTexImage) \
    X(eglSignalSyncKHR) \
    X(eglWaitClient) X(eglWaitGL) X(eglWaitNative) \
    X(eglWaitSync) X(eglWaitSyncKHR) X(eglLockSurfaceKHR) X(eglUnlockSurfaceKHR)

#define DECLARE_SLOT(name) __attribute__((used, visibility("hidden"))) void* host_slot_##name;
FORWARDED(DECLARE_SLOT)

// file-scope assembly: no C declaration to clash with the EGL headers' signatures
#if defined(__aarch64__)
#define DEFINE_STUB(name) \
    __asm__(".text\n.globl " #name "\n.type " #name ",%function\n" #name ":\n\tadrp x16, host_slot_" #name \
            "\n\tldr x16, [x16, :lo12:host_slot_" #name "]\n\tbr x16\n");
#elif defined(__x86_64__)
#define DEFINE_STUB(name) \
    __asm__(".text\n.globl " #name "\n.type " #name ",@function\n" #name ":\n\tmovq host_slot_" #name "(%rip), %r11\n\tjmp *%r11\n");
#endif
FORWARDED(DEFINE_STUB)

// extensions an app or library may ask eglGetProcAddress for: ours where we implement them, the host's otherwise
__eglMustCastToProperFunctionPointerType eglGetProcAddress(const char* name) {
    struct { const char* n; void* f; } ours[] = {
        {"eglInitialize", (void*)eglInitialize}, {"eglTerminate", (void*)eglTerminate},
        {"eglCreateImageKHR", (void*)eglCreateImageKHR}, {"eglCreateImage", (void*)eglCreateImage},
        {"eglGetNativeClientBufferANDROID", (void*)eglGetNativeClientBufferANDROID},
        {"eglSwapBuffersWithDamageKHR", (void*)eglSwapBuffersWithDamageKHR}, {"eglSwapBuffersWithDamageEXT", (void*)eglSwapBuffersWithDamageEXT}, {"eglSetDamageRegionKHR", (void*)eglSetDamageRegionKHR}, {"eglSwapInterval", (void*)eglSwapInterval}, {"eglSwapBuffers", (void*)eglSwapBuffers},
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
    if (void* es1 = apklayer::egl::gles1ProcAddress(name)) return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(es1);
    auto proc = host<__eglMustCastToProperFunctionPointerType (*)(const char*)>("eglGetProcAddress");
    return proc(name);
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
