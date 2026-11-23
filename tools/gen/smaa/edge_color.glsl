#ifdef SRC_EXT
#extension GL_OES_EGL_image_external : require
#endif
precision highp float;
#ifdef SRC_EXT
uniform samplerExternalOES tex;
#else
uniform sampler2D tex;
#endif
uniform vec4 rt; varying vec2 uv; uniform float smaa_th, smaa_lca;
vec3 S(vec2 c) { return texture2D(tex, c).rgb; }
uniform float smaa_tg;  /* apklayer patch: the text gate: edges of a pixel whose 3x3 the RCAS text test (sharpen_fsr.h) calls text are dropped when its value is above this (< 0: off) */
float TL(vec3 c) { return c.b * 0.5 + (c.r * 0.5 + c.g); }
float textMask(vec2 c) {
  vec3 q[9];
  q[0] = S(c + rt.xy * vec2(-1.0, -1.0)); q[1] = S(c + rt.xy * vec2(0.0, -1.0)); q[2] = S(c + rt.xy * vec2(1.0, -1.0));
  q[3] = S(c + rt.xy * vec2(-1.0, 0.0)); q[4] = S(c); q[5] = S(c + rt.xy * vec2(1.0, 0.0));
  q[6] = S(c + rt.xy * vec2(-1.0, 1.0)); q[7] = S(c + rt.xy * vec2(0.0, 1.0)); q[8] = S(c + rt.xy * vec2(1.0, 1.0));
  float lo = 9.0, hi = -9.0; vec3 cLo = q[0], cHi = q[0];
  for (int k = 0; k < 9; k++) { float l = TL(q[k]); if (l < lo) { lo = l; cLo = q[k]; } if (l > hi) { hi = l; cHi = q[k]; } }
  float ctr = 0.5 * (hi - lo);
  vec3 ax = cHi - cLo; float axx = max(dot(ax, ax), 1e-6), res = 0.0;
  for (int k = 0; k < 9; k++) res = max(res, length(q[k] - cLo - ax * clamp(dot(q[k] - cLo, ax) / axx, 0.0, 1.0)));
  float L0 = TL(q[0]), L1 = TL(q[1]), L2 = TL(q[2]), L3 = TL(q[3]), L5 = TL(q[5]), L6 = TL(q[6]), L7 = TL(q[7]), L8 = TL(q[8]);
  float gx = (L2 + 2.0 * L5 + L8) - (L0 + 2.0 * L3 + L6), gy = (L6 + 2.0 * L7 + L8) - (L0 + 2.0 * L1 + L2);
  float axis = abs(abs(gx) - abs(gy)) / (abs(gx) + abs(gy) + 1e-4);
  return smoothstep(0.28, 0.5, ctr) * (1.0 - smoothstep(0.04, 0.10, res)) * smoothstep(0.3, 0.65, axis);
}
float D(vec3 a, vec3 b) { vec3 t = abs(a - b); return max(max(t.r, t.g), t.b); }
void main() {
  vec4 o0 = rt.xyxy * vec4(-1.0, 0.0, 0.0, -1.0) + uv.xyxy, o1 = rt.xyxy * vec4(1.0, 0.0, 0.0, 1.0) + uv.xyxy, o2 = rt.xyxy * vec4(-2.0, 0.0, 0.0, -2.0) + uv.xyxy;
  vec3 C = S(uv);
  vec4 d;
  d.x = D(C, S(o0.xy)); d.y = D(C, S(o0.zw));
  vec2 e = step(vec2(smaa_th), d.xy);
  if (e.x + e.y == 0.0) { gl_FragColor = vec4(0.0); return; }
  d.z = D(C, S(o1.xy)); d.w = D(C, S(o1.zw));
  vec2 md = max(d.xy, d.zw);
  d.z = D(C, S(o2.xy)); d.w = D(C, S(o2.zw));
  md = max(md, d.zw);
  e *= step(max(md.x, md.y), smaa_lca * d.xy);
  if (smaa_tg >= 0.0 && textMask(uv) > smaa_tg) e = vec2(0.0);
  gl_FragColor = vec4(e, 0.0, 0.0);
}
