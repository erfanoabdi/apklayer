// v1.0.124: AOSP `StaticLayout` -- multi-line text layout. Compose UI's
// AndroidParagraph builds one via StaticLayout.Builder during measure.
//
// Per cs.android.com/.../text/StaticLayout.java this extends Layout.
// We implement the minimal Builder + ctor surface Compose touches.
package android.text;

public class StaticLayout extends Layout {
    public static Builder obtain(CharSequence source, int start, int end,
                                  android.text.TextPaint paint, int width) {
        return new Builder(source, start, end, paint, width);
    }

    public static class Builder {
        public static Builder obtain(CharSequence source, int start, int end,
                                      android.text.TextPaint paint, int width) {
            return new Builder(source, start, end, paint, width);
        }

        // NOT final any more: AOSP StaticLayout.Builder.setText(CharSequence)
        // replaces the source and resets the window to [0, text.length()).
        private CharSequence source;
        private int start, end;
        private final int width;
        private final android.text.TextPaint paint;
        private Alignment alignment = Alignment.ALIGN_NORMAL;
        private float spacingMult = 1.0f, spacingAdd = 0f;
        private boolean includePad = true;
        private int maxLines = Integer.MAX_VALUE;
        private android.text.TextUtils.TruncateAt ellipsize;
        private int ellipsizedWidth;
        private android.text.TextDirectionHeuristic textDir
            = android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR;
        private int breakStrategy = 0;
        private int hyphenationFrequency = 0;
        private int justificationMode = 0;
        private boolean useLineSpacingFromFallbacks = false;
        // AOSP Builder defaults: no indents, LineBreakConfig.NONE, bounds not
        // used for width, no start-overhang shift, no minimum font metrics.
        private int[] leftIndents;
        private int[] rightIndents;
        private boolean useBoundsForWidth = false;
        private boolean shiftDrawingOffsetForStartOverhang = false;
        private android.graphics.Paint.FontMetrics minimumFontMetrics;
        private android.graphics.text.LineBreakConfig lineBreakConfig =
                android.graphics.text.LineBreakConfig.NONE;

        Builder(CharSequence src, int start, int end, android.text.TextPaint paint, int width) {
            this.source = src;
            this.start = start;
            this.end = end;
            this.paint = paint;
            this.width = width;
            this.ellipsizedWidth = width;
        }
        public Builder setAlignment(Alignment a) { this.alignment = a; return this; }
        public Builder setLineSpacing(float add, float mult) {
            this.spacingAdd = add; this.spacingMult = mult; return this;
        }
        public Builder setIncludePad(boolean v) { this.includePad = v; return this; }
        public Builder setMaxLines(int n) { this.maxLines = n; return this; }
        public Builder setEllipsize(android.text.TextUtils.TruncateAt e) { this.ellipsize = e; return this; }
        public Builder setEllipsizedWidth(int w) { this.ellipsizedWidth = w; return this; }
        public Builder setTextDirection(android.text.TextDirectionHeuristic h) { this.textDir = h; return this; }
        public Builder setBreakStrategy(int b) { this.breakStrategy = b; return this; }
        public Builder setHyphenationFrequency(int h) { this.hyphenationFrequency = h; return this; }
        public Builder setJustificationMode(int m) { this.justificationMode = m; return this; }
        public Builder setUseLineSpacingFromFallbacks(boolean v) { this.useLineSpacingFromFallbacks = v; return this; }
        /** AOSP StaticLayout.Builder.setIndents(int[], int[]). The values are
         *  RECORDED (Layout.getLeftIndents()/getRightIndents() answer them),
         *  but our greedy breaker lays every line out against the full width,
         *  so they do not yet narrow a line. */
        public Builder setIndents(int[] left, int[] right) {
            this.leftIndents = left;
            this.rightIndents = right;
            return this;
        }

        /** AOSP StaticLayout.Builder.setText(CharSequence) -- replaces the
         *  source and resets the [start, end) window to the whole text. This
         *  is how a Builder is REUSED for a second layout, which is the point
         *  of obtain() being a factory. It was missing entirely. */
        public Builder setText(CharSequence source) {
            this.source = source;
            this.start = 0;
            this.end = (source == null) ? 0 : source.length();
            return this;
        }

        /** AOSP StaticLayout.Builder.setUseBoundsForWidth(boolean). Recorded;
         *  see Layout.getUseBoundsForWidth(). */
        public Builder setUseBoundsForWidth(boolean useBoundsForWidth) {
            this.useBoundsForWidth = useBoundsForWidth;
            return this;
        }

        /** AOSP StaticLayout.Builder.setShiftDrawingOffsetForStartOverhang(boolean). */
        public Builder setShiftDrawingOffsetForStartOverhang(boolean shift) {
            this.shiftDrawingOffsetForStartOverhang = shift;
            return this;
        }

        /** AOSP StaticLayout.Builder.setMinimumFontMetrics(Paint.FontMetrics) --
         *  the per-locale minimum line height introduced in API 35. Recorded
         *  and reported by Layout.getMinimumFontMetrics(); our line heights
         *  come from the paint's own metrics, so it does not yet raise a
         *  short line. */
        public Builder setMinimumFontMetrics(android.graphics.Paint.FontMetrics metrics) {
            this.minimumFontMetrics = metrics;
            return this;
        }

        /** AOSP StaticLayout.Builder.setLineBreakConfig(LineBreakConfig).
         *  Recorded; our breaker is always the simple greedy one. */
        public Builder setLineBreakConfig(android.graphics.text.LineBreakConfig lineBreakConfig) {
            if (lineBreakConfig != null) this.lineBreakConfig = lineBreakConfig;
            return this;
        }
        public StaticLayout build() {
            // Honour the [start, end) window. obtain(text, 0, text.length())
            // is the overwhelmingly common call and is left byte-identical
            // (no subSequence, so a Spanned keeps its exact span objects and
            // its identity for the LineHeightSpan path); a PARTIAL window used
            // to be dropped on the floor and the whole string laid out.
            CharSequence src = source;
            if (src != null && (start != 0 || end != src.length())) {
                src = src.subSequence(start, end);
            }
            StaticLayout sl = new StaticLayout(src, paint, width, alignment,
                spacingMult, spacingAdd, includePad);
            // setTextDirection used to be stored and dropped, which left
            // getParagraphDirection() answering LTR for an explicitly
            // RTL-directed layout.
            sl.macapkSetTextDirection(textDir);
            // useLineSpacingFromFallbacks used to be stored and dropped the
            // same way (this Builder field existed, but build() never read
            // it) -- so isFallbackLineSpacingEnabled() always answered false
            // even for a caller (e.g. Compose's AndroidParagraph, which
            // always calls setUseLineSpacingFromFallbacks(true) --
            // androidx-main compose/ui/ui-text/.../android/TextLayout.
            // android.kt:132 `fallbackLineSpacing: Boolean = true`, threaded
            // through StaticLayoutFactory.android.kt:227-228's
            // `builder.setUseLineSpacingFromFallbacks(useFallbackLineSpacing)`)
            // that correctly opted in. See Layout.java's useFallbackLineSpacing
            // field comment for why the flag lives on the shared base class.
            sl.macapkSetUseFallbackLineSpacing(useLineSpacingFromFallbacks);
            // v1.0.46x: maxLines + ellipsize are no longer silently dropped --
            // configure() drives the base Layout's truncation so a TextView /
            // Compose Text with maxLines / TextOverflow.Ellipsis renders a
            // truncated, ellipsized last line instead of the full overflowing
            // string.
            sl.configure(maxLines, ellipsize, ellipsizedWidth);
            // Record the remaining Builder parameters so Layout's AOSP getters
            // (getBreakStrategy / getHyphenationFrequency / getJustificationMode
            // / getLeftIndents / getRightIndents / getUseBoundsForWidth /
            // getShiftDrawingOffsetForStartOverhang / getMinimumFontMetrics /
            // getLineBreakConfig) answer what the caller actually asked for.
            // breakStrategy / hyphenationFrequency / justificationMode were
            // collected by this Builder and dropped for a long time.
            sl.macapkSetBuilderParams(breakStrategy, hyphenationFrequency, justificationMode,
                    leftIndents, rightIndents, useBoundsForWidth,
                    shiftDrawingOffsetForStartOverhang, minimumFontMetrics, lineBreakConfig);
            return sl;
        }
    }

    // ⚠ `includepad` is NOT decorative and must not be dropped. AOSP's
    // deprecated 6-arg constructor delegates with includepad = TRUE
    // (StaticLayout.java:187), which is also Builder.obtain's default, so
    // every one of these has to hand the base Layout an explicit value or a
    // layout built the old way comes out a few pixels shorter than the same
    // layout built through the Builder (TXT.deprecated.*.sameHeight).

    public StaticLayout() {
        super("", null, 0, Alignment.ALIGN_NORMAL, 1f, 0f);
        macapkSetIncludePad(true);
    }
    public StaticLayout(CharSequence source, android.text.TextPaint paint,
                         int width, Alignment align, float spacingMult, float spacingAdd) {
        this(source, paint, width, align, spacingMult, spacingAdd, true);
    }
    public StaticLayout(CharSequence source, android.text.TextPaint paint,
                         int width, Alignment align, float spacingMult, float spacingAdd,
                         boolean includepad) {
        super(source, paint, width, align, spacingMult, spacingAdd);
        macapkSetIncludePad(includepad);
    }
    public StaticLayout(CharSequence source, int bufstart, int bufend,
                         android.text.TextPaint paint, int outerwidth,
                         Alignment align, float spacingMult, float spacingAdd,
                         boolean includepad) {
        super(source != null ? source.subSequence(bufstart, bufend) : null,
            paint, outerwidth, align, spacingMult, spacingAdd);
        macapkSetIncludePad(includepad);
    }
    public StaticLayout(CharSequence source, int bufstart, int bufend,
                         android.text.TextPaint paint, int outerwidth,
                         Alignment align, float spacingMult, float spacingAdd,
                         boolean includepad,
                         android.text.TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        super(source != null ? source.subSequence(bufstart, bufend) : null,
            paint, outerwidth, align, spacingMult, spacingAdd);
        macapkSetIncludePad(includepad);
        if (ellipsize != null) configure(Integer.MAX_VALUE, ellipsize, ellipsizedWidth);
    }

    // isFallbackLineSpacingEnabled() used to be hardcoded false here,
    // overriding whatever the Builder was actually given (see the dead-code
    // comment in Builder.build() above) -- deleted rather than fixed in
    // place so there is exactly one implementation (Layout.java's, backed by
    // the field macapkSetUseFallbackLineSpacing threads into) instead of two
    // that could disagree. AOSP gives StaticLayout its OWN private field and
    // override (android13-release StaticLayout.java:1406, :1435); we share
    // the field on the base Layout class instead, matching how includePad
    // already works in this tree.
}

