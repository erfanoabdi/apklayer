/* The EASU fragment shader of bridge/shell/sharpen.c on its own, headless: usage easu-gl shader.frag in.raw w h out.raw ow oh.
 * The shader text comes from tests/vm/easu-ref.py (it cuts FS_UP out of sharpen.c, samplerExternalOES -> sampler2D); raw files are RGBA8. */
#define _GNU_SOURCE
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <fcntl.h>
#include <gbm.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static GLuint sh(GLenum t, const char *s)
{
    GLuint o = glCreateShader(t);
    glShaderSource(o, 1, &s, NULL);
    glCompileShader(o);
    GLint ok = 0;
    glGetShaderiv(o, GL_COMPILE_STATUS, &ok);
    if (!ok) { char l[2048]; glGetShaderInfoLog(o, sizeof l, NULL, l); fprintf(stderr, "shader: %s\n", l); exit(3); }
    return o;
}

int main(int argc, char **argv)
{
    if (argc < 8) { fprintf(stderr, "usage: easu-gl shader.frag in.raw w h out.raw ow oh\n"); return 2; }
    int w = atoi(argv[3]), h = atoi(argv[4]), ow = atoi(argv[6]), oh = atoi(argv[7]);
    FILE *f = fopen(argv[1], "rb");
    char *fs = calloc(1, 1 << 20);
    if (!f || !fread(fs, 1, (1 << 20) - 1, f)) return 2;
    fclose(f);
    uint8_t *in = malloc((size_t)w * h * 4);
    f = fopen(argv[2], "rb");
    if (!f || fread(in, 4, (size_t)w * h, f) != (size_t)w * h) return 2;
    fclose(f);
    int fd = open("/dev/dri/renderD128", O_RDWR);
    struct gbm_device *g = gbm_create_device(fd);
    PFNEGLGETPLATFORMDISPLAYEXTPROC gpd = (void *)eglGetProcAddress("eglGetPlatformDisplayEXT");
    EGLDisplay d = gpd(EGL_PLATFORM_GBM_KHR, g, NULL);
    eglInitialize(d, NULL, NULL);
    eglBindAPI(EGL_OPENGL_ES_API);
    EGLint ca[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext cx = eglCreateContext(d, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT, ca);
    if (!eglMakeCurrent(d, EGL_NO_SURFACE, EGL_NO_SURFACE, cx)) { fprintf(stderr, "no context\n"); return 3; }
    GLuint tx, fb, rb;
    glGenTextures(1, &tx);
    glBindTexture(GL_TEXTURE_2D, tx);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, in);
    GLint filt = getenv("EASU_NEAREST") ? GL_NEAREST : GL_LINEAR;  /* the filter of the source texture, as sharpen.c sets it before the pass */
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filt);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filt);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glGenTextures(1, &rb);
    glBindTexture(GL_TEXTURE_2D, rb);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, ow, oh, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glGenFramebuffers(1, &fb);
    glBindFramebuffer(GL_FRAMEBUFFER, fb);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, rb, 0);
    GLuint p = glCreateProgram();
    glAttachShader(p, sh(GL_VERTEX_SHADER, "attribute vec2 p; varying vec2 uv; void main() { uv = p * 0.5 + 0.5; gl_Position = vec4(p, 0.0, 1.0); }"));
    glAttachShader(p, sh(GL_FRAGMENT_SHADER, fs));
    glBindAttribLocation(p, 0, "p");
    glLinkProgram(p);
    glUseProgram(p);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tx);
    glUniform1i(glGetUniformLocation(p, "tex"), 0);
    glUniform2f(glGetUniformLocation(p, "src"), (float)w, (float)h);
    glUniform1f(glGetUniformLocation(p, "down"), 0.0f);
    static const float q[] = { -1, -1, 1, -1, -1, 1, 1, 1 };
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, q);
    glViewport(0, 0, ow, oh);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    uint8_t *out = malloc((size_t)ow * oh * 4);
    glReadPixels(0, 0, ow, oh, GL_RGBA, GL_UNSIGNED_BYTE, out);
    f = fopen(argv[5], "wb");
    fwrite(out, 4, (size_t)ow * oh, f);
    fclose(f);
    return 0;
}
