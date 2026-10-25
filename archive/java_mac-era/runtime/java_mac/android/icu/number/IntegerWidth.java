package android.icu.number;

/**
 * Minimum/maximum integer digits (zero padding).
 * Source: https://developer.android.com/reference/android/icu/number/IntegerWidth
 */
public class IntegerWidth {

    final int mMinInt;

    private IntegerWidth(int minInt) { mMinInt = minInt; }

    public static IntegerWidth zeroFillTo(int minInt) { return new IntegerWidth(minInt); }

    public IntegerWidth truncateAt(int maxInt) { return this; }
}
