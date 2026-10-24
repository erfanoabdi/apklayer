// android.widget.AdapterViewFlipper -- an AdapterViewAnimator that auto-
// advances its adapter items on a timer. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/AdapterViewFlipper.java
//   public class AdapterViewFlipper extends AdapterViewAnimator
package android.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class AdapterViewFlipper extends AdapterViewAnimator {
    private static final int DEFAULT_INTERVAL = 10000;

    private int mFlipInterval = DEFAULT_INTERVAL;
    private boolean mAutoStart;
    private boolean mRunning;
    private boolean mStarted;
    private Handler mHandler;
    private Runnable mTick;

    public AdapterViewFlipper(Context context) { super(context); }
    public AdapterViewFlipper(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public AdapterViewFlipper(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public AdapterViewFlipper(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setFlipInterval(int flipInterval) { mFlipInterval = flipInterval; }
    public int  getFlipInterval() { return mFlipInterval; }

    public void startFlipping() { mStarted = true; updateRunning(); }
    public void stopFlipping() { mStarted = false; updateRunning(); }
    public boolean isFlipping() { return mStarted; }
    public void setAutoStart(boolean autoStart) { mAutoStart = autoStart; }
    public boolean isAutoStart() { return mAutoStart; }

    @Override public void onAttachedToWindow() {
        try { super.onAttachedToWindow(); } catch (Throwable ignored) {}
        if (mAutoStart) startFlipping();
    }
    @Override public void onDetachedFromWindow() {
        try { super.onDetachedFromWindow(); } catch (Throwable ignored) {}
        mRunning = false;
        cancelTick();
    }

    private void updateRunning() {
        boolean running = mStarted && getCount() > 1;
        if (running != mRunning) {
            if (running) scheduleTick(); else cancelTick();
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
                    if (mRunning) { showNext(); mHandler.postDelayed(mTick, mFlipInterval); }
                }
            };
        }
        mHandler.postDelayed(mTick, mFlipInterval);
    }
    private void cancelTick() {
        if (mHandler != null && mTick != null) mHandler.removeCallbacks(mTick);
    }
}
