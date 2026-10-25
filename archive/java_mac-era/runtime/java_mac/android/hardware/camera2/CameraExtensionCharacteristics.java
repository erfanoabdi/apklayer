// android.hardware.camera2.CameraExtensionCharacteristics (API 31; the
// postview and progress queries are API 34).
//
// What vendor camera extensions -- HDR, night mode, bokeh, face retouch,
// automatic -- a given camera supports, and at what sizes and latencies. Apps
// query getSupportedExtensions() and degrade when an extension is missing, so
// they reference the EXTENSION_* constants long before they open anything.
//
// ---------------------------------------------------------------------------
// This is a device-absent answer, and "no extensions" is honest twice over
// ---------------------------------------------------------------------------
// On a real device this class binds to the OEM's camera-extensions service; the
// answer is empty for any device without one, which includes the great majority
// of Android devices. Mac.apk has no camera at all, so it is empty for the
// stronger reason. getSupportedExtensions() returning an empty list is the
// single answer everything else follows from -- an app that respects it never
// calls the size/latency queries, and an app that ignores it gets the same
// empty/false answers a real extension-less device gives.
//
// Claiming an extension we cannot run would be strictly worse than the empty
// list: the app would configure an extension session, and fail past the point
// where its no-extension fallback exists.
//
// Note that even when AVFoundation capture lands, this stays empty unless
// someone implements a vendor extension pipeline -- extensions are an OEM HAL
// feature, not a consequence of having a camera.
//
// Mirrored from
// frameworks/base/core/java/android/hardware/camera2/CameraExtensionCharacteristics.java
// (aosp-mirror/platform_frameworks_base, main): class L102, extension constants
// L109-141 (including the deprecated EXTENSION_BEAUTY alias L123),
// getSupportedExtensions L820, get L860, getKeys L907, isPostviewAvailable L985,
// getPostviewSupportedSizes L1042, getExtensionSupportedSizes(Class) L1143 and
// (int) L1211, getEstimatedCaptureLatencyRangeMillis L1304,
// isCaptureProcessProgressAvailable L1389, getAvailableCaptureRequestKeys L1442,
// getAvailableCaptureResultKeys L1530.
package android.hardware.camera2;

import android.util.Range;
import android.util.Size;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class CameraExtensionCharacteristics {

    /** Device-specific extension that automatically selects the best mode. */
    public static final int EXTENSION_AUTOMATIC = 0;

    /** Retouch the subject's face. */
    public static final int EXTENSION_FACE_RETOUCH = 1;

    /** @deprecated AOSP renamed this to EXTENSION_FACE_RETOUCH and kept the old
     *  name as an alias with the same value, so existing bytecode still links. */
    @Deprecated
    public static final int EXTENSION_BEAUTY = EXTENSION_FACE_RETOUCH;

    /** Blur the background behind the subject. */
    public static final int EXTENSION_BOKEH = 2;

    /** Extended dynamic range. */
    public static final int EXTENSION_HDR = 3;

    /** Low-light capture. */
    public static final int EXTENSION_NIGHT = 4;

    // AOSP's constructor takes (Context, cameraId, Map<String,
    // CameraCharacteristics>) and is @hide; apps only ever obtain an instance
    // from CameraManager.getCameraExtensionCharacteristics. Package-private
    // here for the same reason, and because CameraManager throws before it can
    // reach the construction on this device.
    CameraExtensionCharacteristics() { }

    /** The one answer everything else follows from. Empty -- this device has no
     *  vendor extension service, which is also true of most real devices. */
    public List<Integer> getSupportedExtensions() {
        return Collections.emptyList();
    }

    /** API 34. Null for a key an extension does not report, per AOSP. */
    public <T> T get(int extension, CameraCharacteristics.Key<T> key) {
        return null;
    }

    /** API 34. */
    public Set<CameraCharacteristics.Key> getKeys(int extension) {
        return Collections.emptySet();
    }

    /** API 34. */
    public boolean isPostviewAvailable(int extension) {
        return false;
    }

    /** API 34. */
    public List<Size> getPostviewSupportedSizes(int extension, Size captureSize, int format) {
        return Collections.emptyList();
    }

    /** API 31. The Class overload, for SurfaceTexture/SurfaceHolder targets. */
    public <T> List<Size> getExtensionSupportedSizes(int extension, Class<T> klass) {
        return Collections.emptyList();
    }

    /** API 31. */
    public List<Size> getExtensionSupportedSizes(int extension, int format) {
        return Collections.emptyList();
    }

    /** API 31. AOSP documents null as "the latency is not supported/known",
     *  and this is one of the few camera2 methods where null is the CORRECT
     *  device-absent answer rather than a stub -- an empty Range would claim a
     *  measured 0..0ms latency, which is a false capability. */
    public Range<Long> getEstimatedCaptureLatencyRangeMillis(int extension,
            Size captureOutputSize, int format) {
        return null;
    }

    /** API 34. */
    public boolean isCaptureProcessProgressAvailable(int extension) {
        return false;
    }

    /** API 31. */
    public Set<CaptureRequest.Key> getAvailableCaptureRequestKeys(int extension) {
        return Collections.emptySet();
    }

    /** API 31. */
    public Set<CaptureResult.Key> getAvailableCaptureResultKeys(int extension) {
        return Collections.emptySet();
    }
}
