// android.window.SystemOnBackInvokedCallbacks (API 36) -- ready-made
// OnBackInvokedCallback implementations that reproduce the two default back
// behaviours, so an app that registers its own callback can still delegate to
// the system one. AOSP:
// frameworks/base/core/java/android/window/SystemOnBackInvokedCallbacks.java
//
// These are REAL: both simply call the Activity method they are named after,
// which is exactly what AOSP's implementations do. There is nothing
// platform-specific to bridge.
package android.window;

import android.app.Activity;

import java.util.Objects;

public final class SystemOnBackInvokedCallbacks {
    private SystemOnBackInvokedCallbacks() {}

    /**
     * A callback that finishes the given activity and removes its task, i.e.
     * the default back behaviour of a task's root activity.
     */
    public static OnBackInvokedCallback finishAndRemoveTaskCallback(Activity activity) {
        Objects.requireNonNull(activity);
        return new OnBackInvokedCallback() {
            @Override public void onBackInvoked() {
                activity.finishAndRemoveTask();
            }
        };
    }

    /**
     * A callback that moves the activity's task to the back rather than
     * finishing it -- the behaviour a launcher-style root activity wants.
     * AOSP passes nonRoot = false.
     */
    public static OnBackInvokedCallback moveTaskToBackCallback(Activity activity) {
        Objects.requireNonNull(activity);
        return new OnBackInvokedCallback() {
            @Override public void onBackInvoked() {
                activity.moveTaskToBack(false);
            }
        };
    }
}
