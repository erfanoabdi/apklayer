// android.text.Spannable -- mutable Spanned, the interface variant.
// Per AOSP source, Spannable extends Spanned with setSpan/removeSpan.
// SpannableStringBuilder implements this; SpannableString is a
// less common implementation we may need later.
package android.text;

public interface Spannable extends Spanned {
    void setSpan(Object what, int start, int end, int flags);
    void removeSpan(Object what);

    // AOSP declares Factory as a concrete class (frameworks/base
    // core/java/android/text/Spannable.java), not an interface: it carries
    // a private static singleton + getInstance() + a default newSpannable()
    // that subclasses may override. App bytecode compiled against the real
    // android.jar that does `new Spannable.Factory() { ... }` extends this
    // CLASS (INVOKESPECIAL Spannable$Factory.<init>()V); declaring it as an
    // interface here is a class-hierarchy shape mismatch
    // (IncompatibleClassChangeError/VerifyError at classload).
    public static class Factory {
        private static Spannable.Factory sInstance = new Spannable.Factory();

        /** Returns the standard Spannable Factory. */
        public static Spannable.Factory getInstance() {
            return sInstance;
        }

        /** Returns a new SpannableString from the specified CharSequence.
         *  Subclasses may override to provide a different kind of Spannable. */
        public Spannable newSpannable(CharSequence source) {
            return new SpannableString(source);
        }
    }
}
