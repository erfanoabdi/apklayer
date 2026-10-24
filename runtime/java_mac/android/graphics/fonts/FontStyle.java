package android.graphics.fonts;

/**
 * A font's weight (1..1000) and slant (upright or italic).
 *
 * Was a 1-line auto-generated stub. Typeface.CustomFallbackBuilder.setStyle
 * takes one, so a missing constructor broke the whole custom-fallback path at
 * the first call.
 *
 * Source: frameworks/base/graphics/java/android/graphics/fonts/FontStyle.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/graphics/java/android/graphics/fonts/FontStyle.java
 */
public final class FontStyle {

    public static final int FONT_WEIGHT_UNSPECIFIED = -1;
    public static final int FONT_WEIGHT_THIN = 100;
    public static final int FONT_WEIGHT_EXTRA_LIGHT = 200;
    public static final int FONT_WEIGHT_LIGHT = 300;
    public static final int FONT_WEIGHT_NORMAL = 400;
    public static final int FONT_WEIGHT_MEDIUM = 500;
    public static final int FONT_WEIGHT_SEMI_BOLD = 600;
    public static final int FONT_WEIGHT_BOLD = 700;
    public static final int FONT_WEIGHT_EXTRA_BOLD = 800;
    public static final int FONT_WEIGHT_BLACK = 900;
    public static final int FONT_WEIGHT_MIN = 1;
    public static final int FONT_WEIGHT_MAX = 1000;

    public static final int FONT_SLANT_UPRIGHT = 0;
    public static final int FONT_SLANT_ITALIC = 1;

    private final int mWeight;
    private final int mSlant;

    public FontStyle() { this(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT); }

    public FontStyle(int weight, int slant) {
        if (weight < FONT_WEIGHT_MIN || weight > FONT_WEIGHT_MAX) {
            throw new IllegalArgumentException("weight value must be [1, 1000]");
        }
        if (slant != FONT_SLANT_UPRIGHT && slant != FONT_SLANT_ITALIC) {
            throw new IllegalArgumentException("slant value must be FONT_SLANT_UPRIGHT or FONT_SLANT_ITALIC");
        }
        mWeight = weight;
        mSlant = slant;
    }

    public int getWeight() { return mWeight; }

    public int getSlant() { return mSlant; }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof FontStyle)) return false;
        FontStyle f = (FontStyle) o;
        return f.mWeight == mWeight && f.mSlant == mSlant;
    }

    @Override public int hashCode() { return mWeight * 31 + mSlant; }

    @Override public String toString() {
        return "FontStyle { weight=" + mWeight + ", slant=" + mSlant + "}";
    }
}
