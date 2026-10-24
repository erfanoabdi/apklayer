package android.view;
/** Constants for the sound effects a View plays on click / focus navigation
 *  (a bare stub existed since v1.0.92). Mac.apk has no UI sound bank, but the
 *  constants + the direction mapper must resolve for focus-traversal code.
 *  Source: frameworks/base/core/java/android/view/SoundEffectConstants.java */
public class SoundEffectConstants {
    public static final int CLICK = 0;
    public static final int NAVIGATION_LEFT  = 1;
    public static final int NAVIGATION_UP    = 2;
    public static final int NAVIGATION_RIGHT = 3;
    public static final int NAVIGATION_DOWN  = 4;
    public static final int NAVIGATION_REPEAT_LEFT  = 5;
    public static final int NAVIGATION_REPEAT_UP    = 6;
    public static final int NAVIGATION_REPEAT_RIGHT = 7;
    public static final int NAVIGATION_REPEAT_DOWN  = 8;

    private SoundEffectConstants() {}

    /** AOSP's (misspelled, since API 1) mapper from a View.FOCUS_* direction to
     *  the matching NAVIGATION_* sound constant. */
    public static int getContantForFocusDirection(int direction) {
        switch (direction) {
            case View.FOCUS_RIGHT:               return NAVIGATION_RIGHT;
            case View.FOCUS_FORWARD:
            case View.FOCUS_DOWN:                return NAVIGATION_DOWN;
            case View.FOCUS_LEFT:                return NAVIGATION_LEFT;
            case View.FOCUS_BACKWARD:
            case View.FOCUS_UP:                  return NAVIGATION_UP;
            default:                             return CLICK;
        }
    }

    /** AOSP SoundEffectConstants.getConstantForFocusDirection(int, boolean)
     *  (API 34): the correctly-spelled replacement for the mapper above, plus
     *  the REPEAT variants used while a focus key is held down.
     *  Source: frameworks/base core/java/android/view/SoundEffectConstants.java
     *  (android16-release). AOSP throws IllegalArgumentException on an
     *  unrecognised direction here rather than falling back to CLICK -- the old
     *  misspelled overload keeps its lenient behaviour, this one does not. */
    public static int getConstantForFocusDirection(int direction, boolean repeating) {
        switch (direction) {
            case View.FOCUS_RIGHT:
                return repeating ? NAVIGATION_REPEAT_RIGHT : NAVIGATION_RIGHT;
            case View.FOCUS_FORWARD:
            case View.FOCUS_DOWN:
                return repeating ? NAVIGATION_REPEAT_DOWN : NAVIGATION_DOWN;
            case View.FOCUS_LEFT:
                return repeating ? NAVIGATION_REPEAT_LEFT : NAVIGATION_LEFT;
            case View.FOCUS_BACKWARD:
            case View.FOCUS_UP:
                return repeating ? NAVIGATION_REPEAT_UP : NAVIGATION_UP;
        }
        throw new IllegalArgumentException("direction must be one of "
                + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT, FOCUS_FORWARD, "
                + "FOCUS_BACKWARD}.");
    }
}
