// android.hardware.LutProperties -- what one colour lookup table the display
// pipeline can apply looks like: how many dimensions, how big, and which
// sampling strategies it accepts. Reached through
// OverlayProperties.getLutProperties().
//
// Mirrors frameworks/base/core/java/android/hardware/LutProperties.java
// (android16-release). A pure value carrier; the constructor is
// package-private there and stays package-private here, because only the
// display pipeline can describe its own LUT hardware.
//
// Mac.apk's compositor applies no display LUTs (ANGLE/Metal into one
// CAMetalLayer, no colour-management stage we can expose), so
// OverlayProperties.getLutProperties() returns an EMPTY array and nothing
// constructs one of these. The class must exist so an app that iterates that
// array, or declares a LutProperties local, links and runs.
package android.hardware;

public final class LutProperties {

    public static final int SAMPLING_KEY_RGB = 0;
    public static final int SAMPLING_KEY_MAX_RGB = 1;
    public static final int SAMPLING_KEY_CIE_Y = 2;

    public static final int ONE_DIMENSION = 1;
    public static final int THREE_DIMENSION = 3;

    private final int mDimension;
    private final int mSize;
    private final int[] mSamplingKeys;

    /* package */ LutProperties(int dimension, int size, int[] samplingKeys) {
        mDimension = dimension;
        mSize = size;
        mSamplingKeys = samplingKeys;
    }

    public int getDimension() { return mDimension; }

    public int getSize() { return mSize; }

    /** AOSP throws IllegalStateException for an empty key set rather than
     *  returning an empty array, because @NonNull here means "there is at
     *  least one way to sample this LUT" and a LUT with none is malformed. */
    public int[] getSamplingKeys() {
        if (mSamplingKeys == null || mSamplingKeys.length == 0) {
            throw new IllegalStateException("no sampling key!");
        }
        return mSamplingKeys.clone();
    }
}
