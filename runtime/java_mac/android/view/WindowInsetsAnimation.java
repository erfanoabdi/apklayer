// android.view.WindowInsetsAnimation -- the per-animation handle apps receive
// while a system bar or the IME is animating in or out, so content can move in
// lockstep with the keyboard instead of snapping after it settles.
//
// Was a 1-line auto-generated stub, so androidx.core's WindowInsetsAnimationCompat
// (used by every app that implements keyboard-aware layout on API 30+) hit
// NoSuchMethodError on construction and on Bounds.
//
// Mac.apk never DRIVES these animations (there is no system bar or soft keyboard
// to animate), but the types must exist and behave: apps install a Callback in
// onCreate unconditionally, and a missing Bounds/Callback breaks that path at
// class-load time regardless of whether an animation ever runs.
//
// Source: frameworks/base/core/java/android/view/WindowInsetsAnimation.java
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/view/WindowInsetsAnimation.java
package android.view;

import android.graphics.Insets;

public final class WindowInsetsAnimation {

    private final int mTypeMask;
    private final android.view.animation.Interpolator mInterpolator;
    private final long mDurationMillis;
    private float mFraction;
    private float mAlpha = 1.0f;

    public WindowInsetsAnimation(int typeMask,
                                 android.view.animation.Interpolator interpolator,
                                 long durationMillis) {
        mTypeMask = typeMask;
        mInterpolator = interpolator;
        mDurationMillis = durationMillis;
    }

    /** WindowInsets.Type mask of the insets this animation moves. */
    public int getTypeMask() { return mTypeMask; }

    /** Raw 0..1 progress, before the interpolator. */
    public float getFraction() { return mFraction; }

    public void setFraction(float fraction) { mFraction = fraction; }

    /**
     * Interpolated progress. AOSP applies the interpolator here and returns the
     * raw fraction when there is none, which is what animation code expects to
     * multiply its offsets by.
     */
    public float getInterpolatedFraction() {
        if (mInterpolator != null) return mInterpolator.getInterpolation(mFraction);
        return mFraction;
    }

    public android.view.animation.Interpolator getInterpolator() { return mInterpolator; }

    public long getDurationMillis() { return mDurationMillis; }

    public float getAlpha() { return mAlpha; }

    public void setAlpha(float alpha) { mAlpha = alpha; }

    /**
     * The range an animation will travel through: the smallest and largest
     * insets it will ever report. Apps read these to size a placeholder before
     * the first frame arrives.
     */
    public static final class Bounds {
        private final Insets mLowerBound;
        private final Insets mUpperBound;

        public Bounds(Insets lowerBound, Insets upperBound) {
            mLowerBound = lowerBound;
            mUpperBound = upperBound;
        }

        public Insets getLowerBound() { return mLowerBound; }

        public Insets getUpperBound() { return mUpperBound; }

        /** AOSP clamps the upper bound to what the app actually gets to use. */
        public Bounds inset(Insets insets) {
            return new Bounds(
                    insetInsets(mLowerBound, insets.left, insets.top, insets.right, insets.bottom),
                    insetInsets(mUpperBound, insets.left, insets.top, insets.right, insets.bottom));
        }

        private static Insets insetInsets(Insets in, int left, int top, int right, int bottom) {
            if (in == null) return null;
            int l = Math.max(0, in.left - left);
            int t = Math.max(0, in.top - top);
            int r = Math.max(0, in.right - right);
            int b = Math.max(0, in.bottom - bottom);
            if (l == in.left && t == in.top && r == in.right && b == in.bottom) return in;
            return Insets.of(l, t, r, b);
        }

        @Override public String toString() {
            return "Bounds{lower=" + mLowerBound + " upper=" + mUpperBound + "}";
        }
    }

    /**
     * AOSP declares Callback as an ABSTRACT CLASS with a constructor taking the
     * dispatch mode, not an interface -- apps subclass it and override only
     * onProgress. Declaring it as an interface would throw
     * IncompatibleClassChangeError at class-load for every such subclass.
     */
    public abstract static class Callback {

        /** Stop dispatching to this view's subtree. */
        public static final int DISPATCH_MODE_STOP = 0;
        /** Keep dispatching down the subtree. */
        public static final int DISPATCH_MODE_CONTINUE_ON_SUBTREE = 1;

        private final int mDispatchMode;

        public Callback(int dispatchMode) { mDispatchMode = dispatchMode; }

        public final int getDispatchMode() { return mDispatchMode; }

        public void onPrepare(WindowInsetsAnimation animation) { }

        public Bounds onStart(WindowInsetsAnimation animation, Bounds bounds) {
            return bounds;
        }

        public abstract WindowInsets onProgress(WindowInsets insets,
                                                java.util.List<WindowInsetsAnimation> runningAnimations);

        public void onEnd(WindowInsetsAnimation animation) { }
    }
}
