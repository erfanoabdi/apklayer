// android.graphics.Color -- ARGB packing utilities and named constants, plus
// the API-26 instance / color-space-aware surface.
//
// AOSP ref: frameworks/base/graphics/java/android/graphics/Color.java
//
// Since API 26 Color is three APIs in one class:
//   1. the original static int (ARGB8888) helpers -- alpha/red/green/blue,
//      rgb/argb, parseColor, the HSV family;
//   2. an instantiable value type holding float components + a ColorSpace,
//      obtained from valueOf(...) (Jetpack Compose interop leans on this);
//   3. a parallel static API over "color longs", the 64-bit packed form.
//
// The color-long format (AOSP class javadoc) is the load-bearing bit and is
// mirrored exactly below: when the color space id (the low 6 bits) is 0 the
// value is sRGB and the high 32 bits are a plain ARGB8888 int, otherwise the
// R/G/B channels are IEEE-754 binary16 halves at bits 48/32/16 and alpha is a
// 10-bit fixed-point value at bit 6. Getting that encoding wrong silently
// corrupts every color that round-trips through a long.
//
// v1.0.502: (2) and (3) were entirely absent, so all ~24 of those signatures
// threw NoSuchMethodError at the call site (22 of 37 corpus apps reference at
// least one).
package android.graphics;

import java.util.Arrays;

public class Color {
    public static final int BLACK       = 0xFF000000;
    public static final int DKGRAY      = 0xFF444444;
    public static final int GRAY        = 0xFF888888;
    public static final int LTGRAY      = 0xFFCCCCCC;
    public static final int WHITE       = 0xFFFFFFFF;
    public static final int RED         = 0xFFFF0000;
    public static final int GREEN       = 0xFF00FF00;
    public static final int BLUE        = 0xFF0000FF;
    public static final int YELLOW      = 0xFFFFFF00;
    public static final int CYAN        = 0xFF00FFFF;
    public static final int MAGENTA     = 0xFFFF00FF;
    public static final int TRANSPARENT = 0;

    public static int alpha(int color) { return (color >>> 24) & 0xff; }
    public static int red  (int color) { return (color >> 16) & 0xff; }
    public static int green(int color) { return (color >> 8)  & 0xff; }
    public static int blue (int color) { return  color        & 0xff; }
    public static int rgb(int r, int g, int b)              { return 0xFF000000 | (r << 16) | (g << 8) | b; }
    public static int argb(int a, int r, int g, int b)      { return (a << 24) | (r << 16) | (g << 8) | b; }
    /** ⚠ AOSP ROUNDS, it does not truncate: `(int) (red * 255.0f + 0.5f)`
     *  (Color.java:1300-1306 for rgb, :1337-1343 for argb). Truncating puts
     *  every float-constructed colour ONE UNIT LOW -- 0.5f became 0x7f instead
     *  of 0x80 -- which is every half-opacity scrim, every mid-grey divider,
     *  and every colour an app derives by dividing by 255 and multiplying
     *  back. Off-by-one per channel is invisible in a screenshot and is
     *  measurable everywhere else. */
    public static int rgb(float r, float g, float b) {
        return 0xff000000
             | ((int) (r * 255.0f + 0.5f) << 16)
             | ((int) (g * 255.0f + 0.5f) << 8)
             |  (int) (b * 255.0f + 0.5f);
    }
    public static int argb(float a, float r, float g, float b) {
        return ((int) (a * 255.0f + 0.5f) << 24)
             | ((int) (r * 255.0f + 0.5f) << 16)
             | ((int) (g * 255.0f + 0.5f) << 8)
             |  (int) (b * 255.0f + 0.5f);
    }
    // RGB <-> HSV. Mirrors AOSP android.graphics.Color (native SkRGBToHSV /
    // SkHSVToColor). hsv = { hue[0..360), saturation[0..1], value[0..1] }.
    // Material widgets (and F-Droid's FeatureImage tint) call colorToHSV to
    // derive a darker/lighter variant of a base color.
    public static void colorToHSV(int color, float[] hsv) {
        RGBToHSV(red(color), green(color), blue(color), hsv);
    }
    public static void RGBToHSV(int r, int g, int b, float[] hsv) {
        // AOSP checks the length in JAVA and throws a plain RuntimeException
        // (Color.java: `if (hsv.length < 3) throw new RuntimeException("3
        // components required for hsv")`), not the ArrayIndexOutOfBounds an
        // app would get from writing past the end. An app that catches
        // RuntimeException around a palette computation sees the two
        // differently.
        if (hsv.length < 3) throw new RuntimeException("3 components required for hsv");
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float h;
        if (delta == 0f)        h = 0f;
        else if (max == rf)     h = 60f * (((gf - bf) / delta) % 6f);
        else if (max == gf)     h = 60f * (((bf - rf) / delta) + 2f);
        else                    h = 60f * (((rf - gf) / delta) + 4f);
        if (h < 0f) h += 360f;
        hsv[0] = h;
        hsv[1] = (max == 0f) ? 0f : (delta / max);
        hsv[2] = max;
    }
    public static int HSVToColor(float[] hsv) { return HSVToColor(0xFF, hsv); }
    public static int HSVToColor(int alpha, float[] hsv) {
        // Same Java-side length contract as RGBToHSV above.
        if (hsv.length < 3) throw new RuntimeException("3 components required for hsv");
        float h = hsv[0], s = hsv[1], v = hsv[2];
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float rf, gf, bf;
        if      (h < 60f)  { rf = c; gf = x; bf = 0f; }
        else if (h < 120f) { rf = x; gf = c; bf = 0f; }
        else if (h < 180f) { rf = 0f; gf = c; bf = x; }
        else if (h < 240f) { rf = 0f; gf = x; bf = c; }
        else if (h < 300f) { rf = x; gf = 0f; bf = c; }
        else               { rf = c; gf = 0f; bf = x; }
        return argb(alpha, Math.round((rf + m) * 255f),
                            Math.round((gf + m) * 255f),
                            Math.round((bf + m) * 255f));
    }
    // v1.0.834: was a 6-name switch that silently returned BLACK for every
    // other AOSP-legal name (e.g. the very common "gray"/"yellow"/"silver")
    // and for any malformed-length hex string -- a wrong color with no crash
    // and no log, the hardest class of bug for this project to catch. Fixed
    // to mirror AOSP's real sColorNameMap (all 23 entries, verified against
    // frameworks/base graphics/java/android/graphics/Color.java on both
    // android13-release and main -- identical on both) and its throw-on-
    // unrecognized-input contract. Every INTERNAL caller of this method
    // (Resources.macapkResolveDrawableRef/macapkResolveColorString,
    // TypedArray.getColor, ColorStateList, VectorDrawable.parseColor)
    // already wraps the call in try/catch, so making it throw introduces no
    // new crash in our own framework; it only lets an APK's own try/catch
    // around a user-typed hex color -- the standard Android idiom, since
    // real parseColor has always thrown -- actually run instead of being
    // masked by a silent BLACK.
    public static int parseColor(String s) {
        // ⚠ v1.0.939: this is now AOSP-EXACT and REJECTS the #RGB / #ARGB
        // shorthand, which it used to accept. The shorthand handling did not go
        // away -- it moved to macapkParseResourceColor below, which is what our
        // own resource pipeline calls. See that method for why we need it at
        // all.
        //
        // These really are two different questions and the answers differ:
        //   - "our decompiled XML says #fff, what colour is that": white
        //   - "the APK called Color.parseColor(\"#fff\")": IllegalArgumentException
        // AOSP requires EXACTLY 7 or 9 characters (Color.java:1385-1394), so on
        // a real device an app's own try/catch around a user-typed colour fires
        // for the short form. Ours silently returned white, which is the same
        // class of bug the silent-BLACK guard was: a plausible answer where the
        // platform raises.
        return parseColorInternal(s, false);
    }

    /** The LENIENT parse, for values that came out of OUR resource pipeline.
     *
     *  aapt2 expands #RGB / #ARGB at COMPILE time, so a real device never sees
     *  the short form and AOSP's parseColor rightly rejects it. But apktool
     *  decodes the binary resource back to SOURCE-form XML and can re-emit the
     *  nibble-repeating colour in short form, so at runtime we may genuinely
     *  have to parse "#fff" out of a decompiled drawable and must read it as
     *  "#ffffff". Without this, "#fff" parsed as the raw 0x00000fff (alpha 0)
     *  -- Wikipedia's Compose wordmark vector (fillColor="#fff") rendered into
     *  an empty transparent icon cache.
     *
     *  This is a Mac.apk decompile-pipeline compensation, not a claim that
     *  Android accepts the form, which is exactly why it is no longer reachable
     *  through the public API an APK can call. */
    public static int macapkParseResourceColor(String s) {
        return parseColorInternal(s, true);
    }

    private static int parseColorInternal(String s, boolean allowShorthand) {
        // AOSP's first statement is `colorString.charAt(0) == '#'`
        // (Color.java:1383-1384), so a null string is a NullPointerException
        // and an EMPTY string is a StringIndexOutOfBoundsException -- neither
        // is a quiet BLACK, which is what this method used to answer. Every
        // INTERNAL caller (Resources, TypedArray, ColorStateList,
        // VectorDrawable, LayoutInflater) already null/empty-guards AND wraps
        // this in try/catch, so nothing of ours changes behaviour; an APK's own
        // try/catch around a user-typed colour now runs, as it does on a
        // device.
        if (s.charAt(0) == '#') {
            String h = s.substring(1).trim();
            if (allowShorthand && (h.length() == 3 || h.length() == 4)) {
                StringBuilder e = new StringBuilder(h.length() * 2);
                for (int i = 0; i < h.length(); i++) { char ch = h.charAt(i); e.append(ch).append(ch); }
                h = e.toString();
            }
            // AOSP parseColor(): "Use a long to avoid rollovers on
            // #ffXXXXXX"; only length 7 (#RRGGBB) and 9 (#AARRGGBB) --
            // i.e. h.length() 6 or 8 here -- are legal, everything else
            // throws IllegalArgumentException("Unknown color").
            long c = Long.parseLong(h, 16);
            if (h.length() == 6) {
                c |= 0xFF000000L;   // #RRGGBB -> opaque
            } else if (h.length() != 8) {
                throw new IllegalArgumentException("Unknown color");
            }
            return (int) c;
        }
        Integer named = sColorNameMap.get(s.toLowerCase(java.util.Locale.ROOT));
        if (named != null) return named;
        throw new IllegalArgumentException("Unknown color");
    }
    // AOSP's exact sColorNameMap (Color.java, static initializer), plus one
    // Mac.apk-only addition: "transparent" is NOT one of AOSP's 23 names
    // (verified -- real Color.parseColor("transparent") throws on a device
    // too), but this codebase has long accepted it here, so it stays as a
    // harmless superset rather than a newly-introduced crash for whatever
    // relied on it.
    private static final java.util.HashMap<String, Integer> sColorNameMap = new java.util.HashMap<>();
    static {
        sColorNameMap.put("black", BLACK);
        sColorNameMap.put("darkgray", DKGRAY);
        sColorNameMap.put("gray", GRAY);
        sColorNameMap.put("lightgray", LTGRAY);
        sColorNameMap.put("white", WHITE);
        sColorNameMap.put("red", RED);
        sColorNameMap.put("green", GREEN);
        sColorNameMap.put("blue", BLUE);
        sColorNameMap.put("yellow", YELLOW);
        sColorNameMap.put("cyan", CYAN);
        sColorNameMap.put("magenta", MAGENTA);
        sColorNameMap.put("aqua", 0xFF00FFFF);
        sColorNameMap.put("fuchsia", 0xFFFF00FF);
        sColorNameMap.put("darkgrey", DKGRAY);
        sColorNameMap.put("grey", GRAY);
        sColorNameMap.put("lightgrey", LTGRAY);
        sColorNameMap.put("lime", 0xFF00FF00);
        sColorNameMap.put("maroon", 0xFF800000);
        sColorNameMap.put("navy", 0xFF000080);
        sColorNameMap.put("olive", 0xFF808000);
        sColorNameMap.put("purple", 0xFF800080);
        sColorNameMap.put("silver", 0xFFC0C0C0);
        sColorNameMap.put("teal", 0xFF008080);
        sColorNameMap.put("transparent", TRANSPARENT);  // Mac.apk-only, see above
    }

    // ------------------------------------------------------------------
    // API 26+: Color as an instantiable, color-space-aware value type.
    // ------------------------------------------------------------------

    /** AOSP assigns ColorSpace.Named.SRGB.ordinal() as the sRGB space's id,
     *  and the color-long format keys the sRGB fast path off that same id
     *  (the low 6 bits being 0). android.graphics.ColorSpace in this tree has
     *  no isSrgb() yet, so the id comparison is the test. */
    private static final int SRGB_ID = ColorSpace.Named.SRGB.ordinal();

    private final float[] mComponents;
    private final ColorSpace mColorSpace;

    /** AOSP: "Creates a new color instance set to opaque black in the sRGB
     *  color space." Retained upstream purely for API compatibility. */
    public Color() {
        mComponents = new float[] { 0.0f, 0.0f, 0.0f, 1.0f };
        mColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
    }

    private Color(float r, float g, float b, float a) {
        this(r, g, b, a, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    private Color(float r, float g, float b, float a, ColorSpace colorSpace) {
        mComponents = new float[] { r, g, b, a };
        mColorSpace = colorSpace;
    }

    private Color(float[] components, ColorSpace colorSpace) {
        mComponents = components;
        mColorSpace = colorSpace;
    }

    public ColorSpace getColorSpace() { return mColorSpace; }

    public ColorSpace.Model getModel() { return mColorSpace.getModel(); }

    public boolean isWideGamut() { return getColorSpace().isWideGamut(); }

    public boolean isSrgb() { return isSrgb(getColorSpace()); }

    /** AOSP: the color space's component count plus one for alpha, so 4 or 5. */
    public int getComponentCount() { return mColorSpace.getComponentCount() + 1; }

    public long pack() {
        return pack(mComponents[0], mComponents[1], mComponents[2], mComponents[3], mColorSpace);
    }

    /** AOSP Color.convert(ColorSpace) -- runs the components through
     *  ColorSpace.connect(src, dst).
     *
     *  ⚠ CORRECTED 2026-08-24. This used to copy the components UNCHANGED, and
     *  its comment justified that with "ColorSpace in this tree models exactly
     *  one space (sRGB) ... when ColorSpace grows real primaries / transfer
     *  functions, this must route through ColorSpace.connect() instead". That
     *  premise was already stale -- ColorSpace has carried real primaries and
     *  transfer functions for the whole named table for a long time -- and
     *  ColorSpace.Connector now exists, so this does what the old comment said
     *  it should. Same-space conversions still go through Connector.identity and
     *  are byte-identical to the old behaviour; only a genuine cross-space
     *  convert changes, and it changes from wrong to right.
     *
     *  The components are copied rather than aliased so this Color stays
     *  immutable -- Connector.transform() writes IN PLACE. */
    public Color convert(ColorSpace colorSpace) {
        float[] color = new float[] {
                mComponents[0], mComponents[1], mComponents[2], mComponents[3]
        };
        ColorSpace.Connector connector = ColorSpace.connect(mColorSpace, colorSpace);
        connector.transform(color);
        return new Color(color, colorSpace);
    }

    /** AOSP: "A color int is always in the sRGB color space", so a non-sRGB
     *  color is converted first. */
    public int toArgb() {
        return ((int) (mComponents[3] * 255.0f + 0.5f) << 24) |
               ((int) (mComponents[0] * 255.0f + 0.5f) << 16) |
               ((int) (mComponents[1] * 255.0f + 0.5f) <<  8) |
                (int) (mComponents[2] * 255.0f + 0.5f);
    }

    public float red()   { return mComponents[0]; }
    public float green() { return mComponents[1]; }
    public float blue()  { return mComponents[2]; }
    /** AOSP: alpha is always the LAST component, not index 3, because a
     *  5-component (CMYK) color puts it at index 4. */
    public float alpha() { return mComponents[mComponents.length - 1]; }

    public float[] getComponents() { return Arrays.copyOf(mComponents, mComponents.length); }

    public float[] getComponents(float[] components) {
        if (components == null) {
            return Arrays.copyOf(mComponents, mComponents.length);
        }
        if (components.length < mComponents.length) {
            throw new IllegalArgumentException("The specified array's length must be at "
                    + "least " + mComponents.length);
        }
        System.arraycopy(mComponents, 0, components, 0, mComponents.length);
        return components;
    }

    public float getComponent(int component) { return mComponents[component]; }

    /** AOSP: relative luminance per WCAG 2.0, computed on linear (EOTF-decoded)
     *  components. */
    public float luminance() {
        if (mColorSpace.getModel() != ColorSpace.Model.RGB) {
            throw new IllegalArgumentException("The specified color must be encoded in an RGB " +
                    "color space. The supplied color space is " + mColorSpace.getModel());
        }
        double r = eotf(mColorSpace, mComponents[0]);
        double g = eotf(mColorSpace, mComponents[1]);
        double b = eotf(mColorSpace, mComponents[2]);
        return saturate((float) ((0.2126 * r) + (0.7152 * g) + (0.0722 * b)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Color color = (Color) o;
        if (!Arrays.equals(mComponents, color.mComponents)) return false;
        return mColorSpace.equals(color.mColorSpace);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(mComponents);
        result = 31 * result + mColorSpace.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Color(");
        for (float c : mComponents) {
            b.append(c).append(", ");
        }
        b.append(mColorSpace.getName());
        b.append(')');
        return b.toString();
    }

    // ------------------------------------------------------------------
    // API 26+: the static color-long API. See the color-long format note in
    // the file header; every shift/mask below comes from AOSP verbatim.
    // ------------------------------------------------------------------

    public static ColorSpace colorSpace(long color) {
        return ColorSpace.get((int) (color & 0x3fL));
    }

    public static float red(long color) {
        if ((color & 0x3fL) == 0L) return ((color >> 48) & 0xff) / 255.0f;
        return android.util.Half.toFloat((short) ((color >> 48) & 0xffff));
    }

    public static float green(long color) {
        if ((color & 0x3fL) == 0L) return ((color >> 40) & 0xff) / 255.0f;
        return android.util.Half.toFloat((short) ((color >> 32) & 0xffff));
    }

    public static float blue(long color) {
        if ((color & 0x3fL) == 0L) return ((color >> 32) & 0xff) / 255.0f;
        return android.util.Half.toFloat((short) ((color >> 16) & 0xffff));
    }

    public static float alpha(long color) {
        if ((color & 0x3fL) == 0L) return ((color >> 56) & 0xff) / 255.0f;
        return ((color >> 6) & 0x3ff) / 1023.0f;
    }

    public static boolean isSrgb(long color) { return isSrgb(colorSpace(color)); }

    public static boolean isWideGamut(long color) { return colorSpace(color).isWideGamut(); }

    public static boolean isInColorSpace(long color, ColorSpace colorSpace) {
        return (int) (color & 0x3fL) == colorSpace.getId();
    }

    public static int toArgb(long color) {
        if ((color & 0x3fL) == 0L) return (int) (color >> 32);
        float r = red(color);
        float g = green(color);
        float b = blue(color);
        float a = alpha(color);
        return ((int) (a * 255.0f + 0.5f) << 24) |
               ((int) (r * 255.0f + 0.5f) << 16) |
               ((int) (g * 255.0f + 0.5f) <<  8) |
                (int) (b * 255.0f + 0.5f);
    }

    public static Color valueOf(int color) {
        float r = ((color >> 16) & 0xff) / 255.0f;
        float g = ((color >>  8) & 0xff) / 255.0f;
        float b = ((color      ) & 0xff) / 255.0f;
        float a = ((color >> 24) & 0xff) / 255.0f;
        return new Color(r, g, b, a, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    public static Color valueOf(long color) {
        return new Color(red(color), green(color), blue(color), alpha(color), colorSpace(color));
    }

    public static Color valueOf(float r, float g, float b) {
        return new Color(r, g, b, 1.0f);
    }

    public static Color valueOf(float r, float g, float b, float a) {
        return new Color(saturate(r), saturate(g), saturate(b), saturate(a));
    }

    public static Color valueOf(float r, float g, float b, float a, ColorSpace colorSpace) {
        if (colorSpace.getComponentCount() > 3) {
            throw new IllegalArgumentException("The specified color space must "
                    + "use a color model with at most 3 color components");
        }
        return new Color(r, g, b, a, colorSpace);
    }

    public static Color valueOf(float[] components, ColorSpace colorSpace) {
        if (components.length < colorSpace.getComponentCount() + 1) {
            throw new IllegalArgumentException("Received a component array of length " +
                    components.length + " but the color model requires " +
                    (colorSpace.getComponentCount() + 1) + " (including alpha)");
        }
        return new Color(Arrays.copyOf(components, colorSpace.getComponentCount() + 1), colorSpace);
    }

    /** AOSP: an ARGB int is already sRGB, so packing is a straight shift into
     *  the high half with a color-space id of 0. */
    public static long pack(int color) {
        return (color & 0xffffffffL) << 32;
    }

    public static long pack(float red, float green, float blue) {
        return pack(red, green, blue, 1.0f, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    public static long pack(float red, float green, float blue, float alpha) {
        return pack(red, green, blue, alpha, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    public static long pack(float red, float green, float blue, float alpha,
            ColorSpace colorSpace) {
        if (isSrgb(colorSpace)) {
            int argb =
                    ((int) (alpha * 255.0f + 0.5f) << 24) |
                    ((int) (red   * 255.0f + 0.5f) << 16) |
                    ((int) (green * 255.0f + 0.5f) <<  8) |
                     (int) (blue  * 255.0f + 0.5f);
            return (argb & 0xffffffffL) << 32;
        }

        int id = colorSpace.getId();
        // AOSP compares against ColorSpace.MIN_ID (-1), the id reserved for a
        // space that did not come from ColorSpace.get().
        if (id == -1) {
            throw new IllegalArgumentException(
                    "Unknown color space, please use a color space returned by ColorSpace.get()");
        }
        if (colorSpace.getComponentCount() > 3) {
            throw new IllegalArgumentException(
                    "The color space must use a color model with at most 3 components");
        }

        // AOSP packs the three color components as IEEE-754 binary16 via
        // android.util.Half, which the framework now provides.
        short r = android.util.Half.toHalf(red);
        short g = android.util.Half.toHalf(green);
        short b = android.util.Half.toHalf(blue);

        int a = (int) (Math.max(0.0f, Math.min(alpha, 1.0f)) * 1023.0f + 0.5f);

        // Suppress sign extension
        return  (r & 0xffffL) << 48 |
                (g & 0xffffL) << 32 |
                (b & 0xffffL) << 16 |
                (a & 0x3ffL ) <<  6 |
                id & 0x3fL;
    }

    public static long convert(int color, ColorSpace colorSpace) {
        float r = ((color >> 16) & 0xff) / 255.0f;
        float g = ((color >>  8) & 0xff) / 255.0f;
        float b = ((color      ) & 0xff) / 255.0f;
        float a = ((color >> 24) & 0xff) / 255.0f;
        return convert(r, g, b, a, ColorSpace.get(ColorSpace.Named.SRGB), colorSpace);
    }

    public static long convert(long color, ColorSpace colorSpace) {
        return convert(red(color), green(color), blue(color), alpha(color),
                colorSpace(color), colorSpace);
    }

    /** AOSP Color.convert(r,g,b,a,source,destination) -- builds the connector
     *  and delegates. See the instance convert(ColorSpace) above for why this
     *  stopped being an identity on 2026-08-24. */
    public static long convert(float r, float g, float b, float a,
            ColorSpace source, ColorSpace destination) {
        ColorSpace.Connector connector = ColorSpace.connect(source, destination);
        return convert(r, g, b, a, connector);
    }

    /** AOSP Color.convert(r,g,b,a,connector) (API 26) -- converts through an
     *  already-built Connector and packs into the connector's DESTINATION space.
     *  Alpha is carried through untouched; only the three color components are
     *  transformed. */
    public static long convert(float r, float g, float b, float a,
            ColorSpace.Connector connector) {
        float[] color = connector.transform(r, g, b);
        return pack(color[0], color[1], color[2], a, connector.getDestination());
    }

    public static float luminance(long color) {
        ColorSpace colorSpace = colorSpace(color);
        if (colorSpace.getModel() != ColorSpace.Model.RGB) {
            throw new IllegalArgumentException("The specified color must be encoded in an RGB " +
                    "color space. The supplied color space is " + colorSpace.getModel());
        }
        double r = eotf(colorSpace, red(color));
        double g = eotf(colorSpace, green(color));
        double b = eotf(colorSpace, blue(color));
        return saturate((float) ((0.2126 * r) + (0.7152 * g) + (0.0722 * b)));
    }

    /** AOSP: relative luminance of an sRGB color int, per WCAG 2.0. Note this
     *  overload does NOT saturate the result (the long/instance forms do);
     *  that asymmetry is upstream's, and Material's contrast math depends on
     *  matching it. */
    public static float luminance(int color) {
        double r = srgbEotf(red(color)   / 255.0);
        double g = srgbEotf(green(color) / 255.0);
        double b = srgbEotf(blue(color)  / 255.0);
        return (float) ((0.2126 * r) + (0.7152 * g) + (0.0722 * b));
    }

    private static float saturate(float v) {
        return v <= 0.0f ? 0.0f : (v >= 1.0f ? 1.0f : v);
    }

    private static boolean isSrgb(ColorSpace cs) { return cs.getId() == SRGB_ID; }

    /** AOSP reads the EOTF off ColorSpace.Rgb. ColorSpace here has no transfer
     *  function and only ever hands out sRGB, so the sRGB curve is the right
     *  (and only reachable) answer; a future ColorSpace.Rgb must be consulted
     *  here instead. */
    private static double eotf(ColorSpace cs, float v) { return srgbEotf(v); }

    /** The sRGB electro-optical transfer function, i.e. AOSP's piecewise
     *  gamma response with the SRGB_TRANSFER_PARAMETERS
     *  (a=1/1.055, b=0.055/1.055, c=1/12.92, d=0.04045, g=2.4):
     *      x >= d ? pow(a*x + b, g) : c*x
     *  Input is clamped to [0,1] first, matching ColorSpace.Rgb's
     *  mClampedEotf = clamp.andThen(eotf). */
    private static double srgbEotf(double x) {
        double v = x <= 0.0 ? 0.0 : (x >= 1.0 ? 1.0 : x);
        return v >= 0.04045 ? Math.pow((v + 0.055) / 1.055, 2.4) : v / 12.92;
    }
}
