// android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession (API 21).
//
// The session type CameraDevice.createConstrainedHighSpeedCaptureSession hands
// back for slow-motion recording. Abstract in AOSP and abstract here: apps
// receive one, they never construct one, and they call the single method it
// adds over CameraCaptureSession to batch a high-speed request list.
//
// Mac.apk never creates a session at all, so no instance exists -- but the type
// must resolve, because an app that CASTS a CameraCaptureSession to this in its
// StateCallback fails to link the containing method otherwise, even on the
// branch it never takes.
//
// Declaring createHighSpeedRequestList abstract rather than giving it a stub
// return is deliberate: it is abstract in AOSP, so any concrete subclass an app
// writes (test doubles do this) is forced to implement it in both places, and no
// caller can ever receive a fabricated empty request list that would silently
// record nothing.
//
// Mirrored from
// frameworks/base/core/java/android/hardware/camera2/CameraConstrainedHighSpeedCaptureSession.java
// (aosp-mirror/platform_frameworks_base, main): class declaration L68,
// createHighSpeedRequestList L123-125.
package android.hardware.camera2;

import java.util.List;

public abstract class CameraConstrainedHighSpeedCaptureSession extends CameraCaptureSession {

    /** Build the unmodifiable, batched request list a constrained high-speed
     *  session requires. AOSP throws IllegalArgumentException when the request's
     *  output surfaces or target FPS range do not meet the high-speed
     *  requirements, IllegalStateException when the device is closed, and
     *  CameraAccessException when it is disconnected. */
    public abstract List<CaptureRequest> createHighSpeedRequestList(CaptureRequest request)
            throws CameraAccessException;
}
