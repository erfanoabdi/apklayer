// android.icu.text.Normalizer2 -- Unicode normalization (NFC/NFD/NFKC/NFKD).
// ICU4J:
//   developer.android.com/reference/android/icu/text/Normalizer2
//   public abstract class Normalizer2
//
// The JDK's java.text.Normalizer implements the same four normalization forms
// over the same Unicode data, so the singletons returned by getNFCInstance()/
// getNFDInstance()/getNFKCInstance()/getNFKDInstance() delegate to it. The
// composition-mode getInstance(InputStream,String,Mode) form (custom mapping
// tables) is not modeled because apps overwhelmingly use the four standard
// forms; that overload is declared but resolves to the matching standard form.
package android.icu.text;

import java.text.Normalizer;

public abstract class Normalizer2 {

    public enum Mode { COMPOSE, DECOMPOSE, FCD, COMPOSE_CONTIGUOUS }

    protected Normalizer2() {}

    public static Normalizer2 getNFCInstance() { return NFC; }
    public static Normalizer2 getNFDInstance() { return NFD; }
    public static Normalizer2 getNFKCInstance() { return NFKC; }
    public static Normalizer2 getNFKDInstance() { return NFKD; }

    public static Normalizer2 getInstance(java.io.InputStream data, String name, Mode mode) {
        // Standard mapping tables only: route by mode to the matching form.
        if (mode == Mode.DECOMPOSE) return NFD;
        return NFC;
    }

    public abstract String normalize(CharSequence s);
    public abstract boolean isNormalized(CharSequence s);
    public abstract StringBuilder normalize(CharSequence src, StringBuilder dest);

    private static final Normalizer2 NFC = new Jdk(Normalizer.Form.NFC);
    private static final Normalizer2 NFD = new Jdk(Normalizer.Form.NFD);
    private static final Normalizer2 NFKC = new Jdk(Normalizer.Form.NFKC);
    private static final Normalizer2 NFKD = new Jdk(Normalizer.Form.NFKD);

    private static final class Jdk extends Normalizer2 {
        private final Normalizer.Form form;
        Jdk(Normalizer.Form form) { this.form = form; }
        @Override public String normalize(CharSequence s) {
            return Normalizer.normalize(s, form);
        }
        @Override public boolean isNormalized(CharSequence s) {
            return Normalizer.isNormalized(s, form);
        }
        @Override public StringBuilder normalize(CharSequence src, StringBuilder dest) {
            dest.setLength(0);
            dest.append(Normalizer.normalize(src, form));
            return dest;
        }
    }
}
