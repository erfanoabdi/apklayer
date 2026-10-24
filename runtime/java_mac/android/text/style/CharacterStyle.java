// v1.0.124: AOSP base class for character-level span styles.
// CharacterStyle is the abstract supertype for spans that modify
// text appearance (color, typeface, underline, etc.). Compose UI's
// AndroidParagraphIntrinsics walks the TextPaint's spans, checks
// `instanceof CharacterStyle`, and applies each span's
// updateDrawState(TextPaint) call. Without this class on the
// classpath, RV-card text inflation throws NoClassDefFoundError
// mid-RV-dispatch and aborts the layout pass.
//
// Per cs.android.com/.../text/style/CharacterStyle.java this is an
// abstract class with one method: updateDrawState. Default impl
// in subclasses (UnderlineSpan, ForegroundColorSpan, etc.) writes
// the relevant Paint field. We keep updateDrawState abstract so
// any subclass we don't ship can be referenced without crashing.
package android.text.style;

public abstract class CharacterStyle {
    public abstract void updateDrawState(android.text.TextPaint tp);

    /** AOSP wrap helper -- used when a CharacterStyle needs to be
     *  applied to a sub-range and the original should be preserved.
     *  Returns the same span. */
    public static CharacterStyle wrap(CharacterStyle cs) { return cs; }

    /** AOSP `getUnderlying` -- when this is a wrapped span, returns the
     *  underlying. For non-wrapped spans, returns `this`. */
    public CharacterStyle getUnderlying() { return this; }
}
