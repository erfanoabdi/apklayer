// gles-sampler-limit: a fragment shader with as many samplers as the host's GLES claims (GL_MAX_TEXTURE_IMAGE_UNITS), each picked at run
// time (libgdx/Unciv's TextureArraySpriteBatch), on the host's EGL/GBM: no apklayer code involved. On the UTM VM (virgl) the guest claims 32,
// the shader compiles and links, and after one draw with it the context is dead: even a plain glClear reads back 0,0,0,0 (Unciv's window
// stays black). With 16 samplers it draws. The Parallels VM (virgl too) claims 16 and passes.
// cc -o gles-sampler-limit tests/vm/gles-sampler-limit.c -lEGL -lGLESv2 -lgbm && ./gles-sampler-limit [samplers]   (exit 0 = PASS)
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <gbm.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>

static GLuint sh(GLenum t, const char* s) {
    GLuint h = glCreateShader(t); glShaderSource(h, 1, &s, NULL); glCompileShader(h);
    GLint ok; glGetShaderiv(h, GL_COMPILE_STATUS, &ok); if (!ok) { char l[2000]; glGetShaderInfoLog(h, 2000, NULL, l); printf("compile: %s\n", l); exit(1); }
    return h;
}

int main(int argc, char** argv) {
    int fd = open("/dev/dri/renderD128", O_RDWR);
    EGLDisplay d = eglGetPlatformDisplay(EGL_PLATFORM_GBM_KHR, gbm_create_device(fd), NULL);
    eglInitialize(d, NULL, NULL); eglBindAPI(EGL_OPENGL_ES_API);
    EGLint xa[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext x = eglCreateContext(d, NULL, EGL_NO_CONTEXT, xa);  // EGL_KHR_no_config_context, as Mesa offers
    eglMakeCurrent(d, EGL_NO_SURFACE, EGL_NO_SURFACE, x);
    GLint units; glGetIntegerv(GL_MAX_TEXTURE_IMAGE_UNITS, &units);
    int n = argc > 1 ? atoi(argv[1]) : units;
    printf("%s | %s | GL_MAX_TEXTURE_IMAGE_UNITS %d, shader with %d\n", glGetString(GL_VERSION), glGetString(GL_RENDERER), units, n);

    GLuint target, fbo; glGenTextures(1, &target); glBindTexture(GL_TEXTURE_2D, target);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 8, 8, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glGenFramebuffers(1, &fbo); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
    GLuint green; glGenTextures(1, &green); glBindTexture(GL_TEXTURE_2D, green);
    unsigned char g4[4] = {0, 255, 0, 255};
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, g4);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

    static char fs[16384];
    int o = sprintf(fs, "precision mediump float; uniform float uIndex; uniform sampler2D uTex[%d];\nvoid main() {\n", n);
    for (int i = 0; i < n; i++) o += sprintf(fs + o, "  %sif (uIndex < %d.5) gl_FragColor = texture2D(uTex[%d], vec2(0.5));\n", i ? "else " : "", i, i);
    sprintf(fs + o, "  else gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);\n}\n");
    GLuint p = glCreateProgram();
    glAttachShader(p, sh(GL_VERTEX_SHADER, "attribute vec2 aPos; void main() { gl_Position = vec4(aPos, 0.0, 1.0); }"));
    glAttachShader(p, sh(GL_FRAGMENT_SHADER, fs));
    glBindAttribLocation(p, 0, "aPos"); glLinkProgram(p);
    GLint ok; glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) { printf("link failed (a host may refuse what it cannot do: fine)\nPASS\n"); return 0; }
    glUseProgram(p);
    glUniform1f(glGetUniformLocation(p, "uIndex"), 0.0f);
    for (int i = 0; i < n; i++) { char name[32]; sprintf(name, "uTex[%d]", i); glUniform1i(glGetUniformLocation(p, name), i); glActiveTexture(GL_TEXTURE0 + i); glBindTexture(GL_TEXTURE_2D, green); }
    glViewport(0, 0, 8, 8);
    float tri[] = {-1, -1, 3, -1, -1, 3};
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, tri); glEnableVertexAttribArray(0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    unsigned char drawn[4] = {0}, cleared[4] = {0};
    glReadPixels(4, 4, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, drawn);
    glUseProgram(0);
    glClearColor(1, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
    glReadPixels(4, 4, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, cleared);
    printf("draw: %d,%d,%d,%d (want 0,255,0,255); clear after it: %d,%d,%d,%d (want 255,0,0,255)\n", drawn[0], drawn[1], drawn[2], drawn[3],
           cleared[0], cleared[1], cleared[2], cleared[3]);
    int pass = drawn[1] == 255 && cleared[0] == 255;
    printf("%s\n", pass ? "PASS" : "FAIL");
    return !pass;
}
