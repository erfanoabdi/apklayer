#ifdef SRC_EXT
#extension GL_OES_EGL_image_external : require
#endif
precision highp float;
#ifdef SRC_EXT
uniform samplerExternalOES tex;
#else
uniform sampler2D tex;
#endif
uniform sampler2D blend;
uniform vec4 rt; varying vec2 uv;
void main() {
  vec4 off = rt.xyxy * vec4(1.0, 0.0, 0.0, 1.0) + uv.xyxy, a;
  a.x = texture2D(blend, off.xy).a;
  a.y = texture2D(blend, off.zw).g;
  a.wz = texture2D(blend, uv).xz;
  if (dot(a, vec4(1.0)) < 1e-5) { gl_FragColor = vec4(texture2D(tex, uv).rgb, 1.0); return; }
  bool h = max(a.x, a.z) > max(a.y, a.w);
  vec4 bo = vec4(0.0, a.y, 0.0, a.w);
  vec2 bw = a.yw;
  if (h) { bo = vec4(a.x, 0.0, a.z, 0.0); bw = a.xz; }
  bw /= dot(bw, vec2(1.0));
  vec4 bc = bo * vec4(rt.xy, -rt.xy) + uv.xyxy;
  gl_FragColor = vec4((bw.x * texture2D(tex, bc.xy) + bw.y * texture2D(tex, bc.zw)).rgb, 1.0);
}
