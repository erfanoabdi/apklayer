// android.hardware.camera2.params.Capability -- one (mode, max streaming size,
// zoom ratio range) triple, the element type of
// CameraCharacteristics.CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES.
// API 31.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// Capability.java (aosp-mirror, android16-release).
package android.hardware.camera2.params;

import android.hardware.camera2.utils.HashCodeHelpers;
import android.util.Range;
import android.util.Size;

public final class Capability {

    private final int mMode;
    private final Size mMaxStreamingSize;
    private final Range<Float> mZoomRatioRange;

    /** AOSP Capability.java:64 -- the size must be non-negative in both axes and
     *  the zoom range must be positive and correctly ordered; each violation is
     *  its own IllegalArgumentException. */
    public Capability(int mode, Size maxStreamingSize, Range<Float> zoomRatioRange) {
        mMode = mode;
        if (maxStreamingSize.getWidth() < 0) {
            throw new IllegalArgumentException(
                    "maxStreamingSize.getWidth() must be nonnegative");
        }
        if (maxStreamingSize.getHeight() < 0) {
            throw new IllegalArgumentException(
                    "maxStreamingSize.getHeight() must be nonnegative");
        }
        mMaxStreamingSize = maxStreamingSize;
        if (zoomRatioRange.getLower() > zoomRatioRange.getUpper()) {
            throw new IllegalArgumentException("zoomRatioRange.getLower() "
                    + zoomRatioRange.getLower() + " is greater than zoomRatioRange.getUpper() "
                    + zoomRatioRange.getUpper());
        }
        if (zoomRatioRange.getLower() <= 0) {
            throw new IllegalArgumentException("zoomRatioRange.getLower() must be positive");
        }
        if (zoomRatioRange.getUpper() <= 0) {
            throw new IllegalArgumentException("zoomRatioRange.getUpper() must be positive");
        }
        mZoomRatioRange = zoomRatioRange;
    }

    public int getMode() { return mMode; }
    public Size getMaxStreamingSize() { return mMaxStreamingSize; }
    public Range<Float> getZoomRatioRange() { return mZoomRatioRange; }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof Capability) {
            final Capability other = (Capability) obj;
            return mMode == other.mMode
                    && mMaxStreamingSize.equals(other.mMaxStreamingSize)
                    && mZoomRatioRange.equals(other.mZoomRatioRange);
        }
        return false;
    }

    @Override public int hashCode() {
        return HashCodeHelpers.hashCode(mMode, mMaxStreamingSize.getWidth(),
                mMaxStreamingSize.getHeight(), mZoomRatioRange.getLower(),
                mZoomRatioRange.getUpper());
    }

    @Override public String toString() {
        return String.format("(mode:%d, maxStreamingSize:%d x %d, zoomRatio: %f-%f)",
                mMode, mMaxStreamingSize.getWidth(), mMaxStreamingSize.getHeight(),
                mZoomRatioRange.getLower(), mZoomRatioRange.getUpper());
    }
}
