// OpenGL ES 1 contexts of apklayer's libEGL (Gles1.cpp).
#pragma once

#include <EGL/egl.h>

#include <vector>

namespace apklayer::egl {

using HostCreateContext = EGLContext (*)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);

// Whether an eglCreateContext attribute list (EGL_NONE-terminated, already filtered) asks for OpenGL ES 1 (no client version, or 1).
bool wantsGles1(const std::vector<EGLint>& attribs);
// An ES 1 context: the host's if its driver has ES 1, else a host GLES 3 context carrying libapklayer-gles1's translator.
EGLContext gles1CreateContext(EGLDisplay dpy, EGLConfig cfg, EGLContext share, const std::vector<EGLint>& attribs, HostCreateContext create);
// After a successful host eglMakeCurrent: the thread's GLES 1 hook table (Android's libGLESv1_CM dispatches through it) and translator context.
void gles1MadeCurrent(EGLContext ctx);
// Before the host destroys `ctx`: its translator state (made on its host context).
void gles1DestroyContext(EGLDisplay dpy, EGLContext ctx);
// EGL_CONTEXT_CLIENT_VERSION of an ES 1 context is 1, whatever the host context behind it says; false for other queries.
bool gles1QueryContext(EGLContext ctx, EGLint attribute, EGLint* value);
// For eglGetProcAddress while an ES 1 context is current: Android's libGLESv1_CM entry point (it follows the hook table), else null.
void* gles1ProcAddress(const char* name);

}  // namespace apklayer::egl
