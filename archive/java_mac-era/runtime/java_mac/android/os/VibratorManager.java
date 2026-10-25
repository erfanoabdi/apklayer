// android.os.VibratorManager -- the API-31+ multi-actuator entry point
// (getSystemService(VIBRATOR_MANAGER_SERVICE)). Mac.apk has no vibrators, so
// getVibratorIds() is empty and getDefaultVibrator() returns an inert no-op
// Vibrator. AOSP marks this abstract; we make it concrete so the service
// lookup can return a usable instance.
//
// AOSP ref: frameworks/base/core/java/android/os/VibratorManager.java
package android.os;

public class VibratorManager {
    private final Vibrator defaultVibrator = new Vibrator();

    public VibratorManager() {}

    public int[] getVibratorIds() { return new int[0]; }

    public Vibrator getVibrator(int vibratorId) { return defaultVibrator; }

    public Vibrator getDefaultVibrator() { return defaultVibrator; }

    public void vibrate(CombinedVibration effect) {}
    public void vibrate(CombinedVibration effect, VibrationAttributes attributes) {}

    public void cancel() {}
}
