// android.view.WindowMetrics -- API 30 snapshot of a window's size + insets.
// Was absent. WindowManager.getCurrentWindowMetrics()/getMaximumWindowMetrics()
// and the Jetpack WindowManager library (used by Compose's window-size-class and
// adaptive-layout paths) construct/read these to make responsive-layout
// decisions; a missing class NoClassDefFound'd at those call sites.
// Mirrors frameworks/base/core/java/android/view/WindowMetrics.java.
package android.view;

import android.graphics.Rect;

public final class WindowMetrics {
    private final Rect mBounds;
    private final WindowInsets mWindowInsets;
    private final float mDensity;

    public WindowMetrics(Rect bounds, WindowInsets windowInsets) {
        this(bounds, windowInsets, 1.0f);
    }

    public WindowMetrics(Rect bounds, WindowInsets windowInsets, float density) {
        mBounds = bounds;
        mWindowInsets = windowInsets;
        mDensity = density;
    }

    // AOSP: the full window bounds in window-coordinate space (0,0,w,h).
    public Rect getBounds() { return mBounds; }

    public WindowInsets getWindowInsets() { return mWindowInsets; }

    // API 34 addition; logical density of the window.
    public float getDensity() { return mDensity; }

    @Override public String toString() {
        return WindowMetrics.class.getSimpleName() + ":{"
                + "bounds=" + mBounds
                + ", windowInsets=" + mWindowInsets
                + ", density=" + mDensity
                + "}";
    }
}
