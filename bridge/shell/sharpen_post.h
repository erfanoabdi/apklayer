/* The post passes of the GLES backend (sharpen.c): anti-aliasing (FS_AA), calm (FS_CALM) and a copy (FS_BLIT). */

/* Edge-directed anti-aliasing of the finished picture (the owner: PGR at 50% render scale has jagged lines and shimmer, the game's own picture has no AA). Written for
 * this shell (no code borrowed; the idea is the family of FXAA / morphological filters): the three colour channels give the edge direction (a structure tensor of Sobel gradients) and the largest colour step to a neighbour its strength (3D scenes: low contrast, hue-only edges); where there is an edge the pixel is blurred ALONG the edge with five bilinear taps (weights 3 2 2 1 1) and never across it, so stair steps are smoothed and strokes keep their
 * crispness; low-contrast areas are left alone. `sharp` is the strength and `longk` (0..1) blends in the longer run: both grow with the upscale ratio (sharpen.c aa_pass: the lower the render scale, the heavier). Reads a plain 2D texture (the upscaled picture), draws into the output. */
static const char *FS_AA =
    "precision highp float;\n"
    "uniform sampler2D tex; uniform vec2 px; uniform float sharp; uniform float longk; varying vec2 uv;\n"
    "vec3 T(vec2 o) { return texture2D(tex, uv + o * px).rgb; }\n"
    "float D(vec3 a, vec3 b) { vec3 d = abs(a - b); return max(d.r, max(d.g, d.b)); }\n"
    "void main() {\n"
    "  float ca = texture2D(tex, uv).a;  /* 1 - the text mask of the RCAS pass (sharpen_fsr.h): text pixels are smoothed less */\n"
    "  vec3 c = T(vec2(0.0)), n = T(vec2(0.0, -1.0)), s = T(vec2(0.0, 1.0)), e = T(vec2(1.0, 0.0)), w = T(vec2(-1.0, 0.0));\n"
    "  vec3 nw = T(vec2(-1.0, -1.0)), ne = T(vec2(1.0, -1.0)), sw = T(vec2(-1.0, 1.0)), se = T(vec2(1.0, 1.0));\n"
    "  /* how strong an edge is: the largest COLOUR step to a neighbour (a 3D scene's edges are often low in contrast or differ in hue only) */\n"
    "  float rng = max(max(D(c, n), D(c, s)), max(D(c, e), D(c, w)));\n"
    "  /* its direction: the structure of all three channels (gradient outer products summed), the edge runs along the weaker axis */\n"
    "  vec3 gx = (ne + 2.0 * e + se) - (nw + 2.0 * w + sw), gy = (sw + 2.0 * s + se) - (nw + 2.0 * n + ne);\n"
    "  float jxx = dot(gx, gx), jyy = dot(gy, gy), jxy = dot(gx, gy);\n"
    "  if (rng < 0.015 || jxx + jyy < 0.0001) { gl_FragColor = vec4(c, ca); return; }\n"
    "  float th = 0.5 * atan(2.0 * jxy, jxx - jyy);  /* the gradient's angle */\n"
    "  vec2 t = vec2(-sin(th), cos(th));              /* along the edge */\n"
    "  vec3 blur = (c * 3.0 + (T(t) + T(-t)) * 2.0 + T(t * 2.0) + T(-t * 2.0)) / 9.0;\n"
    "  vec3 blurL = (c * 3.0 + (T(t) + T(-t)) * 2.0 + (T(t * 2.0) + T(-t * 2.0)) * 2.0 + (T(t * 3.0) + T(-t * 3.0)) * 1.5 + T(t * 4.0) + T(-t * 4.0)) / 16.0;  /* a longer run, for the longer steps of a bigger upscale */\n"
    "  blur = mix(blur, blurL, longk);\n"
    "  gl_FragColor = vec4(mix(c, blur, smoothstep(0.015, 0.08, rng) * sharp * ca * ca), ca);\n"
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
