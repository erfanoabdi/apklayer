// android.view.HapticFeedbackConstants -- the effect ids View.performHaptic
// Feedback(int) takes. Values transcribed from AOSP's published API surface
// (frameworks/base core/java/android/view/HapticFeedbackConstants.java,
// android16-release). These are compile-time constants, so an app that names
// one has it inlined -- but a library that reflects over the class, or code
// that reads a value through a non-constant path, needs the class to exist.
// Mac.apk performs no haptics (macOS exposes no equivalent for a trackpad-less
// Mac and View.performHapticFeedback honestly returns false), so nothing here
// is consumed; the ids must still be the platform's.
package android.view;

public class HapticFeedbackConstants {
    public static final int NO_HAPTICS = -1;
    public static final int LONG_PRESS = 0;
    public static final int VIRTUAL_KEY = 1;
    public static final int KEYBOARD_TAP = 3;
    public static final int KEYBOARD_PRESS = KEYBOARD_TAP;
    public static final int CLOCK_TICK = 4;
    public static final int CONTEXT_CLICK = 6;
    public static final int KEYBOARD_RELEASE = 7;
    public static final int VIRTUAL_KEY_RELEASE = 8;
    public static final int TEXT_HANDLE_MOVE = 9;
    public static final int GESTURE_START = 12;
    public static final int GESTURE_END = 13;
    public static final int CONFIRM = 16;
    public static final int REJECT = 17;
    public static final int TOGGLE_ON = 21;
    public static final int TOGGLE_OFF = 22;
    public static final int GESTURE_THRESHOLD_ACTIVATE = 23;
    public static final int GESTURE_THRESHOLD_DEACTIVATE = 24;
    public static final int DRAG_START = 25;
    public static final int SEGMENT_TICK = 26;
    public static final int SEGMENT_FREQUENT_TICK = 27;

    /** Flag for View.performHapticFeedback(int, int): play the effect even when
     *  the view has haptic feedback disabled. */
    public static final int FLAG_IGNORE_VIEW_SETTING = 0x0001;
    /** @deprecated at API 33 -- the global setting can no longer be bypassed. */
    @Deprecated
    public static final int FLAG_IGNORE_GLOBAL_SETTING = 0x0002;

    private HapticFeedbackConstants() {}
}
