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
void main() {
  vec4 o0 = rt.xyxy * vec4(-1.0, 0.0, 0.0, -1.0) + uv.xyxy, o1 = rt.xyxy * vec4(1.0, 0.0, 0.0, 1.0) + uv.xyxy, o2 = rt.xyxy * vec4(-2.0, 0.0, 0.0, -2.0) + uv.xyxy;
  vec3 w = vec3(0.2126, 0.7152, 0.0722);
  float L = dot(S(uv), w), Ll = dot(S(o0.xy), w), Lt = dot(S(o0.zw), w);
  vec4 d; d.xy = abs(L - vec2(Ll, Lt));
  vec2 e = step(vec2(SMAA_THRESHOLD), d.xy);
  if (e.x + e.y == 0.0) { gl_FragColor = vec4(0.0); return; }
  float Lr = dot(S(o1.xy), w), Lb = dot(S(o1.zw), w);
  d.zw = abs(L - vec2(Lr, Lb));
  vec2 md = max(d.xy, d.zw);
  float Lll = dot(S(o2.xy), w), Ltt = dot(S(o2.zw), w);
  d.zw = abs(vec2(Ll, Lt) - vec2(Lll, Ltt));
  md = max(md, d.zw);
  e *= step(max(md.x, md.y), 2.0 * d.xy);
  gl_FragColor = vec4(e, 0.0, 0.0);
}
