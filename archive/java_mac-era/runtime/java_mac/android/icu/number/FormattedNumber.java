package android.icu.number;

import android.icu.util.MeasureUnit;

import java.math.BigDecimal;

/**
 * The result of a format call: the string plus the value and unit it came from.
 * Source: https://developer.android.com/reference/android/icu/number/FormattedNumber
 */
public class FormattedNumber implements CharSequence {

    private final String mText;
    private final Number mValue;
    private final MeasureUnit mUnit;

    FormattedNumber(String text, Number value, MeasureUnit unit) {
        mText = text; mValue = value; mUnit = unit;
    }

    @Override public String toString() { return mText; }

    @Override public int length() { return mText.length(); }

    @Override public char charAt(int index) { return mText.charAt(index); }

    @Override public CharSequence subSequence(int start, int end) {
        return mText.subSequence(start, end);
    }

    public MeasureUnit getOutputUnit() { return mUnit; }

    public BigDecimal toBigDecimal() { return new BigDecimal(mValue.toString()); }

    public <A extends Appendable> A appendTo(A appendable) {
        try { appendable.append(mText); } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return appendable;
    }
}
