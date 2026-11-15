/* glformats: what a conformant GLES 3 driver must give for the colour and format paths a Unity URP game picks from the extension string (sRGB
 * encode/decode, float and packed render targets, compressed textures, depth/stencil, MSAA resolve), checked with glReadPixels. Included by
 * linux.c (the host driver alone, the reference) and by tests/apps/hello-glformats (the same code through apklayer's EGL/GLES wrapper): a check
 * that passes on Linux and fails on Android is the wrapper's. Lines: "glformats: <name> PASS|FAIL|SKIP <detail>"; formats_run returns the FAIL count.
 * The platform makes an ES 3 context current (no window needed: everything renders to FBOs) and passes a print callback. */
#include <GLES3/gl3.h>
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Every GL function comes from eglGetProcAddress (the platform sets formats_proc), as an engine that loads GL itself does: through Android's
 * libGLESv2 the platform's own check (linked_checks in glformats.c) asks the plain linked functions. */
static void *(*formats_proc)(const char *);
#include "glprocs.h"

#define GL_SRGB_DECODE 0x8A48
#define GL_SKIP_DECODE 0x8A4A
#define GL_FRAMEBUFFER_SRGB_EXT 0x8DB9
#define GL_COLOR_ENCODING 0x8210
#define GL_SRGB_ENUM 0x8C40
#define GL_RGBA_ASTC_4x4 0x93B0
#define GL_SRGB_ASTC_4x4 0x93D0

static void (*say)(const char *);
static int fails;
static char exts[16384];

static void out(const char *fmt, ...)
{
    char line[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(line, sizeof line, fmt, ap);
    va_end(ap);
    say(line);
}

static void result(const char *name, int pass, const char *fmt, ...)
{
    char detail[600];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(detail, sizeof detail, fmt, ap);
    va_end(ap);
    if (!pass) fails++;
    out("glformats: %s %s %s", name, pass ? "PASS" : "FAIL", detail);
}

static void skip(const char *name, const char *why) { out("glformats: %s SKIP %s", name, why); }
static int has(const char *ext) { return strstr(exts, ext) != NULL; }
static int near(float a, float b, float tol) { return fabsf(a - b) <= tol; }
static float srgb_dec(float c) { return c <= 0.04045f ? c / 12.92f : powf((c + 0.055f) / 1.055f, 2.4f); }
static float srgb_enc(float c) { return c <= 0.0031308f ? c * 12.92f : 1.055f * powf(c, 1 / 2.4f) - 0.055f; }

static GLuint prog_color, prog_tex;

static GLuint make_prog(const char *vs, const char *fs)
{
    GLuint p = glCreateProgram();
    const char *src[2] = { vs, fs };
    for (int i = 0; i < 2; i++) {
        GLuint s = glCreateShader(i ? GL_FRAGMENT_SHADER : GL_VERTEX_SHADER);
        glShaderSource(s, 1, &src[i], NULL);
        glCompileShader(s);
        GLint ok = 0;
        glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
        if (!ok) { char log[512]; glGetShaderInfoLog(s, sizeof log, NULL, log); out("glformats: shader: %s", log); return 0; }
        glAttachShader(p, s);
    }
    glLinkProgram(p);
    return p;
}

/* A triangle from gl_VertexID: k=1 covers the whole target, k=0.5 its lower-left half; z is the depth. */
static const char *VS = "#version 300 es\nuniform float z; uniform float k; out vec2 uv;\n"
    "void main(){ vec2 p = vec2(float((gl_VertexID<<1)&2), float(gl_VertexID&2)); gl_Position = vec4(p*(2.*k)-1., z, 1.); uv = p*k; }";

static int setup(void)
{
    prog_color = make_prog(VS, "#version 300 es\nprecision highp float; uniform vec4 c; out vec4 o; void main(){ o = c; }");
    prog_tex = make_prog(VS, "#version 300 es\nprecision highp float; uniform sampler2D t; in vec2 uv; out vec4 o; void main(){ o = texture(t, uv); }");
    return prog_color && prog_tex;
}

static void quad(float k, float z, float r, float g, float b, float a)
{
    glUseProgram(prog_color);
    glUniform1f(glGetUniformLocation(prog_color, "z"), z);
    glUniform1f(glGetUniformLocation(prog_color, "k"), k);
    glUniform4f(glGetUniformLocation(prog_color, "c"), r, g, b, a);
    glDrawArrays(GL_TRIANGLES, 0, 3);
}

static void quad_tex(GLuint tex)
{
    glUseProgram(prog_tex);
    glUniform1f(glGetUniformLocation(prog_tex, "z"), 0);
    glUniform1f(glGetUniformLocation(prog_tex, "k"), 1);
    glUniform1i(glGetUniformLocation(prog_tex, "t"), 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex);
    glDrawArrays(GL_TRIANGLES, 0, 3);
}

struct rt { GLuint fbo, tex, rb; int w, h; };

/* a texture-backed colour target (and optionally a depth/stencil renderbuffer); 0 when the driver refuses the format */
static int rt_make(struct rt *r, GLenum fmt, int w, int h, GLenum depth)
{
    memset(r, 0, sizeof *r);
    r->w = w; r->h = h;
    while (glGetError()) {}
    glGenTextures(1, &r->tex);
    glBindTexture(GL_TEXTURE_2D, r->tex);
    glTexStorage2D(GL_TEXTURE_2D, 1, fmt, w, h);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glGenFramebuffers(1, &r->fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, r->fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, r->tex, 0);
    if (depth) {
        glGenRenderbuffers(1, &r->rb);
        glBindRenderbuffer(GL_RENDERBUFFER, r->rb);
        glRenderbufferStorage(GL_RENDERBUFFER, depth, w, h);
        int stencil = depth == GL_DEPTH24_STENCIL8 || depth == GL_DEPTH32F_STENCIL8;
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, stencil ? GL_DEPTH_STENCIL_ATTACHMENT : GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, r->rb);
    }
    glViewport(0, 0, w, h);
    GLenum st = glCheckFramebufferStatus(GL_FRAMEBUFFER), err = glGetError();
    if (st != GL_FRAMEBUFFER_COMPLETE || err) {
        out("glformats: (format 0x%x: status 0x%x, glError 0x%x)", fmt, st, err);
        return 0;
    }
    return 1;
}

static void rt_free(struct rt *r)
{
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &r->fbo);
    glDeleteTextures(1, &r->tex);
    if (r->rb) glDeleteRenderbuffers(1, &r->rb);
}

static void px8(int x, int y, unsigned char *p) { glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, p); }
static void pxf(int x, int y, float *p) { glReadPixels(x, y, 1, 1, GL_RGBA, GL_FLOAT, p); }
static void clear_to(float r, float g, float b, float a) { glClearColor(r, g, b, a); glClear(GL_COLOR_BUFFER_BIT); }

/* sample a texture into a fresh RGBA8 target and read the pixel */
static void sample8(GLuint tex, unsigned char *p)
{
    struct rt t;
    rt_make(&t, GL_RGBA8, 1, 1, 0);
    glDisable(GL_BLEND);
    quad_tex(tex);
    px8(0, 0, p);
    rt_free(&t);
}

static void test_info(void)
{
    out("glformats: GL_VERSION %s", glGetString(GL_VERSION));
    out("glformats: GL_RENDERER %s", glGetString(GL_RENDERER));
    out("glformats: GL_VENDOR %s", glGetString(GL_VENDOR));
    out("glformats: GL_SHADING_LANGUAGE_VERSION %s", glGetString(GL_SHADING_LANGUAGE_VERSION));
    GLint n = 0, v;
    glGetIntegerv(GL_NUM_EXTENSIONS, &n);
    exts[0] = 0;
    for (int i = 0; i < n; i++) {
        const char *s = (const char *)glGetStringi(GL_EXTENSIONS, i);
        if (strlen(exts) + strlen(s) + 2 < sizeof exts) { strcat(exts, s); strcat(exts, " "); }
        out("glformats: ext %s", s);
    }
    /* what glGetString(GL_EXTENSIONS) says must be the same list */
    const char *e = (const char *)glGetString(GL_EXTENSIONS);
    int same = 1;
    for (int i = 0; i < n && e; i++) {
        char *copy = strdup((const char *)glGetStringi(GL_EXTENSIONS, i));
        if (!strstr(e, copy)) { same = 0; out("glformats: ext %s missing from glGetString(GL_EXTENSIONS)", copy); }
        free(copy);
    }
    result("extension-lists-agree", same, "%d names by glGetStringi, glGetString(GL_EXTENSIONS) %s", n, e ? "present" : "NULL");
    static const struct { GLenum e; const char *n; } lim[] = {
        { GL_MAX_TEXTURE_SIZE, "MAX_TEXTURE_SIZE" }, { GL_MAX_SAMPLES, "MAX_SAMPLES" }, { GL_MAX_COLOR_ATTACHMENTS, "MAX_COLOR_ATTACHMENTS" },
        { GL_MAX_DRAW_BUFFERS, "MAX_DRAW_BUFFERS" }, { GL_MAX_TEXTURE_IMAGE_UNITS, "MAX_TEXTURE_IMAGE_UNITS" },
        { GL_MAX_ARRAY_TEXTURE_LAYERS, "MAX_ARRAY_TEXTURE_LAYERS" }, { GL_MAX_3D_TEXTURE_SIZE, "MAX_3D_TEXTURE_SIZE" },
        { GL_MAX_RENDERBUFFER_SIZE, "MAX_RENDERBUFFER_SIZE" }, { GL_MAX_FRAGMENT_UNIFORM_VECTORS, "MAX_FRAGMENT_UNIFORM_VECTORS" },
        { GL_MAX_VERTEX_ATTRIBS, "MAX_VERTEX_ATTRIBS" }, { GL_MAX_VARYING_VECTORS, "MAX_VARYING_VECTORS" },
        { GL_MAX_UNIFORM_BLOCK_SIZE, "MAX_UNIFORM_BLOCK_SIZE" },
    };
    for (unsigned i = 0; i < sizeof lim / sizeof *lim; i++) { v = -1; glGetIntegerv(lim[i].e, &v); out("glformats: limit %s %d", lim[i].n, v); }
    GLint nc = 0, fm[256];
    glGetIntegerv(GL_NUM_COMPRESSED_TEXTURE_FORMATS, &nc);
    glGetIntegerv(GL_COMPRESSED_TEXTURE_FORMATS, fm);
    char list[2000] = "";
    for (int i = 0; i < nc && i < 256; i++) { char t[16]; snprintf(t, sizeof t, " 0x%x", fm[i]); if (strlen(list) < 1900) strcat(list, t); }
    out("glformats: compressed formats %d:%s", nc, list);
    GLint ms[16] = { 0 }, cnt = 0;
    glGetInternalformativ(GL_RENDERBUFFER, GL_RGBA8, GL_NUM_SAMPLE_COUNTS, 1, &cnt);
    glGetInternalformativ(GL_RENDERBUFFER, GL_RGBA8, GL_SAMPLES, cnt > 16 ? 16 : cnt, ms);
    char sl[100] = "";
    for (int i = 0; i < cnt && i < 16; i++) { char t[8]; snprintf(t, sizeof t, " %d", ms[i]); strcat(sl, t); }
    out("glformats: RGBA8 sample counts:%s", sl);
}

static void test_srgb(void)
{
    struct rt r;
    unsigned char p[4], q[4];
    GLint enc = 0;
    /* UNORM8 linear target: 0.5 -> 0x80 */
    rt_make(&r, GL_RGBA8, 4, 4, 0);
    glDisable(GL_BLEND);
    quad(1, 0, 0.5f, 0.5f, 0.5f, 0.5f);
    px8(1, 1, p);
    result("rgba8-write", abs(p[0] - 128) <= 1 && abs(p[3] - 128) <= 1, "0.5 -> %d,%d,%d,%d (want 128)", p[0], p[1], p[2], p[3]);
    glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_COLOR_ENCODING, &enc);
    result("rgba8-encoding", enc == GL_LINEAR, "color encoding 0x%x (want GL_LINEAR 0x2601)", enc);
    rt_free(&r);

    if (!rt_make(&r, GL_SRGB8_ALPHA8, 4, 4, 0)) { skip("srgb-target", "SRGB8_ALPHA8 not renderable"); return; }
    glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_COLOR_ENCODING, &enc);
    result("srgb-encoding", enc == GL_SRGB_ENUM, "color encoding 0x%x (want GL_SRGB 0x8c40)", enc);
    glDisable(GL_BLEND);
    quad(1, 0, 0.5f, 0.25f, 0.5f, 0.5f);
    px8(1, 1, p);
    int e5 = (int)(srgb_enc(0.5f) * 255 + 0.5f), e25 = (int)(srgb_enc(0.25f) * 255 + 0.5f);
    result("srgb-write-encode", abs(p[0] - e5) <= 1 && abs(p[1] - e25) <= 1 && abs(p[3] - 128) <= 1, "linear 0.5,0.25,a0.5 -> raw %d,%d,%d,%d (want %d,%d,b,128: alpha not encoded)", p[0], p[1], p[2], p[3], e5, e25);
    /* the encoded value sampled back (decode) into RGBA8 */
    sample8(r.tex, q);
    glBindFramebuffer(GL_FRAMEBUFFER, r.fbo);
    result("srgb-render-then-sample", abs(q[0] - 128) <= 1 && abs(q[1] - 64) <= 1, "write 0.5,0.25 then sample decoded -> %d,%d (want 128,64)", q[0], q[1]);
    /* blending happens in linear space: 0.5 (stored encoded) + 0.25 additive -> 0.75 linear */
    glViewport(0, 0, 4, 4);
    quad(1, 0, 0.5f, 0.5f, 0.5f, 1);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE);
    quad(1, 0, 0.25f, 0.25f, 0.25f, 0);
    glDisable(GL_BLEND);
    px8(1, 1, p);
    int e75 = (int)(srgb_enc(0.75f) * 255 + 0.5f);
    result("srgb-blend-linear", abs(p[0] - e75) <= 2, "0.5 + 0.25 additive -> raw %d (want %d linear blend; 251 would be blended encoded)", p[0], e75);
    /* EXT_sRGB_write_control */
    if (has("GL_EXT_sRGB_write_control")) {
        GLboolean on = glIsEnabled(GL_FRAMEBUFFER_SRGB_EXT);
        glDisable(GL_FRAMEBUFFER_SRGB_EXT);
        quad(1, 0, 0.5f, 0.5f, 0.5f, 0.5f);
        px8(1, 1, p);
        glEnable(GL_FRAMEBUFFER_SRGB_EXT);
        result("srgb-write-control-off", abs(p[0] - 128) <= 1, "FRAMEBUFFER_SRGB disabled, 0.5 -> raw %d (want 128 unencoded)", p[0]);
        result("srgb-write-control-default", on, "FRAMEBUFFER_SRGB is enabled by default: %d", on);
    } else skip("srgb-write-control", "GL_EXT_sRGB_write_control not advertised");
    rt_free(&r);

    /* sRGB texture upload: byte 188 (0xBC) decodes to 0.5; SKIP_DECODE (EXT_texture_sRGB_decode) gives the raw 0.737 */
    GLuint t;
    unsigned char texel[4] = { 188, 188, 188, 255 };
    glGenTextures(1, &t);
    glBindTexture(GL_TEXTURE_2D, t);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_SRGB8_ALPHA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, texel);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    sample8(t, q);
    result("srgb-texture-decode", abs(q[0] - 128) <= 1, "SRGB8_ALPHA8 texel 188 sampled -> %d (want 128)", q[0]);
    if (has("GL_EXT_texture_sRGB_decode")) {
        glBindTexture(GL_TEXTURE_2D, t);
        glTexParameteri(GL_TEXTURE_2D, GL_SRGB_DECODE, GL_SKIP_DECODE);
        sample8(t, q);
        result("srgb-texture-skip-decode", abs(q[0] - 188) <= 1, "SKIP_DECODE texel 188 -> %d (want 188)", q[0]);
    } else skip("srgb-texture-skip-decode", "GL_EXT_texture_sRGB_decode not advertised");
    glDeleteTextures(1, &t);
    /* a generated mipmap of an sRGB texture (black and white texels) averages in linear space */
    glGenTextures(1, &t);
    glBindTexture(GL_TEXTURE_2D, t);
    unsigned char two[2 * 2 * 4];
    for (int i = 0; i < 4; i++) { two[i * 4] = i < 2 ? 0 : 255; two[i * 4 + 1] = two[i * 4]; two[i * 4 + 2] = two[i * 4]; two[i * 4 + 3] = 255; }
    glTexImage2D(GL_TEXTURE_2D, 0, GL_SRGB8_ALPHA8, 2, 2, 0, GL_RGBA, GL_UNSIGNED_BYTE, two);
    glGenerateMipmap(GL_TEXTURE_2D);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 1);
    sample8(t, q);
    result("srgb-mipmap", abs(q[0] - 128) <= 2, "black/white sRGB texture's 1x1 mip, decoded -> %d (linear average 128; encoded-space average would read 188)", q[0]);
    glDeleteTextures(1, &t);
}

static void test_float_targets(void)
{
    static const struct { GLenum fmt; const char *name; int ch; } F[] = {
        { GL_RGBA16F, "rgba16f", 4 }, { GL_R11F_G11F_B10F, "r11g11b10f", 3 }, { GL_RGBA32F, "rgba32f", 4 }, { GL_R16F, "r16f", 1 }, { GL_RG16F, "rg16f", 2 }, { GL_R32F, "r32f", 1 },
    };
    for (unsigned i = 0; i < sizeof F / sizeof *F; i++) {
        struct rt r;
        char name[48];
        float p[4];
        int ch = F[i].ch;
        if (!rt_make(&r, F[i].fmt, 2, 2, 0)) {
            snprintf(name, sizeof name, "%s-target", F[i].name);
            out("glformats: %s SKIP not renderable (EXT_color_buffer_float %s, EXT_color_buffer_half_float %s)", name, has("GL_EXT_color_buffer_float") ? "listed" : "absent", has("GL_EXT_color_buffer_half_float") ? "listed" : "absent");
            continue;
        }
        glDisable(GL_BLEND);
        quad(1, 0, 0.5f, 2.0f, 0.25f, 0.75f);
        pxf(0, 0, p);
        int okv = near(p[0], 0.5f, 1e-3f) && (ch < 2 || near(p[1], 2.0f, 1e-3f)) && (ch < 3 || near(p[2], 0.25f, 1e-3f)) && (ch < 4 || near(p[3], 0.75f, 1e-3f));
        snprintf(name, sizeof name, "%s-write", F[i].name);
        result(name, okv, "0.5,2,0.25,0.75 -> %g,%g,%g,%g (channels the format lacks read 0,0,0,1)", p[0], p[1], p[2], p[3]);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE);
        quad(1, 0, 0.25f, 100.0f, 0.25f, 0.25f);
        glDisable(GL_BLEND);
        pxf(0, 0, p);
        snprintf(name, sizeof name, "%s-blend", F[i].name);
        result(name, near(p[0], 0.75f, 2e-3f) && (ch < 2 || near(p[1], 102.0f, 0.2f)), "additive blend 0.5+0.25 -> %g, 2+100 -> %g", p[0], p[1]);
        clear_to(3.5f, 0, 0, 1);  /* float targets are not clamped to 0..1 */
        pxf(0, 0, p);
        snprintf(name, sizeof name, "%s-unclamped", F[i].name);
        result(name, near(p[0], 3.5f, 1e-3f), "clear 3.5 -> %g", p[0]);
        rt_free(&r);
    }
    struct rt r;
    unsigned int px = 0;
    if (rt_make(&r, GL_RGB10_A2, 2, 2, 0)) {
        glDisable(GL_BLEND);
        quad(1, 0, 0.5f, 0.25f, 1.0f, 1.0f);
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_INT_2_10_10_10_REV, &px);
        int rr = px & 1023, gg = (px >> 10) & 1023, bb = (px >> 20) & 1023, aa = px >> 30;
        result("rgb10a2-write", abs(rr - 512) <= 1 && abs(gg - 256) <= 1 && bb == 1023 && aa == 3, "0.5,0.25,1,1 -> %d,%d,%d,%d (want 512,256,1023,3)", rr, gg, bb, aa);
        unsigned char p8[4];
        px8(0, 0, p8);
        result("rgb10a2-read8", abs(p8[0] - 128) <= 1 && abs(p8[1] - 64) <= 1 && p8[2] == 255, "read as UNORM8 -> %d,%d,%d,%d", p8[0], p8[1], p8[2], p8[3]);
        rt_free(&r);
    } else skip("rgb10a2-write", "RGB10_A2 not renderable");
    static const struct { GLenum fmt; const char *name; int ch; } U[] = { { GL_R8, "r8", 1 }, { GL_RG8, "rg8", 2 }, { GL_RGB8, "rgb8", 3 }, { GL_RGB565, "rgb565", 3 }, { GL_RGBA4, "rgba4", 4 } };
    for (unsigned i = 0; i < sizeof U / sizeof *U; i++) {
        unsigned char p[4] = { 0 };
        char name[48];
        if (!rt_make(&r, U[i].fmt, 2, 2, 0)) { snprintf(name, sizeof name, "%s-target", U[i].name); skip(name, "not renderable"); continue; }
        quad(1, 0, 0.5f, 0.25f, 0.75f, 1.0f);
        px8(0, 0, p);
        snprintf(name, sizeof name, "%s-write", U[i].name);
        int tol = U[i].fmt == GL_RGB565 || U[i].fmt == GL_RGBA4 ? 9 : 1;
        result(name, abs(p[0] - 128) <= tol && (U[i].ch < 2 || abs(p[1] - 64) <= tol) && (U[i].ch < 3 || abs(p[2] - 191) <= tol), "0.5,0.25,0.75 -> %d,%d,%d,%d", p[0], p[1], p[2], p[3]);
        rt_free(&r);
    }
}

static void test_float_filter(void)
{
    static const struct { GLenum fmt; const char *name, *ext; } F[] = {
        { GL_RGBA16F, "rgba16f-linear", NULL }, { GL_RGBA32F, "rgba32f-linear", "GL_OES_texture_float_linear" }, { GL_R11F_G11F_B10F, "r11g11b10f-linear", NULL },
    };
    for (unsigned i = 0; i < sizeof F / sizeof *F; i++) {
        GLuint t;
        float data[2 * 4] = { 0, 0, 0, 1, 1, 1, 1, 1 }, rgb[6] = { 0, 0, 0, 1, 1, 1 }, p[4];
        glGenTextures(1, &t);
        glBindTexture(GL_TEXTURE_2D, t);
        if (F[i].fmt == GL_R11F_G11F_B10F) glTexImage2D(GL_TEXTURE_2D, 0, F[i].fmt, 2, 1, 0, GL_RGB, GL_FLOAT, rgb);
        else glTexImage2D(GL_TEXTURE_2D, 0, F[i].fmt, 2, 1, 0, GL_RGBA, GL_FLOAT, data);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        struct rt r;
        if (!rt_make(&r, GL_RGBA16F, 1, 1, 0)) { skip(F[i].name, "no float target to read the sample"); glDeleteTextures(1, &t); continue; }
        quad_tex(t);
        pxf(0, 0, p);
        int filtered = near(p[0], 0.5f, 0.02f);
        /* a driver that cannot filter a 32F texture must say so by not listing the extension */
        if (F[i].ext) result(F[i].name, filtered == has(F[i].ext), "linear sample of 0|1 -> %g (%s %s)", p[0], F[i].ext, has(F[i].ext) ? "listed" : "not listed");
        else result(F[i].name, filtered, "linear sample of 0|1 -> %g (want 0.5)", p[0]);
        rt_free(&r);
        glDeleteTextures(1, &t);
    }
}

static void test_depth_msaa(void)
{
    static const struct { GLenum fmt; const char *name; } D[] = { { GL_DEPTH24_STENCIL8, "d24s8" }, { GL_DEPTH_COMPONENT16, "d16" }, { GL_DEPTH_COMPONENT24, "d24" }, { GL_DEPTH_COMPONENT32F, "d32f" }, { GL_DEPTH32F_STENCIL8, "d32fs8" } };
    for (unsigned i = 0; i < sizeof D / sizeof *D; i++) {
        struct rt r;
        unsigned char p[4];
        char name[48];
        snprintf(name, sizeof name, "depth-%s", D[i].name);
        if (!rt_make(&r, GL_RGBA8, 4, 4, D[i].fmt)) { skip(name, "not attachable"); continue; }
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glClearDepthf(1);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        quad(1, 0.2f, 1, 0, 0, 1);  /* near: red */
        quad(1, 0.6f, 0, 1, 0, 1);  /* behind it: must not show */
        px8(1, 1, p);
        glDisable(GL_DEPTH_TEST);
        result(name, p[0] == 255 && p[1] == 0, "red (z .2) then green (z .6) -> %d,%d,%d", p[0], p[1], p[2]);
        if (D[i].fmt == GL_DEPTH24_STENCIL8 || D[i].fmt == GL_DEPTH32F_STENCIL8) {
            glEnable(GL_STENCIL_TEST);
            glClearStencil(0);
            glClear(GL_STENCIL_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
            glStencilFunc(GL_ALWAYS, 1, 0xff);
            glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
            quad(0.5f, 0, 1, 1, 1, 1);  /* the lower-left half sets stencil 1 */
            glStencilFunc(GL_EQUAL, 1, 0xff);
            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
            glClear(GL_COLOR_BUFFER_BIT);
            quad(1, 0, 0, 0, 1, 1);
            glDisable(GL_STENCIL_TEST);
            unsigned char in[4], outside[4];
            px8(0, 0, in);
            px8(3, 3, outside);
            snprintf(name, sizeof name, "stencil-%s", D[i].name);
            result(name, in[2] == 255 && outside[2] == 0, "stencil EQUAL 1 inside %d outside %d (want 255, 0)", in[2], outside[2]);
        }
        rt_free(&r);
    }
    GLint maxs = 0;
    glGetIntegerv(GL_MAX_SAMPLES, &maxs);
    static const GLenum MS[] = { GL_RGBA8, GL_SRGB8_ALPHA8, GL_RGBA16F };
    static const char *MN[] = { "rgba8", "srgb8a8", "rgba16f" };
    for (int i = 0; i < 3; i++) {
        char name[48];
        snprintf(name, sizeof name, "msaa-%s", MN[i]);
        int samples = maxs < 4 ? maxs : 4;
        if (samples < 2) { skip(name, "MAX_SAMPLES < 2"); continue; }
        GLuint fbo, rb;
        while (glGetError()) {}
        glGenRenderbuffers(1, &rb);
        glBindRenderbuffer(GL_RENDERBUFFER, rb);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, MS[i], 16, 16);
        glGenFramebuffers(1, &fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, rb);
        GLenum st = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (st != GL_FRAMEBUFFER_COMPLETE || glGetError()) { skip(name, "multisampled target not supported"); glDeleteFramebuffers(1, &fbo); glDeleteRenderbuffers(1, &rb); continue; }
        glViewport(0, 0, 16, 16);
        clear_to(0, 0, 0, 0);
        glDisable(GL_BLEND);
        quad(0.5f, 0, 0.5f, 0.5f, 0.5f, 1);
        struct rt res;
        /* an sRGB source resolved into a linear target would be decoded by the blit: resolve into the same encoding */
        rt_make(&res, MS[i], 16, 16, 0);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, res.fbo);
        glBlitFramebuffer(0, 0, 16, 16, 0, 0, 16, 16, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, res.fbo);
        float f[3][4];
        if (MS[i] == GL_RGBA16F) { pxf(1, 1, f[0]); pxf(14, 14, f[1]); pxf(8, 7, f[2]); }
        else { unsigned char b[3][4]; px8(1, 1, b[0]); px8(14, 14, b[1]); px8(8, 7, b[2]); for (int j = 0; j < 3; j++) for (int c = 0; c < 4; c++) f[j][c] = b[j][c] / 255.f; }
        float want = MS[i] == GL_SRGB8_ALPHA8 ? srgb_enc(0.5f) : 0.5f;
        result(name, near(f[0][0], want, 0.01f) && f[1][0] == 0 && f[2][0] > 0.01f && f[2][0] < want - 0.01f,
               "%dx resolve: covered %.3f (want %.3f), uncovered %.3f, diagonal edge %.3f (strictly between)", samples, f[0][0], want, f[1][0], f[2][0]);
        rt_free(&res);
        glDeleteFramebuffers(1, &fbo);
        glDeleteRenderbuffers(1, &rb);
    }
}

/* GL_EXT_shader_framebuffer_fetch (Unity's native render passes): a shader that reads the colour it is about to blend with */
static void test_fetch(void)
{
    if (!has("GL_EXT_shader_framebuffer_fetch")) { skip("framebuffer-fetch", "GL_EXT_shader_framebuffer_fetch not advertised"); return; }
    GLuint p = make_prog(VS, "#version 300 es\n#extension GL_EXT_shader_framebuffer_fetch : require\nprecision highp float; uniform vec4 c; layout(location = 0) inout highp vec4 o; void main(){ o = o * 0.5 + c; }");
    if (!p) { result("framebuffer-fetch", 0, "the shader does not build although the extension is listed"); return; }
    static const struct { GLenum fmt; const char *name; } T[] = { { GL_RGBA8, "rgba8" }, { GL_SRGB8_ALPHA8, "srgb8a8" }, { GL_RGBA16F, "rgba16f" } };
    for (int i = 0; i < 3; i++) {
        struct rt r;
        char name[48];
        snprintf(name, sizeof name, "framebuffer-fetch-%s", T[i].name);
        if (!rt_make(&r, T[i].fmt, 2, 2, 0)) { skip(name, "not renderable"); continue; }
        glDisable(GL_BLEND);
        clear_to(0.2f, 0.4f, 0.6f, 1);
        glUseProgram(p);
        glUniform1f(glGetUniformLocation(p, "z"), 0);
        glUniform1f(glGetUniformLocation(p, "k"), 1);
        glUniform4f(glGetUniformLocation(p, "c"), 0.1f, 0.1f, 0.1f, 0);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        float w[4] = { 0.2f, 0.3f, 0.4f, 0.5f }, v[4];  /* in linear values: an sRGB target is fetched decoded */
        if (T[i].fmt == GL_RGBA16F) pxf(0, 0, v);
        else { unsigned char b[4]; px8(0, 0, b); for (int c = 0; c < 4; c++) v[c] = b[c] / 255.f; if (T[i].fmt == GL_SRGB8_ALPHA8) for (int c = 0; c < 3; c++) v[c] = srgb_dec(v[c]); }
        result(name, near(v[0], w[0], 0.01f) && near(v[1], w[1], 0.01f) && near(v[2], w[2], 0.01f) && near(v[3], w[3], 0.01f), "clear .2,.4,.6,1; o = o*.5 + .1 -> %.3f,%.3f,%.3f,%.3f (want .2,.3,.4,.5)", v[0], v[1], v[2], v[3]);
        rt_free(&r);
    }
}

/* One block of a compressed format (one block = the whole texture), sampled into RGBA8 and compared with the colour it must decode to. */
static void test_compressed_one(GLenum fmt, const char *base, int bw, int bh, const unsigned char *block, int bytes, const float *want, float tol, const char *what, int srgb)
{
    char name[96];
    unsigned char q[4];
    GLuint t;
    snprintf(name, sizeof name, "%s-%s", base, what);
    while (glGetError()) {}
    glGenTextures(1, &t);
    glBindTexture(GL_TEXTURE_2D, t);
    glCompressedTexImage2D(GL_TEXTURE_2D, 0, fmt, bw, bh, 0, bytes, block);
    GLenum err = glGetError();
    if (err) { out("glformats: %s SKIP glCompressedTexImage2D error 0x%x (format 0x%x not supported)", name, err, fmt); glDeleteTextures(1, &t); return; }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    sample8(t, q);
    int pass = 1;
    float w[4];
    for (int i = 0; i < 4; i++) {
        w[i] = srgb && i < 3 ? srgb_dec(want[i]) : want[i];
        if (fabsf(q[i] / 255.f - w[i]) > tol) pass = 0;
    }
    result(name, pass, "sampled %d,%d,%d,%d (want %.0f,%.0f,%.0f,%.0f%s)", q[0], q[1], q[2], q[3], 255 * w[0], 255 * w[1], 255 * w[2], 255 * w[3], srgb ? ", sRGB-decoded" : "");
    if (srgb && has("GL_EXT_texture_sRGB_decode")) {  /* the decode override must return the stored values */
        glBindTexture(GL_TEXTURE_2D, t);
        glTexParameteri(GL_TEXTURE_2D, GL_SRGB_DECODE, GL_SKIP_DECODE);
        sample8(t, q);
        snprintf(name, sizeof name, "%s-%s-skipdecode", base, what);
        result(name, fabsf(q[0] / 255.f - want[0]) <= tol && fabsf(q[1] / 255.f - want[1]) <= tol, "SKIP_DECODE sampled %d,%d,%d,%d (want %.0f,%.0f,%.0f)", q[0], q[1], q[2], q[3], 255 * want[0], 255 * want[1], 255 * want[2]);
    }
    glDeleteTextures(1, &t);
}

static void test_compressed(void)
{
    /* ASTC void-extent blocks (solid colour, 16-bit UNORM RGBA): R 0x2000, G 0x8000, B 0xC000, A 0xFFFF in every block size, LDR and sRGB profiles */
    static const unsigned char astc[16] = { 0xFC, 0xFD, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x20, 0x00, 0x80, 0x00, 0xC0, 0xFF, 0xFF };
    static const int dims[14][2] = { {4,4}, {5,4}, {5,5}, {6,5}, {6,6}, {8,5}, {8,6}, {8,8}, {10,5}, {10,6}, {10,8}, {10,10}, {12,10}, {12,12} };
    const float astc_want[4] = { 0x2000 / 65535.f, 0x8000 / 65535.f, 0xC000 / 65535.f, 1 };
    for (int s = 0; s < 2; s++)
        for (int i = 0; i < 14; i++) {
            char n[48];
            snprintf(n, sizeof n, "astc-%dx%d%s", dims[i][0], dims[i][1], s ? "-srgb" : "");
            test_compressed_one((s ? GL_SRGB_ASTC_4x4 : GL_RGBA_ASTC_4x4) + i, n, dims[i][0], dims[i][1], astc, 16, astc_want, 0.02f, "void-extent", s);
        }
    /* ETC2 RGB8 "individual" mode block: base 0x8 (4 bit -> 0x88) in both halves, table 0, every pixel index 0 (+2): 138 */
    static const unsigned char etc2[8] = { 0x88, 0x88, 0x88, 0x00, 0, 0, 0, 0 };
    const float etc_want[4] = { 138 / 255.f, 138 / 255.f, 138 / 255.f, 1 };
    test_compressed_one(0x9274, "etc2-rgb8", 4, 4, etc2, 8, etc_want, 0.012f, "individual", 0);
    test_compressed_one(0x9275, "etc2-srgb8", 4, 4, etc2, 8, etc_want, 0.012f, "individual", 1);
    test_compressed_one(0x8D64, "etc1-rgb8", 4, 4, etc2, 8, etc_want, 0.012f, "individual", 0);
    /* EAC block: base 128, multiplier 1, table 0, indices 0: (128*8+4 - 3*8)/2047 = 0.4905 */
    static const unsigned char eac[8] = { 128, 0x10, 0, 0, 0, 0, 0, 0 };
    unsigned char rgba_etc[16];
    memcpy(rgba_etc, eac, 8);
    memcpy(rgba_etc + 8, etc2, 8);
    const float eac_want[4] = { 138 / 255.f, 138 / 255.f, 138 / 255.f, 1004 / 2047.f };
    test_compressed_one(0x9278, "etc2-rgba8-eac", 4, 4, rgba_etc, 16, eac_want, 0.012f, "individual+eac", 0);
    test_compressed_one(0x9279, "etc2-srgb8-alpha8-eac", 4, 4, rgba_etc, 16, eac_want, 0.012f, "individual+eac", 1);
    const float r11_want[4] = { 1004 / 2047.f, 0, 0, 1 };
    test_compressed_one(0x9270, "eac-r11", 4, 4, eac, 8, r11_want, 0.012f, "eac", 0);
    /* S3TC: colour0 = RGB565 0x8410 -> (132,130,132), all indices 0; DXT5 alpha block: alpha0 = 128 */
    static const unsigned char dxt1[8] = { 0x10, 0x84, 0x00, 0x00, 0, 0, 0, 0 };
    unsigned char dxt5[16] = { 128, 128, 0, 0, 0, 0, 0, 0 };
    memcpy(dxt5 + 8, dxt1, 8);
    const float dxt_want[4] = { 132 / 255.f, 130 / 255.f, 132 / 255.f, 1 }, dxt5_want[4] = { 132 / 255.f, 130 / 255.f, 132 / 255.f, 128 / 255.f };
    test_compressed_one(0x83F0, "s3tc-dxt1", 4, 4, dxt1, 8, dxt_want, 0.012f, "solid", 0);
    test_compressed_one(0x83F3, "s3tc-dxt5", 4, 4, dxt5, 16, dxt5_want, 0.012f, "solid", 0);
}

/* the shaders, a VAO and the extension list (test_info prints the driver's facts) */
static int formats_prepare(void (*print)(const char *))
{
    say = print;
    fails = 0;
    load_procs(formats_proc);
    test_info();
    if (!setup()) { result("setup", 0, "shaders do not build"); return 0; }
    GLuint vao;
    glGenVertexArrays(1, &vao);
    glBindVertexArray(vao);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    return 1;
}

static int formats_tests(void)
{
    test_srgb();
    test_float_targets();
    test_float_filter();
    test_depth_msaa();
    test_fetch();
    test_compressed();
    out("glformats: done fails=%d", fails);
    return fails;
}

static int formats_run(void (*print)(const char *)) { return formats_prepare(print) ? formats_tests() : 1; }
