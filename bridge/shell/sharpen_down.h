/* The downscale shader (sharpen.c: used when the app's buffer is larger than the output, i.e. render scale above 100): a
 * Lanczos 2 area filter. The kernel is stretched by the ratio source/output on each axis (so every source texel under the output pixel counts, nothing
 * aliases as bilinear does at ratios like 1.33 / 1.5), over texel-centre fetches, weights normalised; one pass. An axis that is not larger than the output
 * keeps ratio 1 (a plain Lanczos 2 interpolation there). Handles ratios up to 3 (taps -6..6). Uniforms: tex, src (the source size), scl (output / source). */
static const char *FS_DOWN =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision highp float;\n"
    "uniform samplerExternalOES tex; uniform vec2 src; uniform vec2 scl; varying vec2 uv;\n"
    "float L2(float x) { x = abs(x); if (x < 1e-4) return 1.0; if (x >= 2.0) return 0.0; float p = 3.14159265 * x; return 2.0 * sin(p) * sin(p * 0.5) / (p * p); }\n"
    "void main() {\n"
    "  vec2 r = max(1.0 / scl, vec2(1.0));\n"
    "  r = min(r, vec2(3.0));\n"
    "  vec2 pos = uv * src - 0.5, ip = floor(pos);\n"
    "  vec3 sum = vec3(0.0); float wsum = 0.0;\n"
    "  for (int j = -6; j <= 6; j++) {\n"
    "    float dy = (ip.y + float(j) - pos.y) / r.y;\n"
    "    if (abs(dy) < 2.0) {\n"
    "      float wy = L2(dy);\n"
    "      for (int i = -6; i <= 6; i++) {\n"
    "        float dx = (ip.x + float(i) - pos.x) / r.x;\n"
    "        if (abs(dx) < 2.0) {\n"
    "          float w = L2(dx) * wy;\n"
    "          sum += texture2D(tex, (ip + vec2(float(i), float(j)) + 0.5) / src).rgb * w; wsum += w;\n"
    "        }\n"
    "      }\n"
    "    }\n"
    "  }\n"
    "  gl_FragColor = vec4(clamp(sum / wsum, 0.0, 1.0), 1.0);\n"
    "}\n";
