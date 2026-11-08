// gles1-translator: libapklayer-gles1.so (OpenGL ES 1.1 over a host GLES 3 context, meson -Dgfxstream) draws what ES 1 apps draw: a clear,
// a fixed-function triangle from a client vertex array (glVertexPointer + glColor4f under glOrthof), a texture through glDrawTexiOES
// (SDL 1.2's Android blit). On the host's EGL/GBM window surface, read back through the ES 1 API.
// cc -std=gnu17 -o gles1-translator tests/vm/gles1-translator.c -lEGL -lgbm -ldl && ./gles1-translator <native>/libapklayer-gles1.so   (exit 0 = PASS)
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES/gl.h>
#include <GLES/glext.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <gbm.h>
#include <stdio.h>
#include <string.h>

static void* (*proc)(const char*);
#define GL(name, ...) ((void (*)())proc(#name))(__VA_ARGS__)  // an unprototyped call: -std=gnu17 (C23 reads () as no parameters)

int main(int argc, char** argv) {
    void* lib = dlopen(argc > 1 ? argv[1] : "libapklayer-gles1.so", RTLD_NOW);
    if (!lib) { printf("FAIL %s\n", dlerror()); return 1; }
    void* (*create)(void*) = dlsym(lib, "apklayer_gles1_create");
    void (*makeCurrent)(void*) = dlsym(lib, "apklayer_gles1_make_current");
    proc = dlsym(lib, "apklayer_gles1_proc");
    int fd = open("/dev/dri/renderD128", O_RDWR);
    struct gbm_device* g = gbm_create_device(fd);
    EGLDisplay d = eglGetPlatformDisplay(EGL_PLATFORM_GBM_KHR, g, NULL);
    eglInitialize(d, NULL, NULL); eglBindAPI(EGL_OPENGL_ES_API);
    EGLint ca[] = {EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_NONE};
    EGLConfig c; EGLint n = 0;
    eglChooseConfig(d, ca, &c, 1, &n);
    EGLint vis; eglGetConfigAttrib(d, c, EGL_NATIVE_VISUAL_ID, &vis);
    struct gbm_surface* gs = gbm_surface_create(g, 64, 64, vis, GBM_BO_USE_RENDERING);
    EGLSurface s = eglCreatePlatformWindowSurface(d, c, gs, NULL);
    EGLint xa[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_NONE};
    EGLContext x = eglCreateContext(d, c, EGL_NO_CONTEXT, xa);
    if (!eglMakeCurrent(d, s, s, x)) { printf("FAIL no host GLES 3 context\n"); return 1; }
    void* es1 = create(NULL);
    makeCurrent(es1);
    const char* renderer = ((const char* (*)(GLenum))proc("glGetString"))(GL_RENDERER);
    printf("%s | %s\n", ((const char* (*)(GLenum))proc("glGetString"))(GL_VERSION), renderer);
    if (strstr(renderer, "Translator")) { printf("FAIL renderer is the translator's, not the host GPU's\n"); return 1; }

    GL(glViewport, 0, 0, 64, 64);
    ((void (*)(GLfloat, GLfloat, GLfloat, GLfloat))proc("glClearColor"))(0, 0, 1, 1);
    GL(glClear, GL_COLOR_BUFFER_BIT);
    GL(glMatrixMode, GL_PROJECTION); GL(glLoadIdentity);
    ((void (*)(GLfloat, GLfloat, GLfloat, GLfloat, GLfloat, GLfloat))proc("glOrthof"))(0, 64, 0, 64, -1, 1);
    GL(glMatrixMode, GL_MODELVIEW); GL(glLoadIdentity);
    static const GLfloat tri[] = {0, 0, 32, 0, 0, 32};  // lower-left corner
    GL(glEnableClientState, GL_VERTEX_ARRAY);
    GL(glVertexPointer, 2, GL_FLOAT, 0, tri);
    ((void (*)(GLfloat, GLfloat, GLfloat, GLfloat))proc("glColor4f"))(0, 1, 0, 1);
    GL(glDrawArrays, GL_TRIANGLES, 0, 3);
    GL(glDisableClientState, GL_VERTEX_ARRAY);

    static const unsigned char red[4 * 4] = {255, 0, 0, 255, 255, 0, 0, 255, 255, 0, 0, 255, 255, 0, 0, 255};
    GLuint tex; GL(glGenTextures, 1, &tex);
    GL(glBindTexture, GL_TEXTURE_2D, tex);
    GL(glTexParameteri, GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    GL(glTexImage2D, GL_TEXTURE_2D, 0, GL_RGBA, 2, 2, 0, GL_RGBA, GL_UNSIGNED_BYTE, red);
    GLint crop[4] = {0, 0, 2, 2};
    GL(glTexParameteriv, GL_TEXTURE_2D, GL_TEXTURE_CROP_RECT_OES, crop);
    GL(glEnable, GL_TEXTURE_2D);
    ((void (*)(GLfloat, GLfloat, GLfloat, GLfloat))proc("glColor4f"))(1, 1, 1, 1);
    GL(glDrawTexiOES, 40, 40, 0, 16, 16);  // upper-right square
    GL(glDisable, GL_TEXTURE_2D);

    unsigned char bg[4], green[4], tx[4];
    GL(glReadPixels, 50, 10, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, bg);
    GL(glReadPixels, 8, 8, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, green);
    GL(glReadPixels, 48, 48, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, tx);
    GLenum err = ((GLenum (*)(void))proc("glGetError"))();
    printf("clear %d,%d,%d triangle %d,%d,%d drawtex %d,%d,%d gl error 0x%x\n", bg[0], bg[1], bg[2], green[0], green[1], green[2], tx[0], tx[1], tx[2], err);
    int pass = bg[2] > 200 && bg[0] < 50 && green[1] > 200 && green[0] < 50 && tx[0] > 200 && tx[1] < 50 && err == 0;
    printf("%s\n", pass ? "PASS" : "FAIL");
    return !pass;
}
