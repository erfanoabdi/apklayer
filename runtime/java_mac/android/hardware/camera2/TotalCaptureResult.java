// android.hardware.camera2.TotalCaptureResult -- the complete result for
// a capture (all metadata, no partials missing). Subclass of
// CaptureResult per AOSP.
package android.hardware.camera2;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TotalCaptureResult extends CaptureResult {
    TotalCaptureResult() { super(); }

    /** The populated form, built by CameraCaptureSessionImpl for each frame
     *  the capture backend delivers. `results` carries only metadata we
     *  actually measured -- see the session's dispatch site for why that is a
     *  short list and why nothing is padded out with plausible values. */
    TotalCaptureResult(Map<Key<?>, Object> results, CaptureRequest request, long frameNumber) {
        super(results, request, frameNumber);
    }

    /** The form the session actually uses: it knows the camera id and the
     *  sequence id, and CaptureResult.getCameraId()/getSequenceId() are public
     *  API that must answer with them. */
    TotalCaptureResult(Map<Key<?>, Object> results, CaptureRequest request, long frameNumber,
            String cameraId, int sequenceId) {
        super(results, request, frameNumber, cameraId, sequenceId);
    }

    /** Empty because we emit no partial results: the capture backend produces
     *  a frame's metadata all at once, never in the progressive instalments
     *  onCaptureProgressed exists for. AOSP's own javadoc allows an empty list
     *  when the device does not support partial results. */
    public List<CaptureResult> getPartialResults() { return Collections.emptyList(); }

    // The two physical-camera maps below. AOSP's own javadoc for both says the
    // map is empty unless the request actually targeted streams from the
    // underlying physical cameras of a LOGICAL multi-camera
    // (frameworks/base/core/java/android/hardware/camera2/TotalCaptureResult.java,
    // main: getPhysicalCameraResults L187, getPhysicalCameraTotalResults L212).
    // CameraCharacteristics.getPhysicalCameraIds() is empty here, so there is no
    // physical camera that could contribute an entry -- the empty map is the
    // correct answer, not a placeholder, and it is also what a real
    // single-camera device returns.
    //
    // Note AOSP backs BOTH methods with the same mPhysicalCaptureResults field
    // and differs only in the declared value type, which is why the deprecated
    // one is not implemented in terms of the other here either.

    /** @deprecated AOSP deprecates this in favour of
     *  getPhysicalCameraTotalResults(), whose values are TotalCaptureResult
     *  rather than CaptureResult. */
    @Deprecated
    public Map<String, CaptureResult> getPhysicalCameraResults() {
        return Collections.emptyMap();
    }

    /** API 30. */
    public Map<String, TotalCaptureResult> getPhysicalCameraTotalResults() {
        return Collections.emptyMap();
    }
}
