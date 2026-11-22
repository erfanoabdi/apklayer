/* Lanczos 3 and Anime4K's shaders (the render filter's modes 3 and 4; sharpen.c). */

/* Lanczos 3: the windowed sinc over 6x6 texel-centre fetches, weights normalised; one pass. Anti-ringing: the result is clamped to the range of the 2x2 nearest
 * texels, widened by 0.2 * sharp (mpv's antiring idea, here a hard clamp), and `sharp` also drives an unsharp step against the bilinear tap (restores the edge
 * contrast the clamp takes). A source larger than the output is averaged by exact pixel-footprint area (8x8 fetches). */
static const char *FS_LANCZOS =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision highp float;\n"
    "uniform samplerExternalOES tex; uniform vec2 src; uniform vec2 scl; uniform float sharp; varying vec2 uv;\n"
    "float L(float x) { x = abs(x); if (x < 1e-4) return 1.0; if (x >= 3.0) return 0.0; float p = 3.14159265 * x; return 3.0 * sin(p) * sin(p / 3.0) / (p * p); }\n"
    "void main() {\n"
    "  vec2 pos = uv * src - 0.5, ip = floor(pos), f = pos - ip;\n"
    "  vec3 sum = vec3(0.0); float wsum = 0.0;\n"
    "  if (scl.x < 1.0 || scl.y < 1.0) {  /* a larger source than the output: exact area average of the pixel's footprint (up to 2x; wider is cut and renormalised) */\n"
    "    vec2 c = uv * src, h = 0.5 / min(scl, vec2(1.0)), k0 = floor(c) - 3.0;\n"
    "    for (int j = 0; j < 8; j++) {\n"
    "      float y = k0.y + float(j), wy = max(0.0, min(y + 1.0, c.y + h.y) - max(y, c.y - h.y));\n"
    "      for (int i = 0; i < 8; i++) {\n"
    "        float x = k0.x + float(i), w = max(0.0, min(x + 1.0, c.x + h.x) - max(x, c.x - h.x)) * wy;\n"
    "        sum += texture2D(tex, (vec2(x, y) + 0.5) / src).rgb * w; wsum += w;\n"
    "      }\n"
    "    }\n"
    "    gl_FragColor = vec4(clamp(sum / wsum, 0.0, 1.0), 1.0); return;\n"
    "  }\n"
    "  vec3 mn = vec3(1.0), mx = vec3(0.0);  /* the 2x2 texels around the pixel bound the result (anti-ringing) */\n"
    "  for (int j = -2; j <= 3; j++) {\n"
    "    float wy = L(float(j) - f.y);\n"
    "    for (int i = -2; i <= 3; i++) {\n"
    "      float w = L(float(i) - f.x) * wy;\n"
    "      vec3 c = texture2D(tex, (ip + vec2(float(i), float(j)) + 0.5) / src).rgb;\n"
    "      sum += c * w; wsum += w;\n"
    "      if (i >= 0 && i <= 1 && j >= 0 && j <= 1) { mn = min(mn, c); mx = max(mx, c); }\n"
    "    }\n"
    "  }\n"
    "  vec3 o = sum / wsum;\n"
    "  o += 1.0 * sharp * (o - texture2D(tex, uv).rgb);\n"
    "  float m = 0.2 * sharp; o = clamp(o, mn - (mx - mn) * m, mx + (mx - mn) * m);\n"
    "  gl_FragColor = vec4(clamp(o, 0.0, 1.0), 1.0);\n"
    "}\n";

/* Anime4K v3.2 "Upscale CNN x2 (S)" (bloc97, MIT, github.com/bloc97/Anime4K, glsl/Upscale/Anime4K_Upscale_CNN_x2_S.glsl): a four-layer convolution net run on the
 * source (half-float targets, sharpen_a4k.h): FS_A4K_C1..C4 (generated: sharpen_a4k_cnn.h, tools/gen/a4k-cnn.py) leave four numbers per source texel, FS_A4K_D2S
 * (depth-to-space) adds them as a residual to the bilinear 2x picture, FS_A4K_FIN resamples that 2x picture to the output size (Lanczos 3, as mode 3).
 * The CNN is trained on drawings: it beats Lanczos/FSR clearly on lines and edges, and loses 1.5-2.5 dB on smooth noisy images. */
#include "sharpen_a4k_cnn.h"
/* 2x: the app's buffer (tex, bilinear) + the net's output g (nearest) for the output texel's own sub-pixel (c[y*2+x]) */
static const char *FS_A4K_D2S =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision highp float;\n"
    "uniform samplerExternalOES tex; uniform sampler2D g; uniform vec2 src; varying vec2 uv;\n"
    "void main() {\n"
    "  vec2 f = mod(floor(uv * src * 2.0), 2.0);\n"
    "  vec4 m = vec4((1.0 - f.x) * (1.0 - f.y), f.x * (1.0 - f.y), (1.0 - f.x) * f.y, f.x * f.y);\n"
    "  gl_FragColor = vec4(clamp(texture2D(tex, uv).rgb + dot(texture2D(g, uv), m), 0.0, 1.0), 1.0);\n"
    "}\n";
/* Lanczos 3 of the 2x picture g (src = its size) to the output */
static const char *FS_A4K_FIN =
    "precision highp float;\n"
    "uniform sampler2D g; uniform vec2 src; varying vec2 uv;\n"
    "float L(float x) { x = abs(x); if (x < 1e-4) return 1.0; if (x >= 3.0) return 0.0; float p = 3.14159265 * x; return 3.0 * sin(p) * sin(p / 3.0) / (p * p); }\n"
    "void main() {\n"
    "  vec2 pos = uv * src - 0.5, ip = floor(pos), f = pos - ip;\n"
    "  vec3 sum = vec3(0.0); float wsum = 0.0;\n"
    "  for (int j = -2; j <= 3; j++) {\n"
    "    float wy = L(float(j) - f.y);\n"
    "    for (int i = -2; i <= 3; i++) {\n"
    "      float w = L(float(i) - f.x) * wy;\n"
    "      sum += texture2D(g, (ip + vec2(float(i), float(j)) + 0.5) / src).rgb * w; wsum += w;\n"
    "    }\n"
    "  }\n"
    "  gl_FragColor = vec4(clamp(sum / wsum, 0.0, 1.0), 1.0);\n"
    "}\n";

/* Edge-directed anti-aliasing of the finished picture (the owner: PGR at 50% render scale has jagged lines and shimmer, the game's own picture has no AA). Written for
 * this shell (no code borrowed; the idea is the family of FXAA / morphological filters): the three colour channels give the edge direction (a structure tensor of Sobel gradients) and the largest colour step to a neighbour its strength (3D scenes: low contrast, hue-only edges); where there is an edge the pixel is blurred ALONG the edge with five bilinear taps (weights 3 2 2 1 1) and never across it, so stair steps are smoothed and strokes keep their
 * crispness; low-contrast areas are left alone. `sharp` is the strength and `longk` (0..1) blends in the longer run: both grow with the upscale ratio (sharpen.c aa_pass: the lower the render scale, the heavier). Reads a plain 2D texture (the upscaled picture), draws into the output. */
static const char *FS_AA =
    "precision highp float;\n"
    "uniform sampler2D tex; uniform vec2 px; uniform float sharp; uniform float longk; varying vec2 uv;\n"
    "vec3 T(vec2 o) { return texture2D(tex, uv + o * px).rgb; }\n"
    "float D(vec3 a, vec3 b) { vec3 d = abs(a - b); return max(d.r, max(d.g, d.b)); }\n"
    "void main() {\n"
    "  vec3 c = T(vec2(0.0)), n = T(vec2(0.0, -1.0)), s = T(vec2(0.0, 1.0)), e = T(vec2(1.0, 0.0)), w = T(vec2(-1.0, 0.0));\n"
    "  vec3 nw = T(vec2(-1.0, -1.0)), ne = T(vec2(1.0, -1.0)), sw = T(vec2(-1.0, 1.0)), se = T(vec2(1.0, 1.0));\n"
    "  /* how strong an edge is: the largest COLOUR step to a neighbour (a 3D scene's edges are often low in contrast or differ in hue only) */\n"
    "  float rng = max(max(D(c, n), D(c, s)), max(D(c, e), D(c, w)));\n"
    "  /* its direction: the structure of all three channels (gradient outer products summed), the edge runs along the weaker axis */\n"
    "  vec3 gx = (ne + 2.0 * e + se) - (nw + 2.0 * w + sw), gy = (sw + 2.0 * s + se) - (nw + 2.0 * n + ne);\n"
    "  float jxx = dot(gx, gx), jyy = dot(gy, gy), jxy = dot(gx, gy);\n"
    "  if (rng < 0.015 || jxx + jyy < 0.0001) { gl_FragColor = vec4(c, 1.0); return; }\n"
    "  float th = 0.5 * atan(2.0 * jxy, jxx - jyy);  /* the gradient's angle */\n"
    "  vec2 t = vec2(-sin(th), cos(th));              /* along the edge */\n"
    "  vec3 blur = (c * 3.0 + (T(t) + T(-t)) * 2.0 + T(t * 2.0) + T(-t * 2.0)) / 9.0;\n"
    "  vec3 blurL = (c * 3.0 + (T(t) + T(-t)) * 2.0 + (T(t * 2.0) + T(-t * 2.0)) * 2.0 + (T(t * 3.0) + T(-t * 3.0)) * 1.5 + T(t * 4.0) + T(-t * 4.0)) / 16.0;  /* a longer run, for the longer steps of a bigger upscale */\n"
    "  blur = mix(blur, blurL, longk);\n"
    "  gl_FragColor = vec4(mix(c, blur, smoothstep(0.015, 0.08, rng) * sharp), 1.0);\n"
    "}\n";

/* "Calm" (opt-in, for noisy foliage and grass at a low render scale; written for this shell): (1) temporal: where the picture has not changed since the last frame
 * (static camera, only the game's own sub-pixel noise shimmers) the pixel is blended with the previous OUTPUT frame, so the noise averages out; any real change
 * (a pan, an animation) takes the new pixel, so there is no ghost trail to speak of; (2) spatial: textured areas without a coherent edge (leaves, grass: high range, low
 * gradient coherence) are pulled toward their 3x3 mean. `tstr` and `sstr` are the strengths, `use` is 0 for the first frame (no history). */
static const char *FS_CALM =
    "precision highp float;\n"
    "uniform sampler2D cur; uniform sampler2D hist; uniform vec2 px; uniform float tstr; uniform float sstr; uniform float use; varying vec2 uv;\n"
    "float L(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }\n"
    "vec3 C(vec2 o) { return texture2D(cur, uv + o * px).rgb; }\n"
    "void main() {\n"
    "  vec3 c = C(vec2(0.0)), n = C(vec2(0.0, -1.0)), s = C(vec2(0.0, 1.0)), e = C(vec2(1.0, 0.0)), w = C(vec2(-1.0, 0.0));\n"
    "  vec3 nw = C(vec2(-1.0, -1.0)), ne = C(vec2(1.0, -1.0)), sw = C(vec2(-1.0, 1.0)), se = C(vec2(1.0, 1.0));\n"
    "  float lc = L(c), ln = L(n), ls = L(s), le = L(e), lw = L(w), lnw = L(nw), lne = L(ne), lsw = L(sw), lse = L(se);\n"
    "  float gx = (lne + 2.0 * le + lse) - (lnw + 2.0 * lw + lsw), gy = (lsw + 2.0 * ls + lse) - (lnw + 2.0 * ln + lne);\n"
    "  float rng = max(max(max(lc, ln), max(ls, le)), lw) - min(min(min(lc, ln), min(ls, le)), lw);\n"
    "  float coh = clamp(length(vec2(gx, gy)) / (rng * 4.0 + 0.0001), 0.0, 1.0);\n"
    "  vec3 mean = (c + n + s + e + w + nw + ne + sw + se) / 9.0;\n"
    "  vec3 o = mix(c, mean, smoothstep(0.06, 0.2, rng) * (1.0 - coh) * sstr);\n"
    "  vec3 h = texture2D(hist, uv).rgb;\n"
    "  float d = max(abs(lc - L(h)), 0.5 * (abs(L(n) - L(texture2D(hist, uv + vec2(0.0, -px.y)).rgb)) + abs(L(s) - L(texture2D(hist, uv + vec2(0.0, px.y)).rgb))));\n"
    "  float k = use * tstr * (1.0 - smoothstep(0.03, 0.12, d));\n"
    "  gl_FragColor = vec4(mix(o, h, k), 1.0);\n"
    "}\n";
static const char *FS_BLIT =
    "precision highp float;\n"
    "uniform sampler2D tex; varying vec2 uv;\n"
    "void main() { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); }\n";
