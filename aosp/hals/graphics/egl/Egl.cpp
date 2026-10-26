// libEGL for apklayer's Android userspace (PLAN P7.1 step 3): replaces Android's EGL loader (cfg.d maps libEGL.so here). It
// forwards to the host's EGL on the GBM platform of the host graphics HAL's device (vendor-neutral: Mesa, NVIDIA...) and adds
// what Android expects of an EGL driver: EGL_ANDROID_image_native_buffer (an ANativeWindowBuffer from our gralloc becomes a
// dmabuf EGLImage), eglGetNativeClientBufferANDROID, filtering of attributes the host does not know. Window surfaces are the
// next step; everything not listed in IMPLEMENTED below is a straight tail-call into the host library.
#define LOG_TAG "apklayer.egl"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <android/hardware_buffer.h>
#include <dlfcn.h>
#include <drm_fourcc.h>
#include <log/log.h>
#include <nativebase/nativebase.h>

#include <cstring>
#include <initializer_list>
#include <string>
#include <vector>

#include "gralloc_handle.h"
#include "apklayer/graphics.h"

using namespace apklayer::gralloc;

extern "C" ANativeWindowBuffer* AHardwareBuffer_to_ANativeWindowBuffer(AHardwareBuffer*);

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

template <typename F>
F host(const char* name) {
    return reinterpret_cast<F>(dlsym(hostLib(), name));
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
        auto get = host<PFNEGLGETPLATFORMDISPLAYEXTPROC>("eglGetPlatformDisplayEXT");
        return get ? get(EGL_PLATFORM_GBM_KHR, apklayer_gfx_gbm_device(gfx()), nullptr) : EGL_NO_DISPLAY;
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
    if (buf == nullptr || !decode(buf->handle, &d)) { tError = EGL_BAD_PARAMETER; return EGL_NO_IMAGE; }
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
    return create(dpy, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, a.data());
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
    return buffer ? AHardwareBuffer_to_ANativeWindowBuffer(const_cast<AHardwareBuffer*>(buffer)) : nullptr;
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

EGLSurface eglCreateWindowSurface(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint*) {
    tError = EGL_BAD_NATIVE_WINDOW;  // window surfaces over ANativeWindow: PLAN P7.1 step 3b
    return EGL_NO_SURFACE;
}

EGLSurface eglCreatePlatformWindowSurface(EGLDisplay d, EGLConfig c, void* w, const EGLAttrib*) {
    return eglCreateWindowSurface(d, c, reinterpret_cast<EGLNativeWindowType>(w), nullptr);
}

void* eglGetProcAddress(const char* name);

// Android's EGL extensions the framework asks for that need no real work here.
EGLBoolean eglSetBlobCacheFuncsANDROID(EGLDisplay, void*, void*) { return EGL_TRUE; }
EGLBoolean eglGetFrameTimestampSupportedANDROID(EGLDisplay, EGLSurface, EGLint) { return EGL_FALSE; }
EGLBoolean eglGetCompositorTimingSupportedANDROID(EGLDisplay, EGLSurface, EGLint) { return EGL_FALSE; }
EGLBoolean eglGetCompositorTimingANDROID(EGLDisplay, EGLSurface, EGLint, const EGLint*, EGLnsecsANDROID*) { return fail(EGL_BAD_ACCESS); }
EGLBoolean eglGetNextFrameIdANDROID(EGLDisplay, EGLSurface, EGLuint64KHR*) { return fail(EGL_BAD_ACCESS); }
EGLBoolean eglGetFrameTimestampsANDROID(EGLDisplay, EGLSurface, EGLuint64KHR, EGLint, const EGLint*, EGLnsecsANDROID*) { return fail(EGL_BAD_ACCESS); }
EGLBoolean eglPresentationTimeANDROID(EGLDisplay, EGLSurface, EGLnsecsANDROID) { return EGL_TRUE; }
void eglBeginFrame(EGLDisplay, EGLSurface) {}

// ---- forwarded to the host, untouched ----
// A tail call through a pointer resolved at load: no signature needed.
#define FORWARDED(X) \
    X(eglBindAPI) X(eglBindTexImage) X(eglClientWaitSync) X(eglClientWaitSyncKHR) X(eglCopyBuffers) \
    X(eglCreatePbufferFromClientBuffer) X(eglCreatePbufferSurface) X(eglCreatePixmapSurface) X(eglCreatePlatformPixmapSurface) \
    X(eglCreateSync) X(eglCreateSyncKHR) X(eglDestroyContext) X(eglDestroyImage) X(eglDestroyImageKHR) X(eglDestroySurface) \
    X(eglDestroySync) X(eglDestroySyncKHR) X(eglDupNativeFenceFDANDROID) X(eglGetConfigAttrib) X(eglGetConfigs) \
    X(eglGetCurrentContext) X(eglGetCurrentDisplay) X(eglGetCurrentSurface) X(eglGetSyncAttrib) X(eglGetSyncAttribKHR) \
    X(eglInitialize) X(eglMakeCurrent) X(eglQueryAPI) X(eglQueryContext) X(eglQuerySurface) X(eglReleaseTexImage) \
    X(eglReleaseThread) X(eglSetDamageRegionKHR) X(eglSignalSyncKHR) X(eglSurfaceAttrib) X(eglSwapBuffers) \
    X(eglSwapBuffersWithDamageKHR) X(eglSwapInterval) X(eglTerminate) X(eglWaitClient) X(eglWaitGL) X(eglWaitNative) \
    X(eglWaitSync) X(eglWaitSyncKHR) X(eglLockSurfaceKHR) X(eglUnlockSurfaceKHR)

#define DECLARE_SLOT(name) __attribute__((used)) void* host_slot_##name;
FORWARDED(DECLARE_SLOT)

#define DEFINE_STUB(name) \
    __attribute__((naked, visibility("default"))) void name() { \
        asm volatile("adrp x16, host_slot_" #name "\n\tldr x16, [x16, :lo12:host_slot_" #name "]\n\tbr x16"); \
    }
FORWARDED(DEFINE_STUB)

// extensions an app or library may ask eglGetProcAddress for: ours where we implement them, the host's otherwise
void* eglGetProcAddress(const char* name) {
    struct { const char* n; void* f; } ours[] = {
        {"eglCreateImageKHR", (void*)eglCreateImageKHR}, {"eglCreateImage", (void*)eglCreateImage},
        {"eglGetNativeClientBufferANDROID", (void*)eglGetNativeClientBufferANDROID},
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
    for (auto& o : ours) if (!strcmp(name, o.n)) return o.f;
    return reinterpret_cast<void*>(host<void* (*)(const char*)>("eglGetProcAddress")(name));
}

__attribute__((constructor)) static void resolveForwarded() {
#define RESOLVE(name) host_slot_##name = dlsym(hostLib(), #name);
    FORWARDED(RESOLVE)
}

}  // extern "C"
