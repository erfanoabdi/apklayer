/* SMAA 1x for the render filter: see sharpen_smaa.h. Presets as in the reference SMAA.h:
 * LOW (threshold 0.15, 4 search steps, no diagonal, no corner), MEDIUM (0.1, 8, no diagonal, no corner), HIGH (0.1, 16, diagonal 8, corner 25), ULTRA (0.05, 32, diagonal 16, corner 25).
 * Luma edge detection for LOW and MEDIUM, colour edge detection for HIGH and ULTRA. */
#include "sharpen_smaa.h"

#include <GLES2/gl2ext.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "sharpen_smaa_area.h"
#include "sharpen_smaa_search.h"
#include "sharpen_smaa_fs.h"

static const struct { float th; int steps, diag, corner; } PRESET[4] = { { 0.15f, 4, 0, 0 }, { 0.1f, 8, 0, 0 }, { 0.1f, 16, 8, 25 }, { 0.05f, 32, 16, 25 } };

static GLuint make_tex(GLenum fmt, int w, int h, const void *data)
{
    GLuint t;
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glGenTextures(1, &t);
    glBindTexture(GL_TEXTURE_2D, t);
    glTexImage2D(GL_TEXTURE_2D, 0, fmt, w, h, 0, fmt, GL_UNSIGNED_BYTE, data);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return t;
}

struct smaa *smaa_create(GLuint (*prog)(const char *fs))
{
    struct smaa *a = calloc(1, sizeof *a);
    if (!a) return NULL;
    a->prog = prog;
    a->area = make_tex(GL_LUMINANCE_ALPHA, AREATEX_WIDTH, AREATEX_HEIGHT, areaTexBytes);
    a->search = make_tex(GL_LUMINANCE, SEARCHTEX_WIDTH, SEARCHTEX_HEIGHT, searchTexBytes);
    if (glGetError() != GL_NO_ERROR) { free(a); return NULL; }
    return a;
}

/* a pass program: the preset's macros, SRC_EXT, then the shader */
static GLuint build(struct smaa *a, const char *fs, int level, int ext)
{
    size_t n = strlen(fs) + 512;
    char *t = malloc(n);
    if (!t) return 0;
    snprintf(t, n, "%s#define SMAA_THRESHOLD %.3f\n#define SMAA_MAX_SEARCH_STEPS %d\n#define SMAA_MAX_SEARCH_STEPS_DIAG %d\n#define SMAA_CORNER_ROUNDING %d\n%s%s%s",
             ext ? "#define SRC_EXT 1\n" : "", PRESET[level].th, PRESET[level].steps, PRESET[level].diag ? PRESET[level].diag : 8, PRESET[level].corner ? PRESET[level].corner : 25,
             PRESET[level].diag ? "" : "#define SMAA_DISABLE_DIAG_DETECTION\n", PRESET[level].corner ? "" : "#define SMAA_DISABLE_CORNER_DETECTION\n", fs);
    GLuint p = a->prog(t);
    free(t);
    return p;
}

static void pass(GLuint p, GLuint dst, int w, int h, GLuint vbo)
{
    glBindFramebuffer(GL_FRAMEBUFFER, dst);
    glViewport(0, 0, w, h);
    glUseProgram(p);
    glUniform4f(glGetUniformLocation(p, "rt"), 1.0f / (float)w, 1.0f / (float)h, (float)w, (float)h);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
}

static void bind(int unit, GLenum target, GLuint tex)
{
    glActiveTexture(GL_TEXTURE0 + unit);
    glBindTexture(target, tex);
}

static void sampler(GLuint p, const char *name, int unit)
{
    glUseProgram(p);
    glUniform1i(glGetUniformLocation(p, name), unit);
}

int smaa_run(struct smaa *a, GLuint (*rt_ensure)(struct rt *, int, int), int ext, GLuint src, int w, int h, int level, GLuint dst, GLuint vbo)
{
    if (!a || level < 0 || level > 3 || !rt_ensure(&a->e, w, h) || !rt_ensure(&a->b, w, h)) return -1;
    int x = ext ? 1 : 0;
    GLenum st = ext ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D;
    if (!a->edge[level][x]) a->edge[level][x] = build(a, level < 2 ? SMAA_FS_EDGE_LUMA : SMAA_FS_EDGE_COLOR, level, ext);
    if (!a->wts[level]) a->wts[level] = build(a, SMAA_FS_WEIGHTS, level, 0);
    if (!a->blend[x]) a->blend[x] = build(a, SMAA_FS_BLEND, 0, ext);
    GLuint pe = a->edge[level][x], pw = a->wts[level], pb = a->blend[x];
    if (!pe || !pw || !pb) return -1;
    glDisable(GL_BLEND);
    bind(0, st, src);
    sampler(pe, "tex", 0);
    pass(pe, a->e.fbo, w, h, vbo);
    bind(0, GL_TEXTURE_2D, a->e.tex);
    bind(1, GL_TEXTURE_2D, a->area);
    bind(2, GL_TEXTURE_2D, a->search);
    sampler(pw, "edges", 0); sampler(pw, "area", 1); sampler(pw, "search", 2);
    pass(pw, a->b.fbo, w, h, vbo);
    bind(0, st, src);
    bind(1, GL_TEXTURE_2D, a->b.tex);
    sampler(pb, "tex", 0); sampler(pb, "blend", 1);
    pass(pb, dst, w, h, vbo);
    glActiveTexture(GL_TEXTURE0);
    return 0;
}
