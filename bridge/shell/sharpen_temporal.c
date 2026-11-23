/* The temporal stabiliser of the render filter (GLES backend; apklayer patch, not AMD's). The app's frames come without motion vectors, so the previous output is blended into this
 * one only where the app's own pixels changed by less than a small tolerance (TOL0..TOL1 8 bit levels, the largest per-channel change over the four source texels round the
 * pixel; the weight W at the low end, fading to 0 at the high one), and the history is clamped to the min/max of this frame's 3x3 around the pixel, so changing content cannot trail.
 * It calms what crawls between frames in low-contrast detail (foliage, grass, dithering, +-2 LSB noise); a moving edge, text and scrolling are not touched
 * (tests/vm/shimmer.py, tests/vm/ghost.py, docs/FILTER-EVAL.md "Shimmer"). The Vulkan backend has none. */
#include "sharpen_temporal.h"

#include <GLES2/gl2ext.h>
#include <stdlib.h>

static const char *FS_COPYX =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision highp float;\n"
    "uniform samplerExternalOES tex; varying vec2 uv;\n"
    "void main() { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); }\n";
static const char *FS_BLIT =
    "precision highp float;\n"
    "uniform sampler2D tex; varying vec2 uv;\n"
    "void main() { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); }\n";
static const char *FS_TMP =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision highp float;\n"
    "uniform sampler2D cur, hist, prv; uniform samplerExternalOES src; uniform vec2 px, sz; varying vec2 uv;\n"
    "const float TOL0 = 10.0, TOL1 = 30.0, W = 0.5;\n"
    "void main() {\n"
    "  vec3 c = texture2D(cur, uv).rgb, mn = c, mx = c;\n"
    "  for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) { vec3 q = texture2D(cur, uv + vec2(float(i), float(j)) * px).rgb; mn = min(mn, q); mx = max(mx, q); }\n"
    "  vec2 f = floor(uv * sz - 0.5);\n"
    "  float d = 0.0;\n"
    "  for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) { vec2 q = (f + vec2(float(i), float(j)) + 0.5) / sz; vec3 a = abs(texture2D(src, q).rgb - texture2D(prv, q).rgb); d = max(d, max(a.r, max(a.g, a.b))); }\n"
    "  float w = W * (1.0 - smoothstep(TOL0, TOL1, d * 255.0));\n"
    "  gl_FragColor = vec4(mix(c, clamp(texture2D(hist, uv).rgb, mn, mx), w), 1.0);\n"
    "}\n";

struct tmpl {
    GLuint tmp, copyx, blit;
    struct rt cur, hist, prev;  /* this frame's finished picture, the last output, the last source */
    int ok, sw, sh;
    float sharp;
};

struct tmpl *tmpl_create(GLuint (*prog)(const char *fs))
{
    struct tmpl *t = calloc(1, sizeof *t);
    if (!t) return NULL;
    t->tmp = prog(FS_TMP); t->copyx = prog(FS_COPYX); t->blit = prog(FS_BLIT);
    if (!t->tmp || !t->copyx || !t->blit) { free(t); return NULL; }
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
    if (!rt_ensure(&t->cur, ow, oh) || !rt_ensure(&t->hist, ow, oh) || !rt_ensure(&t->prev, sw, sh)) { t->ok = 0; return; }
    glDisable(GL_BLEND);
    bind(0, GL_TEXTURE_2D, out_tex);  /* the output as a texture: it is read below, so its filter must not use mipmaps */
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    if (t->ok) {
        glUseProgram(t->blit); uni1i(t->blit, "tex", 0);
        draw(t->blit, vbo, t->cur.fbo, ow, oh);
        glUseProgram(t->tmp);
        bind(0, GL_TEXTURE_2D, t->cur.tex); bind(1, GL_TEXTURE_2D, t->hist.tex); bind(2, GL_TEXTURE_2D, t->prev.tex); bind(3, GL_TEXTURE_EXTERNAL_OES, src);
        uni1i(t->tmp, "cur", 0); uni1i(t->tmp, "hist", 1); uni1i(t->tmp, "prv", 2); uni1i(t->tmp, "src", 3);
        glUniform2f(glGetUniformLocation(t->tmp, "px"), 1.0f / (float)ow, 1.0f / (float)oh);
        glUniform2f(glGetUniformLocation(t->tmp, "sz"), (float)sw, (float)sh);
        draw(t->tmp, vbo, out_fbo, ow, oh);
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
