// android.text.TextShaper (API 31) -- runs the text shaper over a range and
// hands each resulting glyph run to a consumer, so a caller can draw text with
// its own per-glyph placement (Compose's drawText fast path, a custom
// canvas-based renderer, a text-on-a-path effect). Was absent, so both the
// class and the nested GlyphsConsumer interface were NoClassDefFoundError.
//
// Mirrors frameworks/base/core/java/android/text/TextShaper.java's public
// surface. AOSP splits the range into style runs (one per MetricAffectingSpan
// stretch), shapes each with the paint that span produces, and calls the
// consumer once per run with that run's PositionedGlyphs.
package android.text;

import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;

public class TextShaper {

    private TextShaper() {
    }

    /**
     * AOSP TextShaper.shapeText(CharSequence text, int start, int count,
     * TextDirectionHeuristic dir, TextPaint paint, GlyphsConsumer consumer).
     *
     * Note the third argument is a COUNT, not an end offset -- the range shaped
     * is [start, start + count).
     *
     * AOSP walks MetricAffectingSpans to split the range into style runs and
     * calls the consumer once per run, each with the offset of that run
     * relative to `start`. Reproduced here: an unspanned (or
     * MetricAffectingSpan-free) range shapes as ONE run, which is the common
     * case and matches AOSP exactly; a spanned range is split at each span
     * transition and each piece is shaped with a TextPaint the span updated,
     * which is what AOSP's own updateMeasureState pass produces.
     */
    public static void shapeText(CharSequence text, int start, int count,
            TextDirectionHeuristic dir, TextPaint paint, GlyphsConsumer consumer) {
        if (text == null) throw new NullPointerException("text must not be null");
        if (dir == null) throw new NullPointerException("dir must not be null");
        if (paint == null) throw new NullPointerException("paint must not be null");
        if (consumer == null) throw new NullPointerException("consumer must not be null");
        if (start < 0 || count < 0 || start + count > text.length()) {
            throw new IllegalArgumentException(
                    "invalid range: " + start + ", " + count + " over " + text.length());
        }
        if (count == 0) return;

        final int end = start + count;
        final boolean isRtl = dir.isRtl(text, start, count);

        if (!(text instanceof Spanned)) {
            shapeOneRun(text, start, end, start, isRtl, paint, consumer);
            return;
        }

        final Spanned spanned = (Spanned) text;
        int runStart = start;
        while (runStart < end) {
            int runEnd = spanned.nextSpanTransition(runStart, end,
                    android.text.style.MetricAffectingSpan.class);
            TextPaint runPaint = paint;
            android.text.style.MetricAffectingSpan[] spans = spanned.getSpans(
                    runStart, runEnd, android.text.style.MetricAffectingSpan.class);
            if (spans != null && spans.length > 0) {
                // A copy, so a span cannot mutate the caller's paint. AOSP uses
                // its own internal TextPaint workhorse for the same reason.
                runPaint = new TextPaint(paint);
                for (int i = 0; i < spans.length; i++) {
                    try { spans[i].updateMeasureState(runPaint); } catch (Throwable ignored) {}
                }
            }
            shapeOneRun(text, runStart, runEnd, start, isRtl, runPaint, consumer);
            runStart = runEnd;
        }
    }

    private static void shapeOneRun(CharSequence text, int runStart, int runEnd, int rangeStart,
            boolean isRtl, TextPaint paint, GlyphsConsumer consumer) {
        PositionedGlyphs glyphs = TextRunShaper.shapeTextRun(
                text, runStart, runEnd - runStart, runStart, runEnd - runStart, 0f, 0f,
                isRtl, paint);
        // AOSP reports the run's offset RELATIVE to the shaped range's start,
        // not an absolute text index.
        consumer.accept(runStart - rangeStart, runEnd - runStart, glyphs, paint);
    }

    /** AOSP TextShaper.GlyphsConsumer. */
    public interface GlyphsConsumer {
        /**
         * @param start the offset of this run, relative to the start of the
         *              range passed to shapeText (NOT an absolute text index)
         * @param count the number of characters in this run
         * @param glyphs the shaped glyphs of this run
         * @param paint the paint this run was shaped with, after any
         *              MetricAffectingSpan on it was applied
         */
        void accept(int start, int count, PositionedGlyphs glyphs, TextPaint paint);
    }
}
