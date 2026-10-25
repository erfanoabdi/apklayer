// android.hardware.camera2.CameraOfflineSession -- the session an app is handed
// after CameraCaptureSession.switchToOffline() moves in-flight requests off the
// camera device so the device itself can be released. API 30.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/
// CameraOfflineSession.java (aosp-mirror, android16-release): an abstract
// subclass of CameraCaptureSession plus the abstract
// CameraOfflineSessionCallback the caller implements.
//
// Abstract with no concrete subclass here, exactly as in AOSP (where
// CameraOfflineSessionImpl is internal). There is nothing to instantiate:
// CameraCaptureSessionImpl.supportsOfflineProcessing() answers false for every
// Surface, so switchToOffline reports onSwitchFailed and no offline session is
// ever produced -- the same outcome as a device whose HAL lacks the
// OFFLINE_PROCESSING capability.
package android.hardware.camera2;

public abstract class CameraOfflineSession extends CameraCaptureSession {

    public CameraOfflineSession() {}

    public abstract static class CameraOfflineSessionCallback {

        /** AOSP :46 -- the generic "something went wrong" reason. */
        public static final int STATUS_INTERNAL_ERROR = 0;

        public CameraOfflineSessionCallback() {}

        /** The offline switch succeeded and the session is now processing. */
        public abstract void onReady(CameraOfflineSession session);

        /** The switch failed; the session is unusable and already closed. */
        public abstract void onSwitchFailed(CameraOfflineSession session);

        /** Every offline request finished; the app may close the session. */
        public abstract void onIdle(CameraOfflineSession session);

        /** An unrecoverable error; the status is STATUS_INTERNAL_ERROR. */
        public abstract void onError(CameraOfflineSession session, int status);

        /** The session released its resources. */
        public abstract void onClosed(CameraOfflineSession session);
    }
}
