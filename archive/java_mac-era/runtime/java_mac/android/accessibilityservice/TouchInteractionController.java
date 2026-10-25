package android.accessibilityservice;

import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * API 31+ handle that lets an accessibility service take over touch
 * interpretation on one display while touch exploration is enabled.
 *
 * Mac.apk never enables touch exploration (there is no accessibility service
 * bound to enable it), so the controller is permanently in STATE_CLEAR and the
 * request* / perform* calls have nothing to hand off to. Registration is
 * tracked so register/unregister stays symmetric.
 *
 * Constant values transcribed verbatim from /tmp/aosp_api/android16.txt
 * (signature format 2.0): STATE_CLEAR=0, STATE_TOUCH_INTERACTING=1,
 * STATE_TOUCH_EXPLORING=2, STATE_DRAGGING=3, STATE_DELEGATING=4.
 *
 * Source: frameworks/base/core/java/android/accessibilityservice/TouchInteractionController.java
 */
public final class TouchInteractionController {

    public static final int STATE_CLEAR = 0;
    public static final int STATE_TOUCH_INTERACTING = 1;
    public static final int STATE_TOUCH_EXPLORING = 2;
    public static final int STATE_DRAGGING = 3;
    public static final int STATE_DELEGATING = 4;

    private final int mDisplayId;
    private final List<Callback> mCallbacks = new ArrayList<Callback>();

    TouchInteractionController(int displayId) { mDisplayId = displayId; }

    public int getDisplayId() { return mDisplayId; }

    /** AOSP reports the display's advertised max pointer count. Mac.apk routes
     *  a single mouse cursor through TouchDispatcher, so one pointer is the
     *  honest answer rather than a fabricated multitouch capability. */
    public int getMaxPointerCount() { return 1; }

    public int getState() { return STATE_CLEAR; }

    public void registerCallback(Executor executor, Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }
        synchronized (mCallbacks) {
            if (!mCallbacks.contains(callback)) mCallbacks.add(callback);
        }
    }

    public boolean unregisterCallback(Callback callback) {
        synchronized (mCallbacks) {
            return mCallbacks.remove(callback);
        }
    }

    public void unregisterAllCallbacks() {
        synchronized (mCallbacks) {
            mCallbacks.clear();
        }
    }

    /** AOSP throws IllegalStateException when the controller is not in a state
     *  that allows the transition. Mac.apk is always STATE_CLEAR and no service
     *  is ever bound, so these are no-ops rather than throws: a service that
     *  calls them is running code that on a device would only run after a state
     *  callback we never deliver. */
    public void requestTouchExploration() { }

    public void requestDragging(int pointerId) { }

    public void requestDelegating() { }

    public void performClick() { }

    public void performLongClickAndStartDrag() { }

    public static String stateToString(int state) {
        switch (state) {
            case STATE_CLEAR: return "STATE_CLEAR";
            case STATE_TOUCH_INTERACTING: return "STATE_TOUCH_INTERACTING";
            case STATE_TOUCH_EXPLORING: return "STATE_TOUCH_EXPLORING";
            case STATE_DRAGGING: return "STATE_DRAGGING";
            case STATE_DELEGATING: return "STATE_DELEGATING";
            default: return "Unknown state: " + state;
        }
    }

    public interface Callback {
        void onMotionEvent(MotionEvent event);

        void onStateChanged(int state);
    }
}
