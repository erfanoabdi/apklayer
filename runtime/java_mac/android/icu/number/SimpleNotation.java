package android.icu.number;

/**
 * ICU's plain (non-scientific, non-compact) notation. Notation.simple() returns
 * this concrete type in the platform, not the Notation base -- the base-typed
 * return is a different descriptor and would not link from SDK-compiled code.
 *
 * Source: android.icu.number.SimpleNotation (android-30 android.jar).
 */
public class SimpleNotation extends Notation {
    SimpleNotation() {}
}
