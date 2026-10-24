// android.graphics.text.LineBreaker -- AOSP low-level greedy/optimal line
// breaker. AOSP:
//   frameworks/base/graphics/java/android/graphics/text/LineBreaker.java
//   (final class with Builder, ParagraphConstraints, Result).
//
// StaticLayout (and Compose's AndroidParagraph) can drive this directly. We
// implement a greedy break over the MeasuredText's per-char advances at the
// constraint width (break at the last breakable space that fits, hard-split an
// over-wide word) -- BREAK_STRATEGY_SIMPLE semantics, which is the common case
// and what every caller falls back to. Hyphenation / optimal balancing are
// honest no-ops (the strategy flags are stored but the greedy result is
// returned).
package android.graphics.text;

import android.graphics.Paint;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class LineBreaker {
    public static final int BREAK_STRATEGY_SIMPLE = 0;
    public static final int BREAK_STRATEGY_HIGH_QUALITY = 1;
    public static final int BREAK_STRATEGY_BALANCED = 2;

    public static final int HYPHENATION_FREQUENCY_NONE = 0;
    public static final int HYPHENATION_FREQUENCY_NORMAL = 1;
    public static final int HYPHENATION_FREQUENCY_FULL = 2;

    public static final int JUSTIFICATION_MODE_NONE = 0;
    public static final int JUSTIFICATION_MODE_INTER_WORD = 1;
    public static final int JUSTIFICATION_MODE_INTER_CHARACTER = 2;

    private final int mBreakStrategy;
    private final int mHyphenationFrequency;
    private final int mJustificationMode;
    private final int[] mIndents;

    private final boolean mUseBoundsForWidth;

    private LineBreaker(int breakStrategy, int hyphenationFrequency,
                        int justificationMode, int[] indents,
                        boolean useBoundsForWidth) {
        mBreakStrategy = breakStrategy;
        mHyphenationFrequency = hyphenationFrequency;
        mJustificationMode = justificationMode;
        mIndents = indents;
        mUseBoundsForWidth = useBoundsForWidth;
    }

    // ---- AOSP LineBreaker getters (API 35). They report exactly what the
    //      Builder was given; nothing is resolved or defaulted here.
    public int getBreakStrategy() { return mBreakStrategy; }
    public int getHyphenationFrequency() { return mHyphenationFrequency; }
    /** @Nullable in AOSP -- null when setIndents was never called. */
    public int[] getIndents() { return mIndents; }
    public int getJustificationMode() { return mJustificationMode; }
    public boolean getUseBoundsForWidth() { return mUseBoundsForWidth; }

    public static final class Builder {
        private int mBreakStrategy = BREAK_STRATEGY_SIMPLE;
        private int mHyphenationFrequency = HYPHENATION_FREQUENCY_NONE;
        private int mJustificationMode = JUSTIFICATION_MODE_NONE;
        private int[] mIndents;

        public Builder() {}

        public Builder setBreakStrategy(int breakStrategy) {
            mBreakStrategy = breakStrategy; return this;
        }
        public Builder setHyphenationFrequency(int hyphenationFrequency) {
            mHyphenationFrequency = hyphenationFrequency; return this;
        }
        public Builder setJustificationMode(int justificationMode) {
            mJustificationMode = justificationMode; return this;
        }
        public Builder setIndents(int[] indents) {
            mIndents = indents; return this;
        }
        /** AOSP LineBreaker.Builder.setUseBoundsForWidth(boolean) (API 34) --
         *  measure lines by the glyphs' painted BOUNDS rather than by their
         *  advances. Our greedy breaker measures advances only, so the flag is
         *  carried for getUseBoundsForWidth() and does not change a break. */
        public Builder setUseBoundsForWidth(boolean useBoundsForWidth) {
            mUseBoundsForWidth = useBoundsForWidth; return this;
        }
        private boolean mUseBoundsForWidth;

        public LineBreaker build() {
            return new LineBreaker(mBreakStrategy, mHyphenationFrequency,
                mJustificationMode, mIndents, mUseBoundsForWidth);
        }
    }

    public static class ParagraphConstraints {
        private float mWidth = 0f;
        private float mFirstWidth = 0f;
        private int mFirstWidthLineCount = 0;
        private float[] mTabStops = null;
        private float mDefaultTabStop = 0f;

        public ParagraphConstraints() {}

        public void setWidth(float width) { mWidth = width; }
        public void setIndent(float firstWidth, int firstWidthLineCount) {
            mFirstWidth = firstWidth;
            mFirstWidthLineCount = firstWidthLineCount;
        }
        public void setTabStops(float[] tabStops, float defaultTabStop) {
            mTabStops = tabStops;
            mDefaultTabStop = defaultTabStop;
        }
        public float getWidth() { return mWidth; }
        public float getFirstWidth() { return mFirstWidth; }
        public int getFirstWidthLineCount() { return mFirstWidthLineCount; }
        public float[] getTabStops() { return mTabStops; }
        public float getDefaultTabStop() { return mDefaultTabStop; }
    }

    public static class Result {
        private final int[] mBreakOffsets;
        private final float[] mWidths;
        private final float mAscent;
        private final float mDescent;

        Result(int[] breakOffsets, float[] widths, float ascent, float descent) {
            mBreakOffsets = breakOffsets;
            mWidths = widths;
            mAscent = ascent;
            mDescent = descent;
        }

        public int getLineCount() { return mBreakOffsets.length; }
        public int getLineBreakOffset(int lineIndex) { return mBreakOffsets[lineIndex]; }
        public float getLineWidth(int lineIndex) { return mWidths[lineIndex]; }
        public float getLineAscent(int lineIndex) { return mAscent; }
        public float getLineDescent(int lineIndex) { return mDescent; }
        public boolean hasLineTab(int lineIndex) { return false; }
        public int getStartLineHyphenEdit(int lineIndex) { return 0; }
        public int getEndLineHyphenEdit(int lineIndex) { return 0; }
    }

    public Result computeLineBreaks(MeasuredText measuredPara,
                                    ParagraphConstraints constraints, int lineNumber) {
        char[] chars = measuredPara.getChars();
        int n = (chars != null) ? chars.length : 0;
        float width = constraints.getWidth();

        java.util.ArrayList<Integer> offsets = new java.util.ArrayList<>();
        java.util.ArrayList<Float> widths = new java.util.ArrayList<>();

        int lineBegin = 0;
        int lastBreak = -1;       // index AFTER the last breakable char on the line
        float lineWidth = 0f;
        float widthAtLastBreak = 0f;
        int i = 0;
        while (i < n) {
            char c = chars[i];
            float adv = measuredPara.getCharWidthAt(i);
            if (c == '\n') {                          // hard break
                offsets.add(i + 1);
                widths.add(lineWidth);
                lineBegin = i + 1; lastBreak = -1; lineWidth = 0f; widthAtLastBreak = 0f;
                i++;
                continue;
            }
            if (width > 0 && lineWidth + adv > width && i > lineBegin) {
                int breakAt;
                float emitWidth;
                if (lastBreak > lineBegin) {
                    breakAt = lastBreak;
                    emitWidth = widthAtLastBreak;
                } else {
                    breakAt = i;                      // hard-split a long word
                    emitWidth = lineWidth;
                }
                offsets.add(breakAt);
                widths.add(emitWidth);
                // Recompute the carried width from breakAt..i for the new line.
                lineBegin = breakAt; lastBreak = -1;
                lineWidth = measuredPara.getWidth(breakAt, i);
                widthAtLastBreak = 0f;
                // do NOT advance i; re-test current char on the new line
                continue;
            }
            lineWidth += adv;
            if (c == ' ') { lastBreak = i + 1; widthAtLastBreak = lineWidth; }
            i++;
        }
        // Final line.
        offsets.add(n);
        widths.add(lineWidth);

        int[] offArr = new int[offsets.size()];
        float[] wArr = new float[widths.size()];
        for (int k = 0; k < offArr.length; k++) { offArr[k] = offsets.get(k); wArr[k] = widths.get(k); }

        float ascent = 0f, descent = 0f;
        // No paint exposed from MeasuredText for metrics here; ascent/descent
        // are left 0 (callers that need them use the Paint they built the
        // MeasuredText with). This matches the greedy-only contract.
        return new Result(offArr, wArr, ascent, descent);
    }
}
