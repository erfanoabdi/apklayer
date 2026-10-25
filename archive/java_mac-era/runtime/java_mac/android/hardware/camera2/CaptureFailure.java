// android.hardware.camera2.CaptureFailure -- describes a failed capture
// delivered to CaptureCallback.onCaptureFailed. Mirrors AOSP getters.
package android.hardware.camera2;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class CaptureFailure {
    public static final int REASON_ERROR = 0;
    public static final int REASON_FLUSHED = 1;

    private final CaptureRequest mRequest;
    private final int mReason;
    private final boolean mWasImageCaptured;
    private final int mSequenceId;
    private final long mFrameNumber;

    CaptureFailure(CaptureRequest request, int reason, boolean wasImageCaptured,
                   int sequenceId, long frameNumber) {
        mRequest = request;
        mReason = reason;
        mWasImageCaptured = wasImageCaptured;
        mSequenceId = sequenceId;
        mFrameNumber = frameNumber;
    }

    public CaptureRequest getRequest() { return mRequest; }
    public long getFrameNumber() { return mFrameNumber; }
    public int getReason() { return mReason; }
    public boolean wasImageCaptured() { return mWasImageCaptured; }
    public int getSequenceId() { return mSequenceId; }

    /**
     * API 28. The physical camera whose stream failed, or null when the failure
     * was not attributable to one.
     *
     * AOSP's javadoc: "the physical camera id ... In case the capture failure
     * is not associated with a particular physical camera, this method will
     * return null." CameraCharacteristics.getPhysicalCameraIds() is empty for
     * every camera this backend exposes, so no failure can name a physical
     * camera and null is the only correct answer.
     */
    public String getPhysicalCameraId() { return null; }
}
