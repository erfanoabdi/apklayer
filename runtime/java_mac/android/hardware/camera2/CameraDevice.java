// android.hardware.camera2.CameraDevice -- an open connection to a
// single camera. Abstract in AOSP; apps interact with it only through
// the StateCallback they pass to CameraManager.openCamera and the
// CaptureRequest.Builder / createCaptureSession methods. Mac.apk has no
// camera, so CameraManager.openCamera throws IllegalArgumentException
// synchronously before StateCallback is ever touched (matching AOSP's
// real openCameraImpl -- see the comment there) and a CameraDevice is
// never actually handed out -- but the type + nested StateCallback must
// exist for apps to compile.
package android.hardware.camera2;

import android.os.Handler;
import android.view.Surface;
import java.util.List;
import java.util.concurrent.Executor;

public abstract class CameraDevice implements AutoCloseable {
    public static final int TEMPLATE_PREVIEW = 1;
    public static final int TEMPLATE_STILL_CAPTURE = 2;
    public static final int TEMPLATE_RECORD = 3;
    public static final int TEMPLATE_VIDEO_SNAPSHOT = 4;
    public static final int TEMPLATE_ZERO_SHUTTER_LAG = 5;
    public static final int TEMPLATE_MANUAL = 6;

    // AOSP CameraDevice.java:210-232 -- the audio-restriction modes accepted by
    // setCameraAudioRestriction. Compile-time int constants, so javac inlines
    // them into the caller and a missing one cannot be observed at runtime; they
    // are here because the setter's argument validation is defined in terms of
    // them.
    /** No system audio is muted while this device is open. */
    public static final int AUDIO_RESTRICTION_NONE = 0;
    /** System vibration is muted; sounds still play. */
    public static final int AUDIO_RESTRICTION_VIBRATION = 1;
    /** Both system vibration and system sounds are muted. */
    public static final int AUDIO_RESTRICTION_VIBRATION_SOUND = 3;

    public abstract String getId();
    public abstract CaptureRequest.Builder createCaptureRequest(int templateType)
            throws CameraAccessException;
    public abstract void createCaptureSession(List<Surface> outputs,
            CameraCaptureSession.StateCallback callback, Handler handler)
            throws CameraAccessException;
    @Override public abstract void close();

    public static abstract class StateCallback {
        public static final int ERROR_CAMERA_IN_USE = 1;
        public static final int ERROR_MAX_CAMERAS_IN_USE = 2;
        public static final int ERROR_CAMERA_DISABLED = 3;
        public static final int ERROR_CAMERA_DEVICE = 4;
        public static final int ERROR_CAMERA_SERVICE = 5;

        public abstract void onOpened(CameraDevice camera);
        public abstract void onDisconnected(CameraDevice camera);
        public abstract void onError(CameraDevice camera, int error);
        public void onClosed(CameraDevice camera) {}
    }

    /**
     * API 28. Configure a session from a SessionConfiguration, which carries
     * its own Executor and StateCallback.
     *
     * Abstract in AOSP. Concrete here only because this class predates the
     * concrete CameraDeviceImpl and something may already hold a subclass;
     * making it abstract now would be a source-breaking change for no gain,
     * since CameraDeviceImpl -- the only instance an app can obtain -- overrides
     * it with the real implementation.
     *
     * The body used to be EMPTY, which is the one answer a camera API must
     * never give: the app configures a session, hears neither onConfigured nor
     * onConfigureFailed, and waits forever with nothing in the log. Throwing
     * names the gap at the moment it is hit.
     */
    public void createCaptureSession(android.hardware.camera2.params.SessionConfiguration config)
            throws android.hardware.camera2.CameraAccessException {
        throw new UnsupportedOperationException(
                "createCaptureSession(SessionConfiguration) is not implemented by "
                        + getClass().getName());
    }

    /**
     * API 24. Configure a session from OutputConfigurations rather than bare
     * Surfaces. Abstract in AOSP; concrete here for the same reason as the
     * overload above, and overridden by CameraDeviceImpl.
     */
    public void createCaptureSessionByOutputConfigurations(
            java.util.List<android.hardware.camera2.params.OutputConfiguration> outputConfigurations,
            CameraCaptureSession.StateCallback callback, Handler handler)
            throws android.hardware.camera2.CameraAccessException {
        throw new UnsupportedOperationException(
                "createCaptureSessionByOutputConfigurations is not implemented by "
                        + getClass().getName());
    }

    // ---------------------------------------------------------------------
    // The rest of the AOSP CameraDevice surface, mirrored from
    // frameworks/base/core/java/android/hardware/camera2/CameraDevice.java
    // (aosp-mirror/platform_frameworks_base, android16-release).
    //
    // AOSP declares several of these ABSTRACT. They are concrete here for the
    // reason already stated on createCaptureSession(SessionConfiguration)
    // above: this class predates the concrete CameraDeviceImpl and something
    // may already hold a subclass. Each carries AOSP's own default-body throw
    // where AOSP has one, and an equivalent throw naming the subclass where
    // AOSP is abstract -- never an empty body, because a camera API that
    // silently does nothing leaves the app waiting on a callback that will not
    // come. CameraDeviceImpl, the only instance an app can obtain, overrides
    // the ones it can answer.
    // ---------------------------------------------------------------------

    /** API 28. A capture request that can address individual PHYSICAL cameras
     *  of a logical multi-camera. AOSP concrete default, which throws
     *  UnsupportedOperationException("Subclasses must override this method"). */
    public CaptureRequest.Builder createCaptureRequest(int templateType,
            java.util.Set<String> physicalCameraIdSet) throws CameraAccessException {
        throw new UnsupportedOperationException(
                "createCaptureRequest(int, Set) is not implemented by " + getClass().getName());
    }

    /** API 23, deprecated at 30. A constrained high-speed (slow-motion) session.
     *  AOSP declares this abstract. */
    @Deprecated
    public void createConstrainedHighSpeedCaptureSession(List<Surface> outputs,
            CameraCaptureSession.StateCallback callback, Handler handler)
            throws CameraAccessException {
        throw new UnsupportedOperationException(
                "createConstrainedHighSpeedCaptureSession is not implemented by "
                        + getClass().getName());
    }

    /** API 31. A vendor-extension session (bokeh, night, HDR). AOSP
     *  CameraDevice.java concrete default: throws
     *  UnsupportedOperationException("No default implementation"). */
    public void createExtensionSession(
            android.hardware.camera2.params.ExtensionSessionConfiguration extensionConfiguration)
            throws CameraAccessException {
        throw new UnsupportedOperationException("No default implementation");
    }

    /** API 23. Seed a request from a previously captured result so the camera
     *  can REPROCESS that frame. AOSP declares this abstract. */
    public CaptureRequest.Builder createReprocessCaptureRequest(TotalCaptureResult inputResult)
            throws CameraAccessException {
        throw new UnsupportedOperationException(
                "createReprocessCaptureRequest is not implemented by " + getClass().getName());
    }

    /** API 23, deprecated at 30. A session that can also consume frames from an
     *  input Surface. AOSP declares this abstract. */
    @Deprecated
    public void createReprocessableCaptureSession(
            android.hardware.camera2.params.InputConfiguration inputConfig,
            List<Surface> outputs, CameraCaptureSession.StateCallback callback, Handler handler)
            throws CameraAccessException {
        throw new UnsupportedOperationException(
                "createReprocessableCaptureSession is not implemented by " + getClass().getName());
    }

    /** API 24, deprecated at 30. The OutputConfiguration form of the above.
     *  AOSP declares this abstract. */
    @Deprecated
    public void createReprocessableCaptureSessionByConfigurations(
            android.hardware.camera2.params.InputConfiguration inputConfig,
            List<android.hardware.camera2.params.OutputConfiguration> outputs,
            CameraCaptureSession.StateCallback callback, Handler handler)
            throws CameraAccessException {
        throw new UnsupportedOperationException(
                "createReprocessableCaptureSessionByConfigurations is not implemented by "
                        + getClass().getName());
    }

    /** API 28. Whether this SessionConfiguration would configure successfully,
     *  asked WITHOUT actually configuring. AOSP concrete default: throws
     *  UnsupportedOperationException("Subclasses must override this method"). */
    public boolean isSessionConfigurationSupported(
            android.hardware.camera2.params.SessionConfiguration sessionConfig)
            throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    /** API 30. The audio-restriction mode currently in force for this device.
     *  AOSP concrete default: throws
     *  UnsupportedOperationException("Subclasses must override this method"). */
    public int getCameraAudioRestriction() throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    /** API 30. Mute system sounds/vibration while this device is open. AOSP
     *  concrete default: throws
     *  UnsupportedOperationException("Subclasses must override this method"). */
    public void setCameraAudioRestriction(int mode) throws CameraAccessException {
        throw new UnsupportedOperationException("Subclasses must override this method");
    }

    /**
     * A limited view of a CameraDevice that answers device-specific questions
     * WITHOUT paying the latency of opening the camera. API 35 (Android 15),
     * gated behind Flags.FLAG_CAMERA_DEVICE_SETUP in AOSP.
     *
     * Obtained only from CameraManager.getCameraDeviceSetup, and only for a
     * camera for which CameraManager.isCameraDeviceSetupSupported returns true.
     * Mac.apk reports no cameras, so both of those throw and no instance is
     * ever created -- but the nested TYPE has to resolve for an app that
     * declares a field or a parameter of it.
     *
     * Abstract with no concrete subclass here, exactly as in AOSP (where
     * CameraDeviceSetupImpl is the internal implementation). That is the honest
     * shape: there is nothing a base-class implementation could return for
     * getSessionCharacteristics that would not be a fabricated capability.
     *
     * Mirrored from
     * frameworks/base/core/java/android/hardware/camera2/CameraDevice.java
     * (aosp-mirror/platform_frameworks_base, main) L1704-1911:
     * createCaptureRequest, isSessionConfigurationSupported,
     * getSessionCharacteristics, openCamera, getId, and the public constructor
     * AOSP marks @hide.
     */
    public abstract static class CameraDeviceSetup {

        public abstract CaptureRequest.Builder createCaptureRequest(int templateType)
                throws CameraAccessException;

        public abstract boolean isSessionConfigurationSupported(
                android.hardware.camera2.params.SessionConfiguration config)
                throws CameraAccessException;

        public abstract CameraCharacteristics getSessionCharacteristics(
                android.hardware.camera2.params.SessionConfiguration sessionConfig)
                throws CameraAccessException;

        public abstract void openCamera(Executor executor, StateCallback callback)
                throws CameraAccessException;

        public abstract String getId();

        public CameraDeviceSetup() {}
    }
}

