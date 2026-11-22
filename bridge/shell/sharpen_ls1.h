/* "LS1-like": an own edge-preserving upscaler in the spirit of Lossless Scaling's LS1 (that one is closed source; nothing of it is copied or claimed to match).
 * One pass in GLES2: Catmull-Rom bicubic (16 texel-centre fetches, so the sampler's filter does not matter), de-ringing against the four texels around the
 * sample, then a local-contrast sharpen that is limited to the same range plus a small overshoot. Below the source size (render scale above 100) it just samples. */
static const char *FS_LS1 =
    "#extension GL_OES_EGL_image_external : require\n"
    "precision highp float;\n"
    "uniform samplerExternalOES tex; uniform vec2 src; uniform float sharp; uniform vec2 scl; varying vec2 uv;\n"
    "vec3 T(vec2 p) { return texture2D(tex, (p + 0.5) / src).rgb; }\n"
    "vec4 W(float t) { float t2 = t * t, t3 = t2 * t;\n"
    "  return vec4(-0.5 * t3 + t2 - 0.5 * t, 1.5 * t3 - 2.5 * t2 + 1.0, -1.5 * t3 + 2.0 * t2 + 0.5 * t, 0.5 * t3 - 0.5 * t2); }\n"
    "vec3 R(vec2 b, vec4 wx) { return T(b + vec2(-1.0, 0.0)) * wx.x + T(b) * wx.y + T(b + vec2(1.0, 0.0)) * wx.z + T(b + vec2(2.0, 0.0)) * wx.w; }\n"
    "void main() {\n"
    "  if (scl.x < 1.0 || scl.y < 1.0) { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); return; }\n"
    "  vec2 pos = uv * src - 0.5, ip = floor(pos), f = pos - ip;\n"
    "  vec4 wx = W(f.x), wy = W(f.y);\n"
    "  vec3 c = R(ip + vec2(0.0, -1.0), wx) * wy.x + R(ip, wx) * wy.y + R(ip + vec2(0.0, 1.0), wx) * wy.z + R(ip + vec2(0.0, 2.0), wx) * wy.w;\n"
    "  vec3 a = T(ip), b = T(ip + vec2(1.0, 0.0)), d = T(ip + vec2(0.0, 1.0)), e = T(ip + vec2(1.0, 1.0));\n"
    "  vec3 mn = min(min(a, b), min(d, e)), mx = max(max(a, b), max(d, e));\n"
    "  c = clamp(c, mn, mx);  /* no ringing at edges */\n"
    "  vec3 blur = (a + b + d + e) * 0.25;\n"
    "  c += (c - blur) * sharp * 0.6;\n"
    "  vec3 o = (mx - mn) * 0.15 + 0.02;  /* a little overshoot keeps the sharpening visible */\n"
    "  gl_FragColor = vec4(clamp(c, max(mn - o, 0.0), min(mx + o, 1.0)), 1.0);\n"
    "}\n";
