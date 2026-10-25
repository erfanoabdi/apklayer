package android.graphics.text;

import android.graphics.Paint;

/**
 * AOSP-faithful android.graphics.text.TextRunShaper (API 31). Shapes a single
 * bidirectional text run into PositionedGlyphs. Real Android runs HarfBuzz; we
 * have no shaper, so we lay out one glyph per character with advances measured
 * by Paint.measureText, walking left-to-right (or right-to-left if isRtl). The
 * result has correct total advance and per-char X positions, which is enough
 * for callers that only measure/position runs. Mirrors
 * frameworks/base/graphics/java/android/graphics/text/TextRunShaper.java.
 */
public class TextRunShaper {
    private TextRunShaper() {}

    public static PositionedGlyphs shapeTextRun(char[] text, int start, int count,
            int contextStart, int contextCount, float xOffset, float yOffset,
            boolean isRtl, Paint paint) {
        return shape(new String(text, start, count), xOffset, yOffset, isRtl, paint);
    }

    public static PositionedGlyphs shapeTextRun(CharSequence text, int start, int count,
            int contextStart, int contextCount, float xOffset, float yOffset,
            boolean isRtl, Paint paint) {
        return shape(text.subSequence(start, start + count).toString(),
                xOffset, yOffset, isRtl, paint);
    }

    private static PositionedGlyphs shape(String run, float xOffset, float yOffset,
            boolean isRtl, Paint paint) {
        int n = run.length();
        int[] ids = new int[n];
        float[] pos = new float[n * 2];
        float total = paint.measureText(run);
        float ascent = paint.ascent();
        float descent = paint.descent();
        if (isRtl) {
            // Place glyphs right-to-left: first char sits at the right edge.
            float cursor = xOffset + total;
            for (int i = 0; i < n; i++) {
                float adv = paint.measureText(run, i, i + 1);
                cursor -= adv;
                ids[i] = run.charAt(i);
                pos[i * 2] = cursor;
                pos[i * 2 + 1] = yOffset;
            }
        } else {
            float cursor = xOffset;
            for (int i = 0; i < n; i++) {
                ids[i] = run.charAt(i);
                pos[i * 2] = cursor;
                pos[i * 2 + 1] = yOffset;
                cursor += paint.measureText(run, i, i + 1);
            }
        }
        return new PositionedGlyphs(ids, pos, xOffset, yOffset, total, ascent, descent);
    }
}
