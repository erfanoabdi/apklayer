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

// Diagnostics for the GL calls that decide whether Skia can use an imported buffer as a render target: wrapped when Skia asks for them
// through eglGetProcAddress, they log what fails (framebuffer completeness, the attachment, the image binding) with the GL error.
typedef unsigned int GLenum_;
typedef int GLint_;
typedef unsigned int GLuint_;
typedef GLenum_ (*GetErrorFn)();
GetErrorFn glErrorFn() {
    static GetErrorFn f = reinterpret_cast<GetErrorFn>(host<void*(*)(const char*)>("eglGetProcAddress")("glGetError"));
    return f;
}
GLenum_ (*realCheck)(GLenum_);
void (*realAttach)(GLenum_, GLenum_, GLenum_, GLuint_, GLint_);
void (*realTarget)(GLenum_, void*);
GLenum_ wrapCheckFramebufferStatus(GLenum_ target) {
    GLenum_ status = realCheck(target);
    ALOGI("glCheckFramebufferStatus(0x%x) = 0x%x (complete is 0x8cd5), glError 0x%x", target, status, glErrorFn()());
    return status;
}
void wrapFramebufferTexture2D(GLenum_ target, GLenum_ attachment, GLenum_ textarget, GLuint_ texture, GLint_ level) {
    realAttach(target, attachment, textarget, texture, level);
    ALOGI("glFramebufferTexture2D(0x%x, 0x%x, 0x%x, tex %u, %d) -> glError 0x%x", target, attachment, textarget, texture, level, glErrorFn()());
}
void wrapEGLImageTargetTexture2DOES(GLenum_ target, void* image) {
    realTarget(target, image);
    ALOGI("glEGLImageTargetTexture2DOES(0x%x, %p) -> glError 0x%x", target, image, glErrorFn()());
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

// apklayer patch (diagnostic): log the GL the context really gives (Skia's renderability caps depend on it)
EGLBoolean eglMakeCurrent(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx) {
    EGLBoolean ok = host<PFNEGLMAKECURRENTPROC>("eglMakeCurrent")(dpy, draw, read, ctx);
    static bool logged;
    if (ok && ctx != EGL_NO_CONTEXT && !logged) {
        logged = true;
        auto gs = host<const unsigned char* (*)(unsigned)>("glGetString");
        if (gs) ALOGI("GL version '%s' renderer '%s' extensions '%s'", gs(0x1F02), gs(0x1F01), gs(0x1F03));
    }
    return ok;
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
    X(eglCreateSync) X(eglCreateSyncKHR) X(eglDestroyContext) X(eglDestroyImage) X(eglDestroyImageKHR) X(eglDestroySurface) \
    X(eglDestroySync) X(eglDestroySyncKHR) X(eglDupNativeFenceFDANDROID) X(eglGetConfigAttrib) X(eglGetConfigs) \
    X(eglGetCurrentContext) X(eglGetCurrentDisplay) X(eglGetCurrentSurface) X(eglGetSyncAttrib) X(eglGetSyncAttribKHR) \
    X(eglInitialize) X(eglQueryAPI) X(eglQueryContext) X(eglQuerySurface) X(eglReleaseTexImage) \
    X(eglReleaseThread) X(eglSetDamageRegionKHR) X(eglSignalSyncKHR) X(eglSurfaceAttrib) X(eglSwapBuffers) \
    X(eglSwapBuffersWithDamageKHR) X(eglSwapInterval) X(eglTerminate) X(eglWaitClient) X(eglWaitGL) X(eglWaitNative) \
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
    if (!strcmp(name, "glCheckFramebufferStatus") && (realCheck = reinterpret_cast<decltype(realCheck)>(proc(name))))
        return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(wrapCheckFramebufferStatus);
    if (!strcmp(name, "glFramebufferTexture2D") && (realAttach = reinterpret_cast<decltype(realAttach)>(proc(name))))
        return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(wrapFramebufferTexture2D);
    if (!strcmp(name, "glEGLImageTargetTexture2DOES") && (realTarget = reinterpret_cast<decltype(realTarget)>(proc(name))))
        return reinterpret_cast<__eglMustCastToProperFunctionPointerType>(wrapEGLImageTargetTexture2DOES);
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

