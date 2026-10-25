// android.hardware.camera2.params.TonemapCurve -- three per-channel tonemap
// curves as (in, out) point pairs. Value type of TONEMAP_CURVE.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// TonemapCurve.java (aosp-mirror, android16-release).
package android.hardware.camera2.params;

import android.graphics.PointF;
import android.hardware.camera2.utils.HashCodeHelpers;
import java.util.Arrays;

public final class TonemapCurve {

    /** AOSP TonemapCurve.java:47 */
    public static final float LEVEL_BLACK = 0.0f;
    /** AOSP :52 */
    public static final float LEVEL_WHITE = 1.0f;
    /** AOSP :57 -- a point is two floats, (in, out). */
    public static final int POINT_SIZE = 2;
    /** AOSP :62 */
    public static final int CHANNEL_RED = 0;
    /** AOSP :67 */
    public static final int CHANNEL_GREEN = 1;
    /** AOSP :72 */
    public static final int CHANNEL_BLUE = 2;

    private static final int OFFSET_POINT_IN = 0;
    private static final int OFFSET_POINT_OUT = 1;
    private static final int TONEMAP_MIN_CURVE_POINTS = 2;
    private static final int MIN_CURVE_LENGTH = TONEMAP_MIN_CURVE_POINTS * POINT_SIZE;

    private final float[] mRed;
    private final float[] mGreen;
    private final float[] mBlue;
    private int mHashCode;
    private boolean mHashCalculated = false;

    /** AOSP :107 -- each curve must be a whole number of points, at least two
     *  points long, and every value inside [LEVEL_BLACK, LEVEL_WHITE]. */
    public TonemapCurve(float[] red, float[] green, float[] blue) {
        checkNotNull(red, "red must not be null");
        checkNotNull(green, "green must not be null");
        checkNotNull(blue, "blue must not be null");
        checkLengthDivisibleBy(red, POINT_SIZE, "red");
        checkLengthDivisibleBy(green, POINT_SIZE, "green");
        checkLengthDivisibleBy(blue, POINT_SIZE, "blue");
        checkLengthNoLessThan(red, MIN_CURVE_LENGTH, "red");
        checkLengthNoLessThan(green, MIN_CURVE_LENGTH, "green");
        checkLengthNoLessThan(blue, MIN_CURVE_LENGTH, "blue");
        checkElementsInRange(red, "red");
        checkElementsInRange(green, "green");
        checkElementsInRange(blue, "blue");
        mRed = Arrays.copyOf(red, red.length);
        mGreen = Arrays.copyOf(green, green.length);
        mBlue = Arrays.copyOf(blue, blue.length);
    }

    private static void checkNotNull(Object o, String message) {
        if (o == null) throw new NullPointerException(message);
    }

    private static void checkLengthDivisibleBy(float[] array, int divisible, String name) {
        if (array.length % divisible != 0) {
            throw new IllegalArgumentException(name + " size must be divisible by " + divisible);
        }
    }

    private static void checkLengthNoLessThan(float[] array, int minLength, String name) {
        if (array.length < minLength) {
            throw new IllegalArgumentException(name + " size must be at least " + minLength);
        }
    }

    // Preconditions.checkArrayElementsInRange is @hide; same contract.
    private static void checkElementsInRange(float[] array, String name) {
        for (int i = 0; i < array.length; i++) {
            float e = array[i];
            if (Float.isNaN(e)) {
                throw new IllegalArgumentException(name + "[" + i + "] must not be NaN");
            }
            if (e < LEVEL_BLACK) {
                throw new IllegalArgumentException(
                        name + "[" + i + "] is out of range of [" + LEVEL_BLACK + ", "
                                + LEVEL_WHITE + "] (too low)");
            }
            if (e > LEVEL_WHITE) {
                throw new IllegalArgumentException(
                        name + "[" + i + "] is out of range of [" + LEVEL_BLACK + ", "
                                + LEVEL_WHITE + "] (too high)");
            }
        }
    }

    private static int checkArgumentColorChannel(int colorChannel) {
        switch (colorChannel) {
            case CHANNEL_RED:
            case CHANNEL_GREEN:
            case CHANNEL_BLUE:
                break;
            default:
                throw new IllegalArgumentException("colorChannel out of range");
        }
        return colorChannel;
    }

    private float[] getCurve(int colorChannel) {
        switch (colorChannel) {
            case CHANNEL_RED: return mRed;
            case CHANNEL_GREEN: return mGreen;
            case CHANNEL_BLUE: return mBlue;
            default: throw new AssertionError("colorChannel out of range");
        }
    }

    /** AOSP :175 */
    public int getPointCount(int colorChannel) {
        checkArgumentColorChannel(colorChannel);
        return getCurve(colorChannel).length / POINT_SIZE;
    }

    /** AOSP :192 */
    public PointF getPoint(int colorChannel, int index) {
        checkArgumentColorChannel(colorChannel);
        if (index < 0 || index >= getPointCount(colorChannel)) {
            throw new IllegalArgumentException("index out of range");
        }
        final float[] curve = getCurve(colorChannel);
        return new PointF(curve[index * POINT_SIZE + OFFSET_POINT_IN],
                curve[index * POINT_SIZE + OFFSET_POINT_OUT]);
    }

    /** AOSP :218. The bounds check is AOSP's, `length + offset` and all -- kept
     *  verbatim so an app that relies on the (odd) accepted range behaves the
     *  same here as on a device. */
    public void copyColorCurve(int colorChannel, float[] destination, int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        checkNotNull(destination, "destination must not be null");
        if (destination.length + offset < getPointCount(colorChannel) * POINT_SIZE) {
            throw new ArrayIndexOutOfBoundsException("destination too small to fit elements");
        }
        float[] curve = getCurve(colorChannel);
        System.arraycopy(curve, 0, destination, offset, curve.length);
    }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof TonemapCurve) {
            final TonemapCurve other = (TonemapCurve) obj;
            return Arrays.equals(mRed, other.mRed)
                    && Arrays.equals(mGreen, other.mGreen)
                    && Arrays.equals(mBlue, other.mBlue);
        }
        return false;
    }

    @Override public int hashCode() {
        if (mHashCalculated) return mHashCode;
        mHashCode = HashCodeHelpers.hashCodeGeneric(mRed, mGreen, mBlue);
        mHashCalculated = true;
        return mHashCode;
    }

    @Override public String toString() {
        return "TonemapCurve{R:" + curveToString(CHANNEL_RED)
                + ", G:" + curveToString(CHANNEL_GREEN)
                + ", B:" + curveToString(CHANNEL_BLUE) + "}";
    }

    private String curveToString(int colorChannel) {
        checkArgumentColorChannel(colorChannel);
        StringBuilder sb = new StringBuilder("[");
        float[] curve = getCurve(colorChannel);
        int pointCount = curve.length / POINT_SIZE;
        for (int i = 0, j = 0; i < pointCount; i++, j += 2) {
            sb.append("(");
            sb.append(curve[j]);
            sb.append(", ");
            sb.append(curve[j + 1]);
            sb.append(")");
            if (i < pointCount - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}
