package android.window;
/** An in-progress back event (API 34; EDGE_NONE and the frame-time accessor are
 *  API 36). AOSP frameworks/base core/java/android/window/BackEvent.java
 *  (android16-release): a `public final class` value type with equals and
 *  toString over all five fields.
 *
 *  Who creates one in Mac.apk: WindowOnBackInvokedDispatcher.macapkOnBackKey,
 *  mirroring ViewRootImpl.doOnBackKeyEvent -- a key-driven BACK hands the top
 *  OnBackAnimationCallback `new BackEvent(0, 0, 0f, EDGE_LEFT)` on the first
 *  DOWN (API 34/35), `EDGE_NONE` from API 36 (predictiveBackSwipeEdgeNoneApi).
 *  No progress events are synthesized: AOSP sends them only for a gesture, and
 *  Mac.apk has no back gesture.
 *
 *  2026-09-26: made final and given equals/toString, as AOSP declares them.
 *  AOSP does NOT override hashCode (identity hash despite a value equals); that
 *  is mirrored rather than "fixed", because an app can observe it. */
public final class BackEvent {
    public static final int EDGE_LEFT  = 0;
    public static final int EDGE_RIGHT = 1;
    public static final int EDGE_NONE  = 2;
    private final float mTouchX;
    private final float mTouchY;
    private final float mProgress;
    private final int mSwipeEdge;
    private final long mFrameTimeMillis;
    public BackEvent(float touchX, float touchY, float progress, int swipeEdge) {
        this(touchX, touchY, progress, swipeEdge, 0L);
    }
    public BackEvent(float touchX, float touchY, float progress, int swipeEdge, long frameTimeMillis) {
        mTouchX = touchX; mTouchY = touchY; mProgress = progress; mSwipeEdge = swipeEdge;
        mFrameTimeMillis = frameTimeMillis;
    }
    public float getTouchX() { return mTouchX; }
    public float getTouchY() { return mTouchY; }
    public float getProgress() { return mProgress; }
    public int getSwipeEdge() { return mSwipeEdge; }
    public long getFrameTimeMillis() { return mFrameTimeMillis; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BackEvent)) return false;
        final BackEvent that = (BackEvent) other;
        return mTouchX == that.mTouchX
                && mTouchY == that.mTouchY
                && mProgress == that.mProgress
                && mSwipeEdge == that.mSwipeEdge
                && mFrameTimeMillis == that.mFrameTimeMillis;
    }

    @Override
    public String toString() {
        return "BackEvent{"
                + "mTouchX=" + mTouchX
                + ", mTouchY=" + mTouchY
                + ", mProgress=" + mProgress
                + ", mSwipeEdge=" + mSwipeEdge
                + ", mFrameTimeMillis=" + mFrameTimeMillis
                + "}";
    }
}
