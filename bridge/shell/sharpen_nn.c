/* The neural upscale pass: see sharpen_nn.h. Feature maps are RGBA16F texture arrays (four channels per layer) kept at the size the valid 3x3 convolutions
 * leave (the input is replicate padded by 7: the first layer clamps its reads), so the result equals the reference's. One thread computes one pixel of
 * one four-channel output layer; the weights are mat4s in a storage buffer. The network is a distilled student of waifu2x upconv_7 (tools/nnup/distill.py,
 * export_student.py): n valid 3x3 convolutions, the last with 12 channels, pixel shuffle, added to a bilinear 2x of the input. No half-precision maths yet. */
#include "sharpen_nn.h"

#include <EGL/egl.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl31.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAXL 12
struct nn {
    GLuint conv0, conv[3], shuffle, resample, blit_ext;   /* conv[k]: 4 >> k output layers per thread */
    GLuint wbuf, tex[2], out2x;
    struct { GLuint tex, fbo; int w, h; } snap[2];  /* RGBA8 copies of the app's frame (nn_capture): the app's buffer is free as soon as the copy is done */
    int w, h;                       /* the source size the textures were made for */
    int n, in4[MAXL], out4[MAXL], moff[MAXL], boff[MAXL], maxout4;  /* layers; offsets in vec4s: matrices and biases of each layer */
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
    "uniform int mbase, bbase, nin4, ow, oh; uniform float slope;\n";
static const char *CS_CONV =  /* 8x8 threads, each 2x2 pixels x OB output layers (a define: 4, 2 or 1); the input tile (16x16 + a 1 pixel border) of one input layer is staged in shared memory */
    "uniform sampler2DArray src;\n"
    "shared vec4 tile[18 * 18];\n"
    "void main() {\n"
    "  ivec2 org = ivec2(gl_WorkGroupID.xy) * 16; ivec2 lt = ivec2(gl_LocalInvocationID.xy); int lid = int(gl_LocalInvocationIndex);\n"
    "  int o0 = int(gl_WorkGroupID.z) * OB; ivec2 tsz = textureSize(src, 0).xy;\n"
    "  vec4 acc[OB * 4];\n"
    "  for (int ob = 0; ob < OB; ob++) { vec4 b = w[bbase + o0 + ob]; for (int p = 0; p < 4; p++) acc[ob * 4 + p] = b; }\n"
    "  for (int i = 0; i < nin4; i++) {\n"
    "    barrier();\n"
    "    for (int k = lid; k < 324; k += 64) tile[k] = texelFetch(src, ivec3(min(org + ivec2(k % 18, k / 18), tsz - 1), i), 0);\n"
    "    barrier();\n"
    "    for (int t = 0; t < 9; t++) {\n"
    "      int tx = t % 3, ty = t / 3; vec4 v0 = tile[(lt.y * 2 + ty) * 18 + lt.x * 2 + tx], v1 = tile[(lt.y * 2 + ty) * 18 + lt.x * 2 + tx + 1];\n"
    "      vec4 v2 = tile[(lt.y * 2 + ty + 1) * 18 + lt.x * 2 + tx], v3 = tile[(lt.y * 2 + ty + 1) * 18 + lt.x * 2 + tx + 1];\n"
    "      for (int ob = 0; ob < OB; ob++) {\n"
    "        int idx = mbase + (((o0 + ob) * 9 + t) * nin4 + i) * 4; mat4 m = mat4(w[idx], w[idx + 1], w[idx + 2], w[idx + 3]);\n"
    "        acc[ob * 4] += m * v0; acc[ob * 4 + 1] += m * v1; acc[ob * 4 + 2] += m * v2; acc[ob * 4 + 3] += m * v3;\n"
    "      }\n"
    "    }\n"
    "  }\n"
    "  for (int ob = 0; ob < OB; ob++) for (int p = 0; p < 4; p++) {\n"
    "    ivec2 q = org + lt * 2 + ivec2(p % 2, p / 2); vec4 a = acc[ob * 4 + p];\n"
    "    if (q.x < ow && q.y < oh) imageStore(dst, ivec3(q, o0 + ob), max(a, a * slope));\n"
    "  }\n"
    "}\n";
static const char *CS_CONV0 =  /* the picture itself: RGBA8, reads clamped (replicate padding by the number of layers) */
    "uniform sampler2D src0; uniform int pad;\n"
    "void main() {\n"
    "  ivec2 p = ivec2(gl_GlobalInvocationID.xy); int o = int(gl_GlobalInvocationID.z); if (p.x >= ow || p.y >= oh) return;\n"
    "  ivec2 sz = textureSize(src0, 0) - 1; vec4 acc = w[bbase + o];\n"
    "  for (int t = 0; t < 9; t++) {\n"
    "    ivec2 q = clamp(p + ivec2(t % 3, t / 3) - pad, ivec2(0), sz); int idx = mbase + (o * 9 + t) * 4; vec4 v = vec4(texelFetch(src0, q, 0).rgb, 0.0);\n"
    "    acc += mat4(w[idx], w[idx + 1], w[idx + 2], w[idx + 3]) * v;\n"
    "  }\n"
    "  imageStore(dst, ivec3(p, o), max(acc, acc * slope));\n"
    "}\n";
static const char *CS_SHUFFLE =  /* pixel shuffle of the 12 channels (layer c = colour c, component 2i+j = row i, column j of the 2x2 block) + the bilinear 2x of the input (the blit texture is LINEAR) */
    "uniform sampler2DArray src; uniform sampler2D lin; uniform float cm;\n"
    "void main() {\n"
    "  ivec2 o = ivec2(gl_GlobalInvocationID.xy); if (o.x >= ow || o.y >= oh) return;\n"
    "  ivec2 h = o >> 1; int k = (o.y & 1) * 2 + (o.x & 1);\n"
    "  vec3 r = vec3(texelFetch(src, ivec3(h, 0), 0)[k], texelFetch(src, ivec3(h, 1), 0)[k], texelFetch(src, ivec3(h, 2), 0)[k]);\n"
    "  vec3 b = texture(lin, (vec2(o) + 0.5) / vec2(ow, oh)).rgb; vec3 v = b + r;\n"
    "  if (cm >= 0.0) {\n"   /* apklayer patch: clamp to the min/max of the 3x3 source pixels around this one (+ margin cm): no halos */
    "    ivec2 sz = textureSize(lin, 0) - 1; vec3 lo = vec3(1.0), hi = vec3(0.0);\n"
    "    for (int t = 0; t < 9; t++) { vec3 s = texelFetch(lin, clamp(h + ivec2(t % 3 - 1, t / 3 - 1), ivec2(0), sz), 0).rgb; lo = min(lo, s); hi = max(hi, s); }\n"
    "    v = clamp(v, lo - cm, hi + cm);\n"
    "  }\n"
    "  imageStore(dst, ivec3(o, 0), vec4(clamp(v, 0.0, 1.0), 1.0));\n"
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

static GLuint compute(const char *body, const char *name, int ob)
{
    size_t n = strlen(CS_HEAD) + strlen(body) + 64;
    char *t = malloc(n);
    if (!t) return 0;
    snprintf(t, n, "%s#define OB %d\n%s", CS_HEAD, ob, body);
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
    unsigned hd[2 + 2 * MAXL];
    float *wt = malloc((size_t)len);
    int off = 0, ok = c && wt && fread(hd, 4, 2, f) == 2 && hd[0] == 0x3153534e && hd[1] >= 2 && hd[1] <= MAXL && fread(hd + 2, 4, 2 * hd[1], f) == 2 * hd[1];
    if (ok) {
        c->n = (int)hd[1];
        for (int l = 0; l < c->n; l++) {
            c->in4[l] = (int)hd[2 + 2 * l]; c->out4[l] = (int)hd[3 + 2 * l];
            c->moff[l] = off; off += c->out4[l] * 9 * c->in4[l] * 4; c->boff[l] = off; off += c->out4[l];
            if (c->out4[l] > c->maxout4) c->maxout4 = c->out4[l];
            if (l && c->in4[l] != c->out4[l - 1]) ok = 0;
        }
        if (c->out4[c->n - 1] != 3 || c->in4[0] != 1) ok = 0;
        long hl = 8 + 8 * (long)c->n;
        ok = ok && len - hl == (long)off * 16 && fread(wt, 1, (size_t)len - (size_t)hl, f) == (size_t)len - (size_t)hl;
    }
    fclose(f);
    if (!ok) { fprintf(stderr, "[sharpen] neural weights %s: not a valid network file\n", path); free(wt); free(c); return NULL; }
    c->dispatch = (void *)eglGetProcAddress("glDispatchCompute");
    c->bind_image = (void *)eglGetProcAddress("glBindImageTexture");
    c->barrier = (void *)eglGetProcAddress("glMemoryBarrier");
    c->tex_storage3 = (void *)eglGetProcAddress("glTexStorage3D");
    c->tex_storage2 = (void *)eglGetProcAddress("glTexStorage2D");
    c->bind_base = (void *)eglGetProcAddress("glBindBufferBase");
    c->conv0 = compute(CS_CONV0, "conv0", 1);
    for (int k = 0; k < 3; k++) c->conv[k] = compute(CS_CONV, "conv", 4 >> k);
    c->shuffle = compute(CS_SHUFFLE, "shuffle", 1);
    c->resample = prog(FS_RESAMPLE);
    c->blit_ext = prog("#extension GL_OES_EGL_image_external : require\nprecision highp float; uniform samplerExternalOES tex; varying vec2 uv; void main() { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); }");
    glGenBuffers(1, &c->wbuf);
    glBindBuffer(GL_SHADER_STORAGE_BUFFER, c->wbuf);
    glBufferData(GL_SHADER_STORAGE_BUFFER, (GLsizeiptr)off * 16, wt, GL_STATIC_DRAW);
    free(wt);
    if (!c->dispatch || !c->bind_image || !c->barrier || !c->tex_storage3 || !c->tex_storage2 || !c->bind_base || !c->conv0 || !c->conv[0] || !c->conv[1] || !c->conv[2] || !c->shuffle || !c->resample || !c->blit_ext || glGetError() != GL_NO_ERROR) {
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
    if (c->tex[0]) { glDeleteTextures(2, c->tex); glDeleteTextures(1, &c->out2x); }
    c->w = c->h = 0;
    glGenTextures(2, c->tex);
    for (int i = 0; i < 2; i++) { glBindTexture(GL_TEXTURE_2D_ARRAY, c->tex[i]); c->tex_storage3(GL_TEXTURE_2D_ARRAY, 1, GL_RGBA16F, w + 2 * c->n, h + 2 * c->n, c->maxout4); param(c->tex[i], GL_TEXTURE_2D_ARRAY); }
    glGenTextures(1, &c->out2x);
    glBindTexture(GL_TEXTURE_2D, c->out2x); c->tex_storage2(GL_TEXTURE_2D, 1, GL_RGBA16F, 2 * w, 2 * h); param(c->out2x, GL_TEXTURE_2D);
    if (glGetError() != GL_NO_ERROR) return 0;
    c->w = w; c->h = h;
    return 1;
}

/* the app's external image into snapshot `snap` (a plain RGBA8 texture, LINEAR): after the GPU has run this the app's buffer is no longer read */
int nn_capture(struct nn *c, GLuint src, int w, int h, int snap, GLuint vbo)
{
    if (!c || snap < 0 || snap > 1 || w <= 0 || h <= 0) return -1;
    if (c->snap[snap].w != w || c->snap[snap].h != h) {
        if (c->snap[snap].tex) { glDeleteTextures(1, &c->snap[snap].tex); glDeleteFramebuffers(1, &c->snap[snap].fbo); }
        c->snap[snap].w = c->snap[snap].h = 0;
        glGenTextures(1, &c->snap[snap].tex);
        glBindTexture(GL_TEXTURE_2D, c->snap[snap].tex); c->tex_storage2(GL_TEXTURE_2D, 1, GL_RGBA8, w, h); param(c->snap[snap].tex, GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glGenFramebuffers(1, &c->snap[snap].fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, c->snap[snap].fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, c->snap[snap].tex, 0);
        if (glGetError() != GL_NO_ERROR) return -1;
        c->snap[snap].w = w; c->snap[snap].h = h;
    }
    glBindFramebuffer(GL_FRAMEBUFFER, c->snap[snap].fbo);
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
    return glGetError() == GL_NO_ERROR ? 0 : -1;
}

int nn_snap_size(struct nn *c, int snap, int *w, int *h)
{
    if (!c || snap < 0 || snap > 1 || c->snap[snap].w <= 0) return -1;
    *w = c->snap[snap].w; *h = c->snap[snap].h;
    return 0;
}

int nn_run(struct nn *c, int snap, GLuint dst, int ow, int oh, GLuint vbo)
{
    int w, h;
    if (nn_snap_size(c, snap, &w, &h) < 0 || !ensure(c, w, h)) return -1;
    GLuint sntex = c->snap[snap].tex;
    c->bind_base(GL_SHADER_STORAGE_BUFFER, 0, c->wbuf);
    int cw = w + 2 * c->n, ch = h + 2 * c->n;
    for (int l = 0; l < c->n; l++) {
        cw -= 2; ch -= 2;
        int ob = l == 0 ? 1 : c->out4[l] % 4 == 0 ? 4 : c->out4[l] % 2 == 0 ? 2 : 1;
        GLuint prog = l == 0 ? c->conv0 : c->conv[ob == 4 ? 0 : ob == 2 ? 1 : 2];
        glUseProgram(prog);
        glUniform1i(glGetUniformLocation(prog, "mbase"), c->moff[l]);
        glUniform1i(glGetUniformLocation(prog, "bbase"), c->boff[l]);
        glUniform1i(glGetUniformLocation(prog, "nin4"), c->in4[l]);
        glUniform1i(glGetUniformLocation(prog, "ow"), cw);
        glUniform1i(glGetUniformLocation(prog, "oh"), ch);
        glUniform1f(glGetUniformLocation(prog, "slope"), l == c->n - 1 ? 1.0f : 0.1f);  /* LeakyReLU 0.1 on every layer but the last (max(a, a * 1) = a) */
        glActiveTexture(GL_TEXTURE0);
        if (l == 0) { glBindTexture(GL_TEXTURE_2D, sntex); glUniform1i(glGetUniformLocation(prog, "src0"), 0); glUniform1i(glGetUniformLocation(prog, "pad"), c->n); }
        else { glBindTexture(GL_TEXTURE_2D_ARRAY, c->tex[(l - 1) & 1]); glUniform1i(glGetUniformLocation(prog, "src"), 0); }
        c->bind_image(1, c->tex[l & 1], 0, GL_TRUE, 0, GL_WRITE_ONLY, GL_RGBA16F);
        if (l == 0) c->dispatch((cw + 7) / 8, (ch + 7) / 8, c->out4[0]); else c->dispatch((cw + 15) / 16, (ch + 15) / 16, c->out4[l] / ob);
        c->barrier(GL_TEXTURE_FETCH_BARRIER_BIT | GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }
    /* pixel shuffle + the bilinear 2x of the input */
    glUseProgram(c->shuffle);
    glUniform1i(glGetUniformLocation(c->shuffle, "ow"), 2 * w);
    glUniform1i(glGetUniformLocation(c->shuffle, "oh"), 2 * h);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D_ARRAY, c->tex[(c->n - 1) & 1]);
    glUniform1i(glGetUniformLocation(c->shuffle, "src"), 0);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, sntex);
    glUniform1i(glGetUniformLocation(c->shuffle, "lin"), 1);
    { const char *e = getenv("APKLAYER_NN_CLAMP"); glUniform1f(glGetUniformLocation(c->shuffle, "cm"), e ? (float)atof(e) : 0.0f); }  /* the margin of the clamp to the source 3x3 min/max (0: hard, as FSR 1's own clamp); a negative value turns it off (test knob) */
    glActiveTexture(GL_TEXTURE0);
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
    if (c->tex[0]) { glDeleteTextures(2, c->tex); glDeleteTextures(1, &c->out2x); }
    for (int i = 0; i < 2; i++) if (c->snap[i].tex) { glDeleteTextures(1, &c->snap[i].tex); glDeleteFramebuffers(1, &c->snap[i].fbo); }
    free(c);
}
