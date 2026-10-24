package android.graphics.fonts;

import java.util.ArrayList;
import java.util.List;

/**
 * A group of Fonts that differ only in weight/slant -- one typographic family.
 *
 * Was a 1-line auto-generated stub. Typeface.CustomFallbackBuilder takes one,
 * so the family API had to exist for any custom-fallback Typeface to be built.
 *
 * Source: frameworks/base/graphics/java/android/graphics/fonts/FontFamily.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/graphics/java/android/graphics/fonts/FontFamily.java
 */
public final class FontFamily {

    private final List<Font> mFonts;

    FontFamily(List<Font> fonts) { mFonts = fonts; }

    public Font getFont(int index) { return mFonts.get(index); }

    public int getSize() { return mFonts.size(); }

    public static final class Builder {

        private final ArrayList<Font> mFonts = new ArrayList<>();

        public Builder(Font font) {
            if (font == null) throw new NullPointerException("font can not be null");
            mFonts.add(font);
        }

        /** AOSP rejects a duplicate (weight, slant) pair within one family. */
        public Builder addFont(Font font) {
            if (font == null) throw new NullPointerException("font can not be null");
            for (Font f : mFonts) {
                if (f.getStyle().equals(font.getStyle())) {
                    throw new IllegalArgumentException(
                            font.getStyle() + " has already been added");
                }
            }
            mFonts.add(font);
            return this;
        }

        public FontFamily build() { return new FontFamily(new ArrayList<>(mFonts)); }

        /** AOSP FontFamily.Builder.buildVariableFamily() (FontFamily.java:158,
         *  API 35) -- build a family that covers the whole weight (and possibly
         *  italic) range from ONE OR TWO VARIABLE fonts, or return NULL when the
         *  fonts cannot form one.
         *
         *  AOSP's analyzeAndResolveVariableType (:266) returns UNKNOWN -- and so
         *  the method returns null -- whenever there are MORE THAN TWO fonts, or
         *  the font(s) do not expose a 'wght' variation axis. Detecting 'wght'
         *  needs the font file's fvar table, which we do not parse; what we DO
         *  know is the axes the caller declared through setFontVariationSettings,
         *  so that is what is tested. A variable font added without declaring
         *  'wght' answers null here where a device would answer a family -- a
         *  NARROWING that keeps every answer we do give correct, and null is an
         *  outcome this API is documented to produce and callers must handle. */
        public FontFamily buildVariableFamily() {
            if (mFonts.isEmpty() || mFonts.size() > 2) return null;
            for (Font f : mFonts) {
                FontVariationAxis[] axes = f.getAxes();
                boolean hasWght = false;
                if (axes != null) {
                    for (FontVariationAxis a : axes) {
                        if ("wght".equals(a.getTag())) { hasWght = true; break; }
                    }
                }
                if (!hasWght) return null;
            }
            return build();
        }
    }
}
