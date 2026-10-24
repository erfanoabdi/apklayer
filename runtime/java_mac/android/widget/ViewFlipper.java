// android.widget.ViewFlipper -- a ViewAnimator that can auto-advance between
// children on a timer (a simple carousel). Was Absent. startFlipping schedules
// showNext on the main Handler at flipInterval; stopFlipping cancels it. AOSP:
//   frameworks/base/core/java/android/widget/ViewFlipper.java
//   public class ViewFlipper extends ViewAnimator
package android.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class ViewFlipper extends ViewAnimator {
    private static final int DEFAULT_INTERVAL = 3000;

    private int mFlipInterval = DEFAULT_INTERVAL;
    private boolean mAutoStart;
    private boolean mRunning;
    private boolean mStarted;
    private Handler mHandler;
    private Runnable mTick;

    public ViewFlipper(Context context) { super(context); }
    public ViewFlipper(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public void setFlipInterval(int milliseconds) { mFlipInterval = milliseconds; }
    public int  getFlipInterval() { return mFlipInterval; }

    public void startFlipping() {
        mStarted = true;
        updateRunning();
    }
    public void stopFlipping() {
        mStarted = false;
        updateRunning();
    }
    public boolean isFlipping() { return mStarted; }

    public void setAutoStart(boolean autoStart) { mAutoStart = autoStart; }
    public boolean isAutoStart() { return mAutoStart; }

    @Override public void onAttachedToWindow() {
        try { super.onAttachedToWindow(); } catch (Throwable ignored) {}
        if (mAutoStart) startFlipping();
        else updateRunning();
    }
    @Override public void onDetachedFromWindow() {
        try { super.onDetachedFromWindow(); } catch (Throwable ignored) {}
        mRunning = false;
        cancelTick();
    }

    private void updateRunning() {
        boolean running = mStarted && getChildCount() > 1;
        if (running != mRunning) {
            if (running) scheduleTick();
            else cancelTick();
            mRunning = running;
        }
    }

    private void ensureHandler() {
        if (mHandler == null) mHandler = new Handler(Looper.getMainLooper());
    }

    private void scheduleTick() {
        ensureHandler();
        if (mTick == null) {
            mTick = new Runnable() {
                @Override public void run() {
                    if (mRunning) {
                        showNext();
                        mHandler.postDelayed(mTick, mFlipInterval);
                    }
                }
            };
        }
        mHandler.postDelayed(mTick, mFlipInterval);
    }

    private void cancelTick() {
        if (mHandler != null && mTick != null) mHandler.removeCallbacks(mTick);
    }
}
