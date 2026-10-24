// android.graphics.drawable.AnimationDrawable -- AOSP DrawableContainer
// that plays its child drawables as frames of a flip-book animation.
// AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/AnimationDrawable.java
// addFrame(drawable, durationMs) registers a frame; start() begins
// stepping via scheduleSelf at each frame's duration; the Callback
// (a View) re-posts the runnable. Used by old-style frame-by-frame
// AnimationDrawable XML and progress spinners built in code.
package android.graphics.drawable;

import java.util.ArrayList;

public class AnimationDrawable extends DrawableContainer implements Animatable, Runnable {
    private final ArrayList<Integer> mDurations = new ArrayList<>();
    private boolean mOneShot = false;
    private boolean mRunning = false;
    private boolean mAnimating = false;
    private int mCurFrame = -1;

    public AnimationDrawable() {}

    /** Add a frame backed by the given drawable, shown for durationMs. */
    public void addFrame(Drawable frame, int duration) {
        int idx = addChild(frame);
        mDurations.add(duration);
        if (mCurFrame < 0) {
            setFrame(idx, true, false);
        }
    }

    public int getNumberOfFrames() { return getChildCount(); }
    public Drawable getFrame(int index) { return getChild(index); }
    public int getDuration(int i) {
        return (i >= 0 && i < mDurations.size()) ? mDurations.get(i) : 0;
    }

    public boolean isOneShot() { return mOneShot; }
    public void setOneShot(boolean oneShot) { mOneShot = oneShot; }

    @Override public boolean isRunning() { return mRunning; }

    @Override public void start() {
        if (getChildCount() == 0) return;
        if (!mRunning) {
            mRunning = true;
            setFrame(0, false, getChildCount() > 1);
        }
    }

    @Override public void stop() {
        if (mRunning) {
            mRunning = false;
            mAnimating = false;
            unscheduleSelf(this);
        }
    }

    /** Scheduled callback: advance to the next frame and reschedule. */
    @Override public void run() {
        nextFrame(false);
    }

    private void nextFrame(boolean unschedule) {
        int n = getChildCount();
        int next = mCurFrame + 1;
        boolean isLast = next >= n;
        if (isLast) {
            if (mOneShot) {
                // Stay on the final frame and stop.
                mRunning = false;
                mAnimating = false;
                return;
            }
            next = 0;
        }
        setFrame(next, unschedule, !mOneShot || next < (n - 1));
    }

    private void setFrame(int frame, boolean unschedule, boolean animate) {
        if (frame >= getChildCount()) return;
        mAnimating = animate;
        mCurFrame = frame;
        selectDrawable(frame);
        if (unschedule || !animate) {
            unscheduleSelf(this);
        }
        if (animate) {
            // Schedule the next step after this frame's duration.
            int dur = getDuration(frame);
            scheduleSelf(this, android.os.SystemClock.uptimeMillis() + dur);
        }
    }

    @Override public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) {
            if (restart || changed) {
                boolean startFromZero = restart || !mRunning;
                setFrame(startFromZero ? 0 : mCurFrame, true, mRunning);
            }
        } else {
            unscheduleSelf(this);
        }
        return changed;
    }
}
