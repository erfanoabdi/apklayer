/* CMAA2 for the render filter: see sharpen_cmaa.h. The reference's quality presets (CMAA2_STATIC_QUALITY_PRESET) are the edge detection threshold only: LOW 0.15, MEDIUM 0.10,
 * HIGH 0.07 (the reference default), ULTRA 0.05; "extra sharpness" (CMAA2_EXTRA_SHARPNESS) stays off, as in the reference default. The working picture is an RGBA8 copy of the
 * source (the kernels read it with texelFetch and the last one writes the blended pixels into it), edges as R32UI (one word per pixel), the blend lists as storage buffers. */
#include "sharpen_cmaa.h"

#include <EGL/egl.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl31.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "sharpen_cmaa_cs.h"

static const float THRESHOLD[4] = { 0.15f, 0.10f, 0.07f, 0.05f };
enum { K_EDGES, K_ARGS, K_CAND, K_APPLY };
static const char *const KNAME[4] = { "K_EDGES", "K_ARGS", "K_CAND", "K_APPLY" };

struct cmaa {
    GLuint (*prog)(const char *fs);
    GLuint edges[4], args, cand, apply;       /* compute programs (edges: by level) */
    GLuint blit_ext, blit_2d;
    GLuint ssbo[6];                           /* control, candidates, blend locations, blend items, indirect arguments */
    GLuint edge_tex, work_tex, work_fbo;
    int w, h;
    void (*dispatch)(GLuint, GLuint, GLuint);
    void (*dispatch_indirect)(GLintptr);
    void (*bind_image)(GLuint, GLuint, GLint, GLboolean, GLint, GLenum, GLenum);
    void (*barrier)(GLbitfield);
    void (*tex_storage)(GLenum, GLsizei, GLenum, GLsizei, GLsizei);
    void (*bind_base)(GLenum, GLuint, GLuint);
};

static GLuint compute_program(int kernel, int level)
{
    size_t n = strlen(CMAA_CS) + 256;
    char *t = malloc(n);
    if (!t) return 0;
    snprintf(t, n, "#version 310 es\n#define CM_EDGE_THRESHOLD %.3f\n#define CM_EXTRA_SHARPNESS 0\n#define %s 1\n%s", THRESHOLD[level], KNAME[kernel], CMAA_CS);
    GLuint s = glCreateShader(GL_COMPUTE_SHADER), p = 0;
    const char *src = t;
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(s, sizeof log, NULL, log);
        fprintf(stderr, "[sharpen] CMAA2 %s: %s\n", KNAME[kernel], log);
    } else {
        p = glCreateProgram();
        glAttachShader(p, s);
        glLinkProgram(p);
        glGetProgramiv(p, GL_LINK_STATUS, &ok);
        if (!ok) { glDeleteProgram(p); p = 0; }
    }
    glDeleteShader(s);
    free(t);
    return p;
}

struct cmaa *cmaa_create(GLuint (*prog)(const char *fs))
{
    const char *ver = (const char *)glGetString(GL_VERSION);
    int major = 0, minor = 0;
    if (!ver || sscanf(ver, "OpenGL ES %d.%d", &major, &minor) != 2 || major * 10 + minor < 31) return NULL;
    struct cmaa *c = calloc(1, sizeof *c);
    if (!c) return NULL;
    c->prog = prog;
    c->dispatch = (void *)eglGetProcAddress("glDispatchCompute");
    c->dispatch_indirect = (void *)eglGetProcAddress("glDispatchComputeIndirect");
    c->bind_image = (void *)eglGetProcAddress("glBindImageTexture");
    c->barrier = (void *)eglGetProcAddress("glMemoryBarrier");
    c->tex_storage = (void *)eglGetProcAddress("glTexStorage2D");
    c->bind_base = (void *)eglGetProcAddress("glBindBufferBase");
    c->blit_ext = prog("#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES tex; varying vec2 uv; void main() { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); }");
    c->blit_2d = prog("precision mediump float; uniform sampler2D tex; varying vec2 uv; void main() { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); }");
    c->args = compute_program(K_ARGS, 0);
    c->cand = compute_program(K_CAND, 0);
    c->apply = compute_program(K_APPLY, 0);
    c->edges[2] = compute_program(K_EDGES, 2);  /* the default level now: a shader that does not build here is found at start, not at the first frame */
    if (!c->dispatch || !c->dispatch_indirect || !c->bind_image || !c->barrier || !c->tex_storage || !c->bind_base || !c->blit_ext || !c->blit_2d || !c->args || !c->cand || !c->apply || !c->edges[2]) {
        free(c);
        return NULL;
    }
    return c;
}

static GLuint storage_tex(struct cmaa *c, GLenum fmt, int w, int h)
{
    GLuint t;
    glGenTextures(1, &t);
    glBindTexture(GL_TEXTURE_2D, t);
    c->tex_storage(GL_TEXTURE_2D, 1, fmt, w, h);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return t;
}

static void buf_make(int i, GLuint *b, GLsizeiptr size, const void *data)
{
    if (!*b) glGenBuffers(1, b);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, *b);
    glBufferData(GL_SHADER_STORAGE_BUFFER, size, data, GL_DYNAMIC_COPY);
    (void)i;
}

/* the working picture and the lists for w x h (the sizes of the reference, 4x margin on an average scene) */
static int ensure(struct cmaa *c, int w, int h)
{
    if (c->w == w && c->h == h) return 1;
    if (c->edge_tex) { glDeleteTextures(1, &c->edge_tex); glDeleteTextures(1, &c->work_tex); glDeleteFramebuffers(1, &c->work_fbo); }
    c->w = c->h = 0;
    c->edge_tex = storage_tex(c, GL_R32UI, w, h);
    c->work_tex = storage_tex(c, GL_RGBA8, w, h);
    glGenFramebuffers(1, &c->work_fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, c->work_fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, c->work_tex, 0);
    GLuint zero[16] = { 0 };
    long long px = (long long)w * h;
    buf_make(0, &c->ssbo[0], sizeof zero, zero);
    buf_make(1, &c->ssbo[1], (GLsizeiptr)(px / 4 + 16) * 4, NULL);
    buf_make(2, &c->ssbo[2], (GLsizeiptr)((px + 3) / 6 + 16) * 4, NULL);
    buf_make(3, &c->ssbo[3], (GLsizeiptr)(px / 2 + 16) * 8, NULL);
    buf_make(4, &c->ssbo[4], sizeof zero, zero);
    /* the list heads start as "empty" (0xFFFFFFFF); the edge kernel sets them again where it finds edges */
    size_t hn = (size_t)((w + 1) / 2) * (size_t)((h + 1) / 2);
    GLuint *ff = malloc(hn * 4);
    if (!ff) return 0;
    memset(ff, 0xff, hn * 4);
    buf_make(5, &c->ssbo[5], (GLsizeiptr)(hn * 4), ff);
    free(ff);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (glGetError() != GL_NO_ERROR) return 0;
    c->w = w; c->h = h;
    return 1;
}

static void blit(GLuint p, GLenum target, GLuint tex, GLuint fbo, int w, int h, GLuint vbo)
{
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glViewport(0, 0, w, h);
    glUseProgram(p);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(target, tex);
    glUniform1i(glGetUniformLocation(p, "tex"), 0);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
}

static void use(GLuint p, int w, int h)
{
    glUseProgram(p);
    glUniform2i(glGetUniformLocation(p, "sz"), w, h);
    glUniform1i(glGetUniformLocation(p, "srcTex"), 0);
}

int cmaa_run(struct cmaa *c, int ext, GLuint src, int w, int h, int level, GLuint dst, GLuint vbo)
{
    if (!c || level < 0 || level > 3 || w < 4 || h < 4 || !ensure(c, w, h)) return -1;
    if (!c->edges[level] && !(c->edges[level] = compute_program(K_EDGES, level))) return -1;
    glDisable(GL_BLEND);
    blit(ext ? c->blit_ext : c->blit_2d, ext ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D, src, c->work_fbo, w, h, vbo);
    for (int i = 0; i < 6; i++) c->bind_base(GL_SHADER_STORAGE_BUFFER, (GLuint)i, c->ssbo[i]);
    c->bind_image(0, c->edge_tex, 0, GL_FALSE, 0, GL_READ_WRITE, GL_R32UI);
    c->bind_image(2, c->work_tex, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA8);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, c->work_tex);
    const GLbitfield all = GL_SHADER_STORAGE_BARRIER_BIT | GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_COMMAND_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT | GL_FRAMEBUFFER_BARRIER_BIT;
    c->barrier(GL_FRAMEBUFFER_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
    use(c->edges[level], w, h);
    c->dispatch((GLuint)((w + 27) / 28), (GLuint)((h + 27) / 28), 1);
    c->barrier(all);
    use(c->args, w, h);
    c->dispatch(2, 1, 1);
    c->barrier(all);
    glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, c->ssbo[4]);
    use(c->cand, w, h);
    c->dispatch_indirect(0);
    c->barrier(all);
    use(c->args, w, h);
    c->dispatch(1, 2, 1);
    c->barrier(all);
    use(c->apply, w, h);
    c->dispatch_indirect(0);
    glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, 0);
    c->barrier(all);
    glUseProgram(0);
    blit(c->blit_2d, GL_TEXTURE_2D, c->work_tex, dst, w, h, vbo);
    return glGetError() == GL_NO_ERROR ? 0 : -1;
}

void cmaa_free(struct cmaa *c)
{
    free(c);  /* the GL objects go with the context */
}
