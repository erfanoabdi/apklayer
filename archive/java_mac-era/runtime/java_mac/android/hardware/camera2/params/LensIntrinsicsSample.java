// android.hardware.camera2.params.LensIntrinsicsSample -- one timestamped
// sample of LENS_INTRINSIC_CALIBRATION, the element type of
// CaptureResult.STATISTICS_LENS_INTRINSIC_SAMPLES. API 34.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// LensIntrinsicsSample.java (aosp-mirror, android16-release).
package android.hardware.camera2.params;

import android.hardware.camera2.utils.HashCodeHelpers;
import java.util.Arrays;

public final class LensIntrinsicsSample {

    private final long mTimestampNs;
    private final float[] mLensIntrinsics;

    /** AOSP LensIntrinsicsSample.java:44 -- exactly five intrinsics
     *  ([f_x, f_y, c_x, c_y, s]); anything else is IllegalArgumentException. */
    public LensIntrinsicsSample(long timestampNs, float[] lensIntrinsics) {
        mTimestampNs = timestampNs;
        if (lensIntrinsics == null || lensIntrinsics.length != 5) {
            throw new IllegalArgumentException();
        }
        mLensIntrinsics = lensIntrinsics;
    }

    public long getTimestampNanos() { return mTimestampNs; }

    // AOSP returns the stored array itself (no defensive copy).
    public float[] getLensIntrinsics() { return mLensIntrinsics; }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof LensIntrinsicsSample) {
            final LensIntrinsicsSample other = (LensIntrinsicsSample) obj;
            return mTimestampNs == other.mTimestampNs
                    && Arrays.equals(mLensIntrinsics, other.getLensIntrinsics());
        }
        return false;
    }

    @Override public int hashCode() {
        int timestampHash = HashCodeHelpers.hashCode((float) mTimestampNs);
        return HashCodeHelpers.hashCode(Arrays.hashCode(mLensIntrinsics), timestampHash);
    }

    @Override public String toString() {
        return String.format("LensIntrinsicsSample{timestamp:%d, sample:%s}",
                mTimestampNs, Arrays.toString(mLensIntrinsics));
    }
}
