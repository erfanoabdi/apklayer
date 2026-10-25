// android.text.PrecomputedText (API 28+). AOSP:
//   frameworks/base/core/java/android/text/PrecomputedText.java
//
// PrecomputedText.create(text, Params) splits text into paragraphs and
// measures each one so a caller (almost always androidx.core.text.
// PrecomputedTextCompat, invoked from a RecyclerView row's background
// prefetch via TextViewCompat.setPrecomputedText) can hand a TextView
// already-measured text instead of making the UI thread do that work.
// androidx.core.text.PrecomputedTextCompat.Params.Builder gates on
// SDK_INT >= 29 to build a real platform Params.Builder instead of its
// own polyfill (androidx-main core/core/.../PrecomputedTextCompat.java:179);
// Mac.apk reports SDK_INT 29 (Linkify.java), so that branch always runs
// here. Before this file was implemented, PrecomputedText was a 10-line
// stub with no nested Params/Builder at all -- any app using the standard
// AndroidX "precompute text off the main thread" optimization (common for
// any text-heavy RecyclerView: feeds, articles, descriptions)
// NoClassDefFoundError'd the first time it built one, typically on a
// background prefetch thread.
//
// Mac.apk has no separate render thread contending for text layout, so
// create() runs synchronously on the calling thread instead of AOSP's
// background-thread dance; the OBSERVABLE contract (a correctly measured,
// paragraph-indexed, span-carrying CharSequence comes back) is the same
// either way. What we do NOT replicate is AOSP's create()-time reuse of a
// "hint" PrecomputedText (the checkResultUsable/NEED_RECOMPUTE machinery)
// -- that is a pure CPU/memory optimization for repeated recomputation
// with slightly different Params and has no app-visible behavioral effect
// beyond doing (harmless, idempotent) extra work.
package android.text;

import android.graphics.text.LineBreakConfig;
import android.graphics.text.LineBreaker;
import android.graphics.text.MeasuredText;
import android.text.style.MetricAffectingSpan;

import java.util.ArrayList;
import java.util.Objects;

public class PrecomputedText implements Spannable {

    // A paragraph is [start, paragraphEnd) of the source text. Per AOSP
    // createMeasuredParagraphs, paragraphEnd includes the '\n' that ends
    // the paragraph (the next paragraph starts right after it); the final
    // paragraph's end is the text's own end whether or not it ends in '\n'.
    private static final class ParagraphInfo {
        final int paragraphEnd;
        final MeasuredText measured;
        ParagraphInfo(int paragraphEnd, MeasuredText measured) {
            this.paragraphEnd = paragraphEnd;
            this.measured = measured;
        }
    }

    private final SpannableString mText;
    private final int mStart;
    private final int mEnd;
    private final Params mParams;
    private final ParagraphInfo[] mParagraphInfo;

    private PrecomputedText(CharSequence text, int start, int end, Params params,
            ParagraphInfo[] paragraphInfo) {
        mText = new SpannableString(text);
        mStart = start;
        mEnd = end;
        mParams = params;
        mParagraphInfo = paragraphInfo;
    }

    /** AOSP PrecomputedText.create(CharSequence, Params). Splits `text` into
     *  paragraphs at line-feed boundaries and measures each paragraph with
     *  params' TextPaint, then wraps the result (spans preserved, since the
     *  backing SpannableString copies any Spanned input's spans). */
    public static PrecomputedText create(CharSequence text, Params params) {
        // AOSP createMeasuredParagraphs: Preconditions.checkNotNull(text/params),
        // which throws NullPointerException.
        if (text == null) throw new NullPointerException("text must not be null");
        if (params == null) throw new NullPointerException("params must not be null");
        ParagraphInfo[] paragraphInfo = createMeasuredParagraphs(text, params, 0, text.length());
        return new PrecomputedText(text, 0, text.length(), params, paragraphInfo);
    }

    private static ParagraphInfo[] createMeasuredParagraphs(
            CharSequence text, Params params, int start, int end) {
        ArrayList<ParagraphInfo> result = new ArrayList<>();
        int paraEnd;
        for (int paraStart = start; paraStart < end; paraStart = paraEnd) {
            paraEnd = TextUtils.indexOf(text, '\n', paraStart, end);
            if (paraEnd < 0) {
                paraEnd = end;
            } else {
                paraEnd++;  // the line feed belongs to the paragraph it ends
            }
            int len = paraEnd - paraStart;
            char[] chars = new char[len];
            for (int i = 0; i < len; i++) chars[i] = text.charAt(paraStart + i);
            MeasuredText measured = new MeasuredText.Builder(chars)
                .appendStyleRun(params.getTextPaint(), len, false)
                .build();
            result.add(new ParagraphInfo(paraEnd, measured));
        }
        return result.toArray(new ParagraphInfo[0]);
    }

    /** The Params this PrecomputedText was computed with. */
    public Params getParams() { return mParams; }

    public int getParagraphCount() { return mParagraphInfo.length; }

    public int getParagraphStart(int paraIndex) {
        checkParaIndex(paraIndex);
        return paraIndex == 0 ? mStart : getParagraphEnd(paraIndex - 1);
    }

    public int getParagraphEnd(int paraIndex) {
        checkParaIndex(paraIndex);
        return mParagraphInfo[paraIndex].paragraphEnd;
    }

    // AOSP guards both getters with Preconditions.checkArgumentInRange, which
    // throws IllegalArgumentException (not IndexOutOfBoundsException). We use
    // the exclusive upper bound [0, getParagraphCount()) for both methods --
    // AOSP's own bound is inclusive on getParagraphStart to let paraIndex ==
    // getParagraphCount() return the overall end, but getParagraphEnd's array
    // index makes that same inclusive bound an ArrayIndexOutOfBoundsException
    // waiting to happen. Every real caller iterates i in [0, getParagraphCount()),
    // so the exclusive bound is the safe, symmetric contract for both methods.
    private void checkParaIndex(int paraIndex) {
        if (paraIndex < 0 || paraIndex >= getParagraphCount()) {
            throw new IllegalArgumentException(
                "paraIndex(" + paraIndex + ") must be in [0, " + getParagraphCount() + ")");
        }
    }

    /**
     * AOSP PrecomputedText.findParaIndex(int pos) -- which paragraph an offset
     * belongs to. AOSP binary-searches its ParagraphInfo array; a linear scan
     * over the same array gives the same answer, and the array is short.
     */
    private int findParaIndex(int pos) {
        for (int i = 0; i < mParagraphInfo.length; ++i) {
            if (pos < mParagraphInfo[i].paragraphEnd) {
                return i;
            }
        }
        throw new IndexOutOfBoundsException(
                "pos must be less than " + mParagraphInfo[mParagraphInfo.length - 1].paragraphEnd
                + ", gave " + pos);
    }

    /** AOSP's shared precondition block for the three range queries below.
     *  Note the exception TYPE: Preconditions.checkArgument throws
     *  IllegalArgumentException, NOT IndexOutOfBoundsException. */
    private void checkRange(int start, int end) {
        if (!(0 <= start && start <= mText.length())) {
            throw new IllegalArgumentException("invalid start offset");
        }
        if (!(0 <= end && end <= mText.length())) {
            throw new IllegalArgumentException("invalid end offset");
        }
        if (start > end) {
            throw new IllegalArgumentException("start offset can not be larger than end offset");
        }
    }

    private int paraIndexFor(int start, int end) {
        final int paraIndex = findParaIndex(start);
        final int paraStart = getParagraphStart(paraIndex);
        final int paraEnd = getParagraphEnd(paraIndex);
        if (start < paraStart || paraEnd < end) {
            throw new IllegalArgumentException("Cannot measured across the paragraph:"
                    + "para: (" + paraStart + ", " + paraEnd + "), "
                    + "request: (" + start + ", " + end + ")");
        }
        return paraIndex;
    }

    /**
     * AOSP PrecomputedText.getWidth(int, int) (PrecomputedText.java:650-668) --
     * the advance width of [start, end), which MUST lie inside one paragraph.
     * Returns 0 for an empty range before any paragraph lookup. This is the
     * whole point of precomputing: the width comes out of the already-measured
     * paragraph rather than re-measuring.
     */
    public float getWidth(int start, int end) {
        checkRange(start, end);
        if (start == end) {
            return 0;
        }
        final int paraIndex = paraIndexFor(start, end);
        final int paraStart = getParagraphStart(paraIndex);
        return mParagraphInfo[paraIndex].measured.getWidth(start - paraStart, end - paraStart);
    }

    /**
     * AOSP PrecomputedText.getBounds(int, int, Rect) (PrecomputedText.java:
     * 680-699). An empty range sets the rect to (0,0,0,0) and returns.
     */
    public void getBounds(int start, int end, android.graphics.Rect bounds) {
        checkRange(start, end);
        if (bounds == null) throw new NullPointerException();
        if (start == end) {
            bounds.set(0, 0, 0, 0);
            return;
        }
        final int paraIndex = paraIndexFor(start, end);
        final int paraStart = getParagraphStart(paraIndex);
        mParagraphInfo[paraIndex].measured.getBounds(start - paraStart, end - paraStart, bounds);
    }

    /**
     * AOSP PrecomputedText.getFontMetricsInt(int, int, Paint.FontMetricsInt)
     * (PrecomputedText.java:711-731). Note the empty-range branch: it fills
     * outMetrics from the PAINT's own metrics rather than zeroing them, which
     * is what makes an empty line keep a sensible height.
     */
    public void getFontMetricsInt(int start, int end,
            android.graphics.Paint.FontMetricsInt outMetrics) {
        checkRange(start, end);
        if (outMetrics == null) throw new NullPointerException();
        if (start == end) {
            mParams.getTextPaint().getFontMetricsInt(outMetrics);
            return;
        }
        final int paraIndex = paraIndexFor(start, end);
        final int paraStart = getParagraphStart(paraIndex);
        mParagraphInfo[paraIndex].measured.getFontMetricsInt(
                start - paraStart, end - paraStart, outMetrics);
    }

    // ---- CharSequence ----
    @Override public int length() { return mText.length(); }
    @Override public char charAt(int index) { return mText.charAt(index); }
    @Override public CharSequence subSequence(int start, int end) {
        return PrecomputedText.create(mText.subSequence(start, end), mParams);
    }
    @Override public String toString() { return mText.toString(); }

    // ---- Spanned ----
    @Override public <T> T[] getSpans(int start, int end, Class<T> type) {
        return mText.getSpans(start, end, type);
    }
    @Override public int getSpanStart(Object tag) { return mText.getSpanStart(tag); }
    @Override public int getSpanEnd(Object tag) { return mText.getSpanEnd(tag); }
    @Override public int getSpanFlags(Object tag) { return mText.getSpanFlags(tag); }
    @Override public int nextSpanTransition(int start, int limit, Class type) {
        return mText.nextSpanTransition(start, limit, type);
    }

    // ---- Spannable ----
    // AOSP: a MetricAffectingSpan (font size / typeface / scale) would
    // invalidate the per-paragraph measurements already computed above, so
    // setSpan/removeSpan refuse it outright instead of silently going stale.
    @Override public void setSpan(Object what, int start, int end, int flags) {
        if (what instanceof MetricAffectingSpan) {
            throw new IllegalArgumentException(
                "MetricAffectingSpan can not be set to PrecomputedText.");
        }
        mText.setSpan(what, start, end, flags);
    }
    @Override public void removeSpan(Object what) {
        if (what instanceof MetricAffectingSpan) {
            throw new IllegalArgumentException(
                "MetricAffectingSpan can not be removed from PrecomputedText.");
        }
        mText.removeSpan(what);
    }

    /** AOSP android.text.PrecomputedText.Params -- the immutable measurement
     *  configuration (paint, text direction, break strategy, hyphenation
     *  frequency, line-break config) a PrecomputedText was/will be computed
     *  with. TextView.getTextMetricsParams() returns one of these describing
     *  the TextView's current settings; PrecomputedTextCompat compares an
     *  existing PrecomputedText's Params against a TextView's via equals()
     *  to decide whether it can be bound as-is or must be recomputed. */
    public static final class Params {
        private final TextPaint mPaint;
        private final TextDirectionHeuristic mTextDir;
        private final int mBreakStrategy;
        private final int mHyphenationFrequency;
        private final LineBreakConfig mLineBreakConfig;

        public Params(TextPaint paint, LineBreakConfig lineBreakConfig,
                TextDirectionHeuristic textDir, int breakStrategy, int hyphenationFrequency) {
            mPaint = paint;
            mTextDir = textDir;
            mBreakStrategy = breakStrategy;
            mHyphenationFrequency = hyphenationFrequency;
            mLineBreakConfig = lineBreakConfig;
        }

        public TextPaint getTextPaint() { return mPaint; }
        public TextDirectionHeuristic getTextDirection() { return mTextDir; }
        public int getBreakStrategy() { return mBreakStrategy; }
        public int getHyphenationFrequency() { return mHyphenationFrequency; }
        public LineBreakConfig getLineBreakConfig() { return mLineBreakConfig; }

        // AOSP equals() delegates to a private checkResultUsable() that folds,
        // for every field it compares, down to "would recomputing with the
        // other Params produce the same measurement." That is exactly a
        // field-by-field comparison of every input create() actually reads:
        // the paint's shape (size/scale/skew/spacing/flags/locale/typeface/
        // elegant-height), text direction, break strategy, hyphenation
        // frequency, and line-break config. (AOSP additionally compares
        // Paint.getFontVariationSettings(), which our TextPaint/Paint has no
        // accessor for -- Mac.apk's Paint does not model variable-font axes.)
        @Override public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Params)) return false;
            Params p = (Params) o;
            if (mBreakStrategy != p.mBreakStrategy) return false;
            if (mHyphenationFrequency != p.mHyphenationFrequency) return false;
            if (!Objects.equals(mTextDir, p.mTextDir)) return false;
            if (mLineBreakConfig.getLineBreakStyle() != p.mLineBreakConfig.getLineBreakStyle()) {
                return false;
            }
            if (mLineBreakConfig.getLineBreakWordStyle()
                    != p.mLineBreakConfig.getLineBreakWordStyle()) {
                return false;
            }
            return mPaint.getTextSize() == p.mPaint.getTextSize()
                && mPaint.getTextScaleX() == p.mPaint.getTextScaleX()
                && mPaint.getTextSkewX() == p.mPaint.getTextSkewX()
                && mPaint.getLetterSpacing() == p.mPaint.getLetterSpacing()
                && mPaint.getWordSpacing() == p.mPaint.getWordSpacing()
                && mPaint.getFlags() == p.mPaint.getFlags()
                && Objects.equals(mPaint.getTextLocales(), p.mPaint.getTextLocales())
                && Objects.equals(mPaint.getTypeface(), p.mPaint.getTypeface())
                && mPaint.isElegantTextHeight() == p.mPaint.isElegantTextHeight();
        }

        @Override public int hashCode() {
            return Objects.hash(mPaint.getTextSize(), mPaint.getTextScaleX(),
                mPaint.getTextSkewX(), mPaint.getLetterSpacing(), mPaint.getWordSpacing(),
                mPaint.getFlags(), mPaint.getTextLocales(), mPaint.getTypeface(),
                mPaint.isElegantTextHeight(), mTextDir, mBreakStrategy, mHyphenationFrequency,
                mLineBreakConfig.getLineBreakStyle(), mLineBreakConfig.getLineBreakWordStyle());
        }

        /** AOSP android.text.PrecomputedText.Params.Builder. Defaults match
         *  frameworks/base core/java/android/text/PrecomputedText.java: text
         *  direction FIRSTSTRONG_LTR, break strategy HIGH_QUALITY, hyphenation
         *  frequency NORMAL, no line-break config. Real AOSP names the break/
         *  hyphenation defaults off android.text.Layout.BREAK_STRATEGY_HIGH_
         *  QUALITY / HYPHENATION_FREQUENCY_NORMAL, which are themselves defined
         *  as aliases of android.graphics.text.LineBreaker's constants of the
         *  same name (frameworks/base Layout.java: "public static final int
         *  BREAK_STRATEGY_HIGH_QUALITY = LineBreaker.BREAK_STRATEGY_HIGH_
         *  QUALITY;"); our Layout.java does not carry these alias constants, so
         *  we reference LineBreaker's directly for the identical int values. */
        public static class Builder {
            private final TextPaint mPaint;
            private TextDirectionHeuristic mTextDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
            private int mBreakStrategy = LineBreaker.BREAK_STRATEGY_HIGH_QUALITY;
            private int mHyphenationFrequency = LineBreaker.HYPHENATION_FREQUENCY_NORMAL;
            private LineBreakConfig mLineBreakConfig = LineBreakConfig.NONE;

            public Builder(TextPaint paint) {
                mPaint = paint;
            }

            public Builder(Params params) {
                mPaint = params.mPaint;
                mTextDir = params.mTextDir;
                mBreakStrategy = params.mBreakStrategy;
                mHyphenationFrequency = params.mHyphenationFrequency;
                mLineBreakConfig = params.mLineBreakConfig;
            }

            public Builder setBreakStrategy(int strategy) {
                mBreakStrategy = strategy;
                return this;
            }

            public Builder setHyphenationFrequency(int frequency) {
                mHyphenationFrequency = frequency;
                return this;
            }

            public Builder setTextDirection(TextDirectionHeuristic textDir) {
                mTextDir = textDir;
                return this;
            }

            public Builder setLineBreakConfig(LineBreakConfig lineBreakConfig) {
                mLineBreakConfig = lineBreakConfig;
                return this;
            }

            public Params build() {
                return new Params(mPaint, mLineBreakConfig, mTextDir, mBreakStrategy,
                    mHyphenationFrequency);
            }
        }
    }
}
