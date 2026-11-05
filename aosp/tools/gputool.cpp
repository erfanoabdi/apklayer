// gputool: which GPU an Android process of this instance gets through libEGL, and whether it draws (docs/HALIUM.md).
//   probe   eglInitialize on the default display, EGL/GL vendor and renderer strings, a 64x64 pbuffer cleared to a known colour and
//           read back with glReadPixels. Exit 0 when the pixel is that colour. On a Halium device with Android's own libEGL mapped
//           in, the device's driver (/vendor/lib64/egl) answers: the GPU spike of plan step (e).
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <stdio.h>
#include <string.h>

extern "C" int main(int argc, char** argv) {
    if (argc < 2 || strcmp(argv[1], "probe")) { fprintf(stderr, "usage: gputool probe\n"); return 2; }
    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major = 0, minor = 0;
    if (dpy == EGL_NO_DISPLAY || !eglInitialize(dpy, &major, &minor)) { printf("gputool: eglInitialize failed (0x%x)\n", eglGetError()); return 1; }
    printf("gputool: EGL %d.%d vendor %s version %s\n", major, minor, eglQueryString(dpy, EGL_VENDOR), eglQueryString(dpy, EGL_VERSION));
    const EGLint attrs[] = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8,
                            EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
    EGLConfig config;
    EGLint n = 0;
    if (!eglChooseConfig(dpy, attrs, &config, 1, &n) || n < 1) { printf("gputool: no RGBA8888 pbuffer config (0x%x)\n", eglGetError()); return 1; }
    const EGLint ctxAttrs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext ctx = eglCreateContext(dpy, config, EGL_NO_CONTEXT, ctxAttrs);
    const EGLint pbAttrs[] = {EGL_WIDTH, 64, EGL_HEIGHT, 64, EGL_NONE};
    EGLSurface pb = eglCreatePbufferSurface(dpy, config, pbAttrs);
    if (ctx == EGL_NO_CONTEXT || pb == EGL_NO_SURFACE || !eglMakeCurrent(dpy, pb, pb, ctx)) {
        printf("gputool: context or pbuffer failed (0x%x)\n", eglGetError());
        return 1;
    }
    printf("gputool: GL vendor %s renderer %s version %s\n", glGetString(GL_VENDOR), glGetString(GL_RENDERER), glGetString(GL_VERSION));
    glClearColor(0.2f, 0.4f, 0.6f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    unsigned char px[4] = {0, 0, 0, 0};
    glReadPixels(32, 32, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
    printf("gputool: pixel %u %u %u %u (expected about 51 102 153 255)\n", px[0], px[1], px[2], px[3]);
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(dpy, pb);
    eglDestroyContext(dpy, ctx);
    eglTerminate(dpy);
    bool ok = px[0] >= 49 && px[0] <= 53 && px[1] >= 100 && px[1] <= 104 && px[2] >= 151 && px[2] <= 155 && px[3] == 255;
    return ok ? 0 : 1;
}
