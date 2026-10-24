package android.app;

import android.graphics.Rect;

/**
 * AOSP's per-window configuration, hung off {@link android.content.res.Configuration}.
 *
 * WHY THIS EXISTS. It is an @hide class, so no app can reference it by name at compile
 * time, and for a long time we did not need it. But AndroidX Window reaches it by
 * REFLECTION to answer "how big is the current window", and reflection does not care
 * that the class is hidden. Measured on Signal 172401 (org.thoughtcrime.securesms), which
 * threw four times during boot:
 *
 *   java.lang.NoSuchFieldException: windowConfiguration
 *     at androidx.window.layout.util.BoundsHelperApi29Impl.currentWindowBounds
 *     at androidx.window.layout.util.BoundsHelperApi28Impl.currentWindowBounds
 *     at androidx.window.layout.util.WindowMetricsCompatHelperBaseImpl.currentWindowMetrics
 *
 * The exact reflective shape was read out of the app's OWN converted bytecode rather than
 * guessed from an AndroidX version (javap -p -c on BoundsHelperApi28Impl): it calls
 * Class.getDeclaredField("windowConfiguration"), setAccessible(true), then
 * getDeclaredMethod("getAppBounds") / getDeclaredMethod("getBounds") on the returned
 * object's own class. Two consequences bind the shape of this file:
 *   - the field must be DECLARED on Configuration itself (getDeclaredField does not
 *     search supertypes), and
 *   - getBounds and getAppBounds must be DECLARED here, not inherited.
 *
 * When the lookup fails AndroidX catches it and falls back to
 * WindowManager.getDefaultDisplay() + Display.getRealSize(), i.e. the whole DISPLAY rather
 * than the window. That happens to be nearly right for us today because our window fills
 * the content area, which is why this was not fatal. It is still the wrong answer the
 * moment a window is not display-sized, so we answer it properly.
 *
 * Mirrors frameworks/base/core/java/android/app/WindowConfiguration.java (android13-release):
 * field names, the WINDOWING_MODE_* / ACTIVITY_TYPE_* constant values, and the getter and
 * setter bodies, including setBounds(null) emptying rather than nulling mBounds.
 */
public class WindowConfiguration implements Comparable<WindowConfiguration> {

    // AOSP values, verbatim. Apps that reflect on getWindowingMode() compare against
    // these literals, so the numbers are load-bearing and must not be renumbered.
    public static final int WINDOWING_MODE_UNDEFINED = 0;
    public static final int WINDOWING_MODE_FULLSCREEN = 1;
    public static final int WINDOWING_MODE_PINNED = 2;
    public static final int WINDOWING_MODE_SPLIT_SCREEN_PRIMARY = 3;
    public static final int WINDOWING_MODE_SPLIT_SCREEN_SECONDARY = 4;
    public static final int WINDOWING_MODE_FREEFORM = 5;
    public static final int WINDOWING_MODE_MULTI_WINDOW = 6;

    public static final int ACTIVITY_TYPE_UNDEFINED = 0;
    public static final int ACTIVITY_TYPE_STANDARD = 1;
    public static final int ACTIVITY_TYPE_HOME = 2;
    public static final int ACTIVITY_TYPE_RECENTS = 3;
    public static final int ACTIVITY_TYPE_ASSISTANT = 4;
    public static final int ACTIVITY_TYPE_DREAM = 5;

    /** AOSP: `private final Rect mBounds = new Rect()`. Never null. */
    private final Rect mBounds = new Rect();
    /** AOSP: `private Rect mAppBounds` -- nullable there, and AOSP's getAppBounds() can
     *  legitimately return null. We keep the nullable field to match, but Boot always
     *  populates it, because AndroidX's API-28 path invokes getAppBounds() FIRST and a
     *  null there sends it straight back to the display-sized fallback this class exists
     *  to avoid. */
    private Rect mAppBounds;
    private final Rect mMaxBounds = new Rect();

    private int mWindowingMode = WINDOWING_MODE_UNDEFINED;
    private int mActivityType = ACTIVITY_TYPE_UNDEFINED;

    public WindowConfiguration() {}

    public WindowConfiguration(WindowConfiguration configuration) { setTo(configuration); }

    public Rect getBounds() { return mBounds; }

    public Rect getAppBounds() { return mAppBounds; }

    public Rect getMaxBounds() { return mMaxBounds; }

    public void setBounds(Rect rect) {
        if (rect == null) {
            mBounds.setEmpty();
            return;
        }
        mBounds.set(rect);
    }

    public void setAppBounds(Rect rect) {
        if (rect == null) {
            mAppBounds = null;
            return;
        }
        setAppBounds(rect.left, rect.top, rect.right, rect.bottom);
    }

    public void setAppBounds(int left, int top, int right, int bottom) {
        if (mAppBounds == null) mAppBounds = new Rect();
        mAppBounds.set(left, top, right, bottom);
    }

    public void setMaxBounds(Rect rect) {
        if (rect == null) {
            mMaxBounds.setEmpty();
            return;
        }
        mMaxBounds.set(rect);
    }

    public int getWindowingMode() { return mWindowingMode; }

    public void setWindowingMode(int windowingMode) { mWindowingMode = windowingMode; }

    public int getActivityType() { return mActivityType; }

    public void setActivityType(int activityType) { mActivityType = activityType; }

    public void setTo(WindowConfiguration other) {
        if (other == null) return;
        setBounds(other.mBounds);
        setAppBounds(other.mAppBounds);
        setMaxBounds(other.mMaxBounds);
        mWindowingMode = other.mWindowingMode;
        mActivityType = other.mActivityType;
    }

    public void setToDefaults() {
        setBounds(null);
        setAppBounds(null);
        setMaxBounds(null);
        mWindowingMode = WINDOWING_MODE_UNDEFINED;
        mActivityType = ACTIVITY_TYPE_UNDEFINED;
    }

    /** AOSP orders by windowing mode then activity type then each bound edge. We only
     *  need a stable total order consistent with equals, which Configuration.compareTo
     *  and any TreeMap of configurations rely on. */
    @Override
    public int compareTo(WindowConfiguration that) {
        int n = mWindowingMode - that.mWindowingMode;
        if (n != 0) return n;
        n = mActivityType - that.mActivityType;
        if (n != 0) return n;
        n = compareBounds(mBounds, that.mBounds);
        if (n != 0) return n;
        n = compareBounds(mAppBounds, that.mAppBounds);
        if (n != 0) return n;
        return compareBounds(mMaxBounds, that.mMaxBounds);
    }

    private static int compareBounds(Rect a, Rect b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        int n = a.left - b.left;
        if (n != 0) return n;
        n = a.top - b.top;
        if (n != 0) return n;
        n = a.right - b.right;
        if (n != 0) return n;
        return a.bottom - b.bottom;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) return true;
        if (!(that instanceof WindowConfiguration)) return false;
        return compareTo((WindowConfiguration) that) == 0;
    }

    @Override
    public int hashCode() {
        int result = mBounds.hashCode();
        if (mAppBounds != null) result = 31 * result + mAppBounds.hashCode();
        result = 31 * result + mMaxBounds.hashCode();
        result = 31 * result + mWindowingMode;
        result = 31 * result + mActivityType;
        return result;
    }

    @Override
    public String toString() {
        return "{bounds=" + mBounds + " mAppBounds=" + mAppBounds
                + " mMaxBounds=" + mMaxBounds
                + " mWindowingMode=" + mWindowingMode
                + " mActivityType=" + mActivityType + "}";
    }
}
