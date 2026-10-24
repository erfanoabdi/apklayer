// android.hardware.camera2.params.InputConfiguration (API 23; the
// multi-resolution constructor is API 31).
//
// The input side of a reprocessable capture session: width, height and format
// of the buffers the app will feed back into the camera. Apps reference the type
// to declare a field or a parameter long before they ever create a session, and
// SessionConfiguration.setInputConfiguration takes it, so it has to resolve.
//
// This is a pure immutable value type. AOSP's implementation touches no camera
// state, so this is a REAL implementation rather than a device-absent answer --
// an app that builds one and reads it back, or compares two, behaves exactly as
// it would on a real device.
//
// Mirrored from
// frameworks/base/core/java/android/hardware/camera2/params/InputConfiguration.java
// (aosp-mirror/platform_frameworks_base, main): fields L39-42, the (w,h,format)
// constructor L59-64, the multi-resolution constructor L90-98, the 4-arg @hide
// constructor L103-108, getWidth L115, getHeight L124, getFormat L133,
// isMultiResolution L145, equals L159-174, hashCode L180-182, toString L193-197.
//
// Two AOSP-internal helpers are inlined rather than pulled in: the
// multi-resolution constructor's checkCollectionNotEmpty (from
// com.android.internal.util.Preconditions) becomes the same
// NullPointerException/IllegalArgumentException pair, and
// utils.HashCodeHelpers.hashCode(int...) becomes the identical 31-multiplier
// fold it implements.
package android.hardware.camera2.params;

import java.util.Collection;

public final class InputConfiguration {

    private final int mWidth;
    private final int mHeight;
    private final int mFormat;
    private final boolean mIsMultiResolution;

    public InputConfiguration(int width, int height, int format) {
        mWidth = width;
        mHeight = height;
        mFormat = format;
        mIsMultiResolution = false;
    }

    /** Create an input configuration from a group of multi-resolution input
     *  streams. AOSP takes the width/height from the FIRST element of the
     *  collection (not the largest), which is reproduced here. */
    public InputConfiguration(Collection<MultiResolutionStreamInfo> multiResolutionInputs,
            int format) {
        if (multiResolutionInputs == null) {
            throw new NullPointerException("Input multi-resolution stream info must not be null");
        }
        if (multiResolutionInputs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Input multi-resolution stream info must not be empty");
        }
        MultiResolutionStreamInfo info = multiResolutionInputs.iterator().next();
        mWidth = info.getWidth();
        mHeight = info.getHeight();
        mFormat = format;
        mIsMultiResolution = true;
    }

    /** AOSP marks this constructor @hide, but it is `public` there and the
     *  camera2 params package uses it, so it is kept public here for the same
     *  reason: a hidden-but-public AOSP member is still something an app's
     *  bytecode can reference. */
    public InputConfiguration(int width, int height, int format, boolean isMultiResolution) {
        mWidth = width;
        mHeight = height;
        mFormat = format;
        mIsMultiResolution = isMultiResolution;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getFormat() {
        return mFormat;
    }

    public boolean isMultiResolution() {
        return mIsMultiResolution;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof InputConfiguration)) {
            return false;
        }
        InputConfiguration otherInputConfig = (InputConfiguration) obj;
        return otherInputConfig.getWidth() == mWidth
                && otherInputConfig.getHeight() == mHeight
                && otherInputConfig.getFormat() == mFormat
                && otherInputConfig.isMultiResolution() == mIsMultiResolution;
    }

    @Override
    public int hashCode() {
        // HashCodeHelpers.hashCode(int...) is seed 1 folded with 31, which is
        // what java.util.Arrays.hashCode(int[]) computes.
        int h = 1;
        h = 31 * h + mWidth;
        h = 31 * h + mHeight;
        h = 31 * h + mFormat;
        h = 31 * h + (mIsMultiResolution ? 1 : 0);
        return h;
    }

    @Override
    public String toString() {
        return String.format("InputConfiguration(w:%d, h:%d, format:%d, isMultiResolution %b)",
                mWidth, mHeight, mFormat, mIsMultiResolution);
    }
}
