/* glbench's scenes and timing (bench.h). Scenes: clear (glClear only: the cost of a frame's trip), fill (a full-target quad with an
 * ALU-heavy fragment shader: GPU bound), draws (2000 small quads, one glUniform + glDrawArrays each: driver and call overhead).
 * Window frames end in eglSwapBuffers; offscreen frames draw into an FBO and keep at most two frames in flight with EGL fences
 * (EGL_KHR_fence_sync), like a two-buffer swapchain without the present. */
#include "bench.h"

#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static double now_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1e3 + ts.tv_nsec / 1e6;
}

static const char *vs_src =
    "attribute vec2 pos;\n"
    "uniform vec4 rect;\n"
    "varying vec2 uv;\n"
    "void main() { uv = pos; gl_Position = vec4(rect.xy + pos * rect.zw, 0.0, 1.0); }\n";
static const char *fs_fill =
    "precision highp float;\n"
    "varying vec2 uv;\n"
    "uniform float t;\n"
    "void main() {\n"
    "  vec3 c = vec3(uv, 0.5);\n"
    "  for (int i = 0; i < ITER; i++) c = abs(sin(c * 3.1 + vec3(t, 0.3 * float(i), 1.7)));\n"
    "  gl_FragColor = vec4(c, 1.0);\n"
    "}\n";
static const char *fs_flat =
    "precision mediump float;\n"
    "uniform vec4 color;\n"
    "void main() { gl_FragColor = color; }\n";

static GLuint shader(GLenum type, const char *src)
{
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[512]; glGetShaderInfoLog(s, sizeof log, NULL, log); fprintf(stderr, "glbench: shader: %s\n", log); }
    return s;
}

static GLuint program(const char *fs)
{
    char src[1024];
    snprintf(src, sizeof src, "#define ITER %d\n%s", getenv("GLBENCH_ITER") ? atoi(getenv("GLBENCH_ITER")) : 400, fs);  /* fill's loop length */
    GLuint p = glCreateProgram();
    glAttachShader(p, shader(GL_VERTEX_SHADER, vs_src));
    glAttachShader(p, shader(GL_FRAGMENT_SHADER, src));
    glBindAttribLocation(p, 0, "pos");
    glLinkProgram(p);
    return p;
}

static int cmp(const void *a, const void *b)
{
    double x = *(const double *)a, y = *(const double *)b;
    return x < y ? -1 : x > y;
}

struct gl_state { GLuint fill, flat, vbo, fbo; GLint fill_t, fill_rect, flat_color, flat_rect; };

static void draw(const struct gl_state *g, const char *scene, int frame)
{
    glClearColor((frame % 60) / 60.0f, 0.2f, 0.3f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    if (!strcmp(scene, "fill")) {
        glUseProgram(g->fill);
        glUniform1f(g->fill_t, frame * 0.01f);
        glUniform4f(g->fill_rect, -1, -1, 2, 2);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    } else if (!strcmp(scene, "draws")) {
        glUseProgram(g->flat);
        for (int i = 0; i < 2000; i++) {
            float x = -1 + (i % 50) * 0.04f, y = -1 + (i / 50) * 0.05f;
            glUniform4f(g->flat_color, (i % 7) / 7.0f, (frame % 30) / 30.0f, (i % 13) / 13.0f, 1);
            glUniform4f(g->flat_rect, x, y, 0.035f, 0.045f);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        }
    }
}

static void run_scene(const struct bench_target *t, const struct gl_state *g, const char *scene, double seconds)
{
    PFNEGLCREATESYNCKHRPROC create_sync = (PFNEGLCREATESYNCKHRPROC)eglGetProcAddress("eglCreateSyncKHR");
    PFNEGLCLIENTWAITSYNCKHRPROC wait_sync = (PFNEGLCLIENTWAITSYNCKHRPROC)eglGetProcAddress("eglClientWaitSyncKHR");
    PFNEGLDESTROYSYNCKHRPROC destroy_sync = (PFNEGLDESTROYSYNCKHRPROC)eglGetProcAddress("eglDestroySyncKHR");
    EGLSyncKHR inflight[2] = { EGL_NO_SYNC_KHR, EGL_NO_SYNC_KHR };
    int cap = 100000, n = 0;
    double *frame_ms = malloc(sizeof(double) * cap), draw_sum = 0, swap_sum = 0, swap_max = 0;
    for (int warm = 0; warm < 10; warm++) {  /* shader compile, buffer allocation, first-frame setup */
        draw(g, scene, warm);
        if (t->offscreen) glFinish(); else eglSwapBuffers(t->dpy, t->surface);
    }
    double start = now_ms(), last = start;
    while (n < cap && last - start < seconds * 1000) {
        double a = now_ms();
        draw(g, scene, n);
        double b = now_ms();
        if (t->offscreen) {
            glFlush();
            EGLSyncKHR s = create_sync ? create_sync(t->dpy, EGL_SYNC_FENCE_KHR, NULL) : EGL_NO_SYNC_KHR;
            if (s == EGL_NO_SYNC_KHR) glFinish();
            EGLSyncKHR old = inflight[n % 2];
            inflight[n % 2] = s;
            if (old != EGL_NO_SYNC_KHR) { wait_sync(t->dpy, old, EGL_SYNC_FLUSH_COMMANDS_BIT_KHR, EGL_FOREVER_KHR); destroy_sync(t->dpy, old); }
        } else {
            eglSwapBuffers(t->dpy, t->surface);
        }
        double c = now_ms();
        draw_sum += b - a;
        swap_sum += c - b;
        if (c - b > swap_max) swap_max = c - b;
        frame_ms[n++] = c - last;
        last = c;
    }
    for (int i = 0; i < 2; i++) if (inflight[i] != EGL_NO_SYNC_KHR) { wait_sync(t->dpy, inflight[i], 0, EGL_FOREVER_KHR); destroy_sync(t->dpy, inflight[i]); }
    glFinish();
    double total = now_ms() - start;
    qsort(frame_ms, n, sizeof *frame_ms, cmp);
    char line[512];
    snprintf(line, sizeof line,
             "glbench: %s %s %s %dx%d frames=%d fps=%.1f ms/frame=%.2f p50=%.2f p95=%.2f max=%.2f draw_ms=%.2f %s_ms=%.2f %s_max=%.2f",
             t->side, t->mode, scene, t->width, t->height, n, n * 1000.0 / total, total / n, frame_ms[n / 2], frame_ms[n * 95 / 100],
             frame_ms[n - 1], draw_sum / n, t->offscreen ? "wait" : "swap", swap_sum / n, t->offscreen ? "wait" : "swap", swap_max);
    t->print(line);
    free(frame_ms);
}

int bench_run(const struct bench_target *t, const char *scenes, double seconds)
{
    char line[512];
    snprintf(line, sizeof line, "glbench: %s %s GL_RENDERER=\"%s\" GL_VERSION=\"%s\" EGL_VENDOR=\"%s\" EGL_VERSION=\"%s\"", t->side, t->mode,
             (const char *)glGetString(GL_RENDERER), (const char *)glGetString(GL_VERSION), eglQueryString(t->dpy, EGL_VENDOR),
             eglQueryString(t->dpy, EGL_VERSION));
    t->print(line);
    struct gl_state g = { program(fs_fill), program(fs_flat), 0, 0, 0, 0, 0, 0 };
    g.fill_t = glGetUniformLocation(g.fill, "t");
    g.fill_rect = glGetUniformLocation(g.fill, "rect");
    g.flat_color = glGetUniformLocation(g.flat, "color");
    g.flat_rect = glGetUniformLocation(g.flat, "rect");
    static const float quad[] = { 0, 0, 1, 0, 0, 1, 1, 1 };
    glGenBuffers(1, &g.vbo);
    glBindBuffer(GL_ARRAY_BUFFER, g.vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof quad, quad, GL_STATIC_DRAW);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glEnableVertexAttribArray(0);
    if (t->offscreen) {
        GLuint tex;
        glGenTextures(1, &tex);
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, t->width, t->height, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
        glGenFramebuffers(1, &g.fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, g.fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { t->print("glbench: FBO incomplete"); return 1; }
    }
    glViewport(0, 0, t->width, t->height);
    const char *all = scenes ? scenes : "clear,fill,draws";
    char buf[128];
    snprintf(buf, sizeof buf, "%s", all);
    for (char *save = NULL, *s = strtok_r(buf, ",", &save); s; s = strtok_r(NULL, ",", &save)) run_scene(t, &g, s, seconds);
    return 0;
}
