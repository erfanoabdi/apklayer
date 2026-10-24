// android.text.DynamicLayout -- AOSP layout for editable text that may
// change after construction. AOSP:
//   frameworks/base/core/java/android/text/DynamicLayout.java
//   (extends Layout). EditText with mutable content and several Compose
//   text-edit paths build one.
//
// Real Android re-flows incrementally as the buffer mutates; we don't host
// an incremental reflow pipeline, so DynamicLayout reuses the base Layout's
// greedy word-wrap over the current display text. Because the base recomputes
// line breaks lazily (lineStarts == null forces a re-break), creating a fresh
// DynamicLayout after an edit yields the correct geometry for the new text --
// which is how every caller that mutates then re-measures observes it.
package android.text;

public class DynamicLayout extends Layout {

    @Deprecated
    public DynamicLayout(CharSequence base, TextPaint paint, int width,
                         Alignment align, float spacingmult, float spacingadd,
                         boolean includepad) {
        this(base, base, paint, width, align, spacingmult, spacingadd, includepad);
    }

    @Deprecated
    public DynamicLayout(CharSequence base, CharSequence display, TextPaint paint,
                         int width, Alignment align, float spacingmult,
                         float spacingadd, boolean includepad) {
        super(display, paint, width, align, spacingmult, spacingadd);
    }

    @Deprecated
    public DynamicLayout(CharSequence base, CharSequence display, TextPaint paint,
                         int width, Alignment align, float spacingmult,
                         float spacingadd, boolean includepad,
                         TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        super(display, paint, width, align, spacingmult, spacingadd);
        if (ellipsize != null) configure(Integer.MAX_VALUE, ellipsize, ellipsizedWidth);
    }

    public static Builder obtain(CharSequence base, TextPaint paint, int width) {
        return new Builder(base, paint, width);
    }

    public static final class Builder {
        private final CharSequence base;
        private CharSequence display;
        private final TextPaint paint;
        private final int width;
        private Alignment alignment = Alignment.ALIGN_NORMAL;
        private float spacingAdd = 0f, spacingMult = 1.0f;
        private boolean includePad = true;
        private TextUtils.TruncateAt ellipsize;
        private int ellipsizedWidth;
        private TextDirectionHeuristic textDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
        private int breakStrategy = 0;
        private int hyphenationFrequency = 0;
        private int justificationMode = 0;
        private boolean useLineSpacingFromFallbacks = false;
        private boolean useBoundsForWidth = false;
        private boolean shiftDrawingOffsetForStartOverhang = false;
        private android.graphics.Paint.FontMetrics minimumFontMetrics;
        private android.graphics.text.LineBreakConfig lineBreakConfig =
                android.graphics.text.LineBreakConfig.NONE;

        /** AOSP DynamicLayout.Builder.obtain(CharSequence, TextPaint, int).
         *  AOSP declares this factory on the BUILDER, not only on
         *  DynamicLayout; code written against the SDK calls
         *  DynamicLayout.Builder.obtain(...), which does not resolve against
         *  DynamicLayout.obtain(...). */
        public static Builder obtain(CharSequence base, TextPaint paint, int width) {
            return new Builder(base, paint, width);
        }

        Builder(CharSequence base, TextPaint paint, int width) {
            this.base = base;
            this.display = base;
            this.paint = paint;
            this.width = width;
            this.ellipsizedWidth = width;
        }

        public Builder setDisplayText(CharSequence display) { this.display = display; return this; }
        public Builder setAlignment(Alignment alignment) { this.alignment = alignment; return this; }
        public Builder setTextDirection(TextDirectionHeuristic textDir) { this.textDir = textDir; return this; }
        public Builder setLineSpacing(float spacingAdd, float spacingMult) {
            this.spacingAdd = spacingAdd; this.spacingMult = spacingMult; return this;
        }
        public Builder setIncludePad(boolean includePad) { this.includePad = includePad; return this; }
        public Builder setUseLineSpacingFromFallbacks(boolean v) { this.useLineSpacingFromFallbacks = v; return this; }
        public Builder setEllipsizedWidth(int ellipsizedWidth) { this.ellipsizedWidth = ellipsizedWidth; return this; }
        public Builder setEllipsize(TextUtils.TruncateAt ellipsize) { this.ellipsize = ellipsize; return this; }
        public Builder setBreakStrategy(int breakStrategy) { this.breakStrategy = breakStrategy; return this; }
        public Builder setHyphenationFrequency(int hyphenationFrequency) { this.hyphenationFrequency = hyphenationFrequency; return this; }
        public Builder setJustificationMode(int justificationMode) { this.justificationMode = justificationMode; return this; }

        /** AOSP DynamicLayout.Builder.setUseBoundsForWidth(boolean). Recorded;
         *  see Layout.getUseBoundsForWidth(). */
        public Builder setUseBoundsForWidth(boolean useBoundsForWidth) {
            this.useBoundsForWidth = useBoundsForWidth;
            return this;
        }

        /** AOSP DynamicLayout.Builder.setShiftDrawingOffsetForStartOverhang(boolean). */
        public Builder setShiftDrawingOffsetForStartOverhang(boolean shift) {
            this.shiftDrawingOffsetForStartOverhang = shift;
            return this;
        }

        /** AOSP DynamicLayout.Builder.setMinimumFontMetrics(Paint.FontMetrics). */
        public Builder setMinimumFontMetrics(android.graphics.Paint.FontMetrics metrics) {
            this.minimumFontMetrics = metrics;
            return this;
        }

        /** AOSP DynamicLayout.Builder.setLineBreakConfig(LineBreakConfig). */
        public Builder setLineBreakConfig(android.graphics.text.LineBreakConfig lineBreakConfig) {
            if (lineBreakConfig != null) this.lineBreakConfig = lineBreakConfig;
            return this;
        }

        public DynamicLayout build() {
            DynamicLayout dl = new DynamicLayout(base, display, paint, width,
                alignment, spacingMult, spacingAdd, includePad);
            // setTextDirection was collected and dropped, exactly as
            // StaticLayout.Builder's was before v1.0.4xx -- so an explicitly
            // RTL-directed DynamicLayout answered LTR from
            // getParagraphDirection().
            dl.macapkSetTextDirection(textDir);
            if (ellipsize != null) dl.configure(Integer.MAX_VALUE, ellipsize, ellipsizedWidth);
            // Record the Builder parameters Layout's AOSP getters report.
            // ⚠ useLineSpacingFromFallbacks is deliberately NOT threaded: AOSP
            // DynamicLayout has no isFallbackLineSpacingEnabled() override, so
            // a real DynamicLayout answers the base class's `false` no matter
            // what its Builder was given. See Layout.isFallbackLineSpacingEnabled().
            dl.macapkSetBuilderParams(breakStrategy, hyphenationFrequency, justificationMode,
                    null, null, useBoundsForWidth, shiftDrawingOffsetForStartOverhang,
                    minimumFontMetrics, lineBreakConfig);
            return dl;
        }
    }
}
