// android.hardware.camera2.params.OisSample -- one optical-image-stabilization
// shift sample, the element type of CaptureResult.STATISTICS_OIS_SAMPLES.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// OisSample.java (aosp-mirror, android16-release).
package android.hardware.camera2.params;

import android.hardware.camera2.utils.HashCodeHelpers;

public final class OisSample {

    private final long mTimestampNs;
    private final float mXShift;
    private final float mYShift;

    /** AOSP OisSample.java:47 -- both shifts are checkArgumentFinite'd. */
    public OisSample(long timestamp, float xShift, float yShift) {
        mTimestampNs = timestamp;
        mXShift = checkFinite(xShift, "xShift must be finite");
        mYShift = checkFinite(yShift, "yShift must be finite");
    }

    private static float checkFinite(float value, String message) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** Nanoseconds, on the same timebase as CaptureResult.SENSOR_TIMESTAMP. */
    public long getTimestamp() { return mTimestampNs; }
    public float getXshift() { return mXShift; }
    public float getYshift() { return mYShift; }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof OisSample) {
            final OisSample other = (OisSample) obj;
            return mTimestampNs == other.mTimestampNs
                    && mXShift == other.mXShift
                    && mYShift == other.mYShift;
        }
        return false;
    }

    // AOSP :106 -- the long timestamp widens to float on the way into
    // HashCodeHelpers.hashCode(float...); kept as-is so the value matches.
    @Override public int hashCode() {
        int timestampHash = HashCodeHelpers.hashCode(mTimestampNs);
        return HashCodeHelpers.hashCode(mXShift, mYShift, timestampHash);
    }

    @Override public String toString() {
        return String.format("OisSample{timestamp:%d, shift_x:%f, shift_y:%f}",
                mTimestampNs, mXShift, mYShift);
    }
}
