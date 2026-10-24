// android.widget.HorizontalScrollView -- AOSP horizontal scroll
// container: a FrameLayout that lets a single child be wider than
// the viewport and scrolls it on the x axis. Calculator wraps its
// formula TextView in a HorizontalScrollView subclass; the class
// must be a real View for the layout to inflate (the old auto-stub
// pattern of a non-View placeholder breaks addView).
// AOSP: frameworks/base/core/java/android/widget/HorizontalScrollView.java
//   public class HorizontalScrollView extends FrameLayout
// We don't drive fling/touch scrolling; scroll state parks at the
// View-level scrollTo contract.
package android.widget;

import android.content.Context;

public class HorizontalScrollView extends FrameLayout {
    private boolean fillViewport = false;
    private boolean smoothScrollingEnabled = true;

    public HorizontalScrollView(Context c) { super(c); }
    public HorizontalScrollView(Context c, android.util.AttributeSet a) {
        super(c, a);
    }
    public HorizontalScrollView(Context c, android.util.AttributeSet a, int s) {
        super(c, a, s);
    }
    public HorizontalScrollView(Context c, android.util.AttributeSet a,
                                int s, int r) {
        super(c, a, s);
    }

    public boolean isFillViewport() { return fillViewport; }
    public void setFillViewport(boolean fill) { this.fillViewport = fill; }
    public boolean isSmoothScrollingEnabled() { return smoothScrollingEnabled; }
    public void setSmoothScrollingEnabled(boolean smooth) {
        this.smoothScrollingEnabled = smooth;
    }
    public int getMaxScrollAmount() {
        return (int) (0.5f * (getRight() - getLeft()));
    }
    public void smoothScrollTo(int x, int y) { scrollTo(x, y); }
    public void smoothScrollBy(int dx, int dy) {
        scrollTo(getScrollX() + dx, getScrollY() + dy);
    }
    /** Horizontal scrollable overflow: child width minus the viewport. */
    private int macapkScrollRangeX() {
        if (getChildCount() == 0) return 0;
        // getMeasuredWidth, not getWidth: getWidth() is 0 before the first
        // layout, which made the range the child's FULL width on an early
        // event. ScrollView's getScrollRange() already used the measured value;
        // this was the asymmetry. Reachable from the touch path as of v1.0.893.
        int viewport = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        return Math.max(0, getChildAt(0).getMeasuredWidth() - viewport);
    }
    // AOSP programmatic horizontal scrolls (mirror ScrollView on the x axis).
    // Return true when scrollX actually changed.
    public boolean fullScroll(int direction) {
        int target = (direction == android.view.View.FOCUS_RIGHT) ? macapkScrollRangeX() : 0;
        if (target == getScrollX()) return false;
        scrollTo(target, 0);
        return true;
    }
    public boolean pageScroll(int direction) {
        int viewport = getWidth() - getPaddingLeft() - getPaddingRight();
        int delta = (direction == android.view.View.FOCUS_RIGHT) ? viewport : -viewport;
        int target = Math.max(0, Math.min(getScrollX() + delta, macapkScrollRangeX()));
        if (target == getScrollX()) return false;
        scrollTo(target, 0);
        return true;
    }
    public boolean arrowScroll(int direction) {
        int delta = (direction == android.view.View.FOCUS_RIGHT) ? getMaxScrollAmount() : -getMaxScrollAmount();
        int target = Math.max(0, Math.min(getScrollX() + delta, macapkScrollRangeX()));
        if (target == getScrollX()) return false;
        scrollTo(target, 0);
        return true;
    }
    /** AOSP HorizontalScrollView.fling -- ScrollView.fling on the x axis:
     *    int width = getWidth() - mPaddingRight - mPaddingLeft;
     *    int right = getChildAt(0).getWidth();
     *    mScroller.fling(mScrollX, mScrollY, velocityX, 0, 0,
     *            Math.max(0, right - width), 0, 0, width/2, 0); */
    public void fling(int velocityX) {
        if (getChildCount() <= 0) return;
        int width = getWidth() - getPaddingRight() - getPaddingLeft();
        int right = getChildAt(0).getWidth();
        mScroller.fling(getScrollX(), getScrollY(), velocityX, 0,
                0, Math.max(0, right - width), 0, 0, width / 2, 0);
        postInvalidateOnAnimation();
    }

    /** AOSP HorizontalScrollView.computeScroll. Same two deviations as
     *  ScrollView.computeScroll -- see the long comment there. */
    @Override public void computeScroll() {
        if (!mScroller.computeScrollOffset()) return;
        int x = mScroller.getCurrX();
        if (x != getScrollX()) scrollTo(x, getScrollY());
        postInvalidateOnAnimation();
    }

    /** AOSP HorizontalScrollView.clamp, and AOSP clamps BOTH axes in BOTH
     *  classes -- so this body is identical to ScrollView's. */
    private static int clamp(int n, int my, int child) {
        if (my >= child || n < 0) return 0;
        if ((my + n) > child)     return child - my;
        return n;
    }
    @Override public void scrollTo(int x, int y) {
        if (getChildCount() <= 0) return;
        android.view.View child = getChildAt(0);
        x = clamp(x, getWidth()  - getPaddingRight()  - getPaddingLeft(), child.getWidth());
        y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(),  child.getHeight());
        if (x != getScrollX() || y != getScrollY()) super.scrollTo(x, y);
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        scrollTo(getScrollX(), getScrollY());
    }

    /** AOSP HorizontalScrollView.onMeasure -- ScrollView's fillViewport on the
     *  x axis. Stored-but-never-read since v1.0.420, same as the vertical case.
     *  OpenCalculator is the corpus's only app-authored use: its
     *  `activity_main.xml` wraps the result display in
     *  `<HorizontalScrollView android:fillViewport="true">` around a
     *  `<LinearLayout android:gravity="end" android:layout_width="wrap_content">`,
     *  so without this the LinearLayout is ~0 wide and `gravity="end"` cannot
     *  right-align anything. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!fillViewport) return;
        if (android.view.View.MeasureSpec.getMode(widthMeasureSpec)
                == android.view.View.MeasureSpec.UNSPECIFIED) return;
        if (getChildCount() == 0) return;
        android.view.View child = getChildAt(0);
        if (child == null) return;
        android.view.ViewGroup.LayoutParams lp = child.getLayoutParams();
        int lm = 0, rm = 0, tm = 0, bm = 0;
        if (lp instanceof android.view.ViewGroup.MarginLayoutParams mlp) {
            lm = mlp.leftMargin; rm = mlp.rightMargin;
            tm = mlp.topMargin;  bm = mlp.bottomMargin;
        }
        int widthPadding  = getPaddingLeft() + getPaddingRight() + lm + rm;
        int heightPadding = getPaddingTop()  + getPaddingBottom() + tm + bm;
        int desiredWidth = getMeasuredWidth() - widthPadding;
        if (child.getMeasuredWidth() < desiredWidth) {
            int childHeightMeasureSpec = android.view.ViewGroup.getChildMeasureSpec(
                heightMeasureSpec, heightPadding,
                lp != null ? lp.height : android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            int childWidthMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                desiredWidth, android.view.View.MeasureSpec.EXACTLY);
            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }

    // ---- v1.0.893: HSV had NO touch handling of ANY kind. It could not be
    // dragged at all, only scrolled programmatically. This is ScrollView's
    // onInterceptTouchEvent/onTouchEvent transposed to X, per AOSP
    // HorizontalScrollView (which is ScrollView with the axes swapped).
    private float mLastMotionX;
    private boolean mIsBeingDragged;
    private static final int TOUCH_SLOP = 8;   // same constant ScrollView uses
    private final OverScroller mScroller = new OverScroller(getContext());
    private android.view.VelocityTracker mVelocityTracker;
    private final int mMinimumVelocity = android.view.ViewConfiguration
        .get(getContext()).getScaledMinimumFlingVelocity();
    private final int mMaximumVelocity = android.view.ViewConfiguration
        .get(getContext()).getScaledMaximumFlingVelocity();

    // AOSP HorizontalScrollView.mEdgeGlowLeft/mEdgeGlowRight (frameworks/base
    // core/java/android/widget/HorizontalScrollView.java): the x-axis sibling of
    // ScrollView.mEdgeGlowTop/mEdgeGlowBottom -- same reasoning applies (see the
    // comment there): we back the public color get/set API without wiring the
    // pull/glow itself. Field initializers run after super(), matching mScroller
    // just above.
    private final EdgeEffect mEdgeGlowLeft = new EdgeEffect(getContext());
    private final EdgeEffect mEdgeGlowRight = new EdgeEffect(getContext());

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) mVelocityTracker = android.view.VelocityTracker.obtain();
    }
    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) { mVelocityTracker.recycle(); mVelocityTracker = null; }
    }

    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == android.view.MotionEvent.ACTION_DOWN) {
            mLastMotionX = ev.getX();
            mIsBeingDragged = false;
            mScroller.computeScrollOffset();
            if (!mScroller.isFinished()) { mScroller.abortAnimation(); mIsBeingDragged = true; }
            initVelocityTrackerIfNotExists();
            mVelocityTracker.clear();
            mVelocityTracker.addMovement(ev);
            return mIsBeingDragged;
        }
        initVelocityTrackerIfNotExists();
        mVelocityTracker.addMovement(ev);
        if (action == android.view.MotionEvent.ACTION_MOVE && macapkScrollRangeX() > 0
                && Math.abs(ev.getX() - mLastMotionX) > TOUCH_SLOP) {
            mIsBeingDragged = true;
            mLastMotionX = ev.getX();
            return true;
        }
        if (action == android.view.MotionEvent.ACTION_UP
                || action == android.view.MotionEvent.ACTION_CANCEL) {
            mIsBeingDragged = false;
            recycleVelocityTracker();
        }
        return mIsBeingDragged;
    }

    /** AOSP HorizontalScrollView.onGenericMotionEvent (android14-release
     *  HorizontalScrollView.java:861-923): AXIS_HSCROLL (or -AXIS_VSCROLL with
     *  Shift held) x the horizontal scroll factor, clamped, not while a drag is
     *  in progress; a MOUSE never overscrolls. WheelScroll.VIEWS=0: not handled. */
    @Override public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        if (com.macapk.loader.WheelScroll.VIEWS
                && event.getAction() == android.view.MotionEvent.ACTION_SCROLL && !mIsBeingDragged) {
            final float axisValue;
            if (event.isFromSource(android.view.InputDevice.SOURCE_CLASS_POINTER)) {
                if ((event.getMetaState() & android.view.KeyEvent.META_SHIFT_ON) != 0) {
                    axisValue = -event.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL);
                } else {
                    axisValue = event.getAxisValue(android.view.MotionEvent.AXIS_HSCROLL);
                }
            } else if (event.isFromSource(android.view.InputDevice.SOURCE_ROTARY_ENCODER)) {
                axisValue = event.getAxisValue(android.view.MotionEvent.AXIS_SCROLL);
            } else {
                axisValue = 0;
            }
            final float factor = android.view.ViewConfiguration.get(getContext())
                    .getScaledHorizontalScrollFactor();
            final int delta = Math.round(axisValue * factor);
            if (delta != 0) {
                final int range = macapkScrollRangeX();
                int oldScrollX = getScrollX();
                int newScrollX = oldScrollX + delta;
                final int overscrollMode = getOverScrollMode();
                boolean canOverscroll = !event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)
                        && (overscrollMode == OVER_SCROLL_ALWAYS
                        || (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0));
                boolean absorbed = false;
                if (newScrollX < 0) {
                    if (canOverscroll) {
                        mEdgeGlowLeft.onPullDistance(-(float) newScrollX / getWidth(), 0.5f);
                        mEdgeGlowLeft.onRelease();
                        invalidate();
                        absorbed = true;
                    }
                    newScrollX = 0;
                } else if (newScrollX > range) {
                    if (canOverscroll) {
                        mEdgeGlowRight.onPullDistance(
                                (float) (newScrollX - range) / getWidth(), 0.5f);
                        mEdgeGlowRight.onRelease();
                        invalidate();
                        absorbed = true;
                    }
                    newScrollX = range;
                }
                if (newScrollX != oldScrollX) {
                    super.scrollTo(newScrollX, getScrollY());
                    return true;
                }
                if (absorbed) return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent ev) {
        initVelocityTrackerIfNotExists();
        boolean handled = true;
        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                mLastMotionX = ev.getX();
                if (!mScroller.isFinished()) mScroller.abortAnimation();
                mIsBeingDragged = macapkScrollRangeX() > 0;
                handled = mIsBeingDragged;
                break;
            case android.view.MotionEvent.ACTION_MOVE: {
                float x = ev.getX();
                int dx = (int) (mLastMotionX - x);
                mLastMotionX = x;
                int newX = Math.max(0, Math.min(getScrollX() + dx, macapkScrollRangeX()));
                if (newX != getScrollX()) scrollTo(newX, 0);
                break;
            }
            case android.view.MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) mVelocityTracker.getXVelocity();
                    if (Math.abs(initialVelocity) > mMinimumVelocity) fling(-initialVelocity);
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                }
                break;
            case android.view.MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                recycleVelocityTracker();
                break;
            default:
                handled = super.onTouchEvent(ev);
        }
        if (mVelocityTracker != null) mVelocityTracker.addMovement(ev);
        return handled;
    }

    /** AOSP HorizontalScrollView measures its child with UNSPECIFIED
     *  width (the child may be wider than the viewport; scrolling
     *  covers the overflow). Per frameworks/base HorizontalScrollView
     *  .measureChildWithMargins: childWidthMeasureSpec is built with
     *  MeasureSpec.UNSPECIFIED. Without this, Calculator's formula
     *  TextView is measured against the parent's EXACT width chain
     *  and collapsed to ~2px (typed digits existed in the model but
     *  had no width to paint in). */
    @Override
    protected void measureChildWithMargins(android.view.View child,
            int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        if (child == null) return;
        android.view.ViewGroup.LayoutParams lp = child.getLayoutParams();
        int childHeightMeasureSpec = android.view.ViewGroup.getChildMeasureSpec(
            parentHeightMeasureSpec,
            getPaddingTop() + getPaddingBottom() + heightUsed,
            lp != null ? lp.height
                       : android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        int childWidthMeasureSpec =
            android.view.View.MeasureSpec.makeMeasureSpec(
                0, android.view.View.MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) { }

    // ---- AOSP HorizontalScrollView edge-effect color API (frameworks/base
    // core/java/android/widget/HorizontalScrollView.java). Thin pass-throughs onto
    // the EdgeEffects declared above -- same shape as ScrollView's Top/Bottom pair,
    // mirrored onto the x axis. ----
    public void setEdgeEffectColor(int color) {
        setLeftEdgeEffectColor(color);
        setRightEdgeEffectColor(color);
    }
    public void setLeftEdgeEffectColor(int color) { mEdgeGlowLeft.setColor(color); }
    public void setRightEdgeEffectColor(int color) { mEdgeGlowRight.setColor(color); }
    public int getLeftEdgeEffectColor() { return mEdgeGlowLeft.getColor(); }
    public int getRightEdgeEffectColor() { return mEdgeGlowRight.getColor(); }

    /** AOSP HorizontalScrollView.computeScrollDeltaToGetChildRectOnScreen,
     *  verbatim -- ScrollView's formula (see the vertical twin for the full
     *  per-branch commentary) transposed onto X. */
    protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) {
        if (getChildCount() == 0) return 0;

        int width = getWidth();
        int screenLeft = getScrollX();
        int screenRight = screenLeft + width;

        int fadingEdge = getHorizontalFadingEdgeLength();

        // leave room for left fading edge as long as rect isn't at very left
        if (rect.left > 0) {
            screenLeft += fadingEdge;
        }

        // leave room for right fading edge as long as rect isn't at very right
        if (rect.right < getChildAt(0).getWidth()) {
            screenRight -= fadingEdge;
        }

        int scrollXDelta = 0;

        if (rect.right > screenRight && rect.left > screenLeft) {
            // need to move right to get it in view: move right just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).
            if (rect.width() > width) {
                // just enough to get screen size chunk on
                scrollXDelta += (rect.left - screenLeft);
            } else {
                // get entire rect at right of screen
                scrollXDelta += (rect.right - screenRight);
            }

            // make sure we aren't scrolling beyond the end of our content
            int right = getChildAt(0).getRight();
            int distanceToRight = right - screenRight;
            scrollXDelta = Math.min(scrollXDelta, distanceToRight);

        } else if (rect.left < screenLeft && rect.right < screenRight) {
            // need to move left to get it in view: move left just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).
            if (rect.width() > width) {
                // screen size chunk
                scrollXDelta -= (screenRight - rect.right);
            } else {
                // entire rect at left
                scrollXDelta -= (screenLeft - rect.left);
            }

            // make sure we aren't scrolling any further than the left of our content
            scrollXDelta = Math.max(scrollXDelta, -getScrollX());
        }
        return scrollXDelta;
    }

    /** AOSP HorizontalScrollView.scrollToChildRect, verbatim. */
    private boolean scrollToChildRect(android.graphics.Rect rect, boolean immediate) {
        final int delta = computeScrollDeltaToGetChildRectOnScreen(rect);
        final boolean scroll = delta != 0;
        if (scroll) {
            if (immediate) {
                scrollBy(delta, 0);
            } else {
                smoothScrollBy(delta, 0);
            }
        }
        return scroll;
    }

    /** AOSP HorizontalScrollView.requestChildRectangleOnScreen, verbatim --
     *  same role as ScrollView's override (see its long comment for the
     *  mIsLayoutDirty deviation, which applies identically here): overrides the
     *  ViewGroup base (plain `return false`) so a descendant's
     *  requestRectangleOnScreen() walk actually scrolls this container. */
    @Override
    public boolean requestChildRectangleOnScreen(android.view.View child,
            android.graphics.Rect rectangle, boolean immediate) {
        // offset into coordinate space of this scroll view
        rectangle.offset(child.getLeft() - child.getScrollX(),
                child.getTop() - child.getScrollY());
        return scrollToChildRect(rectangle, immediate);
    }

    /** AOSP HorizontalScrollView.scrollToChild, verbatim (including AOSP's own
     *  private visibility -- unlike ScrollView's public scrollToDescendant sibling,
     *  AOSP never elevated this one to a public API, so this mirrors that exact
     *  asymmetry rather than "fixing" it). Presently only reachable from a
     *  subclass that calls it directly (AOSP's own only callers are the
     *  mIsLayoutDirty-gated focus-search paths this lane does not port -- see
     *  requestChildRectangleOnScreen above), but it is real, correct AOSP
     *  geometry rather than a stub. */
    private void scrollToChild(android.view.View child) {
        android.graphics.Rect rect = new android.graphics.Rect();
        child.getDrawingRect(rect);
        // Offset from child's local coordinates to ScrollView coordinates
        offsetDescendantRectToMyCoords(child, rect);
        int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(rect);
        if (scrollDelta != 0) {
            scrollBy(scrollDelta, 0);
        }
    }

    /** AOSP HorizontalScrollView.java:487 executeKeyEvent(KeyEvent) -- the x-axis
     *  twin of ScrollView's. Note AOSP's own asymmetries, kept: the no-scroll
     *  branch here does NOT exempt BACK/ESCAPE, and the SPACE case calls
     *  pageScroll WITHOUT assigning `handled` (so a space key scrolls but is
     *  reported unhandled). */
    public boolean executeKeyEvent(android.view.KeyEvent event) {
        if (!canScroll()) {
            if (isFocused()) {
                android.view.View currentFocused = findFocus();
                if (currentFocused == this) currentFocused = null;
                android.view.View nextFocused = android.view.FocusFinder.getInstance()
                        .findNextFocus(this, currentFocused, android.view.View.FOCUS_RIGHT);
                return nextFocused != null && nextFocused != this
                        && nextFocused.requestFocus(android.view.View.FOCUS_RIGHT);
            }
            return false;
        }

        boolean handled = false;
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                    handled = event.isAltPressed() ? fullScroll(android.view.View.FOCUS_LEFT)
                                                   : arrowScroll(android.view.View.FOCUS_LEFT);
                    break;
                case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                    handled = event.isAltPressed() ? fullScroll(android.view.View.FOCUS_RIGHT)
                                                   : arrowScroll(android.view.View.FOCUS_RIGHT);
                    break;
                case android.view.KeyEvent.KEYCODE_SPACE:
                    pageScroll(event.isShiftPressed()
                            ? android.view.View.FOCUS_LEFT : android.view.View.FOCUS_RIGHT);
                    break;
            }
        }
        return handled;
    }

    /** AOSP HorizontalScrollView.canScroll(): the child is wider than the viewport. */
    private boolean canScroll() {
        final android.view.View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child == null) return false;
        return child.getWidth() > getWidth() - getPaddingLeft() - getPaddingRight();
    }
}
