/* The upscale pass: see sharpen.h. EGL on the first render node that imports the app's dmabuf, two GLES2 passes: a Catmull-Rom bicubic resample (9
 * bilinear taps) into an intermediate texture, then contrast adaptive sharpening into one of three exportable dmabufs. */
#define _GNU_SOURCE
#include "sharpen.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <dirent.h>
#include <fcntl.h>
#include <gbm.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define BUFS 8
#define SLOTS 3
#define DRM_FORMAT_XRGB8888 0x34325258u

struct slot {
    struct gbm_bo *bo;
    EGLImage image;
    GLuint tex, fbo;
    int fd, w, h, busy;
    uint32_t stride;
    uint64_t modifier;
};

struct sharpen {
    int drm_fd;
    struct gbm_device *gbm;
    EGLDisplay dpy;
    EGLContext ctx;
    int ready;
    PFNEGLCREATEIMAGEKHRPROC create_image;
    PFNEGLDESTROYIMAGEKHRPROC destroy_image;
    PFNGLEGLIMAGETARGETTEXTURE2DOESPROC image_target;
    struct { EGLImage image; GLuint tex; int w, h; uint32_t fourcc; } in[BUFS];
    GLuint up_prog, cas_prog, mid_tex, mid_fbo, vbo;
    int mid_w, mid_h;
    struct slot out[SLOTS];
    int last;
    uint64_t accept[64];
    int accept_n;
};

static const char *VS = "attribute vec2 p; varying vec2 uv; void main() { uv = p * 0.5 + 0.5; gl_Position = vec4(p, 0.0, 1.0); }";

/* the Catmull-Rom weights folded into bilinear fetches (9 taps instead of 16) */
static const char *FS_UP =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision highp float;\n"
    "uniform samplerExternalOES tex; uniform vec2 src; uniform float down; varying vec2 uv;\n"
    "vec3 T(vec2 p) { return texture2D(tex, p).rgb; }\n"
    "void main() {\n"
    "  vec2 pos = uv * src, c = floor(pos - 0.5) + 0.5, f = pos - c, f2 = f * f, f3 = f2 * f;\n"
    "  vec2 w0 = -0.5 * f3 + f2 - 0.5 * f, w1 = 1.5 * f3 - 2.5 * f2 + 1.0, w2 = -1.5 * f3 + 2.0 * f2 + 0.5 * f, w3 = 0.5 * f3 - 0.5 * f2;\n"
    "  vec2 w12 = w1 + w2, t12 = (c + w2 / w12) / src, t0 = (c - 1.0) / src, t3 = (c + 2.0) / src;\n"
    "  vec3 r = (T(vec2(t0.x, t0.y)) * w0.x + T(vec2(t12.x, t0.y)) * w12.x + T(vec2(t3.x, t0.y)) * w3.x) * w0.y\n"
    "         + (T(vec2(t0.x, t12.y)) * w0.x + T(vec2(t12.x, t12.y)) * w12.x + T(vec2(t3.x, t12.y)) * w3.x) * w12.y\n"
    "         + (T(vec2(t0.x, t3.y)) * w0.x + T(vec2(t12.x, t3.y)) * w12.x + T(vec2(t3.x, t3.y)) * w3.x) * w3.y;\n"
    "  if (down > 0.5) {  /* a larger source than the output: the four taps around the pixel average it */\n"
    "    vec2 h = 0.5 / src * down;\n"
    "    r = 0.25 * (T(uv + vec2(-h.x, -h.y)) + T(uv + vec2(h.x, -h.y)) + T(uv + vec2(-h.x, h.y)) + T(uv + vec2(h.x, h.y)));\n"
    "  }\n"
    "  gl_FragColor = vec4(clamp(r, 0.0, 1.0), 1.0);\n"
    "}\n";

/* contrast adaptive sharpening (AMD FidelityFX CAS, the plain form): the more headroom a pixel has, the more it is sharpened */
static const char *FS_CAS =
    "precision highp float;\n"
    "uniform sampler2D tex; uniform vec2 px; uniform float sharp; varying vec2 uv;\n"
    "void main() {\n"
    "  vec3 b = texture2D(tex, uv + vec2(0.0, -px.y)).rgb, d = texture2D(tex, uv + vec2(-px.x, 0.0)).rgb, e = texture2D(tex, uv).rgb;\n"
    "  vec3 f = texture2D(tex, uv + vec2(px.x, 0.0)).rgb, h = texture2D(tex, uv + vec2(0.0, px.y)).rgb;\n"
    "  vec3 mn = min(min(min(d, e), min(f, b)), h), mx = max(max(max(d, e), max(f, b)), h);\n"
    "  vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, vec3(0.0001)), 0.0, 1.0));\n"
    "  float peak = -1.0 / mix(8.0, 5.0, sharp);\n"
    "  vec3 w = amp * peak;\n"
    "  gl_FragColor = vec4(clamp((b * w + d * w + f * w + h * w + e) / (1.0 + 4.0 * w), 0.0, 1.0), 1.0);\n"
    "}\n";

static GLuint compile(GLenum type, const char *src)
{
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(s, sizeof log, NULL, log);
        fprintf(stderr, "[sharpen] shader: %s\n", log);
        glDeleteShader(s);
        return 0;
    }
    return s;
}

static GLuint program(const char *fs)
{
    GLuint v = compile(GL_VERTEX_SHADER, VS), f = compile(GL_FRAGMENT_SHADER, fs), p = 0;
    if (v && f) {
        p = glCreateProgram();
        glAttachShader(p, v);
        glAttachShader(p, f);
        glBindAttribLocation(p, 0, "p");
        glLinkProgram(p);
        GLint ok = 0;
        glGetProgramiv(p, GL_LINK_STATUS, &ok);
        if (!ok) { glDeleteProgram(p); p = 0; }
    }
    if (v) glDeleteShader(v);
    if (f) glDeleteShader(f);
    return p;
}

struct sharpen *sharpen_open(void)
{
    struct sharpen *s = calloc(1, sizeof *s);
    if (s) { s->drm_fd = -1; for (int i = 0; i < SLOTS; i++) s->out[i].fd = -1; }
    return s;
}

static const EGLint kCtx[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };

/* the device's context, made current; 0 on failure */
static int device_open(struct sharpen *s, const char *node)
{
    int fd = open(node, O_RDWR | O_CLOEXEC);
    if (fd < 0) return 0;
    struct gbm_device *gbm = gbm_create_device(fd);
    PFNEGLGETPLATFORMDISPLAYEXTPROC get_display = (PFNEGLGETPLATFORMDISPLAYEXTPROC)eglGetProcAddress("eglGetPlatformDisplayEXT");
    EGLDisplay dpy = gbm && get_display ? get_display(EGL_PLATFORM_GBM_KHR, gbm, NULL) : EGL_NO_DISPLAY;
    EGLint major, minor;
    if (dpy == EGL_NO_DISPLAY || !eglInitialize(dpy, &major, &minor)) goto fail;
    const char *ext = eglQueryString(dpy, EGL_EXTENSIONS);
    if (!ext || !strstr(ext, "EGL_EXT_image_dma_buf_import") || !strstr(ext, "EGL_KHR_surfaceless_context")) goto fail_term;
    if (!eglBindAPI(EGL_OPENGL_ES_API)) goto fail_term;
    EGLContext ctx = eglCreateContext(dpy, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT, kCtx);
    if (ctx == EGL_NO_CONTEXT || !eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx)) goto fail_term;
    const char *gl = (const char *)glGetString(GL_EXTENSIONS);
    if (!gl || !strstr(gl, "GL_OES_EGL_image_external") || !strstr(gl, "GL_OES_EGL_image")) { eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT); eglDestroyContext(dpy, ctx); goto fail_term; }
    s->drm_fd = fd; s->gbm = gbm; s->dpy = dpy; s->ctx = ctx;
    s->create_image = (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress("eglCreateImageKHR");
    s->destroy_image = (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR");
    s->image_target = (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress("glEGLImageTargetTexture2DOES");
    if (!s->create_image || !s->destroy_image || !s->image_target) { s->ctx = EGL_NO_CONTEXT; goto fail_term; }
    return 1;
fail_term:
    eglTerminate(dpy);
fail:
    if (gbm) gbm_device_destroy(gbm);
    close(fd);
    return 0;
}

static EGLImage make_image(struct sharpen *s, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod)
{
    static const EGLint FD[] = { EGL_DMA_BUF_PLANE0_FD_EXT, EGL_DMA_BUF_PLANE1_FD_EXT, EGL_DMA_BUF_PLANE2_FD_EXT, EGL_DMA_BUF_PLANE3_FD_EXT };
    static const EGLint OFF[] = { EGL_DMA_BUF_PLANE0_OFFSET_EXT, EGL_DMA_BUF_PLANE1_OFFSET_EXT, EGL_DMA_BUF_PLANE2_OFFSET_EXT, EGL_DMA_BUF_PLANE3_OFFSET_EXT };
    static const EGLint PIT[] = { EGL_DMA_BUF_PLANE0_PITCH_EXT, EGL_DMA_BUF_PLANE1_PITCH_EXT, EGL_DMA_BUF_PLANE2_PITCH_EXT, EGL_DMA_BUF_PLANE3_PITCH_EXT };
    static const EGLint ML[] = { EGL_DMA_BUF_PLANE0_MODIFIER_LO_EXT, EGL_DMA_BUF_PLANE1_MODIFIER_LO_EXT, EGL_DMA_BUF_PLANE2_MODIFIER_LO_EXT, EGL_DMA_BUF_PLANE3_MODIFIER_LO_EXT };
    static const EGLint MH[] = { EGL_DMA_BUF_PLANE0_MODIFIER_HI_EXT, EGL_DMA_BUF_PLANE1_MODIFIER_HI_EXT, EGL_DMA_BUF_PLANE2_MODIFIER_HI_EXT, EGL_DMA_BUF_PLANE3_MODIFIER_HI_EXT };
    EGLint a[64];
    int i = 0;
    a[i++] = EGL_WIDTH; a[i++] = w; a[i++] = EGL_HEIGHT; a[i++] = h; a[i++] = EGL_LINUX_DRM_FOURCC_EXT; a[i++] = (EGLint)fourcc;
    int with_mod = mod != 0x00ffffffffffffffull;  /* DRM_FORMAT_MOD_INVALID: the implicit one, no attributes */
    for (int p = 0; p < n && p < 4; p++) {
        a[i++] = FD[p]; a[i++] = fds[p]; a[i++] = OFF[p]; a[i++] = (EGLint)offsets[p]; a[i++] = PIT[p]; a[i++] = (EGLint)strides[p];
        if (with_mod) { a[i++] = ML[p]; a[i++] = (EGLint)(mod & 0xffffffff); a[i++] = MH[p]; a[i++] = (EGLint)(mod >> 32); }
    }
    a[i++] = EGL_NONE;
    return s->create_image(s->dpy, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, NULL, a);
}

static void bind_external(struct sharpen *s, GLuint tex, EGLImage image)
{
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, tex);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    s->image_target(GL_TEXTURE_EXTERNAL_OES, (GLeglImageOES)image);
}

/* The slow part (device probe with a sample buffer, context, shaders), callable from a helper thread; the context is left unbound, the next call
 * on any thread binds it. */
int sharpen_prepare(struct sharpen *s, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod)
{
    if (!s) return -1;
    if (s->ready) return 0;
    DIR *d = opendir("/dev/dri");
    struct dirent *e;
    while (d && (e = readdir(d)) && !s->ready) {
        if (strncmp(e->d_name, "renderD", 7)) continue;
        char path[300];
        snprintf(path, sizeof path, "/dev/dri/%s", e->d_name);
        if (!device_open(s, path)) continue;
        EGLImage probe = make_image(s, fourcc, w, h, n, fds, strides, offsets, mod);
        if (probe == EGL_NO_IMAGE_KHR) {  /* another GPU's buffer: next node */
            eglMakeCurrent(s->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglDestroyContext(s->dpy, s->ctx); eglTerminate(s->dpy); gbm_device_destroy(s->gbm); close(s->drm_fd);
            s->drm_fd = -1; s->gbm = NULL; s->dpy = EGL_NO_DISPLAY;
            continue;
        }
        s->destroy_image(s->dpy, probe);
        s->up_prog = program(FS_UP);
        s->cas_prog = program(FS_CAS);
        if (!s->up_prog || !s->cas_prog) { if (d) closedir(d); return -1; }
        static const GLfloat tri[] = { -1, -1, 3, -1, -1, 3 };
        glGenBuffers(1, &s->vbo);
        glBindBuffer(GL_ARRAY_BUFFER, s->vbo);
        glBufferData(GL_ARRAY_BUFFER, sizeof tri, tri, GL_STATIC_DRAW);
        glFinish();
        eglMakeCurrent(s->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);  /* unbound: the thread that renders binds it */
        s->ready = 1;
        fprintf(stderr, "[sharpen] using %s\n", path);
    }
    if (d) closedir(d);
    return s->ready ? 0 : -1;
}

int sharpen_import(struct sharpen *s, int buf, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod)
{
    if (!s || buf < 0 || buf >= BUFS || n < 1) return -1;
    if (!s->ready && sharpen_prepare(s, fourcc, w, h, n, fds, strides, offsets, mod) < 0) return -1;
    eglMakeCurrent(s->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, s->ctx);
    sharpen_forget(s, buf);
    EGLImage image = make_image(s, fourcc, w, h, n, fds, strides, offsets, mod);
    if (image == EGL_NO_IMAGE_KHR) return -1;
    s->in[buf].image = image; s->in[buf].w = w; s->in[buf].h = h; s->in[buf].fourcc = fourcc;
    glGenTextures(1, &s->in[buf].tex);
    bind_external(s, s->in[buf].tex, image);
    return 0;
}

void sharpen_forget(struct sharpen *s, int buf)
{
    if (!s || !s->ready || buf < 0 || buf >= BUFS || !s->in[buf].image) return;
    eglMakeCurrent(s->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, s->ctx);
    glDeleteTextures(1, &s->in[buf].tex);
    s->destroy_image(s->dpy, s->in[buf].image);
    memset(&s->in[buf], 0, sizeof s->in[buf]);
}

static void slot_free(struct sharpen *s, struct slot *o)
{
    if (o->fbo) glDeleteFramebuffers(1, &o->fbo);
    if (o->tex) glDeleteTextures(1, &o->tex);
    if (o->image) s->destroy_image(s->dpy, o->image);
    if (o->fd >= 0) close(o->fd);
    if (o->bo) gbm_bo_destroy(o->bo);
    memset(o, 0, sizeof *o);
    o->fd = -1;
}

void sharpen_accept(struct sharpen *s, const uint64_t *mods, int n)
{
    if (!s) return;
    s->accept_n = n > 64 ? 64 : n < 0 ? 0 : n;
    for (int i = 0; i < s->accept_n; i++) s->accept[i] = mods[i];
}

static int slot_finish(struct sharpen *s, struct slot *o, int w, int h)
{
    uint32_t off = gbm_bo_get_offset(o->bo, 0);
    o->fd = gbm_bo_get_fd(o->bo);
    o->stride = gbm_bo_get_stride(o->bo);
    o->modifier = gbm_bo_get_modifier(o->bo);
    if (o->fd < 0) return -1;
    o->image = make_image(s, DRM_FORMAT_XRGB8888, w, h, 1, &o->fd, &o->stride, &off, o->modifier);
    if (o->image == EGL_NO_IMAGE_KHR) { o->image = NULL; return -1; }
    glGenTextures(1, &o->tex);
    glBindTexture(GL_TEXTURE_2D, o->tex);
    s->image_target(GL_TEXTURE_2D, (GLeglImageOES)o->image);
    glGenFramebuffers(1, &o->fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, o->fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, o->tex, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return -1;
    o->w = w; o->h = h;
    return 0;
}

static int slot_make(struct sharpen *s, struct slot *o, int w, int h)
{
    /* a modifier the compositor listed (the driver picks one of them), else LINEAR: the shell knows it takes those */
    slot_free(s, o);
    if (s->accept_n > 0) {
        o->bo = gbm_bo_create_with_modifiers2(s->gbm, (uint32_t)w, (uint32_t)h, GBM_FORMAT_XRGB8888, s->accept, (unsigned)s->accept_n, GBM_BO_USE_RENDERING);
        if (o->bo && slot_finish(s, o, w, h) == 0) return 0;
    }
    static const uint32_t usage[] = { GBM_BO_USE_RENDERING | GBM_BO_USE_LINEAR, GBM_BO_USE_WRITE | GBM_BO_USE_LINEAR };
    for (unsigned u = 0; u < sizeof usage / sizeof usage[0]; u++) {
        slot_free(s, o);
        o->bo = gbm_bo_create(s->gbm, (uint32_t)w, (uint32_t)h, GBM_FORMAT_XRGB8888, usage[u]);
        if (o->bo && gbm_bo_get_modifier(o->bo) == 0 && slot_finish(s, o, w, h) == 0) return 0;
    }
    return -1;
}

static void draw(void)
{
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
}

int sharpen_render(struct sharpen *s, int buf, int ow, int oh, float sharpness, int *slot, int *fresh)
{
    static int said_in, said_busy;
    if (!s || !s->ready || buf < 0 || buf >= BUFS || !s->in[buf].image || ow <= 0 || oh <= 0) {
        if (!said_in++) fprintf(stderr, "[sharpen] nothing to draw: buffer %d %s\n", buf, s && buf >= 0 && buf < BUFS && s->in[buf].image ? "ok" : "not imported");
        return -1;
    }
    eglMakeCurrent(s->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, s->ctx);
    int k = -1;
    for (int i = 0; i < SLOTS; i++) if (!s->out[i].busy) { k = i; break; }
    if (k < 0) {  /* the compositor holds all three: show the app's own buffer this once */
        if (!said_busy++) fprintf(stderr, "[sharpen] all %d output buffers are held by the compositor: the newest frame waits for a release\n", SLOTS);
        return -2;
    }
    struct slot *o = &s->out[k];
    *fresh = 0;
    if (!o->bo || o->w != ow || o->h != oh) {
        if (slot_make(s, o, ow, oh) < 0) { slot_free(s, o); fprintf(stderr, "[sharpen] no output buffer %dx%d (%d modifiers offered, gl error 0x%x)\n", ow, oh, s->accept_n, glGetError()); return -1; }
        *fresh = 1;
    }
    if (s->mid_w != ow || s->mid_h != oh) {  /* the intermediate (upscaled, unsharpened) picture */
        if (s->mid_fbo) glDeleteFramebuffers(1, &s->mid_fbo);
        if (s->mid_tex) glDeleteTextures(1, &s->mid_tex);
        glGenTextures(1, &s->mid_tex);
        glBindTexture(GL_TEXTURE_2D, s->mid_tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, ow, oh, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenFramebuffers(1, &s->mid_fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, s->mid_fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, s->mid_tex, 0);
        s->mid_w = ow; s->mid_h = oh;
    }
    glBindBuffer(GL_ARRAY_BUFFER, s->vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glDisable(GL_BLEND);
    /* pass 1: the app's buffer, resampled to the output size */
    glBindFramebuffer(GL_FRAMEBUFFER, s->mid_fbo);
    glViewport(0, 0, ow, oh);
    glUseProgram(s->up_prog);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, s->in[buf].tex);
    glUniform1i(glGetUniformLocation(s->up_prog, "tex"), 0);
    glUniform2f(glGetUniformLocation(s->up_prog, "src"), (float)s->in[buf].w, (float)s->in[buf].h);
    glUniform1f(glGetUniformLocation(s->up_prog, "down"), s->in[buf].w > ow ? (float)s->in[buf].w / (float)ow : 0.0f);
    glBindBuffer(GL_ARRAY_BUFFER, s->vbo);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    /* pass 2: sharpened into the output dmabuf */
    glBindFramebuffer(GL_FRAMEBUFFER, o->fbo);
    glViewport(0, 0, ow, oh);
    glUseProgram(s->cas_prog);
    glBindTexture(GL_TEXTURE_2D, s->mid_tex);
    glUniform1i(glGetUniformLocation(s->cas_prog, "tex"), 0);
    glUniform2f(glGetUniformLocation(s->cas_prog, "px"), 1.0f / (float)ow, 1.0f / (float)oh);
    glUniform1f(glGetUniformLocation(s->cas_prog, "sharp"), sharpness < 0 ? 0 : sharpness > 1 ? 1 : sharpness);
    draw();
    glFinish();  /* the compositor reads the dmabuf next: no explicit sync here */
    o->busy = 1;
    s->last = k;
    *slot = k;
    return 0;
}

int sharpen_slot_info(struct sharpen *s, int slot, struct sharpen_out *out)
{
    if (!s || slot < 0 || slot >= SLOTS || !s->out[slot].bo) return -1;
    struct slot *o = &s->out[slot];
    out->fd = dup(o->fd);
    out->w = o->w; out->h = o->h;
    out->fourcc = DRM_FORMAT_XRGB8888; out->stride = o->stride; out->offset = gbm_bo_get_offset(o->bo, 0); out->modifier = o->modifier;
    return out->fd < 0 ? -1 : 0;
}

void sharpen_slot_release(struct sharpen *s, int slot)
{
    if (s && slot >= 0 && slot < SLOTS) s->out[slot].busy = 0;
}

int sharpen_readback(struct sharpen *s, int slot, void *rgba)
{
    if (!s || !s->ready || slot < 0 || slot >= SLOTS || !s->out[slot].fbo) return -1;
    eglMakeCurrent(s->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, s->ctx);
    glBindFramebuffer(GL_FRAMEBUFFER, s->out[slot].fbo);
    glReadPixels(0, 0, s->out[slot].w, s->out[slot].h, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    return glGetError() == GL_NO_ERROR ? 0 : -1;
}

void sharpen_close(struct sharpen *s)
{
    if (!s) return;
    if (s->ready) {
        eglMakeCurrent(s->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, s->ctx);
        for (int i = 0; i < BUFS; i++) sharpen_forget(s, i);
        for (int i = 0; i < SLOTS; i++) slot_free(s, &s->out[i]);
        eglMakeCurrent(s->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroyContext(s->dpy, s->ctx);
        eglTerminate(s->dpy);
        gbm_device_destroy(s->gbm);
        close(s->drm_fd);
    }
    free(s);
}
