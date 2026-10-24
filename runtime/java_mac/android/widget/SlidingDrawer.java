// android.widget.SlidingDrawer -- deprecated ViewGroup with a handle that
// slides a content panel open/closed. Was Absent. We keep the open/close
// state model + listeners; the slide animation/handle chrome is not painted.
// AOSP: frameworks/base/core/java/android/widget/SlidingDrawer.java
//   @Deprecated public class SlidingDrawer extends ViewGroup
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

@Deprecated
public class SlidingDrawer extends ViewGroup {
    public static final int ORIENTATION_HORIZONTAL = 0;
    public static final int ORIENTATION_VERTICAL = 1;

    public interface OnDrawerOpenListener { void onDrawerOpened(); }
    public interface OnDrawerCloseListener { void onDrawerClosed(); }
    public interface OnDrawerScrollListener {
        void onScrollStarted();
        void onScrollEnded();
    }

    private boolean mExpanded;
    private int mOrientation = ORIENTATION_VERTICAL;
    private OnDrawerOpenListener mOnDrawerOpenListener;
    private OnDrawerCloseListener mOnDrawerCloseListener;
    private OnDrawerScrollListener mOnDrawerScrollListener;
    private int mHandleId;
    private int mContentId;

    public SlidingDrawer(Context context) { super(context); }
    public SlidingDrawer(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public SlidingDrawer(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public SlidingDrawer(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                child.layout(getPaddingLeft(), getPaddingTop(),
                        getPaddingLeft() + child.getMeasuredWidth(),
                        getPaddingTop() + child.getMeasuredHeight());
            }
        }
    }

    public View getHandle() { return mHandleId != 0 ? findViewById(mHandleId) : null; }
    public View getContent() { return mContentId != 0 ? findViewById(mContentId) : null; }

    public void open() {
        mExpanded = true;
        if (mOnDrawerOpenListener != null) mOnDrawerOpenListener.onDrawerOpened();
        invalidate();
    }
    public void close() {
        mExpanded = false;
        if (mOnDrawerCloseListener != null) mOnDrawerCloseListener.onDrawerClosed();
        invalidate();
    }
    public void animateOpen() { open(); }
    public void animateClose() { close(); }
    public void toggle() { if (mExpanded) close(); else open(); }
    public void animateToggle() { toggle(); }

    public boolean isOpened() { return mExpanded; }
    public boolean isMoving() { return false; }

    public void lock() {}
    public void unlock() {}

    public void setOnDrawerOpenListener(OnDrawerOpenListener l) { mOnDrawerOpenListener = l; }
    public void setOnDrawerCloseListener(OnDrawerCloseListener l) { mOnDrawerCloseListener = l; }
    public void setOnDrawerScrollListener(OnDrawerScrollListener l) { mOnDrawerScrollListener = l; }
}
