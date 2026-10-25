package android.icu.number;

/**
 * ICU's scientific/engineering notation. This is a TOP-LEVEL class in the
 * platform (android.icu.number.ScientificNotation); we previously declared it as
 * a nested Notation.ScientificNotation, which gives Notation.scientific() the
 * descriptor ()Landroid/icu/number/Notation$ScientificNotation; instead of
 * ()Landroid/icu/number/ScientificNotation; -- a call compiled against the SDK
 * could not link to it.
 *
 * Source: android.icu.number.ScientificNotation (android-30 android.jar);
 * external/icu ScientificNotation.java.
 */
public class ScientificNotation extends Notation {
    ScientificNotation() {}

    public ScientificNotation withMinExponentDigits(int minExponentDigits) { return this; }

    public ScientificNotation withExponentSignDisplay(
            NumberFormatter.SignDisplay exponentSignDisplay) {
        return this;
    }
}
