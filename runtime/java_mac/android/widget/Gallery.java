// android.widget.Gallery -- deprecated horizontally-scrolling AbsSpinner that
// shows a centered item. Was Absent. We reuse AbsSpinner's selection model;
// the horizontal carousel chrome is not painted. AOSP:
//   frameworks/base/core/java/android/widget/Gallery.java
//   @Deprecated public class Gallery extends AbsSpinner implements GestureDetector.OnGestureListener
package android.widget;

import android.content.Context;

@Deprecated
public class Gallery extends AbsSpinner
        implements android.view.GestureDetector.OnGestureListener {
    private int mSpacing;
    private int mAnimationDuration = 400;
    private float mUnselectedAlpha = 0.5f;
    private int mGravity = android.view.Gravity.CENTER_VERTICAL;

    public Gallery(Context context) { super(context); }
    public Gallery(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public Gallery(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public Gallery(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSpacing(int spacing) { mSpacing = spacing; requestLayout(); }
    public int  getSpacing() { return mSpacing; }
    public void setAnimationDuration(int animationDurationMillis) {
        mAnimationDuration = animationDurationMillis;
    }
    public int  getAnimationDuration() { return mAnimationDuration; }
    public void setUnselectedAlpha(float unselectedAlpha) { mUnselectedAlpha = unselectedAlpha; }
    public float getUnselectedAlpha() { return mUnselectedAlpha; }
    public void setGravity(int gravity) { mGravity = gravity; requestLayout(); }
    public int  getGravity() { return mGravity; }

    /** AOSP Gallery.setCallbackDuringFling: whether selection changes are
     *  reported to the OnItemSelectedListener WHILE a fling is coasting. It
     *  used to discard the flag; onFling/onScroll below read it, exactly as
     *  AOSP's do. */
    public void setCallbackDuringFling(boolean shouldCallback) {
        mShouldCallbackDuringFling = shouldCallback;
    }

    // ==================================================================
    // API-36 surface completion. AOSP declares
    //   @Deprecated public class Gallery extends AbsSpinner
    //       implements GestureDetector.OnGestureListener
    // and this class was missing BOTH the interface and all six of its
    // methods -- so a Gallery could not be handed to a GestureDetector at all,
    // which is the only way AOSP's Gallery receives touch. Bodies mirror
    // frameworks/base/core/java/android/widget/Gallery.java (android16-release);
    // cited line numbers are from that file.
    //
    // Standing caveat, unchanged from this file's header: the horizontal
    // carousel is not painted, AbsSpinner.pointToPosition() answers
    // INVALID_POSITION, and there is no FlingRunnable. So the touch-position
    // dependent branches take AOSP's own `mDownTouchPosition < 0` path. The
    // control flow and the return values are AOSP's; what is missing is the
    // scrolling those branches would drive, and that is a rendering gap in
    // this widget, not an API one.
    // ==================================================================

    private boolean mShouldCallbackDuringFling = true;
    private boolean mShouldCallbackOnUnselectedItemClick = true;
    private boolean mSuppressSelectionChanged;
    private boolean mIsFirstScroll;
    private int mDownTouchPosition = INVALID_POSITION;
    private android.view.View mDownTouchView;

    /** AOSP :1078 -- record which child is under the finger and claim the
     *  gesture. Returning true is required or no MOVE/UP follows. */
    @Override
    public boolean onDown(android.view.MotionEvent e) {
        mDownTouchPosition = pointToPosition((int) e.getX(), (int) e.getY());
        if (mDownTouchPosition >= 0) {
            mDownTouchView = getChildAt(mDownTouchPosition - getFirstVisiblePosition());
            if (mDownTouchView != null) mDownTouchView.setPressed(true);
        }
        mIsFirstScroll = true;
        return true;
    }

    /** AOSP :1000 -- a tap selects the tapped child and, when the click should
     *  be reported, dispatches it. */
    @Override
    public boolean onSingleTapUp(android.view.MotionEvent e) {
        if (mDownTouchPosition >= 0) {
            setSelection(mDownTouchPosition);
            if (mShouldCallbackOnUnselectedItemClick
                    || mDownTouchPosition == getSelectedItemPosition()) {
                performItemClick(mDownTouchView, mDownTouchPosition,
                        getItemIdAtPosition(mDownTouchPosition));
            }
            return true;
        }
        return false;
    }

    /** AOSP :1039. The suppress-selection bookkeeping is AOSP's; the scroll it
     *  drives (trackMotionScroll) has no counterpart here -- see the caveat
     *  above -- so the gesture is consumed without moving the carousel. */
    @Override
    public boolean onScroll(android.view.MotionEvent e1, android.view.MotionEvent e2,
            float distanceX, float distanceY) {
        final android.view.ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        if (!mShouldCallbackDuringFling) {
            if (mIsFirstScroll && !mSuppressSelectionChanged) mSuppressSelectionChanged = true;
        } else if (mSuppressSelectionChanged) {
            mSuppressSelectionChanged = false;
        }
        mIsFirstScroll = false;
        return true;
    }

    /** AOSP :1020 -- consumes the fling and starts the carousel coasting.
     *  There is no FlingRunnable here, so only the suppression flag AOSP sets
     *  on the way in is reproduced; the return value (always true, the gesture
     *  IS consumed by a Gallery) is AOSP's. */
    @Override
    public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2,
            float velocityX, float velocityY) {
        if (!mShouldCallbackDuringFling && !mSuppressSelectionChanged) {
            mSuppressSelectionChanged = true;
        }
        return true;
    }

    /** AOSP :1118 -- haptic, then the long-click listener, then the context
     *  menu. dispatchLongPress's first branch is AdapterView's own
     *  OnItemLongClickListener, which this tree does implement. */
    @Override
    public void onLongPress(android.view.MotionEvent e) {
        if (mDownTouchPosition < 0) return;
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        final long id = getItemIdAtPosition(mDownTouchPosition);
        final AdapterView.OnItemLongClickListener l = getOnItemLongClickListener();
        if (l != null) l.onItemLongClick(this, mDownTouchView, mDownTouchPosition, id);
    }

    /** AOSP :1132 -- empty in AOSP too ("Unused methods from
     *  GestureDetector.OnGestureListener"). */
    @Override
    public void onShowPress(android.view.MotionEvent e) { }
}
