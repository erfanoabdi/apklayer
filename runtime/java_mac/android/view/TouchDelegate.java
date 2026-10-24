// android.view.TouchDelegate -- forwards touch events landing in an
// expanded hit-rect to a smaller delegate view. AOSP's standard
// mechanism for expanding a touch target below the 48dp minimum
// (icon-only toolbar buttons, close/back chevrons, small checkboxes).
// The ancestor view forwards its own onTouchEvent(MotionEvent) calls
// into this class's onTouchEvent(MotionEvent).
//
// Constructor + onTouchEvent below mirror AOSP verbatim (fetched
// 2026-07-22):
// frameworks/base/core/java/android/view/TouchDelegate.java
//
// NOT implemented: getTouchDelegateInfo() and
// onTouchExplorationHoverEvent(). Both are accessibility-only paths --
// the former needs android.view.accessibility.AccessibilityNodeInfo
// .TouchDelegateInfo (no such nested type exists in our accessibility
// stub) and the latter needs View.dispatchHoverEvent (does not exist
// on our View). Neither is referenced by the corpus (only <init> and
// onTouchEvent appear in gaps_methods.tsv); add both if that surface
// ever lands.
package android.view;

import android.graphics.Rect;

public class TouchDelegate {

    /** View that should receive forwarded touch events. */
    private View mDelegateView;

    /** Bounds in local coordinates of the containing view that should be
     *  mapped to the delegate view. Used for initial hit testing. */
    private Rect mBounds;

    /** mBounds inflated by touch slop; tracks whether events after the
     *  initial down still count as within the delegate during a gesture. */
    private Rect mSlopBounds;

    /** True if the delegate was targeted on the down event (hit mBounds). */
    private boolean mDelegateTargeted;

    /** The touchable region of the View extends above its actual extent. */
    public static final int ABOVE = 1;

    /** The touchable region of the View extends below its actual extent. */
    public static final int BELOW = 2;

    /** The touchable region of the View extends to the left of its actual extent. */
    public static final int TO_LEFT = 4;

    /** The touchable region of the View extends to the right of its actual extent. */
    public static final int TO_RIGHT = 8;

    private int mSlop;

    /**
     * @param bounds Bounds in local coordinates of the containing view that
     *        should be mapped to the delegate view.
     * @param delegateView The view that should receive motion events.
     */
    public TouchDelegate(Rect bounds, View delegateView) {
        mBounds = bounds;
        mSlop = ViewConfiguration.get(delegateView.getContext()).getScaledTouchSlop();
        mSlopBounds = new Rect(bounds);
        mSlopBounds.inset(-mSlop, -mSlop);
        mDelegateView = delegateView;
    }

    /**
     * Forward touch events to the delegate view if the event is within the
     * bounds specified in the constructor.
     *
     * @param event The touch event to forward.
     * @return True if the event was consumed by the delegate, false otherwise.
     */
    public boolean onTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        boolean sendToDelegate = false;
        boolean hit = true;
        boolean handled = false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDelegateTargeted = mBounds != null && mBounds.contains(x, y);
                sendToDelegate = mDelegateTargeted;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_MOVE:
                sendToDelegate = mDelegateTargeted;
                if (sendToDelegate) {
                    if (mSlopBounds == null || !mSlopBounds.contains(x, y)) {
                        hit = false;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                sendToDelegate = mDelegateTargeted;
                mDelegateTargeted = false;
                break;
        }
        if (sendToDelegate && mDelegateView != null) {
            if (hit) {
                // Offset event coordinates to be inside the target view.
                event.setLocation(mDelegateView.getWidth() / 2, mDelegateView.getHeight() / 2);
            } else {
                // Offset event coordinates to be outside the target view (in
                // case it does something like tracking pressed state).
                int slop = mSlop;
                event.setLocation(-(slop * 2), -(slop * 2));
            }
            handled = mDelegateView.dispatchTouchEvent(event);
        }
        return handled;
    }

    // The two accessibility-only members the header above says are NOT
    // implemented. They ARE now: View.dispatchHoverEvent exists (View.java) and
    // AccessibilityNodeInfo.TouchDelegateInfo exists (accessibility/
    // AccessibilityNodeInfo.java), so both AOSP bodies transcribe directly.
    // Both are referenced by real apps (YouTube calls each once).

    /** AOSP TouchDelegate.onTouchExplorationHoverEvent(MotionEvent):170 -- the
     *  hover twin of onTouchEvent, used while an accessibility service explores
     *  by touch. Same hit/slop bookkeeping, dispatched as a HOVER event to the
     *  delegate's centre. */
    public boolean onTouchExplorationHoverEvent(MotionEvent event) {
        if (mBounds == null) return false;

        final int x = (int) event.getX();
        final int y = (int) event.getY();
        boolean hit = true;
        boolean handled = false;

        final boolean isInbound = mBounds.contains(x, y);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
                mDelegateTargeted = isInbound;
                break;
            case MotionEvent.ACTION_HOVER_MOVE:
                if (isInbound) {
                    mDelegateTargeted = true;
                } else if (mDelegateTargeted
                        && (mSlopBounds == null || !mSlopBounds.contains(x, y))) {
                    hit = false;
                }
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                mDelegateTargeted = true;
                break;
        }
        if (mDelegateTargeted) {
            if (hit) {
                event.setLocation(mDelegateView.getWidth() / 2, mDelegateView.getHeight() / 2);
            } else {
                mDelegateTargeted = false;
            }
            handled = mDelegateView.dispatchHoverEvent(event);
        }
        return handled;
    }

    /** AOSP TouchDelegate.getTouchDelegateInfo():217 -- builds (and caches) a
     *  one-entry Region -> delegate-View map describing the expanded target, for
     *  an accessibility client to read off the node. */
    private android.view.accessibility.AccessibilityNodeInfo.TouchDelegateInfo
            mTouchDelegateInfo;
    public android.view.accessibility.AccessibilityNodeInfo.TouchDelegateInfo
            getTouchDelegateInfo() {
        if (mTouchDelegateInfo == null) {
            Rect bounds = mBounds != null ? mBounds : new Rect();
            java.util.Map<android.graphics.Region, View> targetMap =
                    new java.util.HashMap<android.graphics.Region, View>(1);
            targetMap.put(new android.graphics.Region(bounds), mDelegateView);
            mTouchDelegateInfo =
                new android.view.accessibility.AccessibilityNodeInfo.TouchDelegateInfo(targetMap);
        }
        return mTouchDelegateInfo;
    }
}
