/* Lanczos 3 and Anime4K's shaders (the render filter's modes 3 and 4; sharpen.c). */

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
