// android.hardware.camera2.CameraCaptureSession -- a configured capture
// session on an open CameraDevice. Abstract in AOSP. Mac.apk never
// creates one (no camera), but the type + nested callbacks must exist
// for apps to compile.
package android.hardware.camera2;

import android.os.Handler;
import android.view.Surface;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

public abstract class CameraCaptureSession implements AutoCloseable {
    public abstract CameraDevice getDevice();
    public abstract int capture(CaptureRequest request, CaptureCallback callback,
            Handler handler) throws CameraAccessException;
    public abstract int captureBurst(List<CaptureRequest> requests, CaptureCallback callback,
            Handler handler) throws CameraAccessException;
    public abstract int setRepeatingRequest(CaptureRequest request, CaptureCallback callback,
            Handler handler) throws CameraAccessException;
    public abstract int setRepeatingBurst(List<CaptureRequest> requests, CaptureCallback callback,
            Handler handler) throws CameraAccessException;
    public abstract void stopRepeating() throws CameraAccessException;
    public abstract void abortCaptures() throws CameraAccessException;
    @Override public abstract void close();

    // ---------------------------------------------------------------------
    // The rest of the AOSP CameraCaptureSession surface, mirrored from
    // frameworks/base/core/java/android/hardware/camera2/CameraCaptureSession.java
    // (aosp-mirror/platform_frameworks_base, android16-release). Abstract here
    // exactly where AOSP is abstract, and carrying AOSP's own default body --
    // `throw new UnsupportedOperationException("Subclasses must override this
    // method")` -- exactly where AOSP has one. Both halves matter: an app that
    // reaches an unimplemented path gets the same exception with the same
    // message it would get on a device, instead of a fabricated success.
    // ---------------------------------------------------------------------

    /** API 21. Whether this session accepts reprocess requests, i.e. whether it
     *  was created with an InputConfiguration. AOSP CameraCaptureSession.java:838, abstract. */
    public abstract boolean isReprocessable();

    /** API 21. The Surface reprocess input buffers are written to, or null when
     *  the session is not reprocessable. AOSP :857, abstract. */
    public abstract Surface getInputSurface();

    /** API 23. Ask the camera to pre-allocate buffers for an output Surface so
     *  the first capture into it does not stall. Completion is reported through
     *  StateCallback.onSurfacePrepared. AOSP :149, abstract. */
    public abstract void prepare(Surface surface) throws CameraAccessException;

    /** API 26. Tell the session that the deferred Surfaces of these
     *  OutputConfigurations are now available. AOSP :288, abstract. */
    public abstract void finalizeOutputConfigurations(
            List<android.hardware.camera2.params.OutputConfiguration> outputConfigs)
            throws CameraAccessException;

    /** API 28. capture() with an Executor instead of a Handler. AOSP
     *  CameraCaptureSession.java:408 default body, verbatim. */
    public int captureSingleRequest(CaptureRequest request, Executor executor,
            CaptureCallback listener) throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    /** API 28. captureBurst() with an Executor. AOSP :526 default body, verbatim. */
    public int captureBurstRequests(List<CaptureRequest> requests, Executor executor,
            CaptureCallback listener) throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    /** API 28. setRepeatingRequest() with an Executor. AOSP :641 default body. */
    public int setSingleRepeatingRequest(CaptureRequest request, Executor executor,
            CaptureCallback listener) throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    /** API 28. setRepeatingBurst() with an Executor. AOSP :763 default body. */
    public int setRepeatingBurstRequests(List<CaptureRequest> requests, Executor executor,
            CaptureCallback listener) throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    /** API 26. Swap in a new set of shared Surfaces on an already-configured
     *  output. AOSP :892 default body. */
    public void updateOutputConfiguration(
            android.hardware.camera2.params.OutputConfiguration config)
            throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    /** API 30. Whether this output Surface can keep processing after the camera
     *  device is released. AOSP :992 default body. */
    public boolean supportsOfflineProcessing(Surface surface) {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    /** API 30. Move in-flight requests for these Surfaces to an offline session
     *  so the camera device can be closed. AOSP :964 default body. */
    public CameraOfflineSession switchToOffline(Collection<Surface> offlineSurfaces,
            Executor executor, CameraOfflineSession.CameraOfflineSessionCallback listener)
            throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    public static abstract class StateCallback {
        public abstract void onConfigured(CameraCaptureSession session);
        public abstract void onConfigureFailed(CameraCaptureSession session);
        public void onReady(CameraCaptureSession session) {}
        public void onActive(CameraCaptureSession session) {}
        public void onClosed(CameraCaptureSession session) {}
        public void onSurfacePrepared(CameraCaptureSession session,
                android.view.Surface surface) {}
        /** API 23. The camera has finished every queued request and has no more
         *  work; apps use it to submit the next burst without a gap. AOSP
         *  declares it with an empty default (CameraCaptureSession.java:1125),
         *  so an app that overrides it simply never hears from us -- the same
         *  thing it observes when a real session is idle-but-not-empty. */
        public void onCaptureQueueEmpty(CameraCaptureSession session) {}
    }

    public static abstract class CaptureCallback {
        public void onCaptureStarted(CameraCaptureSession session,
                CaptureRequest request, long timestamp, long frameNumber) {}
        public void onCaptureProgressed(CameraCaptureSession session,
                CaptureRequest request, CaptureResult partialResult) {}
        public void onCaptureCompleted(CameraCaptureSession session,
                CaptureRequest request, TotalCaptureResult result) {}
        public void onCaptureFailed(CameraCaptureSession session,
                CaptureRequest request, CaptureFailure failure) {}
        public void onCaptureSequenceCompleted(CameraCaptureSession session,
                int sequenceId, long frameNumber) {}
        public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {}
        /** API 24. One output Surface lost its buffer for this request while
         *  the others succeeded -- a partial failure that onCaptureFailed does
         *  not cover. Empty default in AOSP
         *  (CameraCaptureSession.java:1503). */
        public void onCaptureBufferLost(CameraCaptureSession session,
                CaptureRequest request, android.view.Surface target, long frameNumber) {}
        /** API 34. Fires when the sensor begins READING OUT the frame, as
         *  opposed to onCaptureStarted's exposure-start. Apps that align
         *  capture with display timing use it. Empty default in AOSP
         *  (CameraCaptureSession.java:1283). */
        public void onReadoutStarted(CameraCaptureSession session,
                CaptureRequest request, long timestamp, long frameNumber) {}
    }
}
