// android.hardware.camera2.params.MultiResolutionStreamInfo (API 31).
//
// One fixed-size image-buffer stream backing a multi-resolution stream, tagged
// with the physical camera id it originates from. Apps build groups of these to
// construct a MultiResolutionImageReader or a multi-resolution reprocessable
// session.
//
// This is a pure immutable value type -- AOSP's implementation has no device
// dependency at all, so this is a REAL implementation, not a device-absent
// answer. Every method below does what AOSP's does, including the constructor's
// argument validation, so an app that builds one and reads it back gets
// identical behaviour here and on a real device.
//
// Mirrored verbatim from
// frameworks/base/core/java/android/hardware/camera2/params/MultiResolutionStreamInfo.java
// (aosp-mirror/platform_frameworks_base, main): fields L40-42, constructor
// L61-73 including both IllegalArgumentException checks, getWidth L78,
// getHeight L85, getPhysicalCameraId L92, equals L102-117, hashCode L122-126.
// Note AOSP declares this class non-final.
package android.hardware.camera2.params;

import java.util.Objects;

public class MultiResolutionStreamInfo {
    private int mStreamWidth;
    private int mStreamHeight;
    private String mPhysicalCameraId;

    public MultiResolutionStreamInfo(int streamWidth, int streamHeight,
            String physicalCameraId) {
        if (streamWidth <= 0) {
            throw new IllegalArgumentException("Invalid stream width " + streamWidth);
        }
        if (streamHeight <= 0) {
            throw new IllegalArgumentException("Invalid stream height " + streamHeight);
        }
        mStreamWidth = streamWidth;
        mStreamHeight = streamHeight;
        mPhysicalCameraId = physicalCameraId;
    }

    /** The width of this particular image buffer stream in pixels. */
    public int getWidth() {
        return mStreamWidth;
    }

    /** The height of this particular image buffer stream in pixels. */
    public int getHeight() {
        return mStreamHeight;
    }

    /** The physical camera id of this particular image buffer stream. */
    public String getPhysicalCameraId() {
        return mPhysicalCameraId;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (obj instanceof MultiResolutionStreamInfo) {
            final MultiResolutionStreamInfo other = (MultiResolutionStreamInfo) obj;
            return mStreamWidth == other.mStreamWidth
                    && mStreamHeight == other.mStreamHeight
                    && mPhysicalCameraId.equals(other.mPhysicalCameraId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStreamWidth, mStreamHeight, mPhysicalCameraId);
    }
}
