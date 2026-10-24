// android.text.BidiFormatter -- wraps text in Unicode bidi control chars so a
// string of unknown direction renders correctly inside a paragraph. androidx.
// core.text.BidiFormatter delegates to / mirrors this; apps and some framework
// code reference android.text.BidiFormatter directly, so a missing class threw
// NoClassDefFoundError during inflate/format. v1.0.426 Tier-2.
// Mac.apk is LTR-only (macOS has no RTL system pump and our targets are LTR), so
// unicodeWrap is an identity pass -- the OBSERVABLE result for LTR English text
// is identical to AOSP (no isolate chars are emitted when the text direction
// matches the context). Source: frameworks/base/core/java/android/text/BidiFormatter.java
package android.text;

public final class BidiFormatter {
    private static final BidiFormatter LTR_INSTANCE = new BidiFormatter(false);
    private static final BidiFormatter RTL_INSTANCE = new BidiFormatter(true);

    private final boolean rtlContext;
    private BidiFormatter(boolean rtl) { this.rtlContext = rtl; }

    public static BidiFormatter getInstance() { return LTR_INSTANCE; }
    public static BidiFormatter getInstance(boolean rtlContext) {
        return rtlContext ? RTL_INSTANCE : LTR_INSTANCE;
    }
    public static BidiFormatter getInstance(java.util.Locale locale) {
        return getInstance(false);   // LTR-only host
    }

    public boolean isRtlContext() { return rtlContext; }
    public boolean getStereoReset() { return true; }
    // AOSP isRtl (frameworks/base core/java/android/text/BidiFormatter.java:
    // 355-368): "return mDefaultTextDirectionHeuristic.isRtl(str, 0,
    // str.length());" where mDefaultTextDirectionHeuristic defaults to
    // FIRSTSTRONG_LTR for EVERY instance regardless of isRtlContext (that
    // constructor param only affects unicodeWrap's wrapping decision, not
    // this content query). Unlike unicodeWrap -- an identity pass because
    // Mac.apk's renderer has nothing to do with inserted bidi isolate
    // characters -- isRtl() is pure Unicode string analysis with no
    // dependency on system RTL rendering support, and TextDirectionHeuristics
    // already ports the real first-strong-character algorithm faithfully
    // (see that file). Returning a hardcoded false here made every RTL-
    // content-driven decision an app makes through this class (e.g.
    // `if (bidiFormatter.isRtl(name)) setGravity(RIGHT)`) silently wrong.
    public boolean isRtl(String str) { return isRtl((CharSequence) str); }
    public boolean isRtl(CharSequence str) {
        if (str == null) return false;
        return android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(str, 0, str.length());
    }

    public String unicodeWrap(String str) { return str; }
    public CharSequence unicodeWrap(CharSequence str) { return str; }
    public String unicodeWrap(String str, TextDirectionHeuristic heuristic) { return str; }
    public CharSequence unicodeWrap(CharSequence str, TextDirectionHeuristic heuristic) { return str; }
    public String unicodeWrap(String str, TextDirectionHeuristic heuristic, boolean isolate) { return str; }
    public CharSequence unicodeWrap(CharSequence str, TextDirectionHeuristic heuristic, boolean isolate) { return str; }

    public static final class Builder {
        private boolean rtlContext;
        public Builder() { this.rtlContext = false; }
        public Builder(boolean rtlContext) { this.rtlContext = rtlContext; }
        public Builder(java.util.Locale locale) { this.rtlContext = false; }
        public Builder setTextDirectionHeuristic(TextDirectionHeuristic heuristic) { return this; }
        public Builder stereoReset(boolean stereoReset) { return this; }
        public BidiFormatter build() { return getInstance(rtlContext); }
    }
}
