package android.accessibilityservice;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets an accessibility service treat swipes on a fingerprint sensor as a
 * gesture source.
 *
 * There is no fingerprint sensor behind Mac.apk, so detection is unavailable --
 * the same answer AOSP gives on any device without a sensor, or while another
 * client holds it. Registration is still tracked so unregister is symmetric.
 *
 * Constant values transcribed verbatim from /tmp/aosp_api/android16.txt
 * (signature format 2.0): SWIPE_RIGHT=1 (0x1), SWIPE_LEFT=2 (0x2),
 * SWIPE_UP=4 (0x4), SWIPE_DOWN=8 (0x8).
 *
 * Source: frameworks/base/core/java/android/accessibilityservice/FingerprintGestureController.java
 */
public final class FingerprintGestureController {

    public static final int FINGERPRINT_GESTURE_SWIPE_RIGHT = 0x1;
    public static final int FINGERPRINT_GESTURE_SWIPE_LEFT = 0x2;
    public static final int FINGERPRINT_GESTURE_SWIPE_UP = 0x4;
    public static final int FINGERPRINT_GESTURE_SWIPE_DOWN = 0x8;

    private final List<FingerprintGestureCallback> mCallbacks =
            new ArrayList<FingerprintGestureCallback>();

    FingerprintGestureController() { }

    /** No sensor is reachable from Mac.apk. AOSP answers false whenever the
     *  hardware is absent or busy, so this is an ordinary device state. */
    public boolean isGestureDetectionAvailable() { return false; }

    public void registerFingerprintGestureCallback(FingerprintGestureCallback callback,
            Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }
        synchronized (mCallbacks) {
            if (!mCallbacks.contains(callback)) mCallbacks.add(callback);
        }
    }

    public void unregisterFingerprintGestureCallback(FingerprintGestureCallback callback) {
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    /** Empty bodies mirror AOSP: a service overrides only what it needs. */
    public abstract static class FingerprintGestureCallback {
        public FingerprintGestureCallback() { }

        public void onGestureDetectionAvailabilityChanged(boolean available) { }

        public void onGestureDetected(int gesture) { }
    }
}
