/* The neural upscale pass: see sharpen_nn.h. Feature maps are RGBA16F texture arrays (four channels per layer) kept at the size the valid 3x3 convolutions
 * leave (the input is replicate padded by 7: the first layer clamps its reads), so the result equals the reference's. One thread computes one pixel of
 * one four-channel output layer; the weights are mat4s in a storage buffer (tools/nnup/export_waifu.py). First version: no tiling, no half-precision maths. */
#include "sharpen_nn.h"

#include <EGL/egl.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl31.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define NL 6   /* 3x3 convolutions; then the 4x4 transposed one */
static const int NIN4[NL] = { 1, 4, 8, 16, 32, 32 }, NOUT4[NL] = { 4, 8, 16, 32, 32, 64 };

struct nn {
    GLuint conv0, conv, deconv, resample, blit_ext;
    GLuint wbuf, tex[2], out2x, blit_fbo, blit_tex;
    int w, h;                       /* the source size the textures were made for */
    int boff[NL + 1], moff[NL + 1]; /* offsets in vec4s: matrices and biases of each layer; [NL] the transposed conv */
    void (*dispatch)(GLuint, GLuint, GLuint);
    void (*bind_image)(GLuint, GLuint, GLint, GLboolean, GLint, GLenum, GLenum);
    void (*barrier)(GLbitfield);
    void (*tex_storage3)(GLenum, GLsizei, GLenum, GLsizei, GLsizei, GLsizei);
    void (*tex_storage2)(GLenum, GLsizei, GLenum, GLsizei, GLsizei);
    void (*bind_base)(GLenum, GLuint, GLuint);
};

static const char *CS_HEAD =
    "#version 310 es\nprecision highp float; precision highp int; precision highp image2DArray; precision highp sampler2DArray;\n"
    "layout(local_size_x = 8, local_size_y = 8) in;\n"
    "layout(std430, binding = 0) readonly buffer W { vec4 w[]; };\n"
    "layout(rgba16f, binding = 1) writeonly uniform image2DArray dst;\n"
    "uniform int mbase, bbase, nin4, ow, oh;\n";
static const char *CS_CONV =  /* 8x8 threads, each 2x2 pixels x 4 output layers; the input tile (16x16 + a 1 pixel border) of one input layer is staged in shared memory */
    "uniform sampler2DArray src;\n"
    "shared vec4 tile[18 * 18];\n"
    "void main() {\n"
    "  ivec2 org = ivec2(gl_WorkGroupID.xy) * 16; ivec2 lt = ivec2(gl_LocalInvocationID.xy); int lid = int(gl_LocalInvocationIndex);\n"
    "  int o0 = int(gl_WorkGroupID.z) * 4; ivec2 tsz = textureSize(src, 0).xy;\n"
    "  vec4 acc[16];\n"
    "  for (int ob = 0; ob < 4; ob++) { vec4 b = w[bbase + o0 + ob]; for (int p = 0; p < 4; p++) acc[ob * 4 + p] = b; }\n"
    "  for (int i = 0; i < nin4; i++) {\n"
    "    barrier();\n"
    "    for (int k = lid; k < 324; k += 64) tile[k] = texelFetch(src, ivec3(min(org + ivec2(k % 18, k / 18), tsz - 1), i), 0);\n"
    "    barrier();\n"
    "    for (int t = 0; t < 9; t++) {\n"
    "      int tx = t % 3, ty = t / 3; vec4 v0 = tile[(lt.y * 2 + ty) * 18 + lt.x * 2 + tx], v1 = tile[(lt.y * 2 + ty) * 18 + lt.x * 2 + tx + 1];\n"
    "      vec4 v2 = tile[(lt.y * 2 + ty + 1) * 18 + lt.x * 2 + tx], v3 = tile[(lt.y * 2 + ty + 1) * 18 + lt.x * 2 + tx + 1];\n"
    "      for (int ob = 0; ob < 4; ob++) {\n"
    "        int idx = mbase + (((o0 + ob) * 9 + t) * nin4 + i) * 4; mat4 m = mat4(w[idx], w[idx + 1], w[idx + 2], w[idx + 3]);\n"
    "        acc[ob * 4] += m * v0; acc[ob * 4 + 1] += m * v1; acc[ob * 4 + 2] += m * v2; acc[ob * 4 + 3] += m * v3;\n"
    "      }\n"
    "    }\n"
    "  }\n"
    "  for (int ob = 0; ob < 4; ob++) for (int p = 0; p < 4; p++) {\n"
    "    ivec2 q = org + lt * 2 + ivec2(p % 2, p / 2); vec4 a = acc[ob * 4 + p];\n"
    "    if (q.x < ow && q.y < oh) imageStore(dst, ivec3(q, o0 + ob), max(a, a * 0.1));\n"
    "  }\n"
    "}\n";
static const char *CS_CONV0 =  /* the picture itself: RGBA8, reads clamped (the reference's replicate padding of 7) */
    "uniform sampler2D src0;\n"
    "void main() {\n"
    "  ivec2 p = ivec2(gl_GlobalInvocationID.xy); int o = int(gl_GlobalInvocationID.z); if (p.x >= ow || p.y >= oh) return;\n"
    "  ivec2 sz = textureSize(src0, 0) - 1; vec4 acc = w[bbase + o];\n"
    "  for (int t = 0; t < 9; t++) {\n"
    "    ivec2 q = clamp(p + ivec2(t % 3, t / 3) - 7, ivec2(0), sz); int idx = mbase + (o * 9 + t) * 4; vec4 v = vec4(texelFetch(src0, q, 0).rgb, 0.0);\n"
    "    acc += mat4(w[idx], w[idx + 1], w[idx + 2], w[idx + 3]) * v;\n"
    "  }\n"
    "  imageStore(dst, ivec3(p, o), max(acc, acc * 0.1));\n"
    "}\n";
static const char *CS_DECONV =  /* out[o] = sum_i in[i] * W[o + 3 - 2i], 4x4 taps, two of which meet each axis; writes the 2x picture (a plain 2D image) */
    "uniform sampler2DArray src;\n"
    "void main() {\n"
    "  ivec2 o = ivec2(gl_GlobalInvocationID.xy); if (o.x >= ow || o.y >= oh) return;\n"
    "  ivec2 isz = textureSize(src, 0).xy; vec4 acc = w[bbase];\n"
    "  for (int ky = 0; ky < 4; ky++) { int ty = o.y + 3 - ky; if ((ty & 1) != 0 || ty < 0 || ty / 2 >= isz.y) continue;\n"
    "    for (int kx = 0; kx < 4; kx++) { int tx = o.x + 3 - kx; if ((tx & 1) != 0 || tx < 0 || tx / 2 >= isz.x) continue;\n"
    "      int idx = mbase + (ky * 4 + kx) * 4;\n"
    "      for (int i = 0; i < 64; i++) { vec4 v = texelFetch(src, ivec3(tx / 2, ty / 2, i), 0); acc += mat4(w[idx + i * 64], w[idx + i * 64 + 1], w[idx + i * 64 + 2], w[idx + i * 64 + 3]) * v; }\n"
    "    } }\n"
    "  imageStore(dst, ivec3(o, 0), vec4(acc.rgb, 1.0));\n"
    "}\n";
static const char *FS_RESAMPLE =  /* Catmull-Rom, widened by the shrink factor f (2x picture -> output: 1..2): the reference's bicubic with antialias */
    "precision highp float;\n"
    "uniform sampler2D tex; uniform vec2 f; uniform vec2 sz;\n"
    "float k(float x) { x = abs(x); return x < 1.0 ? (1.5 * x - 2.5) * x * x + 1.0 : x < 2.0 ? ((-0.5 * x + 2.5) * x - 4.0) * x + 2.0 : 0.0; }\n"
    "void main() {\n"
    "  vec2 c = gl_FragCoord.xy * f;\n"   /* output pixel centre in the 2x picture */
    "  vec2 fw = max(f, vec2(1.0)); vec2 r = ceil(2.0 * fw);\n"
    "  vec3 acc = vec3(0.0); float ws = 0.0;\n"
    "  for (float j = -r.y + 1.0; j <= r.y; j += 1.0) for (float i = -r.x + 1.0; i <= r.x; i += 1.0) {\n"
    "    vec2 q = floor(c) + vec2(i, j) + 0.5; vec2 d = (q - c) / f; float wt = k(d.x / max(fw.x / f.x, 1.0)) * k(d.y / max(fw.y / f.y, 1.0));\n"
    "    acc += wt * texture2D(tex, (clamp(q, vec2(0.5), sz - 0.5)) / sz).rgb; ws += wt;\n"
    "  }\n"
    "  gl_FragColor = vec4(clamp(acc / ws, 0.0, 1.0), 1.0);\n"
    "}\n";

static GLuint compute(const char *body, const char *name)
{
    size_t n = strlen(CS_HEAD) + strlen(body) + 1;
    char *t = malloc(n);
    if (!t) return 0;
    snprintf(t, n, "%s%s", CS_HEAD, body);
    GLuint s = glCreateShader(GL_COMPUTE_SHADER), p = 0;
    const char *src = t;
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(s, sizeof log, NULL, log);
        fprintf(stderr, "[sharpen] neural %s: %s\n", name, log);
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

struct nn *nn_create(GLuint (*prog)(const char *fs), const char *dflt)
{
    const char *ver = (const char *)glGetString(GL_VERSION);
    int maj = 0, min = 0;
    if (!ver || sscanf(strstr(ver, "ES ") ? strstr(ver, "ES ") + 3 : "", "%d.%d", &maj, &min) != 2 || maj * 10 + min < 31) return NULL;
    const char *path = getenv("APKLAYER_NN_WEIGHTS");
    if (!path || !*path) path = dflt;
    FILE *f = path ? fopen(path, "rb") : NULL;
    if (!f) { fprintf(stderr, "[sharpen] neural weights not found (%s)\n", path ? path : "no path"); return NULL; }
    fseek(f, 0, SEEK_END); long len = ftell(f); fseek(f, 0, SEEK_SET);
    struct nn *c = calloc(1, sizeof *c);
    float *wt = malloc((size_t)len);
    int off = 0;  /* vec4s */
    for (int l = 0; l < NL; l++) { c->moff[l] = off; off += NOUT4[l] * 9 * NIN4[l] * 4; c->boff[l] = off; off += NOUT4[l]; }
    c->moff[NL] = off; off += 64 * 16 * 4; c->boff[NL] = off; off += 1;
    if (!c || !wt || len != (long)off * 16 || fread(wt, 1, (size_t)len, f) != (size_t)len) {
        fprintf(stderr, "[sharpen] neural weights %s: wrong size (%ld, want %d)\n", path, len, off * 16);
        fclose(f); free(wt); free(c); return NULL;
    }
    fclose(f);
    c->dispatch = (void *)eglGetProcAddress("glDispatchCompute");
    c->bind_image = (void *)eglGetProcAddress("glBindImageTexture");
    c->barrier = (void *)eglGetProcAddress("glMemoryBarrier");
    c->tex_storage3 = (void *)eglGetProcAddress("glTexStorage3D");
    c->tex_storage2 = (void *)eglGetProcAddress("glTexStorage2D");
    c->bind_base = (void *)eglGetProcAddress("glBindBufferBase");
    char *conv = malloc(strlen(CS_CONV) + 1);
    strcpy(conv, CS_CONV);
    c->conv0 = compute(CS_CONV0, "conv0"); c->conv = compute(conv, "conv"); c->deconv = compute(CS_DECONV, "deconv"); free(conv);
    c->resample = prog(FS_RESAMPLE);
    c->blit_ext = prog("#extension GL_OES_EGL_image_external : require\nprecision highp float; uniform samplerExternalOES tex; varying vec2 uv; void main() { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); }");
    glGenBuffers(1, &c->wbuf);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, c->wbuf);
    glBufferData(GL_SHADER_STORAGE_BUFFER, len, wt, GL_STATIC_DRAW);
    free(wt);
    if (!c->dispatch || !c->bind_image || !c->barrier || !c->tex_storage3 || !c->tex_storage2 || !c->bind_base || !c->conv0 || !c->conv || !c->deconv || !c->resample || !c->blit_ext || glGetError() != GL_NO_ERROR) {
        free(c);
        return NULL;
    }
    return c;
}

static void param(GLuint t, GLenum tgt)
{
    glTexParameteri(tgt, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(tgt, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(tgt, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(tgt, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    (void)t;
}

static int ensure(struct nn *c, int w, int h)
{
    if (c->w == w && c->h == h) return 1;
    if (c->tex[0]) { glDeleteTextures(2, c->tex); glDeleteTextures(1, &c->out2x); glDeleteTextures(1, &c->blit_tex); glDeleteFramebuffers(1, &c->blit_fbo); }
    c->w = c->h = 0;
    glGenTextures(2, c->tex);
    for (int i = 0; i < 2; i++) { glBindTexture(GL_TEXTURE_2D_ARRAY, c->tex[i]); c->tex_storage3(GL_TEXTURE_2D_ARRAY, 1, GL_RGBA16F, w + 14, h + 14, 64); param(c->tex[i], GL_TEXTURE_2D_ARRAY); }
    glGenTextures(1, &c->out2x);
    glBindTexture(GL_TEXTURE_2D, c->out2x); c->tex_storage2(GL_TEXTURE_2D, 1, GL_RGBA16F, 2 * w, 2 * h); param(c->out2x, GL_TEXTURE_2D);
    glGenTextures(1, &c->blit_tex);
    glBindTexture(GL_TEXTURE_2D, c->blit_tex); c->tex_storage2(GL_TEXTURE_2D, 1, GL_RGBA8, w, h); param(c->blit_tex, GL_TEXTURE_2D);
    glGenFramebuffers(1, &c->blit_fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, c->blit_fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, c->blit_tex, 0);
    if (glGetError() != GL_NO_ERROR) return 0;
    c->w = w; c->h = h;
    return 1;
}

int nn_run(struct nn *c, GLuint (*rt_ensure)(struct rt *, int, int), GLuint src, int w, int h, GLuint dst, int ow, int oh, GLuint vbo)
{
    (void)rt_ensure;
    if (!c || !ensure(c, w, h)) return -1;
    /* the external image into a plain RGBA8 texture */
    glBindFramebuffer(GL_FRAMEBUFFER, c->blit_fbo);
    glViewport(0, 0, w, h);
    glUseProgram(c->blit_ext);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, src);
    glUniform1i(glGetUniformLocation(c->blit_ext, "tex"), 0);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    c->bind_base(GL_SHADER_STORAGE_BUFFER, 0, c->wbuf);
    int cw = w + 14, ch = h + 14;
    for (int l = 0; l < NL; l++) {
        cw -= 2; ch -= 2;
        GLuint prog = l == 0 ? c->conv0 : c->conv;
        glUseProgram(prog);
        glUniform1i(glGetUniformLocation(prog, "mbase"), c->moff[l]);
        glUniform1i(glGetUniformLocation(prog, "bbase"), c->boff[l]);
        glUniform1i(glGetUniformLocation(prog, "nin4"), NIN4[l]);
        glUniform1i(glGetUniformLocation(prog, "ow"), cw);
        glUniform1i(glGetUniformLocation(prog, "oh"), ch);
        glActiveTexture(GL_TEXTURE0);
        if (l == 0) { glBindTexture(GL_TEXTURE_2D, c->blit_tex); glUniform1i(glGetUniformLocation(prog, "src0"), 0); }
        else { glBindTexture(GL_TEXTURE_2D_ARRAY, c->tex[(l - 1) & 1]); glUniform1i(glGetUniformLocation(prog, "src"), 0); }
        c->bind_image(1, c->tex[l & 1], 0, GL_TRUE, 0, GL_WRITE_ONLY, GL_RGBA16F);
        if (l == 0) c->dispatch((cw + 7) / 8, (ch + 7) / 8, NOUT4[l]); else c->dispatch((cw + 15) / 16, (ch + 15) / 16, NOUT4[l] / 4);
        c->barrier(GL_TEXTURE_FETCH_BARRIER_BIT | GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }
    /* the transposed convolution: the last map is tex[(NL-1)&1], (w+2) x (h+2) */
    glUseProgram(c->deconv);
    glUniform1i(glGetUniformLocation(c->deconv, "mbase"), c->moff[NL]);
    glUniform1i(glGetUniformLocation(c->deconv, "bbase"), c->boff[NL]);
    glUniform1i(glGetUniformLocation(c->deconv, "ow"), 2 * w);
    glUniform1i(glGetUniformLocation(c->deconv, "oh"), 2 * h);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D_ARRAY, c->tex[(NL - 1) & 1]);
    glUniform1i(glGetUniformLocation(c->deconv, "src"), 0);
    c->bind_image(1, c->out2x, 0, GL_FALSE, 0, GL_WRITE_ONLY, GL_RGBA16F);
    c->dispatch((2 * w + 7) / 8, (2 * h + 7) / 8, 1);
    c->barrier(GL_TEXTURE_FETCH_BARRIER_BIT);
    /* the 2x picture to the output size */
    glBindFramebuffer(GL_FRAMEBUFFER, dst);
    glViewport(0, 0, ow, oh);
    glUseProgram(c->resample);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, c->out2x);
    glUniform1i(glGetUniformLocation(c->resample, "tex"), 0);
    glUniform2f(glGetUniformLocation(c->resample, "sz"), (float)(2 * w), (float)(2 * h));
    glUniform2f(glGetUniformLocation(c->resample, "f"), (float)(2 * w) / (float)ow, (float)(2 * h) / (float)oh);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    return glGetError() == GL_NO_ERROR ? 0 : -1;
}

void nn_free(struct nn *c)
{
    if (!c) return;
    if (c->tex[0]) { glDeleteTextures(2, c->tex); glDeleteTextures(1, &c->out2x); glDeleteTextures(1, &c->blit_tex); glDeleteFramebuffers(1, &c->blit_fbo); }
    free(c);
}
