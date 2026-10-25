// android.text.Editable -- the mutable form of a TextView's content.
// Apps frequently store the result of EditText.getText() as Editable;
// this is the verifier-visible interface. The actual mutation methods
// are no-ops in our runtime since we do not render an editor caret.
package android.text;

// AOSP: `Editable extends CharSequence, GetChars, Spannable, Appendable`. The
// Spannable (-> Spanned) ancestry is load-bearing: AndroidX text + IME code does
// `Spanned s = editable;` and `if (text instanceof Spannable)` on an Editable
// constantly (e.g. Selection.getSelectionStart(editable)); without it those
// fail at the type level. SpannableStringBuilder (our only Editable) already
// implements Spannable + getChars, so widening the interface is safe. v1.0.425.
public interface Editable extends CharSequence, GetChars, Spannable, Appendable {
    Editable replace(int st, int en, CharSequence source, int start, int end);
    Editable replace(int st, int en, CharSequence text);
    Editable insert(int where, CharSequence text, int start, int end);
    Editable insert(int where, CharSequence text);
    Editable delete(int st, int en);
    Editable append(CharSequence text);
    Editable append(CharSequence text, int start, int end);
    Editable append(char text);
    void clear();
    void clearSpans();
    // setFilters/getFilters take the TOP-LEVEL android.text.InputFilter[], per
    // AOSP. Do NOT reintroduce a nested Editable.InputFilter: an inherited member
    // type outranks a same-package top-level type in simple-name resolution
    // (JLS 6.5.5.1), so a nested copy silently rebinds every unqualified
    // `InputFilter` in Editable AND in implementors like SpannableStringBuilder
    // to Editable.InputFilter -- making setFilters(android.text.InputFilter[])
    // a NoSuchMethodError for callers that pass the real type (e.g. AGDK's
    // GameActivity.onCreateSurfaceView, which aborted Minecraft's onCreate).
    void setFilters(InputFilter[] filters);
    InputFilter[] getFilters();

    // AOSP Editable.Factory is a CONCRETE instantiable class with a singleton
    // accessor and a default newEditable() body (not an interface). Apps +
    // AndroidX (e.g. TextView.setEditableFactory / EditText) call
    // Editable.Factory.getInstance() and subclass it; an interface would break
    // both. Mirrors frameworks/base core/java/android/text/Editable.java.
    class Factory {
        private static Editable.Factory sInstance = new Editable.Factory();
        public static Editable.Factory getInstance() { return sInstance; }
        public Editable newEditable(CharSequence source) {
            return new SpannableStringBuilder(source);
        }
    }
}
