// What the files of apklayer's libEGL share (Egl.cpp: displays, configs, contexts, images, the forwarded entry points; EglWindow.cpp: window
// and pbuffer surfaces; EglAttach.cpp: the zero-copy window present): the host's EGL, GL and GBM functions, our own EGL error, the GBM display.
#pragma once

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <dlfcn.h>
#include <nativebase/nativebase.h>

#include <cstring>

#include "apklayer/graphics.h"

namespace apklayer::egl {

void* hostLib();  // the host's glvnd libEGL.so.1

// A function of the host's libEGL (eglGetProcAddress is the fallback: glvnd exports only the core entry points by name), or with a
// "gbm_" name of its libgbm. Looked up once, at load (Egl.cpp, kHostNames): a lookup takes the shim linker's lock, and windowsLock is
// held across host calls while another thread's library constructor, holding the linker's lock, may call EGL (FCL's game hung so).
void* hostSym(const char* name);

template <typename F>
F host(const char* name) {
    return reinterpret_cast<F>(hostSym(name));
}

// GL entry points of the current context (through the host's eglGetProcAddress: glvnd exports no names)
template <typename F>
F gl(const char* name) {
    return host<F>(name);
}

template <typename F>
F gbmFn(const char* name) {
    return host<F>(name);
}

extern thread_local EGLint tError;  // errors of our own (the host keeps its own)
EGLBoolean fail(EGLint e);          // sets tError, returns EGL_FALSE
apklayer_gfx* gfx();                // the host graphics HAL's render node
EGLDisplay gbmDisplay();            // the host's display on that node's GBM device
// An ANativeWindowBuffer of our gralloc as a dmabuf EGLImage on the host.
EGLImage imageFromNativeBuffer(EGLDisplay dpy, ANativeWindowBuffer* buf);
// The host surface behind one of our surfaces (EglWindow.cpp); anything else is returned as it is.
EGLSurface hostSurface(EGLSurface s);
// A native fence fd for the current context's GL work so far (flushed), or -1 after a glFinish when the host has none.
int gpuFence(EGLDisplay dpy);

}  // namespace apklayer::egl

// EglWindow.cpp's entry points that eglGetProcAddress hands out (eglext.h declares them only with EGL_EGLEXT_PROTOTYPES)
extern "C" {
EGLBoolean eglSwapBuffersWithDamageKHR(EGLDisplay dpy, EGLSurface surface, const EGLint* rects, EGLint n);
EGLBoolean eglSwapBuffersWithDamageEXT(EGLDisplay dpy, EGLSurface surface, const EGLint* rects, EGLint n);
EGLBoolean eglSetDamageRegionKHR(EGLDisplay dpy, EGLSurface surface, EGLint* rects, EGLint n);
EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval);
EGLBoolean eglPresentationTimeANDROID(EGLDisplay dpy, EGLSurface surface, EGLnsecsANDROID time);
// EGL_ANDROID_get_frame_timestamps (as Android's libEGL: egl_platform_entries.cpp)
EGLBoolean eglGetNextFrameIdANDROID(EGLDisplay dpy, EGLSurface surface, EGLuint64KHR* frameId);
EGLBoolean eglGetCompositorTimingANDROID(EGLDisplay dpy, EGLSurface surface, EGLint n, const EGLint* names, EGLnsecsANDROID* values);
EGLBoolean eglGetCompositorTimingSupportedANDROID(EGLDisplay dpy, EGLSurface surface, EGLint name);
EGLBoolean eglGetFrameTimestampsANDROID(EGLDisplay dpy, EGLSurface surface, EGLuint64KHR frameId, EGLint n, const EGLint* names, EGLnsecsANDROID* values);
EGLBoolean eglGetFrameTimestampSupportedANDROID(EGLDisplay dpy, EGLSurface surface, EGLint name);
}
