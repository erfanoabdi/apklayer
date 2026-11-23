#ifdef SRC_EXT
#extension GL_OES_EGL_image_external : require
#endif
precision highp float;
#ifdef SRC_EXT
uniform samplerExternalOES tex;
#else
uniform sampler2D tex;
#endif
uniform vec4 rt; varying vec2 uv;
vec3 S(vec2 c) { return texture2D(tex, c).rgb; }
float D(vec3 a, vec3 b) { vec3 t = abs(a - b); return max(max(t.r, t.g), t.b); }
void main() {
  vec4 o0 = rt.xyxy * vec4(-1.0, 0.0, 0.0, -1.0) + uv.xyxy, o1 = rt.xyxy * vec4(1.0, 0.0, 0.0, 1.0) + uv.xyxy, o2 = rt.xyxy * vec4(-2.0, 0.0, 0.0, -2.0) + uv.xyxy;
  vec3 C = S(uv);
  vec4 d;
  d.x = D(C, S(o0.xy)); d.y = D(C, S(o0.zw));
  vec2 e = step(vec2(SMAA_THRESHOLD), d.xy);
  if (e.x + e.y == 0.0) { gl_FragColor = vec4(0.0); return; }
  d.z = D(C, S(o1.xy)); d.w = D(C, S(o1.zw));
  vec2 md = max(d.xy, d.zw);
  d.z = D(C, S(o2.xy)); d.w = D(C, S(o2.zw));
  md = max(md, d.zw);
  e *= step(max(md.x, md.y), 2.0 * d.xy);
  gl_FragColor = vec4(e, 0.0, 0.0);
}
