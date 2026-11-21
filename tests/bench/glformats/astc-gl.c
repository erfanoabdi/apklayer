/* One .astc file through the host's own EGL/GLES (gbm, surfaceless; $APKLAYER_GPU a render node path): the whole picture as one compressed texture,
 * sampled texel by texel into RGBA8 and written raw. For tests/vm/astc-probe.py's GL mode (Mesa's CPU ASTC decoder on a GPU without ASTC).
 *   cc -o astc-gl tests/bench/glformats/astc-gl.c -lEGL -lGLESv2 -lgbm && ./astc-gl FILE.astc srgb(0|1) OUT.rgba */
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <fcntl.h>
#include <gbm.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(int argc, char **argv)
{
    if (argc < 4) return 2;
    FILE *f = fopen(argv[1], "rb");
    unsigned char h[16];
    if (!f || fread(h, 1, 16, f) != 16) return 2;
    int bx = h[4], by = h[5], w = h[7] | h[8] << 8 | h[9] << 16, ht = h[10] | h[11] << 8 | h[12] << 16, srgb = atoi(argv[2]);
    int bytes = ((w + bx - 1) / bx) * ((ht + by - 1) / by) * 16;
    unsigned char *blocks = malloc(bytes);
    if (fread(blocks, 1, bytes, f) != (size_t)bytes) return 2;
    static const int dims[14][2] = { {4,4}, {5,4}, {5,5}, {6,5}, {6,6}, {8,5}, {8,6}, {8,8}, {10,5}, {10,6}, {10,8}, {10,10}, {12,10}, {12,12} };
    int idx = -1;
    for (int i = 0; i < 14; i++) if (dims[i][0] == bx && dims[i][1] == by) idx = i;
    const char *node = getenv("APKLAYER_GPU");
    int fd = open(node && node[0] == '/' ? node : "/dev/dri/renderD128", O_RDWR);
    EGLDisplay d = eglGetPlatformDisplay(EGL_PLATFORM_GBM_KHR, gbm_create_device(fd), NULL);
    eglInitialize(d, NULL, NULL);
    eglBindAPI(EGL_OPENGL_ES_API);
    EGLint a[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLContext x = eglCreateContext(d, NULL, EGL_NO_CONTEXT, a);
    if (x == EGL_NO_CONTEXT || !eglMakeCurrent(d, EGL_NO_SURFACE, EGL_NO_SURFACE, x) || idx < 0) { puts("no context"); return 99; }
    printf("renderer %s\n", glGetString(GL_RENDERER));
    GLuint t, fbo, rt, vao, p, s[2];
    glGenTextures(1, &t);
    glBindTexture(GL_TEXTURE_2D, t);
    glCompressedTexImage2D(GL_TEXTURE_2D, 0, (srgb ? 0x93D0 : 0x93B0) + idx, w, ht, 0, bytes, blocks);
    if (glGetError()) { puts("upload error"); return 98; }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glGenTextures(1, &rt);
    glBindTexture(GL_TEXTURE_2D, rt);
    glTexStorage2D(GL_TEXTURE_2D, 1, srgb ? GL_SRGB8_ALPHA8 : GL_RGBA8, w, ht);  /* sRGB: the sampled linear value is encoded again on write */
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, rt, 0);
    glViewport(0, 0, w, ht);
    const char *vs = "#version 300 es\nout vec2 uv; void main(){ vec2 p = vec2(float((gl_VertexID<<1)&2), float(gl_VertexID&2)); gl_Position = vec4(p*2.-1., 0., 1.); uv = p; }";
    const char *fs = "#version 300 es\nprecision highp float; uniform sampler2D t; in vec2 uv; out vec4 o; void main(){ o = texture(t, uv); }";
    p = glCreateProgram();
    for (int i = 0; i < 2; i++) { s[i] = glCreateShader(i ? GL_FRAGMENT_SHADER : GL_VERTEX_SHADER); glShaderSource(s[i], 1, i ? &fs : &vs, NULL); glCompileShader(s[i]); glAttachShader(p, s[i]); }
    glLinkProgram(p);
    glUseProgram(p);
    glGenVertexArrays(1, &vao);
    glBindVertexArray(vao);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, t);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    unsigned char *px = malloc((size_t)w * ht * 4);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, w, ht, GL_RGBA, GL_UNSIGNED_BYTE, px);
    FILE *o = fopen(argv[3], "wb");
    fwrite(px, 1, (size_t)w * ht * 4, o);
    fclose(o);
    puts("ok");
    return 0;
}
