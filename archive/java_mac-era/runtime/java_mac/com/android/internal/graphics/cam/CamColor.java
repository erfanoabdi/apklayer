// CAM16 / HCT, the colour space Material You's tonal palettes are built in.
//
// WHY THIS EXISTS (2026-08-20). `android:lStar` on a `<selector>` item is an
// API-31 addition, and it is the mechanism Material3 uses to build its ENTIRE
// tonal palette on API 31-33: one base colour plus a per-tone L* override.
// Aurora Store ships, verbatim:
//
//   res/color-v31/m3_ref_palette_dynamic_neutral_variant6.xml
//     <item android:color="@android:color/system_neutral2_600" android:lStar="6.0" />
//   res/color-v31/m3_ref_palette_dynamic_neutral_variant98.xml
//     <item android:color="@android:color/system_neutral2_600" android:lStar="98.0" />
//
// SAME base, different lStar -- tone 6 is near-black and tone 98 is near-white.
// We ignored lStar entirely, so both resolved to the base #5C5E67 and every
// tone in the palette collapsed onto one mid-grey. MEASURED across the decoded
// corpus: 343 such selectors in 20 apps, every base a neutral ramp
// (system_neutral1_600 or system_neutral2_600).
//
// AOSP's contract, from ColorStateList.modulateColor:
//     final CamColor baseCam = CamColor.fromColor(baseColor);
//     baseColor = CamColor.toColor(baseCam.getHue(), baseCam.getChroma(), lStar);
// i.e. keep the base's HUE and CHROMA, replace its LIGHTNESS with the requested
// L*. That cannot be done in RGB or even in L*a*b* without hue drift, which is
// why AOSP reaches for CAM16.
//
// WHY THE SEARCH RATHER THAN A GREY. AOSP itself short-circuits to a neutral
// grey when `chroma < 1.0` (Cam.getInt). Our neutral bases do NOT qualify:
// measured with this implementation, system_neutral2_600 (#5C5E67) is
// hue 269.29 / chroma 8.31 / tone 40.01 and system_neutral1_600 (#76777D) is
// hue 265.22 / chroma 5.86 / tone 50.13. A grey would drop the blue tint that
// makes a Material You neutral look like Material You.
//
// FIDELITY NOTE, stated rather than hidden: android14-release delegates the
// default viewing conditions to `HctSolver`, a geometric closest-in-gamut
// solver. This implements the OTHER path AOSP ships for the same job -- the
// chroma/lightness binary search of `Cam.getInt` + `findCamByJ`. HctSolver was
// an optimisation of that search, not a change to what it answers, so results
// agree to within the search endpoints. If a future measurement shows a
// visible divergence, port HctSolver rather than fudging constants here.
//
// VALIDATED against the canonical HCT of pure red before this was written:
// #FF0000 -> hue 27.41, chroma 113.36, tone 53.23 (published values 27.4 /
// 113.4 / 53.2). A prototype that got hue and tone right but chroma wrong
// caught a mis-derived surround exponent; keep that check if you edit this.
package com.android.internal.graphics.cam;

public final class CamColor {

    // ---- sRGB <-> XYZ (D65), AOSP CamUtils.SRGB_TO_XYZ -----------------------
    private static final double[][] SRGB_TO_XYZ = {
        { 0.41233895, 0.35762064, 0.18051042 },
        { 0.2126,     0.7152,     0.0722     },
        { 0.01932141, 0.11916382, 0.95034478 },
    };
    private static final double WHITE_X = 95.047, WHITE_Y = 100.0, WHITE_Z = 108.883;

    // ---- Frame.DEFAULT: sRGB-like viewing conditions -------------------------
    // AOSP Frame.make(WHITE_POINT_D65, adaptingLuminance, backgroundLstar 50,
    // surround 2.0, discountingIlluminant false). Computed once; every field
    // below is a pure function of those inputs.
    private static final double N, AW, NBB, NCB, C, NC, FL, FL_ROOT, Z;
    private static final double[] RGB_D = new double[3];
    static {
        double f = 0.8 + 2.0 / 10.0;                     // surround 2.0 -> f = 1.0
        // AOSP: c = lerp(0.59, 0.69, (f-0.9)*10) for f >= 0.9, else
        //           lerp(0.525, 0.59, (f-0.8)*10).
        // Getting this wrong is silent: hue and tone stay correct and only
        // CHROMA comes out wrong, which is exactly how the prototype bug hid.
        C = (f >= 0.9) ? (0.59 + (0.69 - 0.59) * ((f - 0.9) * 10.0))
                       : (0.525 + (0.59 - 0.525) * ((f - 0.8) * 10.0));
        NC = f;
        double adaptingLuminance = (200.0 / Math.PI) * yFromLstar(50.0) / 100.0;
        double yb = yFromLstar(50.0);                    // background L* 50
        N = yb / WHITE_Y;
        double d = f * (1.0 - (1.0 / 3.6) * Math.exp((-adaptingLuminance - 42.0) / 92.0));
        d = Math.min(1.0, Math.max(0.0, d));
        double rW =  0.401288 * WHITE_X + 0.650173 * WHITE_Y - 0.051461 * WHITE_Z;
        double gW = -0.250268 * WHITE_X + 1.204414 * WHITE_Y + 0.045854 * WHITE_Z;
        double bW = -0.002079 * WHITE_X + 0.048952 * WHITE_Y + 0.953127 * WHITE_Z;
        RGB_D[0] = d * WHITE_Y / rW + 1.0 - d;
        RGB_D[1] = d * WHITE_Y / gW + 1.0 - d;
        RGB_D[2] = d * WHITE_Y / bW + 1.0 - d;
        double k = 1.0 / (5.0 * adaptingLuminance + 1.0);
        double k4 = k * k * k * k;
        FL = k4 * adaptingLuminance
             + 0.1 * (1.0 - k4) * (1.0 - k4) * Math.cbrt(5.0 * adaptingLuminance);
        FL_ROOT = Math.pow(FL, 0.25);
        NBB = 0.725 / Math.pow(N, 0.2);
        NCB = NBB;
        Z = 1.48 + Math.sqrt(N);
        double rA = adapt(rW, RGB_D[0]);
        double gA = adapt(gW, RGB_D[1]);
        double bA = adapt(bW, RGB_D[2]);
        AW = ((40.0 * rA + 20.0 * gA + bA) / 20.0) * NBB;
    }

    private static double adapt(double component, double rgbD) {
        double af = Math.pow(FL * Math.abs(component) * rgbD / 100.0, 0.42);
        return Math.signum(component) * 400.0 * af / (af + 27.13);
    }

    /** L* (CIE lightness) -> Y (luminance), AOSP CamUtils.yFromLstar. */
    private static double yFromLstar(double lstar) {
        double ke = 8.0;
        if (lstar > ke) {
            double c = (lstar + 16.0) / 116.0;
            return 100.0 * c * c * c;
        }
        return 100.0 * lstar / (24389.0 / 27.0);
    }

    /** Y -> L*, AOSP CamUtils.lstarFromY. */
    private static double lstarFromY(double y) {
        double yn = y / 100.0;
        return (yn > 216.0 / 24389.0) ? (116.0 * Math.cbrt(yn) - 16.0)
                                      : (yn * 24389.0 / 27.0);
    }

    private static double linearized(int rgb8) {
        double normalized = rgb8 / 255.0;
        return normalized <= 0.040449936
                ? 100.0 * normalized / 12.92
                : 100.0 * Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static int delinearized(double rgbLinear) {
        double normalized = rgbLinear / 100.0;
        double v = normalized <= 0.0031308
                ? normalized * 12.92
                : 1.055 * Math.pow(normalized, 1.0 / 2.4) - 0.055;
        int out = (int) Math.round(v * 255.0);
        return Math.min(255, Math.max(0, out));
    }

    // ---- the value ----------------------------------------------------------
    private final float mHue, mChroma, mJ;
    private CamColor(float hue, float chroma, float j) { mHue = hue; mChroma = chroma; mJ = j; }
    public float getHue()    { return mHue; }
    public float getChroma() { return mChroma; }
    public float getJ()      { return mJ; }

    /** CAM16 forward: an opaque ARGB colour to its hue / chroma / lightness. */
    public static CamColor fromColor(int argb) {
        double r = linearized((argb >> 16) & 0xff);
        double g = linearized((argb >> 8) & 0xff);
        double b = linearized(argb & 0xff);
        double x = SRGB_TO_XYZ[0][0] * r + SRGB_TO_XYZ[0][1] * g + SRGB_TO_XYZ[0][2] * b;
        double y = SRGB_TO_XYZ[1][0] * r + SRGB_TO_XYZ[1][1] * g + SRGB_TO_XYZ[1][2] * b;
        double z = SRGB_TO_XYZ[2][0] * r + SRGB_TO_XYZ[2][1] * g + SRGB_TO_XYZ[2][2] * b;

        double rC =  0.401288 * x + 0.650173 * y - 0.051461 * z;
        double gC = -0.250268 * x + 1.204414 * y + 0.045854 * z;
        double bC = -0.002079 * x + 0.048952 * y + 0.953127 * z;

        double rA = adapt(rC, RGB_D[0]);
        double gA = adapt(gC, RGB_D[1]);
        double bA = adapt(bC, RGB_D[2]);

        double a = (11.0 * rA - 12.0 * gA + bA) / 11.0;
        double bb = (rA + gA - 2.0 * bA) / 9.0;
        double u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0;
        double p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0;

        double hueDegrees = Math.toDegrees(Math.atan2(bb, a));
        if (hueDegrees < 0) hueDegrees += 360.0;
        else if (hueDegrees >= 360) hueDegrees -= 360.0;

        double j = 100.0 * Math.pow(p2 * NBB / AW, C * Z);
        double huePrime = (hueDegrees < 20.14) ? hueDegrees + 360.0 : hueDegrees;
        double eHue = 0.25 * (Math.cos(Math.toRadians(huePrime) + 2.0) + 3.8);
        double p1 = 50000.0 / 13.0 * eHue * NC * NCB;
        double t = p1 * Math.hypot(a, bb) / (u + 0.305);
        double alpha = Math.pow(t, 0.9) * Math.pow(1.64 - Math.pow(0.29, N), 0.73);
        double chroma = alpha * Math.sqrt(j / 100.0);
        return new CamColor((float) hueDegrees, (float) chroma, (float) j);
    }

    /** CAM16 inverse for the default frame: (hue, chroma, J) back to sRGB,
     *  CLIPPED to the gamut. AOSP Cam.viewed(). */
    private static int viewedInSrgb(double hue, double chroma, double j) {
        double alpha = (chroma == 0.0 || j == 0.0)
                ? 0.0 : chroma / Math.sqrt(j / 100.0);
        double t = Math.pow(alpha / Math.pow(1.64 - Math.pow(0.29, N), 0.73), 1.0 / 0.9);
        double hRad = Math.toRadians(hue);
        double eHue = 0.25 * (Math.cos(hRad + 2.0) + 3.8);
        double ac = AW * Math.pow(j / 100.0, 1.0 / C / Z);
        double p1 = eHue * (50000.0 / 13.0) * NC * NCB;
        double p2 = ac / NBB;
        double hSin = Math.sin(hRad), hCos = Math.cos(hRad);
        double gamma = 23.0 * (p2 + 0.305) * t
                / (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin);
        double a = gamma * hCos, b = gamma * hSin;
        double rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0;
        double gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0;
        double bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0;

        double rC = unadapt(rA) / RGB_D[0];
        double gC = unadapt(gA) / RGB_D[1];
        double bC = unadapt(bA) / RGB_D[2];

        double x =  1.86206786 * rC - 1.01125463 * gC + 0.14918677 * bC;
        double y =  0.38752654 * rC + 0.62144744 * gC - 0.00897398 * bC;
        double z = -0.01584150 * rC - 0.03412294 * gC + 1.04996444 * bC;

        double rL =  3.2413774792388685 * x - 1.5376652402851851 * y - 0.49885366846268053 * z;
        double gL = -0.9691452513005321 * x + 1.8758853451067872 * y + 0.04156585616912061 * z;
        double bL =  0.05562093689691305 * x - 0.20395524564742123 * y + 1.0571799111220335 * z;

        return 0xFF000000 | (delinearized(rL) << 16) | (delinearized(gL) << 8) | delinearized(bL);
    }

    private static double unadapt(double adapted) {
        double base = Math.max(0.0, 27.13 * Math.abs(adapted) / (400.0 - Math.abs(adapted)));
        return Math.signum(adapted) * (100.0 / FL) * Math.pow(base, 1.0 / 0.42);
    }

    /** A neutral grey at exactly this L*. AOSP CamUtils.intFromLstar. */
    private static int intFromLstar(double lstar) {
        double component = yFromLstar(lstar);
        int v = delinearized(component);
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /** CAM16-UCS coordinates, AOSP Cam.java:219-222. `m` is COLOURFULNESS,
     *  which is chroma scaled by the frame's FL^0.25 (Cam.java:214). */
    private static double[] ucs(double hue, double chroma, double j) {
        double m = chroma * FL_ROOT;
        double jstar = (1.0 + 100.0 * 0.007) * j / (1.0 + 0.007 * j);
        double mstar = 1.0 / 0.0228 * Math.log1p(0.0228 * m);
        double hr = Math.toRadians(hue);
        return new double[] { jstar, mstar * Math.cos(hr), mstar * Math.sin(hr) };
    }

    /** AOSP Cam.distance() (Cam.java:266-273): perceptual distance in CAM16-UCS.
     *  dE' = |(J*,a*,b*) delta|, dE = 1.41 * dE'^0.63. */
    private static double camDistance(double h1, double c1, double j1,
                                      double h2, double c2, double j2) {
        double[] p = ucs(h1, c1, j1), q = ucs(h2, c2, j2);
        double dJ = p[0] - q[0], dA = p[1] - q[1], dB = p[2] - q[2];
        return 1.41 * Math.pow(Math.sqrt(dJ * dJ + dA * dA + dB * dB), 0.63);
    }

    /** Find the CAM16 J whose CLIPPED sRGB rendering has the requested L*.
     *  Returns Integer.MIN_VALUE when no J does. AOSP Cam.findCamByJ. */
    /** AOSP's tolerances, Cam.java. DL_MAX is the L* error the search accepts;
     *  DE_MAX is the perceptual distance beyond which the clipped colour is a
     *  DIFFERENT colour rather than a less-saturated one. */
    private static final double DL_MAX = 0.2;
    private static final double DE_MAX = 1.0;

    private static int findByJ(double h, double chroma, double lstar) {
        double low = 0.0, high = 100.0;
        double bestDl = 1000.0;
        int best = Integer.MIN_VALUE;
        while (Math.abs(low - high) > 0.01) {
            double mid = low + (high - low) / 2.0;
            int clipped = viewedInSrgb(h, chroma, mid);
            double clippedLstar = lstarFromY(yFromInt(clipped));
            double dL = Math.abs(lstar - clippedLstar);
            // ⚠ THE dL TEST ALONE IS NOT ENOUGH, and omitting the dE half was a
            // real bug (fixed 2026-08-21). viewedInSrgb CLIPS out-of-gamut
            // channels, which rotates HUE while leaving L* intact -- so dL
            // passes and the search happily converges on a completely different
            // colour. MEASURED before the fix: #0000FF (hue 282.79) at tone 30
            // came back #2F4F00, OLIVE, a 151-degree hue rotation; our own
            // system_accent1_600 #475D92 drifted -67 degrees at tone 98.
            // AOSP guards it with a CAM16-UCS distance test (Cam.java:481-494):
            // the clipped colour's own CAM, re-expressed at the INTENDED hue,
            // must be within dE 1.0 of the clipped colour. That is what keeps
            // "chroma may be reduced" from silently becoming "hue may be
            // anything".
            if (dL < DL_MAX && dL < bestDl) {
                com.android.internal.graphics.cam.CamColor cc = fromColor(clipped);
                double dE = camDistance(cc.getHue(), cc.getChroma(), cc.getJ(),
                                        h,            cc.getChroma(), cc.getJ());
                if (dE <= DE_MAX) { bestDl = dL; best = clipped; }
            }
            if (bestDl == 0.0) break;
            if (clippedLstar < lstar) low = mid; else high = mid;
        }
        return best;
    }

    private static double yFromInt(int argb) {
        double r = linearized((argb >> 16) & 0xff);
        double g = linearized((argb >> 8) & 0xff);
        double b = linearized(argb & 0xff);
        return SRGB_TO_XYZ[1][0] * r + SRGB_TO_XYZ[1][1] * g + SRGB_TO_XYZ[1][2] * b;
    }

    /** AOSP CamColor.toColor(hue, chroma, lstar): the closest in-gamut sRGB
     *  colour with the requested hue and chroma whose L* is EXACTLY lstar.
     *
     *  The L* constraint is the one that must hold: Material's whole tonal
     *  system, and every contrast/accessibility guarantee built on it, is
     *  expressed in L*. When the requested chroma is unreachable at that hue
     *  and lightness, chroma is reduced -- never the lightness. */
    public static int toColor(float hue, float chroma, float lstar) {
        // AOSP's own early-out. A colour with no meaningful chroma, or at the
        // extremes of lightness, is a grey and the search would only add error.
        if (chroma < 1.0 || Math.round(lstar) <= 0.0 || Math.round(lstar) >= 100.0) {
            return intFromLstar(lstar);
        }
        double h = (hue < 0) ? 0 : Math.min(360.0, hue);
        double high = chroma, mid = chroma, low = 0.0;
        boolean firstLoop = true;
        int answer = Integer.MIN_VALUE;
        while (Math.abs(low - high) >= 0.4) {
            int possible = findByJ(h, mid, lstar);
            if (firstLoop) {
                // The requested chroma was reachable on the first try, which
                // AOSP notes happens far more often than one would expect.
                if (possible != Integer.MIN_VALUE) return possible;
                firstLoop = false;
                mid = low + (high - low) / 2.0;
                continue;
            }
            if (possible == Integer.MIN_VALUE) high = mid;   // unreachable, less chroma
            else { answer = possible; low = mid; }           // reachable, try more
            mid = low + (high - low) / 2.0;
        }
        // Nothing in gamut at any chroma: fall back to the grey, which always
        // satisfies the L* constraint.
        return (answer == Integer.MIN_VALUE) ? intFromLstar(lstar) : answer;
    }

    /** AOSP ColorStateList.modulateColor: apply an alpha multiplier and/or an
     *  lStar override to a base colour. lStar outside [0,100] means "absent",
     *  which is how AOSP spells a selector item that did not set it. */
    public static int modulateColor(int baseColor, float alphaMod, float lStar) {
        boolean validLStar = lStar >= 0.0f && lStar <= 100.0f;
        if (alphaMod == 1.0f && !validLStar) return baseColor;
        int baseAlpha = (baseColor >>> 24) & 0xff;
        int alpha = Math.min(255, Math.max(0, (int) (baseAlpha * alphaMod + 0.5f)));
        if (validLStar) {
            CamColor baseCam = fromColor(baseColor);
            baseColor = toColor(baseCam.getHue(), baseCam.getChroma(), lStar);
        }
        return (baseColor & 0x00FFFFFF) | (alpha << 24);
    }
}
