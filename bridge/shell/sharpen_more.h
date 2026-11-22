/* Lanczos 3 and Anime4K's shaders (the render filter's modes 5 and 6; sharpen.c). */

/* Lanczos 3: the windowed sinc over 6x6 texel-centre fetches, weights normalised; one pass. Below the source size it just samples. */
static const char *FS_LANCZOS =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision highp float;\n"
    "uniform samplerExternalOES tex; uniform vec2 src; uniform vec2 scl; varying vec2 uv;\n"
    "float L(float x) { x = abs(x); if (x < 1e-4) return 1.0; if (x >= 3.0) return 0.0; float p = 3.14159265 * x; return 3.0 * sin(p) * sin(p / 3.0) / (p * p); }\n"
    "void main() {\n"
    "  if (scl.x < 1.0 || scl.y < 1.0) { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); return; }\n"
    "  vec2 pos = uv * src - 0.5, ip = floor(pos), f = pos - ip;\n"
    "  vec3 sum = vec3(0.0); float wsum = 0.0;\n"
    "  for (int j = -2; j <= 3; j++) {\n"
    "    float wy = L(float(j) - f.y);\n"
    "    for (int i = -2; i <= 3; i++) {\n"
    "      float w = L(float(i) - f.x) * wy;\n"
    "      sum += texture2D(tex, (ip + vec2(float(i), float(j)) + 0.5) / src).rgb * w; wsum += w;\n"
    "    }\n"
    "  }\n"
    "  gl_FragColor = vec4(clamp(sum / wsum, 0.0, 1.0), 1.0);\n"
    "}\n";

/* Anime4K v3.2 "Upscale Original x2" (bloc97, MIT, github.com/bloc97/Anime4K, glsl/Upscale/Anime4K_Upscale_Original_x2.glsl) as GLES2: its mpv hooks are five
 * passes into RGBA8 textures of the output's size (the gradients are signed, stored as v * 0.5 + 0.5; the second sum's range 0..4 as v / 4), then the apply pass
 * pushes each pixel toward the line's darker side. Offsets are one source texel (`d`). Pass sources: A4K_PRE (the app's buffer, luma gradients), A4K_Y (the
 * previous gradients, vertical Sobel half + refinement), A4K_X (gradients of the sobel norm, gated), A4K_DIR (normalised direction), A4K_APPLY. */
#define A4K_HEAD "precision highp float;\nuniform vec2 d; varying vec2 uv;\n"
#define A4K_DEC "vec2 dec(vec4 c) { return vec2((c.x - 0.5) * 2.0, c.y * 4.0); }\n"
static const char *FS_A4K_PRE =
    "#extension GL_OES_EGL_image_external : require\n" A4K_HEAD
    "uniform samplerExternalOES tex;\n"
    "float lum(vec2 p) { return dot(vec3(0.299, 0.587, 0.114), texture2D(tex, p).rgb); }\n"
    "void main() {\n"
    "  float l = lum(uv + vec2(-d.x, 0.0)), c = lum(uv), r = lum(uv + vec2(d.x, 0.0));\n"
    "  gl_FragColor = vec4((r - l) * 0.5 + 0.5, (l + c + c + r) * 0.25, 0.0, 1.0);\n"
    "}\n";
/* from the gradients g: sobel norm and refinement strength (REFINE_STRENGTH 0.5, bias 0) */
static const char *FS_A4K_Y =
    A4K_HEAD A4K_DEC "uniform sampler2D g;\n"
    "float pw(float x) { float x2 = x * x, x3 = x2 * x, x4 = x2 * x2, x5 = x2 * x3;\n"
    "  return 11.68129591 * x5 - 42.46906057 * x4 + 60.28286266 * x3 - 41.84451327 * x2 + 14.05517353 * x - 1.081521930; }\n"
    "void main() {\n"
    "  vec2 t = dec(texture2D(g, uv + vec2(0.0, -d.y))), c = dec(texture2D(g, uv)), b = dec(texture2D(g, uv + vec2(0.0, d.y)));\n"
    "  float xg = t.x + c.x + c.x + b.x, yg = -t.y + b.y;\n"
    "  float n = clamp(sqrt(xg * xg + yg * yg), 0.0, 1.0);\n"
    "  gl_FragColor = vec4(n, clamp(pw(n) * 0.5, 0.0, 1.0), 0.0, 1.0);\n"
    "}\n";
/* gradients of the sobel norm n (gated by the refinement strength m = n's pass .y) */
static const char *FS_A4K_X =
    A4K_HEAD "uniform sampler2D n;\n"
    "void main() {\n"
    "  if (texture2D(n, uv).y < 0.1) { gl_FragColor = vec4(0.5, 0.0, 0.0, 1.0); return; }\n"
    "  float l = texture2D(n, uv + vec2(-d.x, 0.0)).x, c = texture2D(n, uv).x, r = texture2D(n, uv + vec2(d.x, 0.0)).x;\n"
    "  gl_FragColor = vec4((r - l) * 0.5 + 0.5, (l + c + c + r) * 0.25, 0.0, 1.0);\n"
    "}\n";
static const char *FS_A4K_DIR =
    A4K_HEAD A4K_DEC "uniform sampler2D g; uniform sampler2D n;\n"
    "void main() {\n"
    "  if (texture2D(n, uv).y < 0.1) { gl_FragColor = vec4(0.5, 0.5, 0.0, 1.0); return; }\n"
    "  vec2 t = dec(texture2D(g, uv + vec2(0.0, -d.y))), c = dec(texture2D(g, uv)), b = dec(texture2D(g, uv + vec2(0.0, d.y)));\n"
    "  float xg = t.x + c.x + c.x + b.x, yg = -t.y + b.y, nm = sqrt(xg * xg + yg * yg);\n"
    "  if (nm <= 0.001) { xg = 0.0; yg = 0.0; nm = 1.0; }\n"
    "  gl_FragColor = vec4(xg / nm * 0.5 + 0.5, yg / nm * 0.5 + 0.5, 0.0, 1.0);\n"
    "}\n";
static const char *FS_A4K_APPLY =
    "#extension GL_OES_EGL_image_external : require\n" A4K_HEAD
    "uniform samplerExternalOES tex; uniform sampler2D n; uniform sampler2D dir;\n"
    "void main() {\n"
    "  vec4 c0 = texture2D(tex, uv);\n"
    "  float dv = texture2D(n, uv).y;\n"
    "  vec2 dc = (texture2D(dir, uv).xy - 0.5) * 2.0;\n"
    "  if (dv < 0.1 || abs(dc.x) + abs(dc.y) <= 0.02) { gl_FragColor = vec4(c0.rgb, 1.0); return; }\n"
    "  vec4 xv = texture2D(tex, uv + vec2(d.x * -sign(dc.x), 0.0)), yv = texture2D(tex, uv + vec2(0.0, d.y * -sign(dc.y)));\n"
    "  float r = abs(dc.x) / (abs(dc.x) + abs(dc.y));\n"
    "  vec4 avg = r * xv + (1.0 - r) * yv;\n"
    "  gl_FragColor = vec4((avg * dv + c0 * (1.0 - dv)).rgb, 1.0);\n"
    "}\n";
