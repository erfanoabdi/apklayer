package android.graphics.text;

import android.graphics.fonts.Font;

/**
 * AOSP-faithful android.graphics.text.PositionedGlyphs (API 31). The output of
 * TextRunShaper: a laid-out run of glyphs with X/Y positions and an overall
 * advance. Real Android carries native HarfBuzz glyph ids; we have no shaping
 * engine, so we model one "glyph" per character with positions derived from
 * Paint.measureText (a faithful monotone advance layout). Glyph ids are the
 * source char codes. Mirrors
 * frameworks/base/graphics/java/android/graphics/text/PositionedGlyphs.java.
 */
public final class PositionedGlyphs {
    /** AOSP PositionedGlyphs.NO_OVERRIDE (API 35,
     *  com.android.text.flags.new_fonts_fallback_xml) -- the sentinel meaning
     *  "this glyph carries no font-variation override". It is Float.MIN_VALUE
     *  (1.4E-45f), the smallest positive subnormal, chosen because no real
     *  variation value can equal it. */
    public static final float NO_OVERRIDE = Float.MIN_VALUE;

    private final int[] mGlyphIds;
    private final float[] mPositions; // interleaved x,y per glyph
    private final float mXOffset;
    private final float mYOffset;
    private final float mAdvance;
    private final float mAscent;
    private final float mDescent;

    PositionedGlyphs(int[] glyphIds, float[] positions, float xOffset, float yOffset,
            float advance, float ascent, float descent) {
        mGlyphIds = glyphIds;
        mPositions = positions;
        mXOffset = xOffset;
        mYOffset = yOffset;
        mAdvance = advance;
        mAscent = ascent;
        mDescent = descent;
    }

    public int glyphCount() { return mGlyphIds.length; }
    public float getAdvance() { return mAdvance; }
    public float getAscent() { return mAscent; }
    public float getDescent() { return mDescent; }
    public float getOffsetX() { return mXOffset; }
    public float getOffsetY() { return mYOffset; }

    public int getGlyphId(int index) { return mGlyphIds[index]; }
    public float getGlyphX(int index) { return mPositions[index * 2]; }
    public float getGlyphY(int index) { return mPositions[index * 2 + 1]; }

    // Every run is drawn with the one system face (no per-glyph fallback
    // chain), so that is what this reports rather than an empty Font value.
    public Font getFont(int index) { return Font.macapkSystemFont(); }
    public boolean getFakeBold(int index) { return false; }
    public boolean getFakeItalic(int index) { return false; }
    public float getWeightOverride(int index) { return Float.NaN; }
    public float getItalicOverride(int index) { return Float.NaN; }
}
