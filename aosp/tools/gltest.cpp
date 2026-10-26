// gltest: the EGL window-surface smoke test (PLAN P7.1 step 3b). A BufferQueue layer shown through SurfaceFlinger; the app side
// renders GL ES into it with a plain EGL window surface (libapklayer_egl copies each frame on the GPU into the window's buffer).
// Clears to a colour that changes every frame. Usage: gltest [frames]
#include <EGL/egl.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>
#include <utils/String8.h>

#include <stdio.h>
#include <stdlib.h>

using namespace android;

extern "C" int main(int argc, char** argv) {
    const int frames = argc > 1 ? atoi(argv[1]) : 120;
    const uint32_t w = 480, h = 320;

    sp<SurfaceComposerClient> client = sp<SurfaceComposerClient>::make();
    if (client->initCheck() != NO_ERROR) { fprintf(stderr, "gltest: no SurfaceFlinger\n"); return 1; }
    sp<SurfaceControl> sc = client->createSurface(String8("apklayer-gltest"), w, h, PIXEL_FORMAT_RGBA_8888, 0);
    if (sc == nullptr || !sc->isValid()) { fprintf(stderr, "gltest: createSurface failed\n"); return 1; }
    SurfaceComposerClient::Transaction().setLayer(sc, 0x7fffffff).show(sc).apply();
    sp<Surface> surface = sc->getSurface();

    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major, minor;
    if (!eglInitialize(dpy, &major, &minor)) { fprintf(stderr, "gltest: eglInitialize failed\n"); return 1; }
    const EGLint cfgAttr[] = {EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_RED_SIZE, 8,
                              EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
    EGLConfig cfg;
    EGLint n = 0;
    if (!eglChooseConfig(dpy, cfgAttr, &cfg, 1, &n) || n < 1) { fprintf(stderr, "gltest: no config\n"); return 1; }
    const EGLint ctxAttr[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttr);
    EGLSurface win = eglCreateWindowSurface(dpy, cfg, surface.get(), nullptr);
    if (ctx == EGL_NO_CONTEXT || win == EGL_NO_SURFACE) { fprintf(stderr, "gltest: context %p surface %p, EGL error 0x%x\n", ctx, win, eglGetError()); return 1; }
    if (!eglMakeCurrent(dpy, win, win, ctx)) { fprintf(stderr, "gltest: eglMakeCurrent failed 0x%x\n", eglGetError()); return 1; }
    auto clearColor = reinterpret_cast<void (*)(float, float, float, float)>(eglGetProcAddress("glClearColor"));
    auto clear = reinterpret_cast<void (*)(unsigned)>(eglGetProcAddress("glClear"));
    printf("gltest: window surface %p on a %ux%u layer\n", win, w, h);
    for (int f = 0; f < frames; f++) {
        clearColor((f % 60) / 60.0f, 1.0f - (f % 60) / 60.0f, 0.5f, 1.0f);
        clear(0x4000 /* GL_COLOR_BUFFER_BIT */);
        eglSwapBuffers(dpy, win);
    }
    printf("gltest: %d frames swapped\n", frames);
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(dpy, win);
    return 0;
}
