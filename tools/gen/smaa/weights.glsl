precision highp float;
uniform sampler2D edges, area, search;
uniform vec4 rt; varying vec2 uv;
#define AREA_MAXD 16.0
#define AREA_MAXD_DIAG 20.0
#define AREA_PX (1.0 / vec2(160.0, 560.0))
#define AREA_SUB (1.0 / 7.0)
#define SEARCH_SIZE vec2(66.0, 33.0)
#define SEARCH_PACKED vec2(64.0, 16.0)
#define CORNER_NORM (float(SMAA_CORNER_ROUNDING) / 100.0)
vec2 rnd2(vec2 x) { return floor(x + 0.5); }
vec4 rnd4(vec4 x) { return floor(x + 0.5); }
vec4 EO(vec2 c, vec2 o) { return texture2D(edges, c + o * rt.xy); }
vec2 decd2(vec2 e) { e.r = e.r * abs(5.0 * e.r - 5.0 * 0.75); return rnd2(e); }
vec4 decd4(vec4 e) { e.rb = e.rb * abs(5.0 * e.rb - 5.0 * 0.75); return rnd4(e); }
vec2 sd1(vec2 tc, vec2 dir, out vec2 e) {
  vec4 coord = vec4(tc, -1.0, 1.0); vec3 t = vec3(rt.xy, 1.0); e = vec2(0.0);
  for (int i = 0; i < SMAA_MAX_SEARCH_STEPS_DIAG + 1; i++) {
    if (!(coord.z < float(SMAA_MAX_SEARCH_STEPS_DIAG - 1) && coord.w > 0.9)) break;
    coord.xyz = t * vec3(dir, 1.0) + coord.xyz;
    e = texture2D(edges, coord.xy).rg;
    coord.w = dot(e, vec2(0.5, 0.5));
  }
  return coord.zw;
}
vec2 sd2(vec2 tc, vec2 dir, out vec2 e) {
  vec4 coord = vec4(tc, -1.0, 1.0); coord.x += 0.25 * rt.x; vec3 t = vec3(rt.xy, 1.0); e = vec2(0.0);
  for (int i = 0; i < SMAA_MAX_SEARCH_STEPS_DIAG + 1; i++) {
    if (!(coord.z < float(SMAA_MAX_SEARCH_STEPS_DIAG - 1) && coord.w > 0.9)) break;
    coord.xyz = t * vec3(dir, 1.0) + coord.xyz;
    e = decd2(texture2D(edges, coord.xy).rg);
    coord.w = dot(e, vec2(0.5, 0.5));
  }
  return coord.zw;
}
vec2 areaDiag(vec2 dist, vec2 e, float offset) {
  vec2 tc = AREA_MAXD_DIAG * e + dist;
  tc = AREA_PX * tc + 0.5 * AREA_PX;
  tc.x += 0.5; tc.y += AREA_SUB * offset;
  return texture2D(area, tc).ra;
}
vec2 diagWeights(vec2 tc, vec2 e) {
  vec2 weights = vec2(0.0); vec4 d; vec2 end;
  if (e.r > 0.0) { d.xz = sd1(tc, vec2(-1.0, 1.0), end); d.x += float(end.y > 0.9); } else d.xz = vec2(0.0);
  d.yw = sd1(tc, vec2(1.0, -1.0), end);
  if (d.x + d.y > 2.0) {
    vec4 coords = vec4(-d.x + 0.25, d.x, d.y, -d.y - 0.25) * rt.xyxy + tc.xyxy;
    vec4 c;
    c.xy = EO(coords.xy, vec2(-1.0, 0.0)).rg; c.zw = EO(coords.zw, vec2(1.0, 0.0)).rg;
    c.yxwz = decd4(c.xyzw);
    vec2 cc = vec2(2.0) * c.xz + c.yw;
    if (d.z >= 0.9) cc.x = 0.0;
    if (d.w >= 0.9) cc.y = 0.0;
    weights += areaDiag(d.xy, cc, 0.0);
  }
  d.xz = sd2(tc, vec2(-1.0, -1.0), end);
  if (EO(tc, vec2(1.0, 0.0)).r > 0.0) { d.yw = sd2(tc, vec2(1.0, 1.0), end); d.y += float(end.y > 0.9); } else d.yw = vec2(0.0);
  if (d.x + d.y > 2.0) {
    vec4 coords = vec4(-d.x, -d.x, d.y, d.y) * rt.xyxy + tc.xyxy;
    vec4 c;
    c.x = EO(coords.xy, vec2(-1.0, 0.0)).g; c.y = EO(coords.xy, vec2(0.0, -1.0)).r; c.zw = EO(coords.zw, vec2(1.0, 0.0)).gr;
    vec2 cc = vec2(2.0) * c.xz + c.yw;
    if (d.z >= 0.9) cc.x = 0.0;
    if (d.w >= 0.9) cc.y = 0.0;
    weights += areaDiag(d.xy, cc, 0.0).gr;
  }
  return weights;
}
float searchLen(vec2 e, float offset) {
  vec2 scale = SEARCH_SIZE * vec2(0.5, -1.0), bias = SEARCH_SIZE * vec2(offset, 1.0);
  scale += vec2(-1.0, 1.0); bias += vec2(0.5, -0.5);
  scale *= 1.0 / SEARCH_PACKED; bias *= 1.0 / SEARCH_PACKED;
  return texture2D(search, scale * e + bias).r;
}
float sxl(vec2 tc, float end) {
  vec2 e = vec2(0.0, 1.0);
  for (int i = 0; i < SMAA_MAX_SEARCH_STEPS + 1; i++) {
    if (!(tc.x > end && e.g > 0.8281 && e.r == 0.0)) break;
    e = texture2D(edges, tc).rg; tc = -vec2(2.0, 0.0) * rt.xy + tc;
  }
  return rt.x * (-(255.0 / 127.0) * searchLen(e, 0.0) + 3.25) + tc.x;
}
float sxr(vec2 tc, float end) {
  vec2 e = vec2(0.0, 1.0);
  for (int i = 0; i < SMAA_MAX_SEARCH_STEPS + 1; i++) {
    if (!(tc.x < end && e.g > 0.8281 && e.r == 0.0)) break;
    e = texture2D(edges, tc).rg; tc = vec2(2.0, 0.0) * rt.xy + tc;
  }
  return -rt.x * (-(255.0 / 127.0) * searchLen(e, 0.5) + 3.25) + tc.x;
}
float syu(vec2 tc, float end) {
  vec2 e = vec2(1.0, 0.0);
  for (int i = 0; i < SMAA_MAX_SEARCH_STEPS + 1; i++) {
    if (!(tc.y > end && e.r > 0.8281 && e.g == 0.0)) break;
    e = texture2D(edges, tc).rg; tc = -vec2(0.0, 2.0) * rt.xy + tc;
  }
  return rt.y * (-(255.0 / 127.0) * searchLen(e.gr, 0.0) + 3.25) + tc.y;
}
float syd(vec2 tc, float end) {
  vec2 e = vec2(1.0, 0.0);
  for (int i = 0; i < SMAA_MAX_SEARCH_STEPS + 1; i++) {
    if (!(tc.y < end && e.r > 0.8281 && e.g == 0.0)) break;
    e = texture2D(edges, tc).rg; tc = vec2(0.0, 2.0) * rt.xy + tc;
  }
  return -rt.y * (-(255.0 / 127.0) * searchLen(e.gr, 0.5) + 3.25) + tc.y;
}
vec2 areaF(vec2 dist, float e1, float e2, float offset) {
  vec2 tc = vec2(AREA_MAXD) * rnd2(4.0 * vec2(e1, e2)) + dist;
  tc = AREA_PX * tc + 0.5 * AREA_PX;
  tc.y = AREA_SUB * offset + tc.y;
  return texture2D(area, tc).ra;
}
void main() {
  vec2 pix = uv * rt.zw;
  vec4 o0 = rt.xyxy * vec4(-0.25, -0.125, 1.25, -0.125) + uv.xyxy, o1 = rt.xyxy * vec4(-0.125, -0.25, -0.125, 1.25) + uv.xyxy;
  vec4 o2 = rt.xxyy * (vec4(-2.0, 2.0, -2.0, 2.0) * float(SMAA_MAX_SEARCH_STEPS)) + vec4(o0.xz, o1.yw);
  vec4 weights = vec4(0.0);
  vec2 e = texture2D(edges, uv).rg;
  if (e.g > 0.0) {
    weights.rg = diagWeights(uv, e);
    if (weights.r == -weights.g) {
    vec2 d; vec3 coords;
    coords.x = sxl(o0.xy, o2.x);
    coords.y = o1.y;
    d.x = coords.x;
    float e1 = texture2D(edges, coords.xy).r;
    coords.z = sxr(o0.zw, o2.y);
    d.y = coords.z;
    d = abs(rnd2(rt.zz * d - pix.xx));
    vec2 sq = sqrt(d);
    float e2 = EO(coords.zy, vec2(1.0, 0.0)).r;
    weights.rg = areaF(sq, e1, e2, 0.0);
    coords.y = uv.y;
    {
      vec4 tcn = coords.xyzy;
      vec2 lr = step(d.xy, d.yx), rounding = (1.0 - CORNER_NORM) * lr;
      rounding /= lr.x + lr.y;
      vec2 factor = vec2(1.0);
      factor.x -= rounding.x * EO(tcn.xy, vec2(0.0, 1.0)).r;
      factor.x -= rounding.y * EO(tcn.zw, vec2(1.0, 1.0)).r;
      factor.y -= rounding.x * EO(tcn.xy, vec2(0.0, -2.0)).r;
      factor.y -= rounding.y * EO(tcn.zw, vec2(1.0, -2.0)).r;
      weights.rg *= clamp(factor, 0.0, 1.0);
    }
    } else e.r = 0.0;
  }
  if (e.r > 0.0) {
    vec2 d; vec3 coords;
    coords.y = syu(o1.xy, o2.z);
    coords.x = o0.x;
    d.x = coords.y;
    float e1 = texture2D(edges, coords.xy).g;
    coords.z = syd(o1.zw, o2.w);
    d.y = coords.z;
    d = abs(rnd2(rt.ww * d - pix.yy));
    vec2 sq = sqrt(d);
    float e2 = EO(coords.xz, vec2(0.0, 1.0)).g;
    weights.ba = areaF(sq, e1, e2, 0.0);
    coords.x = uv.x;
    {
      vec4 tcn = coords.xyxz;
      vec2 lr = step(d.xy, d.yx), rounding = (1.0 - CORNER_NORM) * lr;
      rounding /= lr.x + lr.y;
      vec2 factor = vec2(1.0);
      factor.x -= rounding.x * EO(tcn.xy, vec2(1.0, 0.0)).g;
      factor.x -= rounding.y * EO(tcn.zw, vec2(1.0, 1.0)).g;
      factor.y -= rounding.x * EO(tcn.xy, vec2(-2.0, 0.0)).g;
      factor.y -= rounding.y * EO(tcn.zw, vec2(-2.0, 1.0)).g;
      weights.ba *= clamp(factor, 0.0, 1.0);
    }
  }
  gl_FragColor = weights;
}
