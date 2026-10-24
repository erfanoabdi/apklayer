package android.graphics.fonts;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One OpenType variable-font axis: a 4-character tag plus its value
 * (for example "wght" 700, "wdth" 87.5).
 *
 * Was a 1-line auto-generated stub. The settings-string parser matters because
 * apps and resource XML both express variation settings as the CSS-style
 * string form ("'wght' 700, 'slnt' -10"), and Paint/Typeface round-trip
 * through it.
 *
 * Source: frameworks/base/graphics/java/android/graphics/fonts/FontVariationAxis.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/graphics/java/android/graphics/fonts/FontVariationAxis.java
 */
public final class FontVariationAxis {

    private final int mTag;
    private final String mTagString;
    private final float mStyleValue;

    public FontVariationAxis(String tagString, float styleValue) {
        if (!isValidTag(tagString)) {
            throw new IllegalArgumentException("Illegal tag pattern: " + tagString);
        }
        mTag = makeTag(tagString);
        mTagString = tagString;
        mStyleValue = styleValue;
    }

    public String getTag() { return mTagString; }

    public float getStyleValue() { return mStyleValue; }

    /** AOSP: a tag is exactly 4 printable ASCII characters (0x20..0x7E). */
    private static boolean isValidTag(String tagString) {
        if (tagString == null || tagString.length() != 4) return false;
        for (int i = 0; i < 4; i++) {
            char c = tagString.charAt(i);
            if (c < 0x20 || c > 0x7E) return false;
        }
        return true;
    }

    private static int makeTag(String tagString) {
        char c1 = tagString.charAt(0), c2 = tagString.charAt(1);
        char c3 = tagString.charAt(2), c4 = tagString.charAt(3);
        return (c1 << 24) | (c2 << 16) | (c3 << 8) | c4;
    }

    int getOpenTypeTagValue() { return mTag; }

    // "'wght' 700" / "\"wdth\" 87.5" / "wght 700"
    private static final Pattern STYLE_VALUE_PATTERN = Pattern.compile(
            "'(.{4})'\\s*([-0-9.]+)|\"(.{4})\"\\s*([-0-9.]+)|(.{4})\\s*([-0-9.]+)");

    /** Parse the CSS-style settings string; AOSP returns null for empty input. */
    public static FontVariationAxis[] fromFontVariationSettings(String settings) {
        if (settings == null || settings.isEmpty()) return null;
        ArrayList<FontVariationAxis> axisList = new ArrayList<>();
        for (String piece : settings.split(",")) {
            String token = piece.trim();
            if (token.isEmpty()) continue;
            Matcher m = STYLE_VALUE_PATTERN.matcher(token);
            if (!m.matches()) {
                throw new IllegalArgumentException(token + " is invalid format.");
            }
            String tag = m.group(1) != null ? m.group(1)
                    : (m.group(3) != null ? m.group(3) : m.group(5));
            String value = m.group(2) != null ? m.group(2)
                    : (m.group(4) != null ? m.group(4) : m.group(6));
            axisList.add(new FontVariationAxis(tag, Float.parseFloat(value)));
        }
        return axisList.isEmpty() ? null : axisList.toArray(new FontVariationAxis[0]);
    }

    /** Render back to the settings string form; null/empty maps to null. */
    public static String toFontVariationSettings(FontVariationAxis[] axes) {
        if (axes == null || axes.length == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < axes.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(axes[i].toString());
        }
        return sb.toString();
    }

    @Override public String toString() {
        return "'" + mTagString + "' " + Float.toString(mStyleValue);
    }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof FontVariationAxis)) return false;
        FontVariationAxis a = (FontVariationAxis) o;
        return mTag == a.mTag && mStyleValue == a.mStyleValue;
    }

    @Override public int hashCode() {
        return mTag * 31 + Float.floatToIntBits(mStyleValue);
    }
}
