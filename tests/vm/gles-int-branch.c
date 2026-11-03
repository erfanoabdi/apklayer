// gles-int-branch: WebRender's packed-glyph block of ps_text_run (an int assigned in if/else branches, then used as a float), alone in
// a GLES 3.0 vertex shader on the host's EGL/GBM: no apklayer code involved. On the UTM VM (virgl) the ints come back as NaN bits
// (0x7fc00000), so Gecko's subpixel-positioned glyphs get NaN quads and page text does not draw. Fennec's own GL calls (apitrace)
// replayed on llvmpipe draw the text; on virgl they do not; Fennec draws text on a real GPU (x86 PC).
// cc -o gles-int-branch tests/vm/gles-int-branch.c -lEGL -lGLESv2 -lgbm && ./gles-int-branch   (exit 0 = PASS)
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <gbm.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>

static const char* vs =
    "#version 300 es\n"
    "in ivec4 aData; in vec2 aPos; uniform vec4 uRect; flat out vec4 vOut;\n"
    "void main(){ int f = aData.z >> 16; int ox = (f >> 4) & 3; int oy = (f >> 6) & 3; int dir = (f >> 8) & 3; int pk = (f >> 10) & 1;\n"
    " vec4 t41 = uRect; vec4 r = t41;\n"
    " if (pk != 0) { highp int sc; highp int vi; if (dir == 3) { vi = oy * 4 + ox; sc = 16; } else { int t; if (dir == 1) t = ox; else t = oy; vi = t; sc = 4; }\n"
    "   float w = (t41.z - t41.x) / float(sc); r.x = t41.x + float(vi) * w; r.z = r.x + w; }\n"
    " vOut = r; gl_Position = vec4(aPos, 0.0, 1.0); gl_PointSize = 1.0; }\n";
static const char* fs =
    "#version 300 es\nprecision highp float;\n"
    "flat in vec4 vOut; layout(location=0) out vec4 o; void main(){ o = vOut; }\n";

static GLuint sh(GLenum t, const char* s) {
    GLuint h = glCreateShader(t); glShaderSource(h, 1, &s, NULL); glCompileShader(h);
    GLint ok; glGetShaderiv(h, GL_COMPILE_STATUS, &ok); if (!ok) { char l[2000]; glGetShaderInfoLog(h, 2000, NULL, l); printf("compile: %s\n", l); exit(1); }
    return h;
}

int main(void) {
    int fd = open("/dev/dri/renderD128", O_RDWR);
    struct gbm_device* g = gbm_create_device(fd);
    EGLDisplay d = eglGetPlatformDisplay(EGL_PLATFORM_GBM_KHR, g, NULL);
    eglInitialize(d, NULL, NULL); eglBindAPI(EGL_OPENGL_ES_API);
    EGLint ca[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT, EGL_NONE}; EGLConfig c; EGLint n;
    eglChooseConfig(d, ca, &c, 1, &n);
    EGLint xa[] = {EGL_CONTEXT_MAJOR_VERSION, 3, EGL_NONE};
    EGLContext x = eglCreateContext(d, c, EGL_NO_CONTEXT, xa);
    eglMakeCurrent(d, EGL_NO_SURFACE, EGL_NO_SURFACE, x);
    printf("%s | %s\n", glGetString(GL_VERSION), glGetString(GL_RENDERER));
    GLuint rb, fb; glGenRenderbuffers(1, &rb); glBindRenderbuffer(GL_RENDERBUFFER, rb); glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA32F, 1, 1);
    glGenFramebuffers(1, &fb); glBindFramebuffer(GL_FRAMEBUFFER, fb); glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, rb);
    GLuint p = glCreateProgram(); glAttachShader(p, sh(GL_VERTEX_SHADER, vs)); glAttachShader(p, sh(GL_FRAGMENT_SHADER, fs));
    glBindAttribLocation(p, 0, "aData"); glBindAttribLocation(p, 1, "aPos"); glLinkProgram(p); glUseProgram(p); glViewport(0, 0, 1, 1);
    int fails = 0;
    glUniform4f(glGetUniformLocation(p, "uRect"), 100, 10, 196, 40);
    for (int ox = 0; ox < 4; ox++) for (int pk = 0; pk < 2; pk++) {
        int flags = 1 | (ox << 4) | (1 << 8) | (pk << 10);
        GLint data[4] = {7, 9, (flags << 16) | 3, (2 << 24) | 0x1234};
        float pos[2] = {0, 0};
        GLuint b[2]; glGenBuffers(2, b);
        glBindBuffer(GL_ARRAY_BUFFER, b[0]); glBufferData(GL_ARRAY_BUFFER, sizeof data, data, GL_STATIC_DRAW); glVertexAttribIPointer(0, 4, GL_INT, 0, 0); glEnableVertexAttribArray(0);
        glVertexAttribDivisor(0, 1);
        glBindBuffer(GL_ARRAY_BUFFER, b[1]); glBufferData(GL_ARRAY_BUFFER, sizeof pos, pos, GL_STATIC_DRAW); glVertexAttribPointer(1, 2, GL_FLOAT, 0, 0, 0); glEnableVertexAttribArray(1);
        GLfloat clr[4] = {-1, -1, -1, -1}; glClearBufferfv(GL_COLOR, 0, clr);
        glDrawArraysInstanced(GL_POINTS, 0, 1, 1);
        GLfloat out[4]; glReadPixels(0, 0, 1, 1, GL_RGBA, GL_FLOAT, out);
        float wx = pk ? 100 + ox * 24 : 100, wz = pk ? wx + 24 : 196;
        int ok = out[0] == wx && out[2] == wz && out[1] == 10 && out[3] == 40;
        if (!ok) fails++;
        printf("ox %d packed %d: %g %g %g %g (want %g 10 %g 40)%s\n", ox, pk, out[0], out[1], out[2], out[3], wx, wz, ok ? "" : "  WRONG");
        glDeleteBuffers(2, b);
    }
    printf("%s (%d failures of 8)\n", fails ? "FAIL" : "PASS", fails);
    return fails != 0;
}
