// android.hardware.camera2.params.RggbChannelVector -- an immutable 4-element
// vector of per-Bayer-channel gains (R, G_even, G_odd, B). Value type of
// CaptureRequest/CaptureResult COLOR_CORRECTION_GAINS.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// RggbChannelVector.java (aosp-mirror, android16-release).
package android.hardware.camera2.params;

public final class RggbChannelVector {

    /** AOSP RggbChannelVector.java:40 */
    public static final int COUNT = 4;
    /** AOSP :45 */
    public static final int RED = 0;
    /** AOSP :50 */
    public static final int GREEN_EVEN = 1;
    /** AOSP :55 */
    public static final int GREEN_ODD = 2;
    /** AOSP :60 */
    public static final int BLUE = 3;

    private final float mRed;
    private final float mGreenEven;
    private final float mGreenOdd;
    private final float mBlue;

    /** AOSP :72 -- every component is checkArgumentFinite'd, so NaN/Inf throws
     *  IllegalArgumentException rather than being stored. */
    public RggbChannelVector(float red, float greenEven, float greenOdd, float blue) {
        mRed = checkFinite(red, "red");
        mGreenEven = checkFinite(greenEven, "greenEven");
        mGreenOdd = checkFinite(greenOdd, "greenOdd");
        mBlue = checkFinite(blue, "blue");
    }

    // android.util.Preconditions.checkArgumentFinite is @hide in AOSP; this is
    // the same contract (IllegalArgumentException naming the parameter).
    private static float checkFinite(float value, String name) {
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException(name + " must not be NaN");
        } else if (Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must not be infinite");
        }
        return value;
    }

    public final float getRed() { return mRed; }
    public float getGreenEven() { return mGreenEven; }
    public float getGreenOdd() { return mGreenOdd; }
    public float getBlue() { return mBlue; }

    /** AOSP :121 */
    public float getComponent(int colorChannel) {
        if (colorChannel < 0 || colorChannel >= COUNT) {
            throw new IllegalArgumentException("Color channel out of range");
        }
        switch (colorChannel) {
            case RED: return mRed;
            case GREEN_EVEN: return mGreenEven;
            case GREEN_ODD: return mGreenOdd;
            case BLUE: return mBlue;
            default: throw new AssertionError("Unhandled case " + colorChannel);
        }
    }

    /** AOSP :150 */
    public void copyTo(float[] destination, int offset) {
        if (destination == null) {
            throw new NullPointerException("destination must not be null");
        }
        if (destination.length - offset < COUNT) {
            throw new ArrayIndexOutOfBoundsException("destination too small to fit elements");
        }
        destination[offset + RED] = mRed;
        destination[offset + GREEN_EVEN] = mGreenEven;
        destination[offset + GREEN_ODD] = mGreenOdd;
        destination[offset + BLUE] = mBlue;
    }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof RggbChannelVector) {
            RggbChannelVector other = (RggbChannelVector) obj;
            return mRed == other.mRed && mGreenEven == other.mGreenEven
                    && mGreenOdd == other.mGreenOdd && mBlue == other.mBlue;
        }
        return false;
    }

    // AOSP :185 -- XOR of the raw bits, not Objects.hash.
    @Override public int hashCode() {
        return Float.floatToIntBits(mRed) ^ Float.floatToIntBits(mGreenEven)
                ^ Float.floatToIntBits(mGreenOdd) ^ Float.floatToIntBits(mBlue);
    }

    @Override public String toString() {
        return String.format("RggbChannelVector%s", toShortString());
    }

    private String toShortString() {
        return String.format("{R:%f, G_even:%f, G_odd:%f, B:%f}",
                mRed, mGreenEven, mGreenOdd, mBlue);
    }
}
