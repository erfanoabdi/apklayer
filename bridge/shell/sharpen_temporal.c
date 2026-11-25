/* The temporal stabiliser of the render filter (GLES backend; apklayer patch, not AMD's). The app's frames come without motion vectors, so the previous output is blended into this
 * one by two rules, and the history is clamped to the min/max of this frame's 3x3 around the pixel, so changing content cannot trail:
 *  1. where the app's own pixels changed by less than a small tolerance (TOL0..TOL1 8 bit levels, the largest per-channel change over the four source texels round the pixel; weight W
 *     at the low end, fading to 0 at the high one): calms low-contrast detail (foliage, grass, dithering, +-2 LSB noise);
 *  2. where the pixel OSCILLATES: a per-pixel counter (FLIP_ADD per hit, times DECAY per frame) counts the sign flips of the filtered picture's luma change (changes over FLIP_EPS only),
 *     each caused by a jump of the app's own texels (their change is JUMP0..JUMP1 of the range of the 2x2 texels: aliased point sampling, not a smooth sub-pixel pan). A thin high-contrast
 *     line or hair that crawls with the camera flips back and forth within a few frames; real motion changes the pixel one way and does not come back, and a smooth picture does not
 *     jump. Where the counter (max over the 3x3) is over OSC0..OSC1 the weight is WO, faded out by the last big luma change of the pixel (CH0..CH1).
 * A moving edge, text and scrolling are not touched (tests/vm/shimmer.py, tests/vm/ghost.py, docs/FILTER-EVAL.md "Shimmer"). The Vulkan backend has none. */
#include "sharpen_temporal.h"

#include <GLES2/gl2ext.h>
#include <stdlib.h>

#define EXT "#extension GL_OES_EGL_image_external : require\n"
#define LUMA "float L(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }\n"
/* the jump of the app's texels round the pixel: ds = largest change since the last frame, rg = largest range of the four now (shared by two shaders) */
#define TEXELS \
    "  vec2 f = floor(uv * sz - 0.5); float ds = 0.0; vec3 tn = vec3(1.0), tx = vec3(0.0);\n" \
    "  for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) { vec2 q = (f + vec2(float(i), float(j)) + 0.5) / sz; vec3 s = texture2D(src, q).rgb; vec3 a = abs(s - texture2D(prv, q).rgb);\n" \
    "    ds = max(ds, max(a.r, max(a.g, a.b))); tn = min(tn, s); tx = max(tx, s); }\n" \
    "  vec3 r = tx - tn; float rg = max(r.r, max(r.g, r.b));\n"

static const char *FS_COPYX =
    EXT "precision highp float;\n"
    "uniform samplerExternalOES tex; varying vec2 uv;\n"
    "void main() { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); }\n";
static const char *FS_BLIT =
    "precision highp float;\n"
    "uniform sampler2D tex; varying vec2 uv;\n"
    "void main() { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); }\n";
/* the filtered picture of this frame: (colour, the last luma change over FLIP_EPS, held; 0.5 + x / 2) */
static const char *FS_RAW =
    "precision highp float;\n"
    "uniform sampler2D cur, rawp; varying vec2 uv;\n" LUMA
    "const float FLIP_EPS = 0.07;\n"
    "void main() { vec3 c = texture2D(cur, uv).rgb; vec4 rp = texture2D(rawp, uv); float d = L(c) - L(rp.rgb), dp = (rp.a - 0.5) * 2.0;\n"
    "  if (abs(d) > FLIP_EPS) dp = d; gl_FragColor = vec4(c, clamp(0.5 + 0.5 * dp, 0.0, 1.0)); }\n";
/* the oscillation counter */
static const char *FS_OSC =
    EXT "precision highp float;\n"
    "uniform sampler2D cur, rawp, oscp, prv; uniform samplerExternalOES src; uniform vec2 sz; varying vec2 uv;\n" LUMA
    "const float FLIP_EPS = 0.07, FLIP_ADD = 0.3, DECAY = 0.9, JUMP0 = 0.5, JUMP1 = 0.9;\n"
    "void main() {\n"
    "  vec4 rp = texture2D(rawp, uv);\n"
    "  float d = L(texture2D(cur, uv).rgb) - L(rp.rgb), dp = (rp.a - 0.5) * 2.0;\n"
    "  float flip = (abs(d) > FLIP_EPS && abs(dp) > FLIP_EPS && d * dp < 0.0) ? 1.0 : 0.0;\n"
    "  if (flip > 0.0) {\n"
    TEXELS
    "    flip *= smoothstep(JUMP0, JUMP1, ds / max(rg, 0.04));\n"
    "  }\n"
    "  gl_FragColor = vec4(min(texture2D(oscp, uv).r * DECAY + flip * FLIP_ADD, 1.0), 0.0, 0.0, 1.0);\n"
    "}\n";
static const char *FS_TMP =
    EXT "precision highp float;\n"
    "uniform sampler2D cur, hist, prv, osc; uniform samplerExternalOES src; uniform vec2 px, sz; varying vec2 uv;\n"
    "const float TOL0 = 10.0, TOL1 = 30.0, W = 0.5, WO = 0.7, OSC0 = 0.25, OSC1 = 0.5, CH0 = 0.1, CH1 = 0.4;\n"
    "void main() {\n"
    "  vec4 cc = texture2D(cur, uv); vec3 c = cc.rgb, mn = c, mx = c; float o = 0.0;\n"
    "  for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) { vec2 u = uv + vec2(float(i), float(j)) * px; vec3 q = texture2D(cur, u).rgb; mn = min(mn, q); mx = max(mx, q); o = max(o, texture2D(osc, u).r); }\n"
    TEXELS
    "  float w = W * (1.0 - smoothstep(TOL0, TOL1, ds * 255.0));\n"
    "  float ch = abs(cc.a - 0.5) * 2.0;\n"  /* alpha of the filtered picture = the last luma change over FLIP_EPS, held */
    "  w = max(w, WO * smoothstep(OSC0, OSC1, o) * (1.0 - smoothstep(CH0, CH1, ch)));\n"
    "  gl_FragColor = vec4(mix(c, clamp(texture2D(hist, uv).rgb, mn, mx), w), 1.0);\n"
    "}\n";

struct tmpl {
    GLuint tmp, copyx, blit, raw, osc;
    struct rt cur[2], cnt[2], hist, prev;  /* the filtered pictures (this frame's, the last) and the oscillation counters, alternating; the last output; the last source */
    int ok, flip, sw, sh;
    float sharp;
};

struct tmpl *tmpl_create(GLuint (*prog)(const char *fs))
{
    struct tmpl *t = calloc(1, sizeof *t);
    if (!t) return NULL;
    t->tmp = prog(FS_TMP); t->copyx = prog(FS_COPYX); t->blit = prog(FS_BLIT); t->raw = prog(FS_RAW); t->osc = prog(FS_OSC);
    if (!t->tmp || !t->copyx || !t->blit || !t->raw || !t->osc) { free(t); return NULL; }
    return t;
}

void tmpl_free(struct tmpl *t) { free(t); }

static void draw(GLuint prog, GLuint vbo, GLuint fbo, int w, int h)
{
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glViewport(0, 0, w, h);
    glUseProgram(prog);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
}

static void bind(int unit, GLenum target, GLuint tex)
{
    glActiveTexture(GL_TEXTURE0 + unit);
    glBindTexture(target, tex);
}

static void uni1i(GLuint prog, const char *name, int v) { glUniform1i(glGetUniformLocation(prog, name), v); }

void tmpl_run(struct tmpl *t, GLuint (*rt_ensure)(struct rt *, int, int), GLuint vbo, GLuint out_tex, GLuint out_fbo, int ow, int oh, GLuint src, int sw, int sh, float sharp)
{
    if (!t) return;
    if (t->hist.w != ow || t->hist.h != oh || t->sw != sw || t->sh != sh || t->sharp != sharp) t->ok = 0;
    if (!rt_ensure(&t->cur[0], ow, oh) || !rt_ensure(&t->cur[1], ow, oh) || !rt_ensure(&t->cnt[0], ow, oh) || !rt_ensure(&t->cnt[1], ow, oh) || !rt_ensure(&t->hist, ow, oh) || !rt_ensure(&t->prev, sw, sh)) { t->ok = 0; return; }
    int a = t->flip, b = !a;  /* [a]: last frame's filtered picture and counter, [b]: this frame's */
    glDisable(GL_BLEND);
    bind(0, GL_TEXTURE_2D, out_tex);  /* the output as a texture: it is read below, so its filter must not use mipmaps */
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    if (t->ok) {
        bind(1, GL_TEXTURE_2D, t->cur[a].tex); bind(2, GL_TEXTURE_2D, t->cnt[a].tex); bind(3, GL_TEXTURE_2D, t->prev.tex); bind(4, GL_TEXTURE_EXTERNAL_OES, src);
        glUseProgram(t->raw); uni1i(t->raw, "cur", 0); uni1i(t->raw, "rawp", 1);
        draw(t->raw, vbo, t->cur[b].fbo, ow, oh);
        glUseProgram(t->osc);
        uni1i(t->osc, "cur", 0); uni1i(t->osc, "rawp", 1); uni1i(t->osc, "oscp", 2); uni1i(t->osc, "prv", 3); uni1i(t->osc, "src", 4);
        glUniform2f(glGetUniformLocation(t->osc, "sz"), (float)sw, (float)sh);
        draw(t->osc, vbo, t->cnt[b].fbo, ow, oh);
        bind(0, GL_TEXTURE_2D, t->cur[b].tex); bind(1, GL_TEXTURE_2D, t->hist.tex); bind(2, GL_TEXTURE_2D, t->prev.tex); bind(3, GL_TEXTURE_2D, t->cnt[b].tex);
        glUseProgram(t->tmp);
        uni1i(t->tmp, "cur", 0); uni1i(t->tmp, "hist", 1); uni1i(t->tmp, "prv", 2); uni1i(t->tmp, "osc", 3); uni1i(t->tmp, "src", 4);
        glUniform2f(glGetUniformLocation(t->tmp, "px"), 1.0f / (float)ow, 1.0f / (float)oh);
        glUniform2f(glGetUniformLocation(t->tmp, "sz"), (float)sw, (float)sh);
        draw(t->tmp, vbo, out_fbo, ow, oh);
        t->flip = b;
    } else {  /* a restart: this picture is the "last" one, no oscillation seen yet */
        glUseProgram(t->blit); uni1i(t->blit, "tex", 0);
        draw(t->blit, vbo, t->cur[a].fbo, ow, oh);
        glBindFramebuffer(GL_FRAMEBUFFER, t->cnt[a].fbo); glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
    }
    bind(0, GL_TEXTURE_2D, out_tex);  /* the history is this output, the previous source this source */
    glUseProgram(t->blit); uni1i(t->blit, "tex", 0);
    draw(t->blit, vbo, t->hist.fbo, ow, oh);
    bind(0, GL_TEXTURE_EXTERNAL_OES, src);
    glUseProgram(t->copyx); uni1i(t->copyx, "tex", 0);
    draw(t->copyx, vbo, t->prev.fbo, sw, sh);
    glActiveTexture(GL_TEXTURE0);
    t->ok = 1; t->sw = sw; t->sh = sh; t->sharp = sharp;
}
