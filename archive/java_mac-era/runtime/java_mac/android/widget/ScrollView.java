// android.widget.ScrollView -- AOSP vertical scroll container: a FrameLayout
// that lets a single child be TALLER than the viewport and scrolls it on the y
// axis. Was an auto-generated NON-View stub (`class ScrollView {}`), which
// breaks inflation / addView for every layout that nests content in a
// ScrollView (F-Droid + most settings / detail / dialog-body screens). Now a
// real FrameLayout subclass; mirrors HorizontalScrollView exactly, just on the
// vertical axis: the child is measured with UNSPECIFIED height so content below
// the fold is not clamped/lost. Like HSV we do NOT drive fling/touch scrolling
// (View-level scrollTo is a no-op), so the visible (top) portion renders
// correctly and interactive scroll parks at the View scroll contract -- a
// separate, deeper gap (View scroll-offset support). AOSP:
//   frameworks/base/core/java/android/widget/ScrollView.java
//   public class ScrollView extends FrameLayout
// v1.0.420.
package android.widget;

import android.content.Context;
import android.view.MotionEvent;

public class ScrollView extends FrameLayout {
    private boolean fillViewport = false;
    private boolean smoothScrollingEnabled = true;
    private float mLastMotionY;
    private boolean mIsBeingDragged;
    private static final int TOUCH_SLOP = 8;

    // v1.0.893 momentum fling. OverScroller is a faithful AOSP
    // SplineOverScroller port that was sitting ENTIRELY UNUSED -- nothing
    // constructed it and nothing polled it, because fling()/computeScroll()
    // were empty bodies AND ViewGroup.dispatchDraw never called computeScroll()
    // at all (both fixed in this version). Field initializers run after super(),
    // so getContext() is set; OverScroller(Context) is null-Context-safe.
    private final OverScroller mScroller = new OverScroller(getContext());
    private android.view.VelocityTracker mVelocityTracker;
    private final int mMinimumVelocity = android.view.ViewConfiguration
        .get(getContext()).getScaledMinimumFlingVelocity();
    private final int mMaximumVelocity = android.view.ViewConfiguration
        .get(getContext()).getScaledMaximumFlingVelocity();

    // AOSP ScrollView.mEdgeGlowTop/mEdgeGlowBottom (frameworks/base core/java/
    // android/widget/ScrollView.java): the overscroll "glow" fed from the touch/
    // fling path when a drag or fling runs past the content edge. We do not wire
    // the pull itself (no View.overScrollBy in this tree yet, see the long
    // computeScroll comment above), so these exist ONLY to back the public
    // color get/set API telegram's own scroll-view subclasses call -- EdgeEffect
    // (android/widget/EdgeEffect.java) already stores + returns a color even
    // though it paints no glow. Field initializers run after super(), so
    // getContext() is set here exactly as it already is for mScroller above.
    private final EdgeEffect mEdgeGlowTop = new EdgeEffect(getContext());
    private final EdgeEffect mEdgeGlowBottom = new EdgeEffect(getContext());

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) mVelocityTracker = android.view.VelocityTracker.obtain();
    }
    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) { mVelocityTracker.recycle(); mVelocityTracker = null; }
    }

    /** The scrollable overflow: child content height minus the viewport. */
    private int getScrollRange() {
        if (getChildCount() == 0) return 0;
        int viewport = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        return Math.max(0, getChildAt(0).getMeasuredHeight() - viewport);
    }

    // AOSP ScrollView touch handling: intercept a vertical drag once it clears
    // touch-slop (so child taps still work), then scrollBy on MOVE clamped to
    // the content range. macOS scroll-wheel/trackpad arrives here as a
    // synthesized DOWN->MOVE->UP drag (cocoa_bridge scrollWheel:), so this is
    // what makes wheel/trackpad scrolling work end-to-end now that View scroll
    // offset is real. v1.0.421.
    @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mLastMotionY = ev.getY();
            mIsBeingDragged = false;
            // AOSP: a DOWN landing on a coasting fling GRABS it
            // (mIsBeingDragged = !mScroller.isFinished()) rather than letting it
            // keep running under the finger. computeScrollOffset() first so
            // getCurrY() is current.
            mScroller.computeScrollOffset();
            if (!mScroller.isFinished()) { mScroller.abortAnimation(); mIsBeingDragged = true; }
            initVelocityTrackerIfNotExists();
            mVelocityTracker.clear();
            mVelocityTracker.addMovement(ev);
            return mIsBeingDragged;
        }
        // AOSP tracks velocity from onInterceptTouchEvent TOO, because a child
        // that consumed the DOWN means onTouchEvent never saw the start of the
        // gesture -- without this the release velocity is computed from one
        // post-steal sample and the fling is dead on arrival.
        initVelocityTrackerIfNotExists();
        mVelocityTracker.addMovement(ev);
        if (action == MotionEvent.ACTION_MOVE && getScrollRange() > 0
                && Math.abs(ev.getY() - mLastMotionY) > TOUCH_SLOP) {
            mIsBeingDragged = true;
            mLastMotionY = ev.getY();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mIsBeingDragged = false;
            recycleVelocityTracker();
        }
        return mIsBeingDragged;
    }

    /** AOSP ScrollView.onGenericMotionEvent (android14-release ScrollView.java:
     *  942-996): a mouse wheel step scrolls by AXIS_VSCROLL x the vertical scroll
     *  factor (64dp), clamped to the content; a step that cannot move the view
     *  is not consumed, so it reaches the scroller around this one. A MOUSE never
     *  overscrolls (canOverscroll is false for SOURCE_MOUSE).
     *  WheelScroll.VIEWS=0: not handled (pre-v1.0.2273). */
    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if (com.macapk.loader.WheelScroll.VIEWS
                && event.getAction() == MotionEvent.ACTION_SCROLL) {
            final float axisValue;
            if (event.isFromSource(android.view.InputDevice.SOURCE_CLASS_POINTER)) {
                axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            } else if (event.isFromSource(android.view.InputDevice.SOURCE_ROTARY_ENCODER)) {
                axisValue = event.getAxisValue(MotionEvent.AXIS_SCROLL);
            } else {
                axisValue = 0;
            }
            final float factor = android.view.ViewConfiguration.get(getContext())
                    .getScaledVerticalScrollFactor();
            final int delta = Math.round(axisValue * factor);
            if (delta != 0) {
                final int range = getScrollRange();
                int oldScrollY = getScrollY();
                int newScrollY = oldScrollY - delta;
                final int overscrollMode = getOverScrollMode();
                boolean canOverscroll = !event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)
                        && (overscrollMode == OVER_SCROLL_ALWAYS
                        || (overscrollMode == OVER_SCROLL_IF_CONTENT_SCROLLS && range > 0));
                boolean absorbed = false;
                if (newScrollY < 0) {
                    if (canOverscroll) {
                        mEdgeGlowTop.onPullDistance(-(float) newScrollY / getHeight(), 0.5f);
                        mEdgeGlowTop.onRelease();
                        invalidate();
                        absorbed = true;
                    }
                    newScrollY = 0;
                } else if (newScrollY > range) {
                    if (canOverscroll) {
                        mEdgeGlowBottom.onPullDistance(
                                (float) (newScrollY - range) / getHeight(), 0.5f);
                        mEdgeGlowBottom.onRelease();
                        invalidate();
                        absorbed = true;
                    }
                    newScrollY = range;
                }
                if (newScrollY != oldScrollY) {
                    super.scrollTo(getScrollX(), newScrollY);
                    return true;
                }
                if (absorbed) return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        initVelocityTrackerIfNotExists();
        boolean handled = true;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLastMotionY = ev.getY();
                if (!mScroller.isFinished()) mScroller.abortAnimation();
                mIsBeingDragged = getScrollRange() > 0;
                handled = mIsBeingDragged;
                break;
            case MotionEvent.ACTION_MOVE: {
                float y = ev.getY();
                int dy = (int) (mLastMotionY - y);
                mLastMotionY = y;
                int newY = Math.max(0, Math.min(getScrollY() + dy, getScrollRange()));
                if (newY != getScrollY()) scrollTo(0, newY);
                break;
            }
            case MotionEvent.ACTION_UP:
                // AOSP ScrollView.onTouchEvent ACTION_UP:
                //   velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                //   int initialVelocity = (int) velocityTracker.getYVelocity(mActivePointerId);
                //   if (Math.abs(initialVelocity) > mMinimumVelocity)
                //       flingWithNestedDispatch(-initialVelocity);
                // Sign: a finger moving DOWN yields a POSITIVE getYVelocity and
                // must scroll TOWARD THE TOP, i.e. decreasing scrollY -- hence
                // the negation, matching our ACTION_MOVE (dy = last - y).
                // No nested-scroll dispatch (we have no NestedScrollingParent
                // chain), so this is AOSP's flingWithNestedDispatch minus the
                // dispatchNestedPreFling/dispatchNestedFling hops.
                if (mIsBeingDragged) {
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) mVelocityTracker.getYVelocity();
                    if (Math.abs(initialVelocity) > mMinimumVelocity) fling(-initialVelocity);
                    mIsBeingDragged = false;
                    recycleVelocityTracker();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                recycleVelocityTracker();
                break;
            default:
                handled = super.onTouchEvent(ev);
        }
        // AOSP appends the sample at the END of onTouchEvent, AFTER the UP case
        // has already read the velocity -- so the release velocity comes from the
        // MOVE history and is not contaminated by the UP sample.
        if (mVelocityTracker != null) mVelocityTracker.addMovement(ev);
        return handled;
    }

    public ScrollView(Context c) { super(c); }
    public ScrollView(Context c, android.util.AttributeSet a) { super(c, a); }
    public ScrollView(Context c, android.util.AttributeSet a, int s) { super(c, a, s); }
    public ScrollView(Context c, android.util.AttributeSet a, int s, int r) { super(c, a, s); }

    public boolean isFillViewport() { return fillViewport; }
    public void setFillViewport(boolean fill) { this.fillViewport = fill; }
    public boolean isSmoothScrollingEnabled() { return smoothScrollingEnabled; }
    public void setSmoothScrollingEnabled(boolean smooth) { this.smoothScrollingEnabled = smooth; }
    public int getMaxScrollAmount() { return (int) (0.5f * (getBottom() - getTop())); }
    public void smoothScrollTo(int x, int y) { scrollTo(x, y); }
    public void smoothScrollBy(int dx, int dy) { scrollTo(getScrollX() + dx, getScrollY() + dy); }
    // AOSP programmatic scrolls. scrollTo + getScrollRange are real here (see
    // onTouchEvent), so these drive actual scroll position. fullScroll(FOCUS_DOWN)
    // is the ubiquitous "scroll to bottom after appending content" idiom; was a
    // no-op. Return true when scrollY actually changed, mirroring AOSP.
    public boolean fullScroll(int direction) {
        int target = (direction == android.view.View.FOCUS_DOWN) ? getScrollRange() : 0;
        if (target == getScrollY()) return false;
        scrollTo(0, target);
        return true;
    }
    public boolean pageScroll(int direction) {
        int viewport = getHeight() - getPaddingTop() - getPaddingBottom();
        int delta = (direction == android.view.View.FOCUS_DOWN) ? viewport : -viewport;
        int target = Math.max(0, Math.min(getScrollY() + delta, getScrollRange()));
        if (target == getScrollY()) return false;
        scrollTo(0, target);
        return true;
    }
    public boolean arrowScroll(int direction) {
        int delta = (direction == android.view.View.FOCUS_DOWN) ? getMaxScrollAmount() : -getMaxScrollAmount();
        int target = Math.max(0, Math.min(getScrollY() + delta, getScrollRange()));
        if (target == getScrollY()) return false;
        scrollTo(0, target);
        return true;
    }
    /** AOSP ScrollView.fling:
     *    int height = getHeight() - mPaddingBottom - mPaddingTop;
     *    int bottom = getChildAt(0).getHeight();
     *    mScroller.fling(mScrollX, mScrollY, 0, velocityY, 0, 0, 0,
     *            Math.max(0, bottom - height), 0, height/2);
     *    postInvalidateOnAnimation();
     *  velocityY arrives already sign-flipped from ACTION_UP. */
    public void fling(int velocityY) {
        if (getChildCount() <= 0) return;
        int height = getHeight() - getPaddingBottom() - getPaddingTop();
        int bottom = getChildAt(0).getHeight();
        mScroller.fling(getScrollX(), getScrollY(), 0, velocityY,
                0, 0, 0, Math.max(0, bottom - height), 0, height / 2);
        postInvalidateOnAnimation();
    }

    /** AOSP ScrollView.computeScroll. Called once per frame from
     *  ViewGroup.dispatchDraw (which did not call it at all before v1.0.893, so
     *  this body being empty was only half the reason flings never ran). Polls
     *  the scroller, writes the new position, and asks for another frame until
     *  the spline finishes.
     *
     *  Two documented deviations from AOSP:
     *  - AOSP applies the delta via overScrollBy(...) because that also drives
     *    the overscroll glow and the ballistic bounce. We have no
     *    View.overScrollBy and no EdgeEffect wiring, so we use the clamping
     *    scrollTo override below -- the same clamp AOSP's overScrollBy applies
     *    when mOverflingDistance == 0, which it is here.
     *  - AOSP guards the repost with `if (!awakenScrollBars())`; our
     *    View.awakenScrollBars is a hardcoded `return false`, so that branch is
     *    always taken. Called unconditionally.
     *
     *  AOSP's own comment defends firing onScrollChanged from inside the draw
     *  pass here ("the alternative, which I think would be worse, is to post
     *  something and tell the subclasses later"), so listener re-entrancy during
     *  draw is faithful rather than something we introduced. */
    @Override public void computeScroll() {
        if (!mScroller.computeScrollOffset()) return;
        int y = mScroller.getCurrY();
        if (y != getScrollY()) scrollTo(getScrollX(), y);
        postInvalidateOnAnimation();
    }

    /** AOSP ScrollView.clamp, verbatim. */
    private static int clamp(int n, int my, int child) {
        if (my >= child || n < 0) return 0;
        if ((my + n) > child)     return child - my;
        return n;
    }

    /** AOSP ScrollView.scrollTo. EVERY scroll -- fling, drag, programmatic, and
     *  View.scrollBy which delegates here -- funnels through this clamp, so no
     *  caller can park the view outside its content. We had no override at all.
     *  ⚠ The `getChildCount() > 0` guard means a childless ScrollView silently
     *  DROPS the scroll. That is AOSP's actual behaviour; keep it. */
    @Override public void scrollTo(int x, int y) {
        if (getChildCount() <= 0) return;
        android.view.View child = getChildAt(0);
        x = clamp(x, getWidth()  - getPaddingRight()  - getPaddingLeft(), child.getWidth());
        y = clamp(y, getHeight() - getPaddingBottom() - getPaddingTop(),  child.getHeight());
        if (x != getScrollX() || y != getScrollY()) super.scrollTo(x, y);
    }

    /** AOSP ScrollView.onLayout ends with `scrollTo(mScrollX, mScrollY)` --
     *  "Calling this with the present values causes it to re-claim them" -- which
     *  re-clamps the existing offset against the NEW content height. That is the
     *  fix for a view left scrolled past the end after its content shrinks. */
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        scrollTo(getScrollX(), getScrollY());
    }

    /** The app's OWN declared targetSdkVersion, or the MODERN answer
     *  (Integer.MAX_VALUE) when it cannot be read. Unresolved never latches and
     *  never guesses legacy -- the convention stated on
     *  android.app.Application.macapkAppTargetSdk. */
    private int macapkTargetSdk() {
        try {
            android.content.Context c = getContext();
            android.content.pm.ApplicationInfo ai =
                    (c != null) ? c.getApplicationInfo() : null;
            if (ai != null && android.app.Application.macapkTargetSdkKnown(ai.targetSdkVersion)) return ai.targetSdkVersion;
        } catch (Throwable ignored) { }
        return Integer.MAX_VALUE;
    }

    /** AOSP ScrollView.onMeasure. `fillViewport="true"` means "if the single
     *  child measured SHORTER than the viewport, re-measure it EXACTLY at the
     *  viewport height" -- the idiom for a scrollable screen whose content must
     *  still reach the bottom edge. setFillViewport() has stored the flag since
     *  v1.0.420 and NOTHING read it, because there was no onMeasure override at
     *  all, so the API was a complete no-op.
     *  AOSP gates the margin terms on targetSdk >= M (ScrollView.java:490-497,
     *  android16-release). This comment used to say "every APK in the corpus is
     *  well past M, so the pre-M branch is dead and omitted" -- MEASURED FALSE
     *  2026-08-24: 4 of 62 APKs in ../Apks target below 23 (jetpackjoyride
     *  effective 8, frozenbubble 12, flappybird 15, hillclimb-1.8.1 16), and
     *  two of those are protected-sweep apps. Re-measure with
     *  `debug/compatgate.py --corpus ../Apks` before writing any such claim;
     *  four of the six in this tree were false the day they were checked.
     *
     *  Below M only the PADDING counts. Including the margins there measures the
     *  child taller than the viewport by exactly its vertical margins, so a
     *  fillViewport screen that should sit still becomes scrollable. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!fillViewport) return;
        if (android.view.View.MeasureSpec.getMode(heightMeasureSpec)
                == android.view.View.MeasureSpec.UNSPECIFIED) return;
        if (getChildCount() == 0) return;
        android.view.View child = getChildAt(0);
        if (child == null) return;
        android.view.ViewGroup.LayoutParams lp = child.getLayoutParams();
        int lm = 0, rm = 0, tm = 0, bm = 0;
        // The MODERN branch is literally the previous code path: an app at or
        // above M keeps counting the child's margins, byte for byte.
        int targetSdkVersion = macapkTargetSdk();
        if (targetSdkVersion >= android.os.Build.VERSION_CODES.M) {
            if (lp instanceof android.view.ViewGroup.MarginLayoutParams mlp) {
                lm = mlp.leftMargin; rm = mlp.rightMargin;
                tm = mlp.topMargin;  bm = mlp.bottomMargin;
            }
        }
        int widthPadding  = getPaddingLeft() + getPaddingRight() + lm + rm;
        int heightPadding = getPaddingTop()  + getPaddingBottom() + tm + bm;
        int desiredHeight = getMeasuredHeight() - heightPadding;
        if (child.getMeasuredHeight() < desiredHeight) {
            int childWidthMeasureSpec = android.view.ViewGroup.getChildMeasureSpec(
                widthMeasureSpec, widthPadding,
                lp != null ? lp.width : android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            int childHeightMeasureSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                desiredHeight, android.view.View.MeasureSpec.EXACTLY);
            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }

    /** AOSP ScrollView measures its child with UNSPECIFIED HEIGHT (the child may
     *  be taller than the viewport; the overflow is scrolled). Without this the
     *  child is measured against the parent's bounded-height chain and content
     *  below the fold is clamped away. Vertical analog of
     *  HorizontalScrollView.measureChildWithMargins. */
    @Override
    protected void measureChildWithMargins(android.view.View child,
            int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        if (child == null) return;
        android.view.ViewGroup.LayoutParams lp = child.getLayoutParams();
        // lane layout A, 2026-08-02: include the child's HORIZONTAL MARGINS.
        // AOSP (android13-release ScrollView.java:1429-1440):
        //   final int childWidthMeasureSpec = getChildMeasureSpec(parentWidthMeasureSpec,
        //           mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin
        //           + widthUsed, lp.width);
        // Ours omitted them, so a margined child measured at the FULL parent
        // width while being LAID OUT at its margin offset -- its match_parent
        // text then wrapped to a width that runs off the window and clipped
        // mid-glyph at the right edge. Measured: DeskClock's features block
        // (a LinearLayout with layout_marginLeft/Right=22dp inside the
        // ScrollView) wrapped at 808 where the emulator wraps at 720
        // ("...except custo|"). See docs/canvas-ab-analyst-report-a_2026-08-02.md
        // item 3.
        int hMargins = 0;
        if (lp instanceof android.view.ViewGroup.MarginLayoutParams mlp)
            hMargins = mlp.leftMargin + mlp.rightMargin;
        int childWidthMeasureSpec = android.view.ViewGroup.getChildMeasureSpec(
            parentWidthMeasureSpec,
            getPaddingLeft() + getPaddingRight() + hMargins + widthUsed,
            lp != null ? lp.width
                       : android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        int childHeightMeasureSpec =
            android.view.View.MeasureSpec.makeMeasureSpec(
                0, android.view.View.MeasureSpec.UNSPECIFIED);
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    // ---- AOSP ScrollView edge-effect color API (frameworks/base core/java/
    // android/widget/ScrollView.java). Real stored properties on the top/bottom
    // EdgeEffects declared above -- setColor/getColor already exist on EdgeEffect
    // itself, so these are thin AOSP-shaped pass-throughs, not stubs. ----
    public void setEdgeEffectColor(int color) {
        setTopEdgeEffectColor(color);
        setBottomEdgeEffectColor(color);
    }
    public void setTopEdgeEffectColor(int color) { mEdgeGlowTop.setColor(color); }
    public void setBottomEdgeEffectColor(int color) { mEdgeGlowBottom.setColor(color); }
    public int getTopEdgeEffectColor() { return mEdgeGlowTop.getColor(); }
    public int getBottomEdgeEffectColor() { return mEdgeGlowBottom.getColor(); }

    /** AOSP ScrollView.computeScrollDeltaToGetChildRectOnScreen, verbatim
     *  (frameworks/base core/java/android/widget/ScrollView.java). Real geometry:
     *  how far to scroll on Y so `rect` (already in THIS view's coordinate space)
     *  lands fully on screen, or -- when it's taller than the viewport -- so the
     *  first screen-size chunk of it does. getVerticalFadingEdgeLength() is a
     *  real override point (View.java) that returns 0 here (we don't fade), which
     *  collapses the fading-edge margin terms to no-ops without changing the
     *  formula's shape. */
    protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) {
        if (getChildCount() == 0) return 0;

        int height = getHeight();
        int screenTop = getScrollY();
        int screenBottom = screenTop + height;

        int fadingEdge = getVerticalFadingEdgeLength();

        // leave room for top fading edge as long as rect isn't at very top
        if (rect.top > 0) {
            screenTop += fadingEdge;
        }

        // leave room for bottom fading edge as long as rect isn't at very bottom
        if (rect.bottom < getChildAt(0).getHeight()) {
            screenBottom -= fadingEdge;
        }

        int scrollYDelta = 0;

        if (rect.bottom > screenBottom && rect.top > screenTop) {
            // need to move down to get it in view: move down just enough so
            // that the entire rectangle is in view (or at least the first
            // screen size chunk).
            if (rect.height() > height) {
                // just enough to get screen size chunk on
                scrollYDelta += (rect.top - screenTop);
            } else {
                // get entire rect at bottom of screen
                scrollYDelta += (rect.bottom - screenBottom);
            }

            // make sure we aren't scrolling beyond the end of our content
            int bottom = getChildAt(0).getBottom();
            int distanceToBottom = bottom - screenBottom;
            scrollYDelta = Math.min(scrollYDelta, distanceToBottom);

        } else if (rect.top < screenTop && rect.bottom < screenBottom) {
            // need to move up to get it in view: move up just enough so that
            // entire rectangle is in view (or at least the first screen
            // size chunk of it).
            if (rect.height() > height) {
                // screen size chunk
                scrollYDelta -= (screenBottom - rect.bottom);
            } else {
                // entire rect at top
                scrollYDelta -= (screenTop - rect.top);
            }

            // make sure we aren't scrolling any further than the top our content
            scrollYDelta = Math.max(scrollYDelta, -getScrollY());
        }
        return scrollYDelta;
    }

    /** AOSP ScrollView.scrollToChildRect, verbatim. The shared tail of
     *  requestChildRectangleOnScreen and scrollToDescendant below: turn a
     *  computed delta into an actual (immediate or smooth) scroll. */
    private boolean scrollToChildRect(android.graphics.Rect rect, boolean immediate) {
        final int delta = computeScrollDeltaToGetChildRectOnScreen(rect);
        final boolean scroll = delta != 0;
        if (scroll) {
            if (immediate) {
                scrollBy(0, delta);
            } else {
                smoothScrollBy(0, delta);
            }
        }
        return scroll;
    }

    /** AOSP ScrollView.requestChildRectangleOnScreen, verbatim. Overrides the
     *  ViewGroup base (which just returns false -- "no scrolling was performed")
     *  so a descendant's requestRectangleOnScreen() walk actually scrolls this
     *  container instead of being a no-op once it reaches us. This is the real
     *  AOSP caller that gives computeScrollDeltaToGetChildRectOnScreen a live
     *  wiring rather than leaving it reachable only via a subclass's own
     *  super.computeScrollDeltaToGetChildRectOnScreen() call.
     *
     *  ⚠ Deliberately narrower than AOSP in one respect: real ScrollView defers
     *  via mIsLayoutDirty/mChildToScrollTo when called mid-layout (the rect isn't
     *  trustworthy yet) and replays the scroll from onLayout. That deferred path
     *  is tied to AOSP's onLayout/requestLayout bookkeeping, which is a separate,
     *  larger piece of state-tracking this lane does not add; the immediate path
     *  below -- which is what actually runs once layout has settled, the common
     *  case -- is AOSP-verbatim. */
    @Override
    public boolean requestChildRectangleOnScreen(android.view.View child,
            android.graphics.Rect rectangle, boolean immediate) {
        // offset into coordinate space of this scroll view
        rectangle.offset(child.getLeft() - child.getScrollX(),
                child.getTop() - child.getScrollY());
        return scrollToChildRect(rectangle, immediate);
    }

    /** AOSP ScrollView.scrollToDescendant, verbatim minus the mIsLayoutDirty
     *  defer branch (see requestChildRectangleOnScreen above for why). */
    public void scrollToDescendant(android.view.View child) {
        android.graphics.Rect rect = new android.graphics.Rect();
        child.getDrawingRect(rect);
        // Offset from child's local coordinates to ScrollView coordinates
        offsetDescendantRectToMyCoords(child, rect);
        int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(rect);
        if (scrollDelta != 0) {
            scrollBy(0, scrollDelta);
        }
    }

    /** AOSP ScrollView.java:525 executeKeyEvent(KeyEvent) -- the arrow/page/
     *  home/end key handling a host calls from its own onKeyDown so a
     *  ScrollView can be driven from the keyboard. Verbatim structure; the
     *  fullScroll/pageScroll/arrowScroll it dispatches to are the real ones
     *  above. mTempRect exists only as scratch for AOSP's focus search, so it
     *  has no counterpart here. */
    public boolean executeKeyEvent(android.view.KeyEvent event) {
        if (!canScroll()) {
            if (isFocused() && event.getKeyCode() != android.view.KeyEvent.KEYCODE_BACK
                    && event.getKeyCode() != android.view.KeyEvent.KEYCODE_ESCAPE) {
                android.view.View currentFocused = findFocus();
                if (currentFocused == this) currentFocused = null;
                android.view.View nextFocused = android.view.FocusFinder.getInstance()
                        .findNextFocus(this, currentFocused, android.view.View.FOCUS_DOWN);
                return nextFocused != null && nextFocused != this
                        && nextFocused.requestFocus(android.view.View.FOCUS_DOWN);
            }
            return false;
        }

        boolean handled = false;
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case android.view.KeyEvent.KEYCODE_DPAD_UP:
                    handled = event.isAltPressed() ? fullScroll(android.view.View.FOCUS_UP)
                                                   : arrowScroll(android.view.View.FOCUS_UP);
                    break;
                case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                    handled = event.isAltPressed() ? fullScroll(android.view.View.FOCUS_DOWN)
                                                   : arrowScroll(android.view.View.FOCUS_DOWN);
                    break;
                case android.view.KeyEvent.KEYCODE_MOVE_HOME:
                    handled = fullScroll(android.view.View.FOCUS_UP);
                    break;
                case android.view.KeyEvent.KEYCODE_MOVE_END:
                    handled = fullScroll(android.view.View.FOCUS_DOWN);
                    break;
                case android.view.KeyEvent.KEYCODE_PAGE_UP:
                    handled = pageScroll(android.view.View.FOCUS_UP);
                    break;
                case android.view.KeyEvent.KEYCODE_PAGE_DOWN:
                    handled = pageScroll(android.view.View.FOCUS_DOWN);
                    break;
                case android.view.KeyEvent.KEYCODE_SPACE:
                    handled = pageScroll(event.isShiftPressed()
                            ? android.view.View.FOCUS_UP : android.view.View.FOCUS_DOWN);
                    break;
            }
        }
        return handled;
    }

    /** AOSP ScrollView.canScroll(): there is somewhere to scroll iff the child
     *  is taller than the viewport. */
    private boolean canScroll() {
        final android.view.View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child == null) return false;
        return child.getHeight() > getHeight() - getPaddingTop() - getPaddingBottom();
    }
}
