// android.hardware.SensorPrivacyManager -- the user-facing camera and
// microphone KILL SWITCHES (Android 12's privacy toggles). The public surface
// is only "does this device have such a toggle"; actually reading or flipping
// one is @SystemApi and needs a signature permission, so an ordinary app can
// only ask about support.
//
// Mirrors frameworks/base/core/java/android/hardware/SensorPrivacyManager.java
// (android16-release) -- the public half of it, which is what current.txt
// publishes: supportsSensorToggle(int), supportsSensorToggle(int, int), the
// two TOGGLE_TYPE_* constants and the Sensors holder.
//
// WHAT THIS DEVICE REPORTS. false, for both toggle types and both sensors,
// which is the answer AOSP gives on any device whose hardware and OS do not
// implement a privacy toggle -- the majority of Android devices, including
// every one that predates Android 12. macOS has nothing that maps onto it: the
// camera indicator light and the TCC permission model are per-APP consent, not
// a global user-flipped mute that an app can query. Reporting true would tell
// an app there is a switch it can explain to the user and point them at, and
// there is not.
//
// This is NOT the same question as "is the camera available" -- that is
// CameraManager's, and it answers from the real device.
package android.hardware;

public final class SensorPrivacyManager {

    /** A software toggle in Settings. */
    public static final int TOGGLE_TYPE_SOFTWARE = 1;
    /** A physical switch on the device. */
    public static final int TOGGLE_TYPE_HARDWARE = 2;

    /** AOSP's sensor identifiers for the toggles. Note these are NOT
     *  Sensor.TYPE_* values and not CameraBridge facings -- a separate,
     *  two-valued namespace. */
    public static class Sensors {
        public static final int MICROPHONE = 1;
        public static final int CAMERA = 2;
    }

    /** AOSP's constructor is package-private (an app gets one from
     *  Context.getSystemService). Kept package-private. */
    /* package */ SensorPrivacyManager() { }

    public boolean supportsSensorToggle(int sensor) {
        return false;
    }

    public boolean supportsSensorToggle(int toggleType, int sensor) {
        return false;
    }
}
