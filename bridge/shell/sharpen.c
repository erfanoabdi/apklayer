/* The upscale pass: see sharpen.h. EGL on the first render node that imports the app's dmabuf, two GLES2 passes: FSR 1's EASU upscale into an
 * intermediate texture, then its RCAS sharpening into one of the exportable dmabufs (a source larger than the output is shrunk with an area filter instead). */
#define _GNU_SOURCE
#include "sharpen.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <gbm.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "sharpen_down.h"
#include "sharpen_smaa.h"  /* struct rt, SMAA 1x */
#include "sharpen_cmaa.h"  /* CMAA2 (GLES 3.1 compute) */
#include "sharpen_temporal.h"  /* the temporal stabiliser (apklayer patch) */
#include "sharpen_nn.h"  /* the neural upscaler, waifu2x on compute (apklayer patch) */
#ifdef HAVE_SHARPEN_VK
#include "sharpen_vk.h"
#endif

#define BUFS 8
#define SLOTS 8
#define DRM_FORMAT_XRGB8888 0x34325258u

struct slot {
    struct gbm_bo *bo;
    EGLImage image;
    GLuint tex, fbo;
    int fd, w, h, busy;
    uint64_t used;  /* the render count of its last use (the least recently used free slot is drawn into next) */
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
    PFNEGLCREATESYNCKHRPROC create_sync;      /* native fences: wait for the GPU asleep in poll(), glFinish spins a CPU core on NVIDIA */
    PFNEGLDESTROYSYNCKHRPROC destroy_sync;
    PFNEGLDUPNATIVEFENCEFDANDROIDPROC dup_fence;
    struct { EGLImage image; GLuint tex; int w, h; uint32_t fourcc; } in[BUFS];
    GLuint up_prog, cas_prog, down_prog, mid_tex, mid_fbo, vbo;
    int mid_w, mid_h;
    uint64_t tick;
    struct cmaa *cmaa;                            /* CMAA2 (ultra), NULL without an ES 3.1 context (then SMAA ultra runs) */
    struct smaa *smaa;                            /* SMAA 1x (sharpen_smaa.c), NULL when its tables or shaders failed */
    GLuint up2d_prog;                             /* EASU reading a GL_TEXTURE_2D: the anti-aliased source (SMAA / CMAA2 run before the upscale) */
    struct rt sm_src;                             /* their output: the source-sized picture before EASU, or the output-sized one before RCAS */
    GLuint blit_prog;                             /* a copy (FS_BLIT), where SMAA / CMAA2 fail */
    struct tmpl *tmpl;                            /* the temporal pass, NULL where it does not build or APKLAYER_SHARPEN_TEMPORAL=off (a test knob) */
    struct rt t0;                                 /* the filter's output before SMAA / CMAA2 on it */
    int tmp_off;
    int nn_want, nn_want_env, nn_tried, nn_slow;  /* nn_slow: frames in a row over the budget; at 12 the pass is switched off for this window */
    double nn_ms;                                 /* moving average of a neural frame, ms */
    double nn_busy, nn_t0;                        /* ms spent in neural frames since nn_t0 (ms, CLOCK_MONOTONIC): the duty-cycle guard */                        /* sharpen_set_neural; the network is made at the first frame that wants it */
    struct nn *nn;                                /* the neural upscale pass (sharpen_nn.c), NULL unless APKLAYER_SHARPEN_NN=1 and ES 3.1 compute and the weights are there */
    int aa_off, aa_smaa;                          /* APKLAYER_SHARPEN_AA=off|smaa: test knobs (FSR 1 alone; SMAA ultra where CMAA2 would run), not settings */
#ifdef HAVE_SHARPEN_VK
    struct svk *vk;  /* the Vulkan backend, when it took over (sharpen_vk.h) */
#endif
    struct slot out[SLOTS];
    int last;
    uint64_t accept[64];
    int accept_n;
};

static const char *FS_BLIT =
    "precision highp float;\n"
    "uniform sampler2D tex; varying vec2 uv;\n"
    "void main() { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); }\n";
static const char *VS = "attribute vec2 p; varying vec2 uv; void main() { uv = p * 0.5 + 0.5; gl_Position = vec4(p, 0.0, 1.0); }";

#include "sharpen_fsr.h"

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

/* the same shader reading a plain 2D texture: the external-image extension line is dropped and the sampler type changed */
static GLuint program_2d(const char *fs)
{
    static const char ext[] = "#extension GL_OES_EGL_image_external : require\n";
    char *t = strdup(fs), *q;
    if (!t) return 0;
    if ((q = strstr(t, ext))) memmove(q, q + sizeof ext - 1, strlen(q + sizeof ext - 1) + 1);
    while ((q = strstr(t, "samplerExternalOES"))) { memcpy(q, "sampler2D", 9); memmove(q + 9, q + 18, strlen(q + 18) + 1); }
    GLuint g = program(t);
    free(t);
    return g;
}

struct sharpen *sharpen_open(void)
{
    struct sharpen *s = calloc(1, sizeof *s);
    if (s) { const char *t = getenv("APKLAYER_SHARPEN_TEMPORAL"); s->tmp_off = t && !strcmp(t, "off"); }
    if (s) { const char *a = getenv("APKLAYER_SHARPEN_AA"); s->aa_off = a && !strcmp(a, "off"); s->aa_smaa = a && !strcmp(a, "smaa"); s->drm_fd = -1; for (int i = 0; i < SLOTS; i++) s->out[i].fd = -1; }
    return s;
}

#ifdef HAVE_SHARPEN_VK
#define VK_OR(call) if (s && s->vk) return call
#define SHARPEN_HAS_VK(s) ((s)->vk != NULL)
#else
#define VK_OR(call)
#define SHARPEN_HAS_VK(s) 0
#endif

#ifdef HAVE_SHARPEN_VK
static int vk_start(struct sharpen *s, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod)
{
    if (svk_prepare(&s->vk, fourcc, w, h, n, fds, strides, offsets, mod) < 0) { s->vk = NULL; return -1; }
    svk_accept(s->vk, s->accept, s->accept_n);
    return 0;
}
#endif

static const EGLint kCtx[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
static const EGLint kCtx31[] = { EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 1, EGL_NONE };  /* tried first: CMAA2 needs compute; the GLES2 shaders run in it unchanged (APKLAYER_SHARPEN_ES2 skips it) */

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
    EGLContext ctx = getenv("APKLAYER_SHARPEN_ES2") ? EGL_NO_CONTEXT : eglCreateContext(dpy, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT, kCtx31);
    if (ctx != EGL_NO_CONTEXT && !eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx)) { eglDestroyContext(dpy, ctx); ctx = EGL_NO_CONTEXT; }
    if (ctx == EGL_NO_CONTEXT) ctx = eglCreateContext(dpy, EGL_NO_CONFIG_KHR, EGL_NO_CONTEXT, kCtx);
    if (ctx == EGL_NO_CONTEXT || !eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx)) goto fail_term;
    const char *gl = (const char *)glGetString(GL_EXTENSIONS);
    if (!gl || !strstr(gl, "GL_OES_EGL_image_external") || !strstr(gl, "GL_OES_EGL_image")) { eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT); eglDestroyContext(dpy, ctx); goto fail_term; }
    s->drm_fd = fd; s->gbm = gbm; s->dpy = dpy; s->ctx = ctx;
    s->create_image = (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress("eglCreateImageKHR");
    s->destroy_image = (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR");
    s->image_target = (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress("glEGLImageTargetTexture2DOES");
    if (!s->create_image || !s->destroy_image || !s->image_target) { s->ctx = EGL_NO_CONTEXT; goto fail_term; }
    if (strstr(ext, "EGL_KHR_fence_sync") && strstr(ext, "EGL_ANDROID_native_fence_sync")) {
        s->create_sync = (PFNEGLCREATESYNCKHRPROC)eglGetProcAddress("eglCreateSyncKHR");
        s->destroy_sync = (PFNEGLDESTROYSYNCKHRPROC)eglGetProcAddress("eglDestroySyncKHR");
        s->dup_fence = (PFNEGLDUPNATIVEFENCEFDANDROIDPROC)eglGetProcAddress("eglDupNativeFenceFDANDROID");
    }
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
#ifdef HAVE_SHARPEN_VK
    if (s->vk) return 0;
    const char *backend = getenv("APKLAYER_SHARPEN_BACKEND");  /* gles, vulkan; else GLES first and Vulkan where no EGL device takes the buffers (Vulkan flickered in PGR on KWin/NVIDIA, the owner's test 2026-11-22: GLES does not) */
    int only_gl = backend && !strcmp(backend, "gles"), only_vk = backend && !strcmp(backend, "vulkan");
    if (only_vk) return vk_start(s, fourcc, w, h, n, fds, strides, offsets, mod);
#endif
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
        s->up2d_prog = program_2d(FS_UP);
        s->smaa = s->up2d_prog ? smaa_create(program) : NULL;
        s->cmaa = s->smaa ? cmaa_create(program) : NULL;
        fprintf(stderr, "[sharpen] %s; SMAA %s, CMAA2 %s\n", glGetString(GL_VERSION), s->smaa ? "ready" : "unavailable", s->cmaa ? "ready" : "unavailable (needs OpenGL ES 3.1 compute)");
        s->blit_prog = program(FS_BLIT);
        s->tmpl = s->tmp_off ? NULL : tmpl_create(program);
        { const char *e = getenv("APKLAYER_SHARPEN_NN"); s->nn_want = s->nn_want_env = e && !strcmp(e, "1"); }  /* a test knob; the setting arrives through sharpen_set_neural */
        s->down_prog = program(FS_DOWN);  /* optional: without it a larger source is shrunk by EASU's own `down` branch */
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
#ifdef HAVE_SHARPEN_VK
    if (!s->ready && !only_gl) return vk_start(s, fourcc, w, h, n, fds, strides, offsets, mod);
#endif
    return s->ready ? 0 : -1;
}

int sharpen_import(struct sharpen *s, int buf, uint32_t fourcc, int w, int h, int n, const int *fds, const uint32_t *strides, const uint32_t *offsets, uint64_t mod)
{
    if (!s || buf < 0 || buf >= BUFS || n < 1) return -1;
    if (!s->ready && !SHARPEN_HAS_VK(s) && sharpen_prepare(s, fourcc, w, h, n, fds, strides, offsets, mod) < 0) return -1;
    VK_OR(svk_import(s->vk, buf, fourcc, w, h, n, fds, strides, offsets, mod));
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
#ifdef HAVE_SHARPEN_VK
    if (s && s->vk) { svk_forget(s->vk, buf); return; }
#endif
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

/* The GPU work is done when this returns; the thread sleeps meanwhile (a native fence fd in poll) where the driver has them. */
static void gpu_wait(struct sharpen *s)
{
    if (s->create_sync && s->destroy_sync && s->dup_fence) {
        EGLSyncKHR sy = s->create_sync(s->dpy, EGL_SYNC_NATIVE_FENCE_ANDROID, NULL);
        if (sy != EGL_NO_SYNC_KHR) {
            glFlush();
            int fd = s->dup_fence(s->dpy, sy);
            s->destroy_sync(s->dpy, sy);
            if (fd >= 0) {
                struct pollfd pf = { fd, POLLIN, 0 };
                int r;
                do r = poll(&pf, 1, 1000); while (r < 0 && errno == EINTR);
                close(fd);
                if (r > 0) return;
            }
        }
    }
    glFinish();
}

static void draw(void)
{
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
}

/* The post pass (GLES backend): SMAA / CMAA2 on the filter's output (the downscale path: no place before an upscale), t0 into the output. */
static GLuint rt_ensure(struct rt *r, int w, int h)
{
    if (r->w != w || r->h != h) {
        if (r->fbo) glDeleteFramebuffers(1, &r->fbo);
        if (r->tex) glDeleteTextures(1, &r->tex);
        r->fbo = r->tex = 0;
        glGenTextures(1, &r->tex);
        glBindTexture(GL_TEXTURE_2D, r->tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenFramebuffers(1, &r->fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, r->fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, r->tex, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) { r->w = r->h = 0; return 0; }
        r->w = w; r->h = h;
    }
    return r->fbo;
}

static void post_draw(struct sharpen *s, GLuint prog, GLuint fbo, int w, int h)
{
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glViewport(0, 0, w, h);
    glUseProgram(prog);
    glBindBuffer(GL_ARRAY_BUFFER, s->vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    draw();
}

static void post_tex(GLuint prog, int unit, const char *name, GLuint tex)
{
    glActiveTexture(GL_TEXTURE0 + unit);
    glBindTexture(GL_TEXTURE_2D, tex);
    glUseProgram(prog);
    glUniform1i(glGetUniformLocation(prog, name), unit);
    glActiveTexture(GL_TEXTURE0);
}

/* CMAA2 (aa 2) or SMAA (aa 1, the fallback), both ultra, on src into dst, w x h; 0: done */
static int aa_run(struct sharpen *s, int ext, GLuint src, int w, int h, int aa, float ratio, GLuint dst)
{
    static int said;
    if (aa == 2 && s->cmaa) {
        if (cmaa_run(s->cmaa, ext, src, w, h, ratio, dst, s->vbo) == 0) return 0;
        if (!said++) fprintf(stderr, "[sharpen] CMAA2 failed (gl error 0x%x): SMAA ultra runs\n", glGetError());
    }
    return smaa_run(s->smaa, rt_ensure, ext, src, w, h, ratio, dst, s->vbo);
}

static void post_run(struct sharpen *s, GLuint out_fbo, int w, int h, float ratio, int aa)
{
    if (aa_run(s, 0, s->t0.tex, w, h, aa, ratio, out_fbo) < 0) { post_tex(s->blit_prog, 0, "tex", s->t0.tex); post_draw(s, s->blit_prog, out_fbo, w, h); }
}

void sharpen_set_neural(struct sharpen *s, int on)
{
    if (s) s->nn_want = on || s->nn_want_env;
}

/* the weights: $APKLAYER_NN_WEIGHTS, the installed copy, or beside the shell in a build tree */
static const char *nn_weights(char *buf, size_t n)
{
    static const char inst[] = "/usr/share/apklayer/bridge/native/nn/student.bin";
    char exe[512];
    ssize_t r = readlink("/proc/self/exe", exe, sizeof exe - 1);
    if (r > 0) {
        exe[r] = 0;
        char *sl = strrchr(exe, '/');
        if (sl) { *sl = 0; snprintf(buf, n, "%s/../../../share/apklayer/bridge/native/nn/student.bin", exe); if (access(buf, R_OK) == 0) return buf; snprintf(buf, n, "%s/../../bridge/native/nn/student.bin", exe); if (access(buf, R_OK) == 0) return buf; }
    }
    return inst;
}

int sharpen_render(struct sharpen *s, int buf, int ow, int oh, float sharpness, int *slot, int *fresh)
{
    static int said_in, said_busy;
#ifdef HAVE_SHARPEN_VK
    if (s && s->vk) {
        static int said_vkaa;
        if (!s->aa_off && !said_vkaa++) fprintf(stderr, "[sharpen] the Vulkan backend has no anti-aliasing (SMAA and CMAA2 are GLES only): none is applied\n");
        int rc = svk_render(s->vk, buf, ow, oh, sharpness, slot, fresh);
        if (rc == -2 && !said_busy++) fprintf(stderr, "[sharpen] all %d output buffers are held by the compositor: the newest frame waits for a release\n", SVK_SLOTS);
        return rc;
    }
#endif
    if (!s || !s->ready || buf < 0 || buf >= BUFS || !s->in[buf].image || ow <= 0 || oh <= 0) {
        if (!said_in++) fprintf(stderr, "[sharpen] nothing to draw: buffer %d %s\n", buf, s && buf >= 0 && buf < BUFS && s->in[buf].image ? "ok" : "not imported");
        return -1;
    }
    eglMakeCurrent(s->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, s->ctx);
    int k = -1;
    for (int i = 0; i < SLOTS; i++) if (!s->out[i].busy && (k < 0 || s->out[i].used < s->out[k].used)) k = i;  /* not the slot just released: the compositor may still read it */
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
    int up = ow > s->in[buf].w && oh > s->in[buf].h;  /* an upscale: SMAA / CMAA2 run on the app's buffer before EASU */
    if (s->nn_want && !s->nn && !s->nn_tried) {
        char pb[600];
        s->nn_tried = 1;
        struct timespec c0, c1;
        clock_gettime(CLOCK_MONOTONIC, &c0);
        s->nn = nn_create(program, nn_weights(pb, sizeof pb));
        clock_gettime(CLOCK_MONOTONIC, &c1);
        if (getenv("APKLAYER_RUNTIME_DEBUG")) fprintf(stderr, "[sharpen] neural network made in %.0f ms\n", (double)(c1.tv_sec - c0.tv_sec) * 1000 + (double)(c1.tv_nsec - c0.tv_nsec) / 1e6);
        if (!s->nn) fprintf(stderr, "[sharpen] neural upscaler unavailable: FSR 1 runs\n");
    }
    if (s->nn && s->nn_want && up && s->nn_slow < 12) {
        static int said_nn_on;
        if (!said_nn_on++) fprintf(stderr, "[sharpen] neural pass: %dx%d -> %dx%d\n", s->in[buf].w, s->in[buf].h, ow, oh);  /* the neural pass: the network's 2x picture resampled straight into the output (no anti-aliasing, RCAS or temporal pass) */
        struct timespec t0, t1;
        clock_gettime(CLOCK_MONOTONIC, &t0);
        if (nn_run(s->nn, rt_ensure, s->in[buf].tex, s->in[buf].w, s->in[buf].h, o->fbo, ow, oh, s->vbo) == 0) {
            gpu_wait(s);
            clock_gettime(CLOCK_MONOTONIC, &t1);
            static int n_nn; static double sum_nn;
            double ms = (double)(t1.tv_sec - t0.tv_sec) * 1000 + (double)(t1.tv_nsec - t0.tv_nsec) / 1e6;
            sum_nn += ms;
            /* the shell's main thread waits for every frame: a pass that takes more than 25 ms (a big window, a GPU busy with a game) would freeze every window, so it ends for this one */
            s->nn_ms = s->nn_ms ? s->nn_ms * 0.8 + ms * 0.2 : ms;
            s->nn_slow = s->nn_ms > 25.0 ? s->nn_slow + 1 : 0;
            if (s->nn_slow == 12) fprintf(stderr, "[sharpen] the neural pass takes %.0f ms per frame at %dx%d -> %dx%d: FSR 1 runs for this window\n", s->nn_ms, s->in[buf].w, s->in[buf].h, ow, oh);
            /* the duty cycle: an app that draws at the display's rate (180 Hz here) keeps the single-threaded shell in the pass for most of every second, and every window's input and frames wait behind it: over 60 % of a second ends the pass for this window */
            double now = (double)t1.tv_sec * 1000 + (double)t1.tv_nsec / 1e6;
            if (s->nn_t0 == 0) s->nn_t0 = now;
            s->nn_busy += ms;
            if (now - s->nn_t0 >= 1000.0) {
                if (s->nn_slow < 12 && s->nn_busy > 0.6 * (now - s->nn_t0)) {
                    s->nn_slow = 12;
                    fprintf(stderr, "[sharpen] the neural pass kept the shell busy %.0f%% of the last second (%.1f ms per frame, %dx%d -> %dx%d): FSR 1 runs for this window\n", 100.0 * s->nn_busy / (now - s->nn_t0), s->nn_ms, s->in[buf].w, s->in[buf].h, ow, oh);
                }
                s->nn_t0 = now; s->nn_busy = 0;
            }
            if (getenv("APKLAYER_RUNTIME_DEBUG") && n_nn < 10) fprintf(stderr, "[sharpen] neural frame %d: %.1f ms\n", n_nn, (double)(t1.tv_sec - t0.tv_sec) * 1000 + (double)(t1.tv_nsec - t0.tv_nsec) / 1e6);
            if (getenv("APKLAYER_RUNTIME_DEBUG") && ++n_nn % 30 == 0) { fprintf(stderr, "[sharpen] neural pass: %.1f ms per frame (last 30)\n", sum_nn / 30); sum_nn = 0; }
            o->busy = 1;
            o->used = ++s->tick;
            s->last = k;
            *slot = k;
            return 0;
        }
        static int said_nn;
        if (!said_nn++) fprintf(stderr, "[sharpen] the neural pass failed (gl error 0x%x): FSR 1 runs\n", glGetError());
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    int aa = s->aa_off ? 0 : s->cmaa && !s->aa_smaa ? 2 : s->smaa ? 1 : 0;
    static int said_aa, said_cmaa;
    if (!s->aa_off && !s->cmaa && s->smaa && !said_cmaa++) fprintf(stderr, "[sharpen] CMAA2 needs OpenGL ES 3.1 compute, not available here: SMAA ultra runs\n");
    if (!s->aa_off && !s->smaa && !said_aa++) fprintf(stderr, "[sharpen] SMAA unavailable on this GPU: no anti-aliasing\n");
    GLuint dst = aa ? rt_ensure(&s->t0, ow, oh) : o->fbo;
    if (aa && !dst) { aa = 0; dst = o->fbo; }
    if (s->down_prog && (s->in[buf].w > ow || s->in[buf].h > oh)) {  /* render scale above 100: the larger picture is shrunk with the Lanczos 2 area filter, one pass straight into dst */
        glBindFramebuffer(GL_FRAMEBUFFER, dst);
        glViewport(0, 0, ow, oh);
        glUseProgram(s->down_prog);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, s->in[buf].tex);
        glUniform1i(glGetUniformLocation(s->down_prog, "tex"), 0);
        glUniform2f(glGetUniformLocation(s->down_prog, "src"), (float)s->in[buf].w, (float)s->in[buf].h);
        glUniform2f(glGetUniformLocation(s->down_prog, "scl"), (float)ow / (float)s->in[buf].w, (float)oh / (float)s->in[buf].h);
        glBindBuffer(GL_ARRAY_BUFFER, s->vbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
        draw();
        if (aa) post_run(s, o->fbo, ow, oh, (float)ow / (float)s->in[buf].w, aa);
        gpu_wait(s);
        o->busy = 1;
        o->used = ++s->tick;
        s->last = k;
        *slot = k;
        return 0;
    }
    /* CMAA2 / SMAA before FSR 1 (AMD: anti-alias the source, then upscale): on the app's buffer when it is smaller than the output (EASU then reads the result),
     * else on the shrunk picture before RCAS */
    int inl = aa, aa4 = aa, pre = 0;
    GLuint e_tex = s->in[buf].tex, e_prog = s->up_prog;
    GLenum e_tgt = GL_TEXTURE_EXTERNAL_OES;
    if (inl) {
        aa = 0;
        dst = o->fbo;
        if (up && rt_ensure(&s->sm_src, s->in[buf].w, s->in[buf].h) && aa_run(s, 1, s->in[buf].tex, s->in[buf].w, s->in[buf].h, aa4, (float)ow / (float)s->in[buf].w, s->sm_src.fbo) == 0) {
            e_tex = s->sm_src.tex; e_prog = s->up2d_prog; e_tgt = GL_TEXTURE_2D; inl = 0; pre = 1;
        }
    }
    /* pass 1: the app's buffer, resampled to the output size */
    glBindFramebuffer(GL_FRAMEBUFFER, s->mid_fbo);
    glViewport(0, 0, ow, oh);
    glUseProgram(e_prog);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(e_tgt, e_tex);
    glUniform1i(glGetUniformLocation(e_prog, "tex"), 0);
    glUniform2f(glGetUniformLocation(e_prog, "src"), (float)s->in[buf].w, (float)s->in[buf].h);
    glUniform1f(glGetUniformLocation(e_prog, "down"), !pre && s->in[buf].w > ow ? (float)s->in[buf].w / (float)ow : 0.0f);
    glBindBuffer(GL_ARRAY_BUFFER, s->vbo);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    GLuint cas_in = s->mid_tex;
    if (inl) {
        glBindTexture(GL_TEXTURE_2D, s->mid_tex);  /* SMAA's blending reads with the bilinear filter */
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        if (rt_ensure(&s->sm_src, ow, oh) && aa_run(s, 0, s->mid_tex, ow, oh, aa4, 1.0f, s->sm_src.fbo) == 0) cas_in = s->sm_src.tex;
        glBindTexture(GL_TEXTURE_2D, s->mid_tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }
    /* pass 2: sharpened into the output dmabuf */
    glBindFramebuffer(GL_FRAMEBUFFER, dst);
    glViewport(0, 0, ow, oh);
    glUseProgram(s->cas_prog);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, cas_in);
    glUniform1i(glGetUniformLocation(s->cas_prog, "tex"), 0);
    glUniform2f(glGetUniformLocation(s->cas_prog, "px"), 1.0f / (float)ow, 1.0f / (float)oh);
    glUniform1f(glGetUniformLocation(s->cas_prog, "sharp"), sharpness < 0 ? 0 : sharpness > 1 ? 1 : sharpness);
    draw();
    if (aa) post_run(s, o->fbo, ow, oh, (float)ow / (float)s->in[buf].w, aa);
    if (s->in[buf].w <= ow && s->in[buf].h <= oh)  /* no temporal pass on a downscale: the area filter is stable by itself and the history would only blur it (tests/vm/shimmer.py, 150 %) */
        tmpl_run(s->tmpl, rt_ensure, s->vbo, o->tex, o->fbo, ow, oh, s->in[buf].tex, s->in[buf].w, s->in[buf].h, sharpness);
    gpu_wait(s);  /* the compositor reads the dmabuf next: no explicit sync here */
    o->busy = 1;
    o->used = ++s->tick;
    s->last = k;
    *slot = k;
    return 0;
}

int sharpen_slot_info(struct sharpen *s, int slot, struct sharpen_out *out)
{
    VK_OR(svk_slot_info(s->vk, slot, out));
    if (!s || slot < 0 || slot >= SLOTS || !s->out[slot].bo) return -1;
    struct slot *o = &s->out[slot];
    out->fd = dup(o->fd);
    out->w = o->w; out->h = o->h;
    out->fourcc = DRM_FORMAT_XRGB8888; out->stride = o->stride; out->offset = gbm_bo_get_offset(o->bo, 0); out->modifier = o->modifier;
    return out->fd < 0 ? -1 : 0;
}

void sharpen_slot_release(struct sharpen *s, int slot)
{
#ifdef HAVE_SHARPEN_VK
    if (s && s->vk) { svk_slot_release(s->vk, slot); return; }
#endif
    if (s && slot >= 0 && slot < SLOTS) s->out[slot].busy = 0;
}

int sharpen_readback(struct sharpen *s, int slot, void *rgba)
{
    VK_OR(svk_readback(s->vk, slot, rgba));
    if (!s || !s->ready || slot < 0 || slot >= SLOTS || !s->out[slot].fbo) return -1;
    eglMakeCurrent(s->dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, s->ctx);
    glBindFramebuffer(GL_FRAMEBUFFER, s->out[slot].fbo);
    glReadPixels(0, 0, s->out[slot].w, s->out[slot].h, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    return glGetError() == GL_NO_ERROR ? 0 : -1;
}

void sharpen_close(struct sharpen *s)
{
    if (!s) return;
#ifdef HAVE_SHARPEN_VK
    svk_close(s->vk);
#endif
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
    free(s->smaa);
    cmaa_free(s->cmaa);
    nn_free(s->nn);
    tmpl_free(s->tmpl);
    free(s);
}
