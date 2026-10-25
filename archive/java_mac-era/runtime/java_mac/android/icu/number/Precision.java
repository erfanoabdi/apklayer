package android.icu.number;

import java.text.DecimalFormat;

/**
 * Rounding precision: fixed/max fraction digits, significant digits, or
 * unlimited. Applied onto the backing DecimalFormat.
 * Source: https://developer.android.com/reference/android/icu/number/Precision
 */
public abstract class Precision {

    abstract void applyTo(DecimalFormat df);

    public static Precision unlimited() {
        return new Precision() {
            @Override void applyTo(DecimalFormat df) {
                df.setMaximumFractionDigits(340);   // DecimalFormat's documented max
                df.setMinimumFractionDigits(0);
            }
        };
    }

    // AOSP: integer/fixedFraction/minFraction/maxFraction/minMaxFraction all return
    // FractionPrecision (so callers can chain .withMinDigits/.withMaxDigits). A
    // Precision-typed return is a different descriptor and cannot be linked from
    // SDK-compiled bytecode. android.icu.number.Precision (android-30 android.jar).
    public static FractionPrecision integer() {
        return new FractionPrecision() {
            @Override void applyTo(DecimalFormat df) {
                df.setMaximumFractionDigits(0);
                df.setMinimumFractionDigits(0);
            }
        };
    }

    public static FractionPrecision fixedFraction(final int digits) {
        return new FractionPrecision() {
            @Override void applyTo(DecimalFormat df) {
                df.setMinimumFractionDigits(digits);
                df.setMaximumFractionDigits(digits);
            }
        };
    }

    public static FractionPrecision minFraction(final int digits) {
        return new FractionPrecision() {
            @Override void applyTo(DecimalFormat df) { df.setMinimumFractionDigits(digits); }
        };
    }

    public static FractionPrecision maxFraction(final int digits) {
        return new FractionPrecision() {
            @Override void applyTo(DecimalFormat df) { df.setMaximumFractionDigits(digits); }
        };
    }

    public static FractionPrecision minMaxFraction(final int min, final int max) {
        return new FractionPrecision() {
            @Override void applyTo(DecimalFormat df) {
                df.setMinimumFractionDigits(min);
                df.setMaximumFractionDigits(max);
            }
        };
    }

    // java.text.DecimalFormat has no significant-digit API (ICU4J's
    // setM*SignificantDigits does not exist there), so significant digits are
    // realised by converting to a fraction-digit count against the value's
    // magnitude at format time. applyTo cannot see the value, so these carry
    // the request and LocalizedNumberFormatter resolves it per number.
    public static Precision fixedSignificantDigits(final int digits) {
        return new SignificantDigits(digits, digits);
    }

    public static Precision maxSignificantDigits(final int digits) {
        return new SignificantDigits(-1, digits);
    }

    public static Precision minSignificantDigits(final int digits) {
        return new SignificantDigits(digits, -1);
    }

    static final class SignificantDigits extends Precision {
        final int min;
        final int max;

        SignificantDigits(int min, int max) { this.min = min; this.max = max; }

        @Override void applyTo(DecimalFormat df) {
            // Resolved against the actual value in resolveFor(); nothing to do
            // without one.
        }

        /** Convert significant digits to fraction digits for this magnitude. */
        void resolveFor(DecimalFormat df, Number value) {
            double v = Math.abs(value.doubleValue());
            int intDigits = (v < 1.0 || Double.isNaN(v) || Double.isInfinite(v))
                    ? 1 : (int) Math.floor(Math.log10(v)) + 1;
            if (max >= 0) df.setMaximumFractionDigits(Math.max(0, max - intDigits));
            if (min >= 0) df.setMinimumFractionDigits(Math.max(0, min - intDigits));
        }
    }
}
