package android.accessibilityservice;

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

/**
 * Handle an accessibility service uses to observe the system's "accessibility
 * button" (the shortcut in the navigation bar / gesture area).
 *
 * Mac.apk draws no navigation bar, so the button does not exist and
 * isAccessibilityButtonAvailable() is false -- which is a state real Android
 * also reports (on devices without the shortcut, and whenever another service
 * has claimed it), so callback-driven code takes a path it already handles.
 * Registrations are still remembered so register/unregister is symmetric and a
 * service that unregisters in onDestroy does not leak or throw.
 *
 * Source: frameworks/base/core/java/android/accessibilityservice/AccessibilityButtonController.java
 */
public final class AccessibilityButtonController {

    private final List<AccessibilityButtonCallback> mCallbacks =
            new ArrayList<AccessibilityButtonCallback>();

    // Instances come from AccessibilityService.getAccessibilityButtonController().
    AccessibilityButtonController() { }

    /**
     * False on Mac.apk: there is no navigation-bar shortcut to press. AOSP
     * returns false in exactly the same situations (no shortcut configured, or
     * the service is not connected), so this is a state apps already handle.
     */
    public boolean isAccessibilityButtonAvailable() { return false; }

    public void registerAccessibilityButtonCallback(AccessibilityButtonCallback callback) {
        registerAccessibilityButtonCallback(callback, null);
    }

    public void registerAccessibilityButtonCallback(AccessibilityButtonCallback callback,
            Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }
        synchronized (mCallbacks) {
            if (!mCallbacks.contains(callback)) mCallbacks.add(callback);
        }
    }

    public void unregisterAccessibilityButtonCallback(AccessibilityButtonCallback callback) {
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    /** AOSP declares this abstract-but-empty: a service overrides only the
     *  callback it cares about. Both bodies stay empty here for the same
     *  reason, NOT because the class is a stub. */
    public abstract static class AccessibilityButtonCallback {
        public AccessibilityButtonCallback() { }

        public void onClicked(AccessibilityButtonController controller) { }

        public void onAvailabilityChanged(AccessibilityButtonController controller,
                boolean available) { }
    }
}
