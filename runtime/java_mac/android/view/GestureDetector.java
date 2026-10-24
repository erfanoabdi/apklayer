// android.view.GestureDetector -- detects taps/scrolls/flings/long-press/
// double-tap from a stream of MotionEvents. Mirrors AOSP
// frameworks/base/core/java/android/view/GestureDetector.java.
//
// The interface surface (OnGestureListener / OnDoubleTapListener /
// OnContextClickListener / SimpleOnGestureListener) MUST exist on the
// bootclasspath because callers reference these types in method signatures
// (e.g. Reddit's onCreate) -- a bare class would NoClassDefFoundError at load.
//
// Previously the detection body was down/up only: onScroll/onFling/onLongPress/
// onDoubleTap never fired, so every swipe UI (RecyclerView, ViewPager, maps,
// image viewers) was dead. This is now the real AOSP state machine:
//   - ACTION_DOWN posts SHOW_PRESS (after tap timeout) and LONG_PRESS (after
//     long-press timeout) via a Handler; records the down point.
//   - ACTION_MOVE past touch slop fires onScroll and cancels the long-press,
//     accumulating focus deltas like AOSP (distanceX/Y are last-minus-current).
//   - ACTION_UP within slop+tap-timeout is a tap: onSingleTapUp now, then
//     onSingleTapConfirmed deferred (so a second tap can upgrade to onDoubleTap).
//   - A VelocityTracker measures release velocity; > minimum fling velocity and
//     within the max -> onFling.
// Timing constants come from ViewConfiguration so they match the rest of the
// platform.
package android.view;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class GestureDetector {
    public interface OnGestureListener {
        boolean onDown(MotionEvent e);
        void onShowPress(MotionEvent e);
        boolean onSingleTapUp(MotionEvent e);
        boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY);
        void onLongPress(MotionEvent e);
        boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY);
    }

    public interface OnDoubleTapListener {
        boolean onSingleTapConfirmed(MotionEvent e);
        boolean onDoubleTap(MotionEvent e);
        boolean onDoubleTapEvent(MotionEvent e);
    }

    public interface OnContextClickListener {
        boolean onContextClick(MotionEvent e);
    }

    public static class SimpleOnGestureListener
            implements OnGestureListener, OnDoubleTapListener, OnContextClickListener {
        public boolean onDown(MotionEvent e) { return false; }
        public void onShowPress(MotionEvent e) {}
        public boolean onSingleTapUp(MotionEvent e) { return false; }
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) { return false; }
        public void onLongPress(MotionEvent e) {}
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) { return false; }
        public boolean onSingleTapConfirmed(MotionEvent e) { return false; }
        public boolean onDoubleTap(MotionEvent e) { return false; }
        public boolean onDoubleTapEvent(MotionEvent e) { return false; }
        public boolean onContextClick(MotionEvent e) { return false; }
    }

    // Handler message ids (AOSP GestureDetector.GestureHandler).
    private static final int SHOW_PRESS = 1;
    private static final int LONG_PRESS = 2;
    private static final int TAP        = 3;

    private final OnGestureListener mListener;
    private OnDoubleTapListener mDoubleTapListener;
    private OnContextClickListener mContextClickListener;
    private boolean mIsLongpressEnabled = true;

    // Timing/slop, pulled from ViewConfiguration so they match the platform.
    private final int mTouchSlopSquare;
    private final int mDoubleTapSlopSquare;
    private final int mMinimumFlingVelocity;
    private final int mMaximumFlingVelocity;
    private static final int TAP_TIMEOUT         = ViewConfiguration.getTapTimeout();
    private static final int LONGPRESS_TIMEOUT   = ViewConfiguration.getLongPressTimeout();
    private static final int DOUBLE_TAP_TIMEOUT  = ViewConfiguration.getDoubleTapTimeout();
    private static final int DOUBLE_TAP_MIN_TIME = 40;

    // Tracking state.
    private final Handler mHandler;
    private MotionEvent mCurrentDownEvent;
    private MotionEvent mPreviousUpEvent;
    private boolean mStillDown;
    private boolean mInLongPress;
    private boolean mInContextClick;
    private boolean mAlwaysInTapRegion;
    private boolean mAlwaysInBiggerTapRegion;
    private boolean mIsDoubleTapping;
    private boolean mDeferConfirmSingleTap;
    private float mLastFocusX, mLastFocusY;
    private float mDownFocusX, mDownFocusY;
    private VelocityTracker mVelocityTracker;

    public GestureDetector(OnGestureListener listener) {
        this(null, listener, null);
    }

    public GestureDetector(android.content.Context context, OnGestureListener listener) {
        this(context, listener, null);
    }

    public GestureDetector(android.content.Context context, OnGestureListener listener,
                            Handler handler) {
        if (handler != null) {
            mHandler = new GestureHandler(handler.getLooper());
        } else {
            Looper looper = Looper.myLooper();
            mHandler = (looper != null) ? new GestureHandler(looper) : new GestureHandler();
        }
        mListener = listener;
        if (listener instanceof OnDoubleTapListener) {
            setOnDoubleTapListener((OnDoubleTapListener) listener);
        }
        if (listener instanceof OnContextClickListener) {
            setContextClickListener((OnContextClickListener) listener);
        }

        ViewConfiguration vc = ViewConfiguration.get(context);
        int touchSlop = vc.getScaledTouchSlop();
        int doubleTapSlop = vc.getScaledDoubleTapSlop();
        mTouchSlopSquare = touchSlop * touchSlop;
        mDoubleTapSlopSquare = doubleTapSlop * doubleTapSlop;
        mMinimumFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = vc.getScaledMaximumFlingVelocity();
    }

    public void setOnDoubleTapListener(OnDoubleTapListener onDoubleTapListener) {
        mDoubleTapListener = onDoubleTapListener;
    }
    public void setContextClickListener(OnContextClickListener onContextClickListener) {
        mContextClickListener = onContextClickListener;
    }
    public void setIsLongpressEnabled(boolean isLongpressEnabled) {
        mIsLongpressEnabled = isLongpressEnabled;
    }
    public boolean isLongpressEnabled() { return mIsLongpressEnabled; }

    public boolean onTouchEvent(MotionEvent ev) {
        if (mListener == null || ev == null) return false;
        final int action = ev.getAction();

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        // Focus = centroid of all non-up pointers (AOSP behavior on multi-touch).
        final boolean pointerUp =
                (action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_UP;
        final int skipIndex = pointerUp ? ev.getActionIndex() : -1;
        float sumX = 0, sumY = 0;
        final int count = ev.getPointerCount();
        for (int i = 0; i < count; i++) {
            if (skipIndex == i) continue;
            sumX += ev.getX(i);
            sumY += ev.getY(i);
        }
        final int div = pointerUp ? count - 1 : count;
        final float focusX = div > 0 ? sumX / div : ev.getX();
        final float focusY = div > 0 ? sumY / div : ev.getY();

        boolean handled = false;

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_POINTER_DOWN:
                mLastFocusX = focusX; mDownFocusX = focusX;
                mLastFocusY = focusY; mDownFocusY = focusY;
                cancelTaps();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                mLastFocusX = focusX; mDownFocusX = focusX;
                mLastFocusY = focusY; mDownFocusY = focusY;
                // Check the up pointer's fling velocity to ignore a flick from a
                // lifted finger (AOSP cancels if it exceeds the max).
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                final int upIndex = ev.getActionIndex();
                final int id1 = ev.getPointerId(upIndex);
                final float x1 = mVelocityTracker.getXVelocity(id1);
                final float y1 = mVelocityTracker.getYVelocity(id1);
                for (int i = 0; i < count; i++) {
                    if (i == upIndex) continue;
                    final int id2 = ev.getPointerId(i);
                    final float x = x1 * mVelocityTracker.getXVelocity(id2);
                    final float y = y1 * mVelocityTracker.getYVelocity(id2);
                    final float dot = x + y;
                    if (dot < 0) { mVelocityTracker.clear(); break; }
                }
                break;

            case MotionEvent.ACTION_DOWN:
                if (mDoubleTapListener != null) {
                    boolean hadTapMessage = mHandler.hasMessages(TAP);
                    if (hadTapMessage) mHandler.removeMessages(TAP);
                    if (mCurrentDownEvent != null && mPreviousUpEvent != null && hadTapMessage
                            && isConsideredDoubleTap(mCurrentDownEvent, mPreviousUpEvent, ev)) {
                        mIsDoubleTapping = true;
                        handled |= mDoubleTapListener.onDoubleTap(mCurrentDownEvent);
                        handled |= mDoubleTapListener.onDoubleTapEvent(ev);
                    } else {
                        mHandler.sendEmptyMessageDelayed(TAP, DOUBLE_TAP_TIMEOUT);
                    }
                }

                mDownFocusX = mLastFocusX = focusX;
                mDownFocusY = mLastFocusY = focusY;
                if (mCurrentDownEvent != null) mCurrentDownEvent.recycle();
                mCurrentDownEvent = MotionEvent.obtain(ev);
                mAlwaysInTapRegion = true;
                mAlwaysInBiggerTapRegion = true;
                mStillDown = true;
                mInLongPress = false;
                mDeferConfirmSingleTap = false;

                if (mIsLongpressEnabled) {
                    mHandler.removeMessages(LONG_PRESS);
                    mHandler.sendMessageAtTime(mHandler.obtainMessage(LONG_PRESS),
                            ev.getDownTime() + LONGPRESS_TIMEOUT);
                }
                mHandler.sendMessageAtTime(mHandler.obtainMessage(SHOW_PRESS),
                        ev.getDownTime() + TAP_TIMEOUT);
                handled |= mListener.onDown(ev);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mInLongPress || mInContextClick) break;
                final float scrollX = mLastFocusX - focusX;
                final float scrollY = mLastFocusY - focusY;
                if (mIsDoubleTapping) {
                    handled |= mDoubleTapListener.onDoubleTapEvent(ev);
                } else if (mAlwaysInTapRegion) {
                    final int deltaX = (int) (focusX - mDownFocusX);
                    final int deltaY = (int) (focusY - mDownFocusY);
                    int distance = (deltaX * deltaX) + (deltaY * deltaY);
                    if (distance > mTouchSlopSquare) {
                        handled = mListener.onScroll(mCurrentDownEvent, ev, scrollX, scrollY);
                        mLastFocusX = focusX; mLastFocusY = focusY;
                        mAlwaysInTapRegion = false;
                        mHandler.removeMessages(TAP);
                        mHandler.removeMessages(SHOW_PRESS);
                        mHandler.removeMessages(LONG_PRESS);
                    }
                    if (distance > mDoubleTapSlopSquare) {
                        mAlwaysInBiggerTapRegion = false;
                    }
                } else if (Math.abs(scrollX) >= 1 || Math.abs(scrollY) >= 1) {
                    handled = mListener.onScroll(mCurrentDownEvent, ev, scrollX, scrollY);
                    mLastFocusX = focusX; mLastFocusY = focusY;
                }
                break;

            case MotionEvent.ACTION_UP:
                mStillDown = false;
                MotionEvent currentUpEvent = MotionEvent.obtain(ev);
                if (mIsDoubleTapping) {
                    handled |= mDoubleTapListener.onDoubleTapEvent(ev);
                } else if (mInLongPress) {
                    mHandler.removeMessages(TAP);
                    mInLongPress = false;
                } else if (mAlwaysInTapRegion) {
                    handled = mListener.onSingleTapUp(ev);
                    if (mDeferConfirmSingleTap && mDoubleTapListener != null) {
                        mDoubleTapListener.onSingleTapConfirmed(ev);
                    }
                } else {
                    // A fling: compute velocity and report if over threshold.
                    final VelocityTracker vt = mVelocityTracker;
                    final int pointerId = ev.getPointerId(0);
                    vt.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                    final float velocityY = vt.getYVelocity(pointerId);
                    final float velocityX = vt.getXVelocity(pointerId);
                    if ((Math.abs(velocityY) > mMinimumFlingVelocity)
                            || (Math.abs(velocityX) > mMinimumFlingVelocity)) {
                        handled = mListener.onFling(mCurrentDownEvent, ev, velocityX, velocityY);
                    }
                }
                if (mPreviousUpEvent != null) mPreviousUpEvent.recycle();
                mPreviousUpEvent = currentUpEvent;
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                mIsDoubleTapping = false;
                mDeferConfirmSingleTap = false;
                mHandler.removeMessages(SHOW_PRESS);
                mHandler.removeMessages(LONG_PRESS);
                break;

            case MotionEvent.ACTION_CANCEL:
                cancel();
                break;
        }

        return handled;
    }

    private void cancel() {
        mHandler.removeMessages(SHOW_PRESS);
        mHandler.removeMessages(LONG_PRESS);
        mHandler.removeMessages(TAP);
        if (mVelocityTracker != null) { mVelocityTracker.recycle(); mVelocityTracker = null; }
        mIsDoubleTapping = false;
        mStillDown = false;
        mAlwaysInTapRegion = false;
        mAlwaysInBiggerTapRegion = false;
        mDeferConfirmSingleTap = false;
        mInLongPress = false;
        mInContextClick = false;
    }

    private void cancelTaps() {
        mHandler.removeMessages(SHOW_PRESS);
        mHandler.removeMessages(LONG_PRESS);
        mHandler.removeMessages(TAP);
        mIsDoubleTapping = false;
        mAlwaysInTapRegion = false;
        mAlwaysInBiggerTapRegion = false;
        mDeferConfirmSingleTap = false;
        mInLongPress = false;
        mInContextClick = false;
    }

    private boolean isConsideredDoubleTap(MotionEvent firstDown, MotionEvent firstUp,
            MotionEvent secondDown) {
        if (!mAlwaysInBiggerTapRegion) return false;
        final long deltaTime = secondDown.getEventTime() - firstUp.getEventTime();
        if (deltaTime > DOUBLE_TAP_TIMEOUT || deltaTime < DOUBLE_TAP_MIN_TIME) return false;
        int deltaX = (int) firstDown.getX() - (int) secondDown.getX();
        int deltaY = (int) firstDown.getY() - (int) secondDown.getY();
        return (deltaX * deltaX + deltaY * deltaY) < mDoubleTapSlopSquare;
    }

    private void dispatchLongPress() {
        mHandler.removeMessages(TAP);
        mDeferConfirmSingleTap = false;
        mInLongPress = true;
        mListener.onLongPress(mCurrentDownEvent);
    }

    private class GestureHandler extends Handler {
        GestureHandler() { super(); }
        GestureHandler(Looper looper) { super(looper); }
        @Override public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_PRESS:
                    mListener.onShowPress(mCurrentDownEvent);
                    break;
                case LONG_PRESS:
                    if (mIsLongpressEnabled && mStillDown && !mInLongPress) {
                        dispatchLongPress();
                    }
                    break;
                case TAP:
                    if (mDoubleTapListener != null) {
                        if (!mStillDown) {
                            mDoubleTapListener.onSingleTapConfirmed(mCurrentDownEvent);
                        } else {
                            mDeferConfirmSingleTap = true;
                        }
                    }
                    break;
            }
        }
    }

    public GestureDetector(android.content.Context context, OnGestureListener listener,
                           android.os.Handler handler, boolean unused) {
        this(context, listener, handler);
    }

    /** AOSP GestureDetector.onGenericMotionEvent(MotionEvent):841 -- the
     *  BUTTON_PRESS / BUTTON_RELEASE half of gesture detection, which is how a
     *  right-click or stylus-button press becomes an OnContextClickListener
     *  callback. Transcribed from AOSP minus its InputEventConsistencyVerifier
     *  (a debug-only checker that is @hide and off in release builds).
     *
     *  ⚠ Mac.apk's MotionEvent.getActionButton() currently returns a constant 0
     *  (MotionEvent.java:448) and the Cocoa bridge does not synthesise
     *  ACTION_BUTTON_PRESS, so today no path reaches the listener -- exactly as
     *  on a device with no mouse or stylus attached. The moment the bridge
     *  starts reporting a secondary button this becomes live with no further
     *  change here. */
    public boolean onGenericMotionEvent(MotionEvent ev) {
        final int actionButton = ev.getActionButton();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_BUTTON_PRESS:
                if (mContextClickListener != null && !mInContextClick
                        && (actionButton == MotionEvent.BUTTON_STYLUS_PRIMARY
                            || actionButton == MotionEvent.BUTTON_SECONDARY)) {
                    if (mContextClickListener.onContextClick(ev)) {
                        mInContextClick = true;
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                if (mInContextClick && (actionButton == MotionEvent.BUTTON_STYLUS_PRIMARY
                        || actionButton == MotionEvent.BUTTON_SECONDARY)) {
                    mInContextClick = false;
                }
                break;
        }
        return false;
    }
}
