// android.graphics.RadialGradient -- a Shader drawing a radial color gradient.
// Vector drawables (and Compose's vector parser) create these for radial
// <gradient> fills; provided so vector parsing doesn't NoClassDefFoundError.
// v1.0.406.
package android.graphics;

public class RadialGradient extends Shader {
    public final float centerX, centerY, radius;
    public final int[] colors;
    public final float[] stops;
    public final TileMode tile;

    public RadialGradient(float centerX, float centerY, float radius,
                          int[] colors, float[] stops, TileMode tile) {
        this.centerX = centerX; this.centerY = centerY; this.radius = radius;
        this.colors = colors; this.stops = stops;
        this.tile = tile != null ? tile : TileMode.CLAMP;
    }

    public RadialGradient(float centerX, float centerY, float radius,
                          int centerColor, int edgeColor, TileMode tile) {
        this(centerX, centerY, radius, new int[] { centerColor, edgeColor }, null, tile);
    }

    public RadialGradient(float centerX, float centerY, float radius,
                          long[] colors, float[] stops, TileMode tile) {
        this(centerX, centerY, radius, LinearGradient.longsToInts(colors), stops, tile);
    }

    /** AOSP RadialGradient(startX, startY, startRadius, endX, endY, endRadius,
     *  colors, positions, tileMode) (API 36) -- a TWO-POINT CONICAL gradient
     *  (Skia's SkGradientShader::MakeTwoPointConical). Our Shader plumbing
     *  models a single-circle radial only, so we keep both circles' parameters
     *  and expose the END circle through the existing centerX/centerY/radius
     *  fields, which is what a renderer that ignores the start circle would
     *  draw. The start circle is retained on macapkStart* for a renderer that
     *  can honour it; nothing invents a value it was not given. */
    public RadialGradient(float startX, float startY, float startRadius,
                          float endX, float endY, float endRadius,
                          long[] colors, float[] stops, TileMode tile) {
        this(endX, endY, endRadius, LinearGradient.longsToInts(colors), stops, tile);
        macapkStartX = startX;
        macapkStartY = startY;
        macapkStartRadius = startRadius;
    }
    /** The inner circle of a two-point conical gradient; 0 for the plain
     *  single-circle constructors, which is exactly the degenerate case. */
    public float macapkStartX, macapkStartY, macapkStartRadius;

    public RadialGradient(float centerX, float centerY, float radius,
                          long centerColor, long edgeColor, TileMode tile) {
        // ⚠ `(int) color` is WRONG and was the bug fixed in LinearGradient/SweepGradient at
        // v1.0.1105: AOSP `Color.pack(int)` is `(argb & 0xffffffffL) << 32`, so for an sRGB
        // ColorLong the ARGB int is in the HIGH 32 bits and the low 32 are ZERO. This two-stop
        // long-colour ctor was MISSED by that pass (the array form goes through
        // LinearGradient.longsToInts, which was fixed), so every two-stop long-colour radial had
        // stops of 0x00000000 -- transparent black. After the premul-safe-stop fix an
        // all-transparent gradient paints nothing at all, so this now fails silently rather than
        // as a dark wash. Color.toArgb(long) is the documented inverse.
        this(centerX, centerY, radius,
             new int[] { Color.toArgb(centerColor), Color.toArgb(edgeColor) }, null, tile);
    }
}
