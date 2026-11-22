/* The upscale pass: see sharpen.h. EGL on the first render node that imports the app's dmabuf, two GLES2 passes: FSR 1's EASU upscale into an
 * intermediate texture, then its RCAS sharpening into one of three exportable dmabufs. */
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
#include <string.h>
#include <unistd.h>

#include "sharpen_nis.h"

#define BUFS 8
#define SLOTS 5
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
    PFNEGLCREATESYNCKHRPROC create_sync;      /* native fences: wait for the GPU asleep in poll(), glFinish spins a CPU core on NVIDIA */
    PFNEGLDESTROYSYNCKHRPROC destroy_sync;
    PFNEGLDUPNATIVEFENCEFDANDROIDPROC dup_fence;
    struct { EGLImage image; GLuint tex; int w, h; uint32_t fourcc; } in[BUFS];
    GLuint up_prog, cas_prog, nis_prog, mid_tex, mid_fbo, vbo;
    int mode;  /* 0: FSR 1, 1: NIS */
    int mid_w, mid_h;
    struct slot out[SLOTS];
    int last;
    uint64_t accept[64];
    int accept_n;
};

static const char *VS = "attribute vec2 p; varying vec2 uv; void main() { uv = p * 0.5 + 0.5; gl_Position = vec4(p, 0.0, 1.0); }";

/* AMD FidelityFX Super Resolution 1 (MIT): EASU, an edge adaptive upscale (12 taps, a Lanczos-like kernel stretched along the edge it finds), then RCAS,
 * a sharpening that limits itself so it cannot ring. Written for GLES2 (no gather: the taps are fetched at texel centres) after AMD's ffx_fsr1.h.
 * A source larger than the output (render scale above 100) is averaged down instead. */
static const char *FS_UP =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision highp float;\n"
    "uniform samplerExternalOES tex; uniform vec2 src; uniform float down; varying vec2 uv;\n"
    "vec3 T(vec2 q) { return texture2D(tex, (q + 0.5) / src).rgb; }\n"
    "float L(vec3 c) { return c.g + 0.5 * (c.r + c.b); }\n"
    "void setf(inout vec2 dir, inout float len, vec2 pp, float w, float a, float b, float c, float d, float e) {\n"
    "  float dc = d - c, cb = c - b, lenX = 1.0 / max(max(abs(dc), abs(cb)), 0.00001), dirX = d - b;\n"
    "  dir.x += dirX * w;\n"
    "  lenX = clamp(abs(dirX) * lenX, 0.0, 1.0); lenX *= lenX; len += lenX * w;\n"
    "  float ec = e - c, ca = c - a, lenY = 1.0 / max(max(abs(ec), abs(ca)), 0.00001), dirY = e - a;\n"
    "  dir.y += dirY * w;\n"
    "  lenY = clamp(abs(dirY) * lenY, 0.0, 1.0); lenY *= lenY; len += lenY * w;\n"
    "}\n"
    "void tap(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len, float lob, float clp, vec3 c) {\n"
    "  vec2 v = vec2(off.x * dir.x + off.y * dir.y, -off.x * dir.y + off.y * dir.x) * len;\n"
    "  float d2 = min(dot(v, v), clp);\n"
    "  float wB = 0.4 * d2 - 1.0, wA = lob * d2 - 1.0;\n"
    "  wB *= wB; wA *= wA;\n"
    "  wB = 1.5625 * wB - 0.5625;\n"
    "  float w = wB * wA;\n"
    "  aC += c * w; aW += w;\n"
    "}\n"
    "void main() {\n"
    "  if (down > 0.5) {  /* a larger source than the output: the four taps around the pixel average it */\n"
    "    vec2 h = 0.5 / src * down;\n"
    "    vec3 r = 0.25 * (texture2D(tex, uv + vec2(-h.x, -h.y)).rgb + texture2D(tex, uv + vec2(h.x, -h.y)).rgb + texture2D(tex, uv + vec2(-h.x, h.y)).rgb + texture2D(tex, uv + vec2(h.x, h.y)).rgb);\n"
    "    gl_FragColor = vec4(r, 1.0); return;\n"
    "  }\n"
    "  vec2 pos = uv * src - 0.5, fp = floor(pos), pp = pos - fp;\n"
    "  /*   b c        f g j k are the four texels round the sample\n"
    "     e f g h\n"
    "     i j k l\n"
    "       n o   */\n"
    "  vec3 b = T(fp + vec2(0.0, -1.0)), c = T(fp + vec2(1.0, -1.0)), e = T(fp + vec2(-1.0, 0.0)), f = T(fp), g = T(fp + vec2(1.0, 0.0)), h = T(fp + vec2(2.0, 0.0));\n"
    "  vec3 i = T(fp + vec2(-1.0, 1.0)), j = T(fp + vec2(0.0, 1.0)), k = T(fp + vec2(1.0, 1.0)), l = T(fp + vec2(2.0, 1.0)), n = T(fp + vec2(0.0, 2.0)), o = T(fp + vec2(1.0, 2.0));\n"
    "  float bL = L(b), cL = L(c), eL = L(e), fL = L(f), gL = L(g), hL = L(h), iL = L(i), jL = L(j), kL = L(k), lL = L(l), nL = L(n), oL = L(o);\n"
    "  vec2 dir = vec2(0.0); float len = 0.0;\n"
    "  setf(dir, len, pp, (1.0 - pp.x) * (1.0 - pp.y), bL, eL, fL, gL, jL);\n"
    "  setf(dir, len, pp, pp.x * (1.0 - pp.y), cL, fL, gL, hL, kL);\n"
    "  setf(dir, len, pp, (1.0 - pp.x) * pp.y, fL, iL, jL, kL, nL);\n"
    "  setf(dir, len, pp, pp.x * pp.y, gL, jL, kL, lL, oL);\n"
    "  float dirR = dot(dir, dir);\n"
    "  bool zro = dirR < (1.0 / 32768.0);\n"
    "  dirR = zro ? 1.0 : inversesqrt(dirR);\n"
    "  dir.x = zro ? 1.0 : dir.x;\n"
    "  dir *= dirR;\n"
    "  len = len * 0.5; len *= len;\n"
    "  float stretch = dot(dir, dir) / max(abs(dir.x), abs(dir.y));\n"
    "  vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);\n"
    "  float lob = 0.5 + ((0.25 - 0.04) - 0.5) * len, clp = 1.0 / lob;\n"
    "  vec3 aC = vec3(0.0); float aW = 0.0;\n"
    "  tap(aC, aW, vec2(0.0, -1.0) - pp, dir, len2, lob, clp, b);\n"
    "  tap(aC, aW, vec2(1.0, -1.0) - pp, dir, len2, lob, clp, c);\n"
    "  tap(aC, aW, vec2(-1.0, 0.0) - pp, dir, len2, lob, clp, e);\n"
    "  tap(aC, aW, vec2(0.0, 0.0) - pp, dir, len2, lob, clp, f);\n"
    "  tap(aC, aW, vec2(1.0, 0.0) - pp, dir, len2, lob, clp, g);\n"
    "  tap(aC, aW, vec2(2.0, 0.0) - pp, dir, len2, lob, clp, h);\n"
    "  tap(aC, aW, vec2(-1.0, 1.0) - pp, dir, len2, lob, clp, i);\n"
    "  tap(aC, aW, vec2(0.0, 1.0) - pp, dir, len2, lob, clp, j);\n"
    "  tap(aC, aW, vec2(1.0, 1.0) - pp, dir, len2, lob, clp, k);\n"
    "  tap(aC, aW, vec2(2.0, 1.0) - pp, dir, len2, lob, clp, l);\n"
    "  tap(aC, aW, vec2(0.0, 2.0) - pp, dir, len2, lob, clp, n);\n"
    "  tap(aC, aW, vec2(1.0, 2.0) - pp, dir, len2, lob, clp, o);\n"
    "  vec3 pix = aC / aW;  /* ringing: not beyond the four nearest texels */\n"
    "  pix = clamp(pix, min(min(f, g), min(j, k)), max(max(f, g), max(j, k)));\n"
    "  gl_FragColor = vec4(clamp(pix, 0.0, 1.0), 1.0);\n"
    "}\n";

/* RCAS: sharpening from the 5-tap cross, the strength per pixel limited so the result stays inside the range (no clipping, no halos); `sharp` 0..1 is
 * FSR's sharpness in stops (1: the strongest, 0: two stops weaker) */
static const char *FS_CAS =
    "precision highp float;\n"
    "uniform sampler2D tex; uniform vec2 px; uniform float sharp; varying vec2 uv;\n"
    "float L(vec3 c) { return c.g + 0.5 * (c.r + c.b); }\n"
    "void main() {\n"
    "  vec3 b = texture2D(tex, uv + vec2(0.0, -px.y)).rgb, d = texture2D(tex, uv + vec2(-px.x, 0.0)).rgb, e = texture2D(tex, uv).rgb;\n"
    "  vec3 f = texture2D(tex, uv + vec2(px.x, 0.0)).rgb, h = texture2D(tex, uv + vec2(0.0, px.y)).rgb;\n"
    "  float bL = L(b), dL = L(d), eL = L(e), fL = L(f), hL = L(h);\n"
    "  float nz = 0.25 * (bL + dL + fL + hL) - eL;\n"
    "  float rng = max(max(max(bL, dL), max(eL, fL)), hL) - min(min(min(bL, dL), min(eL, fL)), hL);\n"
    "  nz = clamp(abs(nz) / max(rng, 0.00001), 0.0, 1.0);\n"
    "  nz = -0.5 * nz + 1.0;\n"
    "  vec3 mn4 = min(min(b, d), min(f, h)), mx4 = max(max(b, d), max(f, h));\n"
    "  vec3 hitMin = min(mn4, e) / max(4.0 * mx4, 0.0001);\n"
    "  vec3 hitMax = (1.0 - max(mx4, e)) / min(4.0 * mn4 - 4.0, -0.0001);\n"
    "  vec3 lobeV = max(-hitMin, hitMax);\n"
    "  float lobe = max(-0.1875, min(max(lobeV.r, max(lobeV.g, lobeV.b)), 0.0)) * exp2(-2.0 * (1.0 - sharp)) * nz;\n"
    "  gl_FragColor = vec4(clamp((lobe * (b + d + h + f) + e) / (4.0 * lobe + 1.0), 0.0, 1.0), 1.0);\n"
    "}\n";

/* NVIDIA Image Scaling (NIS, MIT): a 6-tap polyphase scaler on luma blended with four directional filters (0, 45, 90, 135 degrees, weighted by an edge
 * map) and an adaptive unsharp mask; chroma comes from one bilinear tap. This is NVScaler of NIS_Scaler.h written as a fragment shader (no shared memory:
 * every pixel reads its own 6x6 luma). NVIDIA specifies 1x to 2x upscaling; the coefficient banks are in sharpen_nis.h. One pass, no RCAS. */
static const char *FS_NIS =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision highp float;\n"
    "uniform samplerExternalOES tex; uniform vec2 src; uniform float sharp; uniform vec4 cs[128]; uniform vec4 cu[128]; varying vec2 uv;\n"
    "float p[36];\n"
    "#define W(r, c) p[(r) * 6 + (c)]\n"
    "float Y(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }\n"
    "vec4 edgeMap(float a, float b, float c, float d, float e, float f, float g, float h, float i) {\n"
    "  float g0 = abs(a + b + c - g - h - i), g45 = abs(d + a + b - h - i - f), g90 = abs(a + d + g - c - f - i), g135 = abs(d + g + h - b - c - f);\n"
    "  float m09 = max(g0, g90), n09 = min(g0, g90), m45 = max(g45, g135), n45 = min(g45, g135);\n"
    "  if (m09 + m45 == 0.0) return vec4(0.0);\n"
    "  float e09 = min(m09 / (m09 + m45), 1.0), e45 = 1.0 - e09;\n"
    "  bool c09 = (m09 > n09 * 2.201171875) && (m09 > 0.0625) && (m09 > n45);\n"
    "  bool c45 = (m45 > n45 * 2.201171875) && (m45 > 0.0625) && (m45 > n09);\n"
    "  bool is0 = m09 == g0, is45 = m45 == g45;\n"
    "  float f09 = (c09 && c45) ? e09 : 1.0, f45 = (c09 && c45) ? e45 : 1.0;\n"
    "  return vec4((c09 && is0) ? f09 : 0.0, (c09 && !is0) ? f09 : 0.0, (c45 && is45) ? f45 : 0.0, (c45 && !is45) ? f45 : 0.0);\n"
    "}\n"
    "#define EDGE(r, c) edgeMap(W(r, c), W(r, (c) + 1), W(r, (c) + 2), W((r) + 1, c), W((r) + 1, (c) + 1), W((r) + 1, (c) + 2), W((r) + 2, c), W((r) + 2, (c) + 1), W((r) + 2, (c) + 2))\n"
    "float lti(float p0, float p1, float p2, float p3, float p4, float p5, int ph) {\n"
    "  bool sel = ph <= 32;\n"
    "  float s0 = sel ? p0 : p3, aMin = min(min(p1, p2), s0), aMax = max(max(p1, p2), s0);\n"
    "  s0 = sel ? p2 : p5;\n"
    "  float bMin = min(min(p3, p4), s0), bMax = max(max(p3, p4), s0), ac = aMax - aMin, bc = bMax - bMin;\n"
    "  float ratio = max(ac, bc) / (min(ac, bc) + 0.00392157);\n"
    "  return 1.0 - clamp((ratio - 2.0) * 0.125, 0.0, 1.0);\n"
    "}\n"
    "float poly6(float p0, float p1, float p2, float p3, float p4, float p5, int ph) {\n"
    "  vec4 a = cs[ph * 2], b = cs[ph * 2 + 1], ua = cu[ph * 2], ub = cu[ph * 2 + 1];\n"
    "  vec4 q = vec4(p0, p1, p2, p3);\n"
    "  float y = dot(a, q) + b.x * p4 + b.y * p5, yu = dot(ua, q) + ub.x * p4 + ub.y * p5;\n"
    "  float sl = sharp - 0.5, k = sl >= 0.0 ? 1.25 : 1.0, kMax = sl >= 0.0 ? 1.25 : 1.75;\n"
    "  float strMin = max(0.0, 0.4 + sl * k * 1.2), strMax = 1.6 + sl * kMax * 1.8, limMin = max(0.1, 0.14 + sl * k * 0.32), limMax = 0.5 + sl * k * 0.6;\n"
    "  float ys = 1.0 - clamp((y - 0.45) * (1.0 / 0.45), 0.0, 1.0);\n"
    "  yu *= ys * (strMax - strMin) + strMin;\n"
    "  float lim = (ys * (limMax - limMin) + limMin) * y;\n"
    "  yu = min(lim, max(-lim, yu));\n"
    "  return y + yu * lti(p0, p1, p2, p3, p4, p5, ph);\n"
    "}\n"
    "int phase(float f) { return int(min(f * 64.0, 63.0)); }\n"
    "void main() {\n"
    "  vec2 pos = uv * src - 0.5, fl = floor(pos), fr = pos - fl;\n"
    "  int fxi = phase(fr.x), fyi = phase(fr.y);\n"
    "  for (int i = 0; i < 6; i++)\n"
    "    for (int j = 0; j < 6; j++) p[i * 6 + j] = Y(texture2D(tex, (fl + vec2(float(j) - 2.0, float(i) - 2.0) + 0.5) / src).rgb);\n"
    "  vec4 e00 = EDGE(1, 1), e01 = EDGE(1, 2), e10 = EDGE(2, 1), e11 = EDGE(2, 2);\n"
    "  vec4 w = mix(mix(e00, e01, fr.x), mix(e10, e11, fr.x), fr.y);\n"
    "  float base = 1.0 - w.x - w.y - w.z - w.w;\n"
    "  vec4 yA = cs[fyi * 2], yB = cs[fyi * 2 + 1], xA = cs[fxi * 2], xB = cs[fxi * 2 + 1];\n"
    "#define COLV(j) (p[j] * yA.x + p[6 + (j)] * yA.y + p[12 + (j)] * yA.z + p[18 + (j)] * yA.w + p[24 + (j)] * yB.x + p[30 + (j)] * yB.y)\n"
    "  float opY = (COLV(0) * xA.x + COLV(1) * xA.y + COLV(2) * xA.z + COLV(3) * xA.w + COLV(4) * xB.x + COLV(5) * xB.y) * base;\n"
    "  if (w.x > 0.0)  /* 0 degrees */\n"
    "    opY += poly6(mix(W(0, 2), W(0, 3), fr.x), mix(W(1, 2), W(1, 3), fr.x), mix(W(2, 2), W(2, 3), fr.x), mix(W(3, 2), W(3, 3), fr.x), mix(W(4, 2), W(4, 3), fr.x), mix(W(5, 2), W(5, 3), fr.x), fyi) * w.x;\n"
    "  if (w.y > 0.0)  /* 90 degrees */\n"
    "    opY += poly6(mix(W(2, 0), W(3, 0), fr.y), mix(W(2, 1), W(3, 1), fr.y), mix(W(2, 2), W(3, 2), fr.y), mix(W(2, 3), W(3, 3), fr.y), mix(W(2, 4), W(3, 4), fr.y), mix(W(2, 5), W(3, 5), fr.y), fxi) * w.y;\n"
    "  if (w.z > 0.0) {  /* 45 degrees */\n"
    "    float pb = 0.5 + 0.5 * (fr.x - fr.y);\n"
    "    float t1 = mix(W(2, 1), W(1, 2), pb), t3 = mix(W(3, 2), W(2, 3), pb), t5 = mix(W(4, 3), W(3, 4), pb);\n"
    "    float d = pb - 0.5, ad = abs(d); bool up = d >= 0.0;\n"
    "    float t0 = mix(W(1, 1), up ? W(0, 2) : W(2, 0), ad), t2 = mix(W(2, 2), up ? W(1, 3) : W(3, 1), ad), t4 = mix(W(3, 3), up ? W(2, 4) : W(4, 2), ad), t6 = mix(W(4, 4), up ? W(3, 5) : W(5, 3), ad);\n"
    "    float pp = fr.x + fr.y;\n"
    "    if (pp >= 1.0) opY += poly6(t1, t2, t3, t4, t5, t6, phase(pp - 1.0)) * w.z;\n"
    "    else opY += poly6(t0, t1, t2, t3, t4, t5, phase(pp)) * w.z;\n"
    "  }\n"
    "  if (w.w > 0.0) {  /* 135 degrees */\n"
    "    float pb = 0.5 * (fr.x + fr.y);\n"
    "    float t1 = mix(W(3, 1), W(4, 2), pb), t3 = mix(W(2, 2), W(3, 3), pb), t5 = mix(W(1, 3), W(2, 4), pb);\n"
    "    float d = pb - 0.5, ad = abs(d); bool up = d >= 0.0;\n"
    "    float t0 = mix(W(4, 1), up ? W(5, 2) : W(3, 0), ad), t2 = mix(W(3, 2), up ? W(4, 3) : W(2, 1), ad), t4 = mix(W(2, 3), up ? W(3, 4) : W(1, 2), ad), t6 = mix(W(1, 4), up ? W(2, 5) : W(0, 3), ad);\n"
    "    float pp = 1.0 + (fr.x - fr.y);\n"
    "    if (pp >= 1.0) opY += poly6(t1, t2, t3, t4, t5, t6, phase(pp - 1.0)) * w.w;\n"
    "    else opY += poly6(t0, t1, t2, t3, t4, t5, phase(pp)) * w.w;\n"
    "  }\n"
    "  vec3 op = texture2D(tex, uv).rgb;\n"
    "  gl_FragColor = vec4(clamp(op + (opY - Y(op)), 0.0, 1.0), 1.0);\n"
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
        s->nis_prog = program(FS_NIS);  /* optional: without it the NIS mode draws with FSR */
        if (!s->nis_prog) fprintf(stderr, "[sharpen] NIS shader unavailable on this GPU: FSR 1 is used\n");
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

void sharpen_set_mode(struct sharpen *s, int mode) { if (s) s->mode = mode; }

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
    if (s->mode == 1 && s->nis_prog) {  /* NIS: one pass, straight into the output dmabuf */
        glBindFramebuffer(GL_FRAMEBUFFER, o->fbo);
        glViewport(0, 0, ow, oh);
        glUseProgram(s->nis_prog);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, s->in[buf].tex);
        glUniform1i(glGetUniformLocation(s->nis_prog, "tex"), 0);
        glUniform2f(glGetUniformLocation(s->nis_prog, "src"), (float)s->in[buf].w, (float)s->in[buf].h);
        glUniform1f(glGetUniformLocation(s->nis_prog, "sharp"), sharpness < 0 ? 0 : sharpness > 1 ? 1 : sharpness);
        glUniform4fv(glGetUniformLocation(s->nis_prog, "cs"), 128, nis_coef_scale);
        glUniform4fv(glGetUniformLocation(s->nis_prog, "cu"), 128, nis_coef_usm);
        glBindBuffer(GL_ARRAY_BUFFER, s->vbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, 0);
        draw();
        gpu_wait(s);
        o->busy = 1;
        s->last = k;
        *slot = k;
        return 0;
    }
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
    gpu_wait(s);  /* the compositor reads the dmabuf next: no explicit sync here */
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
