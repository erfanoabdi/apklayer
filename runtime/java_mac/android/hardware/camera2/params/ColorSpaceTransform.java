// android.hardware.camera2.params.ColorSpaceTransform -- an immutable 3x3
// matrix of Rationals, the value type of COLOR_CORRECTION_TRANSFORM and the
// SENSOR_CALIBRATION/COLOR_TRANSFORM characteristics.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// ColorSpaceTransform.java (aosp-mirror, android16-release). The int[] form is
// 18 ints: nine (numerator, denominator) pairs in row-major order, which is
// exactly how the HAL metadata carries it.
package android.hardware.camera2.params;

import android.util.Rational;
import java.util.Arrays;

public final class ColorSpaceTransform {

    private static final int ROWS = 3;
    private static final int COLUMNS = 3;
    private static final int COUNT = ROWS * COLUMNS;
    private static final int RATIONAL_SIZE = 2;
    private static final int OFFSET_NUMERATOR = 0;
    private static final int OFFSET_DENOMINATOR = 1;
    private static final int COUNT_INT = ROWS * COLUMNS * RATIONAL_SIZE;

    private final int[] mElements;

    /** AOSP ColorSpaceTransform.java:58 -- nine Rationals, row-major. */
    public ColorSpaceTransform(Rational[] elements) {
        if (elements == null) {
            throw new NullPointerException("elements must not be null");
        }
        if (elements.length != COUNT) {
            throw new IllegalArgumentException("elements must be " + COUNT + " length");
        }
        mElements = new int[COUNT_INT];
        for (int i = 0; i < elements.length; ++i) {
            if (elements[i] == null) {
                throw new NullPointerException("element[" + i + "] must not be null");
            }
            mElements[i * RATIONAL_SIZE + OFFSET_NUMERATOR] = elements[i].getNumerator();
            mElements[i * RATIONAL_SIZE + OFFSET_DENOMINATOR] = elements[i].getDenominator();
        }
    }

    /** AOSP :89 -- 18 ints, (numerator, denominator) pairs. */
    public ColorSpaceTransform(int[] elements) {
        if (elements == null) {
            throw new NullPointerException("elements must not be null");
        }
        if (elements.length != COUNT_INT) {
            throw new IllegalArgumentException("elements must be " + COUNT_INT + " length");
        }
        mElements = Arrays.copyOf(elements, elements.length);
    }

    /** AOSP :111 */
    public Rational getElement(int column, int row) {
        if (column < 0 || column >= COLUMNS) {
            throw new IllegalArgumentException("column out of range");
        } else if (row < 0 || row >= ROWS) {
            throw new IllegalArgumentException("row out of range");
        }
        int numerator = mElements[(row * COLUMNS + column) * RATIONAL_SIZE + OFFSET_NUMERATOR];
        int denominator = mElements[(row * COLUMNS + column) * RATIONAL_SIZE + OFFSET_DENOMINATOR];
        return new Rational(numerator, denominator);
    }

    /** AOSP :131 */
    public void copyElements(Rational[] destination, int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (destination == null) {
            throw new NullPointerException("destination must not be null");
        }
        if (destination.length - offset < COUNT) {
            throw new ArrayIndexOutOfBoundsException("destination too small to fit elements");
        }
        for (int i = 0, j = 0; i < COUNT; ++i, j += RATIONAL_SIZE) {
            destination[i + offset] =
                    new Rational(mElements[j + OFFSET_NUMERATOR], mElements[j + OFFSET_DENOMINATOR]);
        }
    }

    /** AOSP :158 */
    public void copyElements(int[] destination, int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (destination == null) {
            throw new NullPointerException("destination must not be null");
        }
        if (destination.length - offset < COUNT_INT) {
            throw new ArrayIndexOutOfBoundsException("destination too small to fit elements");
        }
        for (int i = 0; i < COUNT_INT; ++i) {
            destination[i + offset] = mElements[i];
        }
    }

    // AOSP compares element-wise through Rational, NOT by raw int array: 1/2 and
    // 2/4 are equal transforms, and Rational reduces on construction.
    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof ColorSpaceTransform) {
            final ColorSpaceTransform other = (ColorSpaceTransform) obj;
            for (int i = 0, j = 0; i < COUNT; ++i, j += RATIONAL_SIZE) {
                Rational r = new Rational(mElements[j + OFFSET_NUMERATOR],
                        mElements[j + OFFSET_DENOMINATOR]);
                Rational rOther = new Rational(other.mElements[j + OFFSET_NUMERATOR],
                        other.mElements[j + OFFSET_DENOMINATOR]);
                if (!r.equals(rOther)) return false;
            }
            return true;
        }
        return false;
    }

    // AOSP uses com.android.internal.util.HashCodeHelpers.hashCode(int[]), which
    // is @hide; this is that method's body verbatim (h = h*31 ^ x).
    @Override public int hashCode() {
        int h = 1;
        for (int x : mElements) {
            h = ((h << 5) - h) ^ x;
        }
        return h;
    }

    @Override public String toString() {
        return String.format("ColorSpaceTransform%s", toShortString());
    }

    private String toShortString() {
        StringBuilder sb = new StringBuilder("(");
        for (int row = 0, i = 0; row < ROWS; row++) {
            sb.append("[");
            for (int col = 0; col < COLUMNS; col++, i += RATIONAL_SIZE) {
                sb.append(mElements[i + OFFSET_NUMERATOR]);
                sb.append("/");
                sb.append(mElements[i + OFFSET_DENOMINATOR]);
                if (col < COLUMNS - 1) sb.append(", ");
            }
            sb.append("]");
            if (row < ROWS - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }
}
