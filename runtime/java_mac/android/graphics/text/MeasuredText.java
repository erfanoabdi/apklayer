// android.graphics.text.MeasuredText -- AOSP precomputed per-character text
// measurement, built from style runs over a char[]. AOSP:
//   frameworks/base/graphics/java/android/graphics/text/MeasuredText.java
//   (a final class with a Builder of appendStyleRun / appendReplacementRun).
//
// Real Android measures glyphs natively and caches per-char advances; we back
// it with the Paint that each style run carries, measuring widths via
// Paint.measureText / per-char widths. That gives faithful getWidth /
// getCharWidthAt / getBounds for the LineBreaker and any caller that
// precomputes a paragraph's measurement.
package android.graphics.text;

import android.graphics.Paint;
import android.graphics.Rect;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class MeasuredText {
    private final char[] mChars;
    private final float[] mCharWidths;   // advance per char index
    private final Paint mPaintForBounds; // last style run's paint (for bounds)

    private MeasuredText(char[] chars, float[] widths, Paint paint) {
        mChars = chars;
        mCharWidths = widths;
        mPaintForBounds = paint;
    }

    public char[] getChars() { return mChars; }

    public float getWidth(int start, int end) {
        float w = 0f;
        int s = Math.max(0, start), e = Math.min(mCharWidths.length, end);
        for (int i = s; i < e; i++) w += mCharWidths[i];
        return w;
    }

    public float getCharWidthAt(int offset) {
        if (offset < 0 || offset >= mCharWidths.length) return 0f;
        return mCharWidths[offset];
    }

    public void getBounds(int start, int end, Rect rect) {
        if (rect == null) return;
        int w = Math.round(getWidth(start, end));
        int h = 0;
        if (mPaintForBounds != null) {
            Paint.FontMetricsInt fm = mPaintForBounds.getFontMetricsInt();
            h = fm.descent - fm.ascent;
        }
        rect.set(0, -((mPaintForBounds != null)
            ? -mPaintForBounds.getFontMetricsInt().ascent : 0), w, h);
    }

    /** AOSP MeasuredText.getFontMetricsInt(start, end, outMetrics)
     *  (MeasuredText.java:157, API 34) -- the vertical extent of the glyphs in
     *  [start, end), which on device can be TALLER than the paint's own metrics
     *  when a fallback font is used for part of the run.
     *
     *  AOSP's own rule for top/bottom is the important half and is reproduced
     *  exactly: ascent/descent come from the RUN, then top = min(ascent, the
     *  measured text's overall top) and bottom = max(descent, overall bottom).
     *  We resolve one face per paint, so the run's ascent/descent are the
     *  paint's -- but the min/max still runs, so the answer stays consistent
     *  with getFontMetricsInt() on the same paint.
     *
     *  Throws IndexOutOfBoundsException for a bad range and NPE for a null out
     *  param, both of which AOSP does before touching native. */
    public void getFontMetricsInt(int start, int end, Paint.FontMetricsInt outMetrics) {
        if (start < 0 || end < start || end > mChars.length) {
            throw new IndexOutOfBoundsException(
                    "start(" + start + ") end(" + end + ") length(" + mChars.length + ")");
        }
        if (outMetrics == null) throw new NullPointerException("outMetrics must not be null");
        if (mPaintForBounds == null) {
            outMetrics.top = outMetrics.ascent = outMetrics.descent = outMetrics.bottom = 0;
            outMetrics.leading = 0;
            return;
        }
        Paint.FontMetricsInt fm = mPaintForBounds.getFontMetricsInt();
        outMetrics.ascent = fm.ascent;
        outMetrics.descent = fm.descent;
        outMetrics.top = Math.min(outMetrics.ascent, fm.top);
        outMetrics.bottom = Math.max(outMetrics.descent, fm.bottom);
        outMetrics.leading = fm.leading;
    }

    public int getMemoryUsage() { return mChars.length * 4; }

    public static final class Builder {
        /** AOSP MeasuredText.Builder hyphenation modes (API 33). Passed to
         *  setComputeHyphenation(int). */
        public static final int HYPHENATION_MODE_NONE = 0;
        public static final int HYPHENATION_MODE_NORMAL = 1;
        public static final int HYPHENATION_MODE_FAST = 2;

        private final char[] mText;
        private final float[] mWidths;
        private int mCursor = 0;
        private Paint mLastPaint;

        public Builder(char[] text) {
            mText = text;
            mWidths = new float[text != null ? text.length : 0];
        }

        public Builder(MeasuredText text) {
            mText = (text != null) ? text.mChars : new char[0];
            mWidths = (text != null) ? text.mCharWidths.clone() : new float[0];
            mCursor = mWidths.length;
            mLastPaint = (text != null) ? text.mPaintForBounds : null;
        }

        public Builder appendStyleRun(Paint paint, int length, boolean isRtl) {
            measureRun(paint, length);
            return this;
        }

        public Builder appendStyleRun(Paint paint, LineBreakConfig lineBreakConfig,
                                      int length, boolean isRtl) {
            measureRun(paint, length);
            return this;
        }

        public Builder appendReplacementRun(Paint paint, int length, float width) {
            // A replacement run occupies a fixed total width; AOSP puts the
            // whole advance on the first char of the run, 0 on the rest.
            int start = mCursor;
            for (int i = 0; i < length && mCursor < mWidths.length; i++) {
                mWidths[mCursor++] = (i == 0) ? width : 0f;
            }
            mLastPaint = paint;
            return this;
        }

        public Builder setComputeHyphenation(boolean computeHyphenation) { return this; }
        public Builder setComputeHyphenation(int mode) { return this; }
        public Builder setComputeLayout(boolean computeLayout) { return this; }

        private void measureRun(Paint paint, int length) {
            mLastPaint = paint;
            for (int i = 0; i < length && mCursor < mWidths.length; i++) {
                float w = (paint != null)
                    ? paint.measureText(String.valueOf(mText[mCursor])) : 0f;
                mWidths[mCursor++] = w;
            }
        }

        public MeasuredText build() {
            return new MeasuredText(mText, mWidths, mLastPaint);
        }
    }
}
