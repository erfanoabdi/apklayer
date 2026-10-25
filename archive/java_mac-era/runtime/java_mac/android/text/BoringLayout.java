// v1.0.124: AOSP BoringLayout -- a Layout impl for single-line text
// that fits entirely on one line. Compose UI's AndroidParagraphIntrinsics
// uses BoringLayout.Metrics.isBoring() to decide whether to use the
// fast-path BoringLayout (LTR, no italic, no shaping required) or
// fall back to the heavier StaticLayout. Without this class on the
// classpath, Compose text measurement throws NoClassDefFoundError
// mid-RV-dispatch.
//
// Per cs.android.com/.../text/BoringLayout.java BoringLayout extends
// Layout. We implement the minimal surface Compose touches.
package android.text;

public class BoringLayout extends Layout implements android.text.TextUtils.EllipsizeCallback {
    public static class Metrics extends android.graphics.Paint.FontMetricsInt {
        public int width;

        // AOSP BoringLayout.Metrics.mDrawingBoundingBox (android16-release
        // BoringLayout.java) -- the ink box isBoring() measured, empty until
        // one is measured. Ours is never populated because isBoring() below
        // always declines the fast path, so getDrawingBoundingBox() answers the
        // empty rectangle -- which is what AOSP also answers for a Metrics the
        // caller built by hand.
        private final android.graphics.RectF mDrawingBoundingBox = new android.graphics.RectF();

        /** AOSP BoringLayout.Metrics.getDrawingBoundingBox(): returns a COPY,
         *  so a caller cannot mutate the Metrics through it. */
        public android.graphics.RectF getDrawingBoundingBox() {
            return new android.graphics.RectF(mDrawingBoundingBox);
        }
    }

    // AOSP BoringLayout.mEllipsizedStart / mEllipsizedCount, written by the
    // EllipsizeCallback below and read by getEllipsisStart/getEllipsisCount.
    private int mEllipsizedStart;
    private int mEllipsizedCount;

    public BoringLayout(CharSequence source, android.text.TextPaint paint,
                         int outerwidth, android.text.Layout.Alignment align,
                         float spacingmult, float spacingadd,
                         Metrics metrics, boolean includepad) {
        super(source, paint, outerwidth, align, spacingmult, spacingadd);
    }

    public BoringLayout(CharSequence source, android.text.TextPaint paint,
                         int outerwidth, android.text.Layout.Alignment align,
                         float spacingmult, float spacingadd,
                         Metrics metrics, boolean includepad,
                         android.text.TextUtils.TruncateAt ellipsize,
                         int ellipsizedWidth) {
        super(source, paint, outerwidth, align, spacingmult, spacingadd);
    }

    /** AOSP `BoringLayout.isBoring` -- returns a Metrics if the text
     *  qualifies for fast-path BoringLayout, null otherwise. Used by
     *  Compose's AndroidParagraphIntrinsics to choose layout class.
     *  We always return null so callers fall back to StaticLayout. */
    public static Metrics isBoring(CharSequence text, android.text.TextPaint paint) {
        return null;
    }
    public static Metrics isBoring(CharSequence text, android.text.TextPaint paint, Metrics metrics) {
        return null;
    }
    public static Metrics isBoring(CharSequence text, android.text.TextPaint paint,
                                    android.text.TextDirectionHeuristic textDir, Metrics metrics) {
        return null;
    }

    /** AOSP factory method. */
    public static BoringLayout make(CharSequence source, android.text.TextPaint paint,
                                     int outerwidth, android.text.Layout.Alignment align,
                                     float spacingmult, float spacingadd,
                                     Metrics metrics, boolean includepad) {
        return new BoringLayout(source, paint, outerwidth, align,
            spacingmult, spacingadd, metrics, includepad);
    }
    /**
     * AOSP BoringLayout.make(CharSequence, TextPaint, int, Alignment, float,
     * float, Metrics, boolean, TruncateAt, int) -- the ellipsizing factory. It
     * was missing, and it is the overload YouTube calls (gapuse:
     * BoringLayout.make/10).
     */
    public static BoringLayout make(CharSequence source, android.text.TextPaint paint,
                                     int outerwidth, android.text.Layout.Alignment align,
                                     float spacingmult, float spacingadd,
                                     Metrics metrics, boolean includepad,
                                     android.text.TextUtils.TruncateAt ellipsize,
                                     int ellipsizedWidth) {
        return new BoringLayout(source, paint, outerwidth, align, spacingmult, spacingadd,
                metrics, includepad, ellipsize, ellipsizedWidth);
    }

    /**
     * AOSP BoringLayout.make(CharSequence, TextPaint, int, Alignment, Metrics,
     * boolean, TruncateAt, int, boolean) -- the API-33 form with no
     * spacingmult/spacingadd (they default to the AOSP line-spacing defaults)
     * and an explicit useFallbackLineSpacing.
     */
    public static BoringLayout make(CharSequence source, android.text.TextPaint paint,
                                     int outerwidth, android.text.Layout.Alignment align,
                                     Metrics metrics, boolean includepad,
                                     android.text.TextUtils.TruncateAt ellipsize,
                                     int ellipsizedWidth, boolean useFallbackLineSpacing) {
        return new BoringLayout(source, paint, outerwidth, align,
                Layout.DEFAULT_LINESPACING_MULTIPLIER, Layout.DEFAULT_LINESPACING_ADDITION,
                metrics, includepad, ellipsize, ellipsizedWidth, useFallbackLineSpacing);
    }

    /**
     * AOSP BoringLayout.replaceOrMake(...) rebinds THIS layout to the new text
     * and parameters (Layout.replaceWith + init) and returns `this`, so a
     * caller that reuses one BoringLayout across many rows -- which is the
     * entire point of the method, and how TextView uses it -- gets its own
     * object back carrying the NEW text.
     *
     * ⚠ BEHAVIOUR CHANGE, deliberate: this used to `return this` WITHOUT
     * rebinding anything, so the returned layout still measured and drew the
     * PREVIOUS text. Every argument was discarded. It now performs the real
     * in-place replace through Layout.macapkReplaceWith(), which is why
     * Layout's text/paint/alignment/spacing fields stopped being final.
     */
    public BoringLayout replaceOrMake(CharSequence source, android.text.TextPaint paint,
                                       int outerwidth, android.text.Layout.Alignment align,
                                       float spacingmult, float spacingadd,
                                       Metrics metrics, boolean includepad) {
        macapkReplaceWith(source, paint, outerwidth, align, spacingmult, spacingadd);
        macapkSetIncludePad(includepad);
        mEllipsizedStart = 0;
        mEllipsizedCount = 0;
        configure(Integer.MAX_VALUE, null, outerwidth);
        return this;
    }

    /** AOSP BoringLayout.replaceOrMake(..., TruncateAt, int) -- same, with
     *  ellipsizing. */
    public BoringLayout replaceOrMake(CharSequence source, android.text.TextPaint paint,
                                       int outerwidth, android.text.Layout.Alignment align,
                                       float spacingmult, float spacingadd,
                                       Metrics metrics, boolean includepad,
                                       android.text.TextUtils.TruncateAt ellipsize,
                                       int ellipsizedWidth) {
        macapkReplaceWith(source, paint, outerwidth, align, spacingmult, spacingadd);
        macapkSetIncludePad(includepad);
        mEllipsizedStart = 0;
        mEllipsizedCount = 0;
        configure(Integer.MAX_VALUE, ellipsize, ellipsizedWidth);
        return this;
    }

    /** AOSP BoringLayout.replaceOrMake(CharSequence, TextPaint, int, Alignment,
     *  Metrics, boolean, TruncateAt, int, boolean) -- the API-33 form. */
    public BoringLayout replaceOrMake(CharSequence source, android.text.TextPaint paint,
                                       int outerwidth, android.text.Layout.Alignment align,
                                       Metrics metrics, boolean includepad,
                                       android.text.TextUtils.TruncateAt ellipsize,
                                       int ellipsizedWidth, boolean useFallbackLineSpacing) {
        macapkReplaceWith(source, paint, outerwidth, align,
                Layout.DEFAULT_LINESPACING_MULTIPLIER, Layout.DEFAULT_LINESPACING_ADDITION);
        macapkSetIncludePad(includepad);
        macapkSetUseFallbackLineSpacing(useFallbackLineSpacing);
        mEllipsizedStart = 0;
        mEllipsizedCount = 0;
        configure(Integer.MAX_VALUE, ellipsize, ellipsizedWidth);
        return this;
    }

    /**
     * AOSP BoringLayout.ellipsized(int, int) -- the TextUtils.EllipsizeCallback
     * BoringLayout implements so that TextUtils.ellipsize() can report back
     * WHICH range it replaced with the ellipsis:
     *     mEllipsizedStart = start;
     *     mEllipsizedCount = end - start;
     * Implementing the interface is itself part of the class shape: a caller
     * that passes a BoringLayout where an EllipsizeCallback is expected fails a
     * TYPE check without it, not a NoSuchMethodError.
     */
    @Override
    public void ellipsized(int start, int end) {
        mEllipsizedStart = start;
        mEllipsizedCount = end - start;
    }

    /** AOSP BoringLayout.getEllipsisStart(int) returns mEllipsizedStart for
     *  every line. Defers to the base Layout's own ellipsis bookkeeping until
     *  an ellipsized() callback has actually fired, so the existing
     *  configure()-driven truncation path is unchanged. */
    @Override
    public int getEllipsisStart(int line) {
        return mEllipsizedCount > 0 ? mEllipsizedStart : super.getEllipsisStart(line);
    }

    /** AOSP BoringLayout.getEllipsisCount(int) returns mEllipsizedCount. */
    @Override
    public int getEllipsisCount(int line) {
        return mEllipsizedCount > 0 ? mEllipsizedCount : super.getEllipsisCount(line);
    }

    // isFallbackLineSpacingEnabled() used to be hardcoded false here,
    // overriding whatever the 11-arg constructor below was actually given --
    // the identical dead-code shape StaticLayout.java had (see that file's
    // Builder.build() comment). Deleted rather than fixed in place so there
    // is exactly one implementation (Layout.java's, backed by the field
    // macapkSetUseFallbackLineSpacing threads into) instead of two that
    // could disagree. AOSP gives BoringLayout its OWN private field and
    // override (android13-release BoringLayout.java:589-591, :614); we
    // share the field on the base Layout class instead, matching how
    // includePad already works in this tree.

    public static Metrics isBoring(CharSequence text, android.text.TextPaint paint,
                                    android.text.TextDirectionHeuristic textDir,
                                    boolean useFallbackLineSpacing, Metrics metrics) {
        return null;
    }
    public BoringLayout(CharSequence source, android.text.TextPaint paint,
                         int outerwidth, android.text.Layout.Alignment align,
                         float spacingmult, float spacingadd,
                         Metrics metrics, boolean includepad,
                         android.text.TextUtils.TruncateAt ellipsize,
                         int ellipsizedWidth, boolean useFallbackLineSpacing) {
        super(source, paint, outerwidth, align, spacingmult, spacingadd);
        // The one BoringLayout ctor overload that AOSP itself threads this
        // through for (android13-release BoringLayout.java:186-207,
        // `mUseFallbackLineSpacing = useFallbackLineSpacing;`) -- every
        // other ctor here has no such parameter and so, like AOSP's own
        // (BoringLayout.java:149, :258 `mUseFallbackLineSpacing = false`),
        // leaves the base Layout field at its default false.
        macapkSetUseFallbackLineSpacing(useFallbackLineSpacing);
    }
}


