/* glformats on the host's own EGL/GLES (gbm, surfaceless context): the reference run, no apklayer code involved.
 *   cc -o glformats tests/bench/glformats/linux.c -lEGL -lGLESv2 -lgbm -lm && ./glformats [grep-pattern]   (exit status: number of failed checks) */
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <fcntl.h>
#include <gbm.h>

#include "formats.c"

static void print(const char *line) { puts(line); }

int main(void)
{
    int fd = open("/dev/dri/renderD128", O_RDWR);
    EGLDisplay d = eglGetPlatformDisplay(EGL_PLATFORM_GBM_KHR, gbm_create_device(fd), NULL);
    eglInitialize(d, NULL, NULL);
    eglBindAPI(EGL_OPENGL_ES_API);
    EGLint a[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLContext x = eglCreateContext(d, NULL, EGL_NO_CONTEXT, a);  /* EGL_KHR_no_config_context, as Mesa offers */
    if (x == EGL_NO_CONTEXT || !eglMakeCurrent(d, EGL_NO_SURFACE, EGL_NO_SURFACE, x)) { puts("glformats: no ES 3 context"); return 99; }
    formats_proc = (void *(*)(const char *))eglGetProcAddress;
    printf("glformats: EGL_EXTENSIONS %s\n", eglQueryString(d, EGL_EXTENSIONS));
    return formats_run(print);
}
