/* The post passes of the GLES backend (sharpen.c): calm (FS_CALM) and a copy (FS_BLIT). */

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
