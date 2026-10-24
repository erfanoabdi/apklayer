// android.hardware.camera2.CameraExtensionSession (API 31; the progress and
// postview callbacks are API 34).
//
// A capture session running through a vendor camera extension (HDR, night mode,
// bokeh, face retouch). Abstract in AOSP, exactly as here: an app only ever
// receives one from CameraDevice.createExtensionSession, and it implements the
// nested StateCallback/ExtensionCaptureCallback rather than the session itself.
//
// Mac.apk exposes no extensions (see CameraExtensionCharacteristics), so no
// instance is ever created -- but the type and the two callback bases must
// resolve, because an app that subclasses ExtensionCaptureCallback fails to
// LINK without them, whether or not it ever opens a session. AOSP keeps the
// non-abstract methods here for backward compatibility and they throw
// UnsupportedOperationException in the base class; that is preserved verbatim,
// so a subclass that forgets to override behaves the same on both platforms.
//
// Mirrored from
// frameworks/base/core/java/android/hardware/camera2/CameraExtensionSession.java
// (aosp-mirror/platform_frameworks_base, main): class L46, ExtensionCaptureCallback
// L62-266 (onCaptureStarted L85, onCaptureProcessStarted L107, onCaptureFailed
// L132 and the reason-carrying overload L159, onCaptureSequenceCompleted L182,
// onCaptureSequenceAborted L204, onCaptureResultAvailable L230,
// onCaptureProcessProgressed L265), StateCallback L275-318, getDevice L327,
// capture L385, setRepeatingRequest L439, stopRepeating L459, StillCaptureLatency
// L468-516, getRealtimeStillCaptureLatency L533, close L563.
package android.hardware.camera2;

import java.util.concurrent.Executor;

public abstract class CameraExtensionSession implements AutoCloseable {

    public CameraExtensionSession() { }

    /** Per-request progress for an extension capture. Every method has an empty
     *  default so an app overrides only what it needs, matching AOSP. */
    public abstract static class ExtensionCaptureCallback {
        public void onCaptureStarted(CameraExtensionSession session, CaptureRequest request,
                long timestamp) { }

        public void onCaptureProcessStarted(CameraExtensionSession session,
                CaptureRequest request) { }

        public void onCaptureFailed(CameraExtensionSession session, CaptureRequest request) { }

        /** API 34. AOSP added the reason-carrying overload alongside the
         *  original; both exist, and a subclass may override either. */
        public void onCaptureFailed(CameraExtensionSession session, CaptureRequest request,
                int failure) { }

        public void onCaptureSequenceCompleted(CameraExtensionSession session, int sequenceId) { }

        public void onCaptureSequenceAborted(CameraExtensionSession session, int sequenceId) { }

        /** API 34. */
        public void onCaptureResultAvailable(CameraExtensionSession session,
                CaptureRequest request, TotalCaptureResult result) { }

        /** API 34. Progress is 0..100. */
        public void onCaptureProcessProgressed(CameraExtensionSession session,
                CaptureRequest request, int progress) { }
    }

    /** onConfigured/onConfigureFailed are abstract in AOSP -- an app MUST
     *  handle both -- and onClosed has an empty default. */
    public abstract static class StateCallback {
        public abstract void onConfigured(CameraExtensionSession session);

        public abstract void onConfigureFailed(CameraExtensionSession session);

        public void onClosed(CameraExtensionSession session) { }
    }

    /** The still-capture latency estimate an extension reports, in
     *  milliseconds. A plain immutable value type in AOSP, reproduced in full.
     *  API 34. */
    public static final class StillCaptureLatency {
        private final long mCaptureLatency;
        private final long mProcessingLatency;

        public StillCaptureLatency(long captureLatency, long processingLatency) {
            mCaptureLatency = captureLatency;
            mProcessingLatency = processingLatency;
        }

        public long getCaptureLatency() {
            return mCaptureLatency;
        }

        public long getProcessingLatency() {
            return mProcessingLatency;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof StillCaptureLatency)) {
                return false;
            }
            StillCaptureLatency other = (StillCaptureLatency) o;
            return mCaptureLatency == other.mCaptureLatency
                    && mProcessingLatency == other.mProcessingLatency;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(mCaptureLatency, mProcessingLatency);
        }

        @Override
        public String toString() {
            return "StillCaptureLatency(processingLatency:" + mProcessingLatency
                    + ", captureLatency: " + mCaptureLatency + ")";
        }
    }

    // AOSP declares the methods below non-abstract and throws
    // UnsupportedOperationException from the base class ("Subclasses must
    // override this method"), because CameraExtensionSession shipped before the
    // methods did and an existing subclass must keep compiling. Preserving the
    // throw rather than returning a benign value keeps a subclass that misses an
    // override failing here exactly as it fails on a real device.

    public CameraDevice getDevice() {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    public int capture(CaptureRequest request, Executor executor,
            ExtensionCaptureCallback listener) throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    public int setRepeatingRequest(CaptureRequest request, Executor executor,
            ExtensionCaptureCallback listener) throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    public void stopRepeating() throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    /** API 34. */
    public StillCaptureLatency getRealtimeStillCaptureLatency() throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    @Override
    public void close() throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }
}
