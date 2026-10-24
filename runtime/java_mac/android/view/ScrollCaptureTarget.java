// android.view.ScrollCaptureTarget -- one candidate scrolling container found
// during a scroll-capture (long-screenshot) search: the view, its visible rect,
// where that rect sits in the window, and the callback that can drive it.
// Source: frameworks/base core/java/android/view/ScrollCaptureTarget.java
// (android16-release).
//
// Mac.apk runs no scroll-capture host, so nothing consumes the targets a
// View.onScrollCaptureSearch produces -- but the search itself is real (see
// View.onScrollCaptureSearch), and an app that implements the search must be
// able to build and read these.
package android.view;

public class ScrollCaptureTarget {
    private final View mContainingView;
    private final android.graphics.Rect mLocalVisibleRect;
    private final android.graphics.Point mPositionInWindow;
    private final ScrollCaptureCallback mCallback;
    private android.graphics.Rect mScrollBounds;

    /** @hide -- Mac.apk keeps the no-arg form the tree already handed out. */
    public ScrollCaptureTarget() {
        this(null, new android.graphics.Rect(), new android.graphics.Point(), null);
    }

    /** AOSP ScrollCaptureTarget(View, Rect, Point, ScrollCaptureCallback). */
    public ScrollCaptureTarget(View scrollTarget, android.graphics.Rect localVisibleRect,
            android.graphics.Point positionInWindow, ScrollCaptureCallback callback) {
        mContainingView = scrollTarget;
        mLocalVisibleRect = localVisibleRect;
        mPositionInWindow = positionInWindow;
        mCallback = callback;
    }

    /** AOSP ScrollCaptureTarget.getCallback(). */
    public ScrollCaptureCallback getCallback() { return mCallback; }
    /** AOSP ScrollCaptureTarget.getContainingView(). */
    public View getContainingView() { return mContainingView; }
    /** AOSP ScrollCaptureTarget.getLocalVisibleRect(). */
    public android.graphics.Rect getLocalVisibleRect() { return mLocalVisibleRect; }
    /** AOSP ScrollCaptureTarget.getPositionInWindow(). */
    public android.graphics.Point getPositionInWindow() { return mPositionInWindow; }
    /** AOSP ScrollCaptureTarget.getHint(): the containing view's
     *  View.SCROLL_CAPTURE_HINT_* value, which the host uses to rank targets. */
    public int getHint() {
        return mContainingView != null
                ? mContainingView.getScrollCaptureHint() : View.SCROLL_CAPTURE_HINT_AUTO;
    }
    /** AOSP ScrollCaptureTarget.getScrollBounds() / setScrollBounds(Rect): the
     *  area the callback reported it can actually scroll, null until it does. */
    public android.graphics.Rect getScrollBounds() { return mScrollBounds; }
    public void setScrollBounds(android.graphics.Rect scrollBounds) {
        mScrollBounds = scrollBounds != null ? new android.graphics.Rect(scrollBounds) : null;
    }
    /** AOSP ScrollCaptureTarget.updatePositionInWindow(): re-reads the
     *  containing view's window position (it can move between the search and the
     *  capture). AOSP uses mContainingView.getLocationInWindow(int[2]). */
    public void updatePositionInWindow() {
        if (mContainingView == null) return;
        int[] loc = new int[2];
        mContainingView.getLocationInWindow(loc);
        mPositionInWindow.set(loc[0], loc[1]);
    }
}
