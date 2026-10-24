package android.icu.number;
/** A Precision fixed by fraction-digit count, further refinable by significant
 *  digits (ICU number-formatter API). */
public abstract class FractionPrecision extends Precision {
    FractionPrecision() {}
    public Precision withMinDigits(int minSignificantDigits) { return this; }
    public Precision withMaxDigits(int maxSignificantDigits) { return this; }
}
