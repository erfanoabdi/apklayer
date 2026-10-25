// android.widget.LinearLayout -- Tier 3 M5: real AOSP-faithful onMeasure /
// onLayout (was a 48-line stub that deferred ALL layout to the
// ViewCanvasDriver fixupLinearLayouts / forceLayoutZeroExtentChildren
// per-frame hacks). The measure/layout here mirrors
// frameworks/base/core/java/android/widget/LinearLayout.java
// (measureVertical / measureHorizontal / layoutVertical / layoutHorizontal):
// stack children along the orientation axis, honor margins + padding, run
// the weight-distribution second pass (the 0dp+layout_weight idiom), and
// position each child per its layout_gravity on the cross axis.
//
// Why this is safe to add now (the old comment said a full impl "regressed
// every LinearLayout-based widget"): (1) the ViewGroup measure helpers
// (getChildMeasureSpec / measureChildWithMargins) are AOSP-faithful as of
// v1.0.115, which the old stub predated; (2) the inflater now parses
// android:layout_weight -> LayoutParams.weight (Tier 3 M5) so weights are
// real; (3) the per-frame hacks remain in place as a backstop until Tier 3
// M8 retires them -- forceLayoutZeroExtentChildren only acts on ZERO-extent
// children and fixupVerticalLinearLayouts only acts on GAP/over-divide, both
// of which a correct measure+layout eliminates, so they no-op here rather
// than fight us.
//
// Faithful to AOSP for the common surface (orientation, margins, weights,
// MATCH_PARENT/WRAP_CONTENT/explicit on both axes, per-child + container
// gravity, horizontal baseline alignment, and LinearLayout.getBaseline()
// itself -- this container reporting ITS OWN baseline to an outer
// baseline-aligned parent via macapkBaselineAlignedChildIndex, a separate,
// VERTICAL-orientation-only AOSP feature from the horizontal child-
// baseline-alignment implemented below). Deliberately omitted (rare /
// separately stubbed): dividers (setShowDividers is a no-op so
// mDividerHeight is 0), measureWithLargestChild, RTL child order (laid out
// LTR -- START/END resolve to LEFT/RIGHT via the gravity mask).
package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL   = 1;

    /** AOSP LinearLayout.java:112-124. A flag set, not an enum: BEGINNING |
     *  MIDDLE | END is the "show a divider everywhere" idiom. */
    public static final int SHOW_DIVIDER_NONE      = 0;
    public static final int SHOW_DIVIDER_BEGINNING = 1;
    public static final int SHOW_DIVIDER_MIDDLE    = 2;
    public static final int SHOW_DIVIDER_END       = 4;

    int macapkOrientation = HORIZONTAL;
    int macapkGravity = Gravity.START | Gravity.TOP;
    float macapkWeightSum = -1f;

    /** Running sum of child extents along the orientation axis from the last
     *  measure pass; layoutVertical/Horizontal use it to honor container
     *  CENTER/BOTTOM/RIGHT gravity (AOSP LinearLayout.mTotalLength). */
    private int mTotalLength;

    /** AOSP LinearLayout.mBaselineAligned (default true) / mBaselineAlignedChildIndex
     *  (default -1, unset). mBaselineAlignedChildIndex feeds the
     *  getBaseline() override below (this container reporting ITS OWN
     *  baseline to an outer baseline-aligned VERTICAL parent) -- a separate
     *  AOSP feature from the horizontal child-baseline-alignment done in
     *  measureHorizontal/layoutHorizontal. */
    boolean macapkBaselineAligned = true;
    int macapkBaselineAlignedChildIndex = -1;

    /** AOSP LinearLayout.mBaselineChildTop (LinearLayout.java:156,
     *  android11-release): the running mTotalLength captured mid-measure,
     *  right after the child immediately BEFORE
     *  macapkBaselineAlignedChildIndex has been folded in -- i.e. the top
     *  (before that child's own topMargin) of the baseline child itself.
     *  Latched once in measureVertical; consumed by getBaseline() below. */
    private int mBaselineChildTop;

    /** AOSP LinearLayout.mMaxAscent/mMaxDescent: per-vertical-gravity-bucket
     *  (CENTER_VERTICAL/TOP/BOTTOM/FILL) tallest ascent/descent among this
     *  row's baseline-aligned children, computed by measureHorizontal and
     *  consumed by layoutHorizontal to offset each child's top so text
     *  baselines line up instead of box edges. Lazily allocated (mirrors
     *  AOSP) so a purely-VERTICAL LinearLayout never pays for them. */
    private int[] mMaxAscent;
    private int[] mMaxDescent;
    private static final int VERTICAL_GRAVITY_COUNT = 4;
    private static final int INDEX_CENTER_VERTICAL = 0;
    private static final int INDEX_TOP = 1;
    private static final int INDEX_BOTTOM = 2;
    private static final int INDEX_FILL = 3;

    /** AOSP LinearLayout.mDivider / mDividerWidth / mDividerHeight /
     *  mShowDividers / mDividerPadding (LinearLayout.java:126-129, 362-377).
     *  A divider occupies REAL SPACE in the measure and layout passes even
     *  before anything draws it, so these are load-bearing for geometry and
     *  not merely cosmetic: a container with three children and BEGINNING |
     *  MIDDLE | END dividers of an 8px drawable is 32px longer than the sum
     *  of its children. */
    private android.graphics.drawable.Drawable mDivider;
    private int mDividerWidth;
    private int mDividerHeight;
    private int mShowDividers = SHOW_DIVIDER_NONE;
    private int mDividerPadding;

    /** AOSP LinearLayout.mUseLargestChild (android:measureWithLargestChild).
     *  When set, every child is treated as being as long as the LONGEST one
     *  along the orientation axis for the purposes of the container's own
     *  size (and, when there is no hard limit, for the weighted children's
     *  re-measure). */
    private boolean mUseLargestChild;

    /** AOSP LinearLayout.java:234-262 / :296-297. Two targetSdk compat gates,
     *  and they pull in OPPOSITE directions, so reading targetSdkVersion
     *  wrongly moves weighted layouts by a few pixels in a way that looks like
     *  bad arithmetic rather than a bad compat answer:
     *
     *    sRemeasureWeightedChildren  targetSdk >= P (28): run the weight pass
     *                                whenever there is any weight at all, even
     *                                when there is exactly zero excess to
     *                                distribute. Static and latched, exactly as
     *                                AOSP does it (first LinearLayout wins).
     *    mAllowInconsistentMeasurement  targetSdk <= M (23): the LEGACY
     *                                behaviour, in which a height=0 + weight
     *                                child under a non-EXACTLY parent keeps its
     *                                wrap-content measurement and merely ADDS
     *                                its share, and consumedExcessSpace is not
     *                                added back into the excess.
     *
     *  Defaults are the modern answers, which is also what an unreadable
     *  ApplicationInfo should degrade to: every app in the current corpus
     *  targets well past P, so guessing "legacy" on a lookup failure would
     *  mis-measure the common case rather than the rare one. */
    private static boolean sCompatibilityDone = false;
    private static boolean sRemeasureWeightedChildren = true;
    private boolean mAllowInconsistentMeasurement = false;

    /** AOSP View.sUseZeroUnspecifiedMeasureSpec (targetSdk &lt; M), the gate
     *  behind MeasureSpec.makeSafeMeasureSpec. Mac.apk's MeasureSpec has no
     *  makeSafeMeasureSpec, so the gate is latched here alongside the other
     *  targetSdk compat answers and applied by the local helper below. */
    private static boolean sUseZeroUnspecifiedMeasureSpec = false;

    /** AOSP MeasureSpec.makeSafeMeasureSpec: pre-M apps got a bare 0 for an
     *  UNSPECIFIED spec (size bits included), and some of them read the size
     *  back out, so the constant matters rather than being cosmetic. */
    private static int makeSafeMeasureSpec(int size, int mode) {
        if (sUseZeroUnspecifiedMeasureSpec && mode == MeasureSpec.UNSPECIFIED) return 0;
        return MeasureSpec.makeMeasureSpec(size, mode);
    }

    public LinearLayout(Context c) { super(c); macapkInitCompat(c); }
    public LinearLayout(Context c, android.util.AttributeSet a) { super(c, a); macapkInitCompat(c); }
    public LinearLayout(Context c, android.util.AttributeSet a, int s) { super(c, a, s); macapkInitCompat(c); }
    public LinearLayout(Context c, android.util.AttributeSet a, int s, int r) { super(c, a, s, r); macapkInitCompat(c); }

    /** AOSP runs this inline in the 4-arg constructor; Mac.apk's constructors
     *  do not chain, so it is factored out. The try/catch is a Mac.apk
     *  addition, not AOSP laxness: our tree has several Context implementations
     *  (framework-internal ones included) whose getApplicationInfo may be null
     *  or may throw, and a LinearLayout constructor that throws takes down
     *  every screen rather than mis-measuring one weighted row. */
    private void macapkInitCompat(Context context) {
        int targetSdkVersion = Integer.MAX_VALUE;
        // `resolved` exists because the sentinel alone cannot say whether the
        // read succeeded: targetSdkVersion is pre-set to the MODERN fallback,
        // so "still MAX_VALUE" and "the app really targets MAX_VALUE" are the
        // same value. Before this the latch below closed on the FALLBACK -- a
        // LinearLayout built against a half-wired Context froze
        // sRemeasureWeightedChildren / sUseZeroUnspecifiedMeasureSpec
        // PROCESS-WIDE off a transient, and no later constructor could correct
        // it. See docs/legacy-compat-audit_2026-08-24.md 5.1; ViewGroup
        // .macapkDefaultSplitMotionEvents and TextView
        // .macapkUseFallbackLineSpacing already only latch a REAL answer, and
        // this now matches them.
        boolean resolved = false;
        try {
            android.content.pm.ApplicationInfo ai =
                    (context != null) ? context.getApplicationInfo() : null;
            if (ai != null && android.app.Application.macapkTargetSdkKnown(ai.targetSdkVersion)) {
                targetSdkVersion = ai.targetSdkVersion;
                resolved = true;
            }
        } catch (Throwable ignored) { }
        if (!sCompatibilityDone && resolved) {
            sRemeasureWeightedChildren = targetSdkVersion >= android.os.Build.VERSION_CODES.P;
            sUseZeroUnspecifiedMeasureSpec = targetSdkVersion < android.os.Build.VERSION_CODES.M;
            sCompatibilityDone = true;
        }
        mAllowInconsistentMeasurement = targetSdkVersion <= android.os.Build.VERSION_CODES.M;
    }
    public void setOrientation(int orientation) { macapkOrientation = orientation; requestLayout(); }
    public int  getOrientation() { return macapkOrientation; }
    public void setGravity(int g) { macapkGravity = g; requestLayout(); }
    public int  getGravity() { return macapkGravity; }
    public void setWeightSum(float s) { macapkWeightSum = s; requestLayout(); }
    public float getWeightSum() { return macapkWeightSum; }
    // AOSP's setBaselineAligned/setBaselineAlignedChildIndex do NOT call
    // requestLayout() (verified against frameworks/base's LinearLayout.java) --
    // a runtime call only takes effect on whatever measure/layout pass the
    // caller triggers next by some other means. Mirrored exactly, even
    // though it looks surprising for a property setter.
    public void setBaselineAligned(boolean baselineAligned) { macapkBaselineAligned = baselineAligned; }
    public boolean isBaselineAligned() { return macapkBaselineAligned; }
    public int getBaselineAlignedChildIndex() { return macapkBaselineAlignedChildIndex; }
    public void setBaselineAlignedChildIndex(int i) {
        if (i < 0 || i >= getChildCount()) {
            throw new IllegalArgumentException("base aligned child index out "
                    + "of range (0, " + getChildCount() + ")");
        }
        macapkBaselineAlignedChildIndex = i;
    }

    /** AOSP LinearLayout.java:583-631 (android11-release), verbatim
     *  structure. Delegates to the designated child's OWN getBaseline() --
     *  per the v1.0.937 contract (View.getBaseline() already does the
     *  padding-aware font-ascent math), a container's getBaseline() must
     *  never recompute anything font-related, only offset the child's
     *  answer by mBaselineChildTop and this container's own gravity. */
    @Override
    public int getBaseline() {
        if (macapkBaselineAlignedChildIndex < 0) {
            return super.getBaseline();
        }
        if (getChildCount() <= macapkBaselineAlignedChildIndex) {
            throw new RuntimeException("mBaselineAlignedChildIndex of LinearLayout "
                    + "set to an index that is out of bounds.");
        }

        final View child = getChildAt(macapkBaselineAlignedChildIndex);
        final int childBaseline = child.getBaseline();
        if (childBaseline == -1) {
            if (macapkBaselineAlignedChildIndex == 0) {
                // default case, safe to return -1 (AOSP comment, verbatim intent)
                return -1;
            }
            throw new RuntimeException("mBaselineAlignedChildIndex of LinearLayout "
                    + "points to a View that doesn't know how to get its baseline.");
        }

        int childTop = mBaselineChildTop;
        if (macapkOrientation == VERTICAL) {
            final int majorGravity = macapkGravity & Gravity.VERTICAL_GRAVITY_MASK;
            if (majorGravity != Gravity.TOP) {
                switch (majorGravity) {
                    case Gravity.BOTTOM:
                        childTop = getBottom() - getTop() - getPaddingBottom() - mTotalLength;
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop += ((getBottom() - getTop() - getPaddingTop() - getPaddingBottom())
                                - mTotalLength) / 2;
                        break;
                }
            }
        }

        // AOSP casts to LinearLayout.LayoutParams and reads lp.topMargin
        // directly (an unchecked cast that CCEs on a foreign LP type). We
        // route through this file's existing topMargin() helper instead
        // (used everywhere else here, see the "LayoutParams field access"
        // section below) so a programmatically-added child carrying a plain
        // ViewGroup.LayoutParams degrades to 0 margin rather than throwing.
        return childTop + topMargin(child.getLayoutParams()) + childBaseline;
    }

    public void setMeasureWithLargestChildEnabled(boolean enabled) {
        mUseLargestChild = enabled;
        requestLayout();
    }
    public boolean isMeasureWithLargestChildEnabled() { return mUseLargestChild; }

    // ------------------------------------------------------------- dividers
    // AOSP LinearLayout.java:306-421. These were no-op stubs, which meant a
    // container that asked for dividers got neither the line nor the SPACE the
    // line occupies -- every child after the first landed one divider-width
    // too early, compounding down the row.

    private boolean isShowingDividers() {
        return (mShowDividers != SHOW_DIVIDER_NONE) && (mDivider != null);
    }

    public void setShowDividers(int showDividers) {
        if (showDividers == mShowDividers) return;
        mShowDividers = showDividers;
        setWillNotDraw(!isShowingDividers());
        requestLayout();
    }
    public int getShowDividers() { return mShowDividers; }

    public android.graphics.drawable.Drawable getDividerDrawable() { return mDivider; }

    /** The divider's extent comes from the drawable's INTRINSIC size
     *  (AOSP LinearLayout.java:368), not from a separately-settable dimension. */
    public void setDividerDrawable(android.graphics.drawable.Drawable divider) {
        if (divider == mDivider) return;
        mDivider = divider;
        if (divider != null) {
            mDividerWidth = divider.getIntrinsicWidth();
            mDividerHeight = divider.getIntrinsicHeight();
        } else {
            mDividerWidth = 0;
            mDividerHeight = 0;
        }
        setWillNotDraw(!isShowingDividers());
        requestLayout();
    }

    public void setDividerPadding(int padding) {
        if (padding == mDividerPadding) return;
        mDividerPadding = padding;
        if (isShowingDividers()) {
            requestLayout();
            invalidate();
        }
    }
    public int getDividerPadding() { return mDividerPadding; }
    public int getDividerWidth() { return mDividerWidth; }

    /** AOSP LinearLayout.hasDividerBeforeChildAt (LinearLayout.java:734-746).
     *  Asked with childIndex == getVirtualChildCount() it answers for the
     *  TRAILING divider. The allViewsAreGoneBefore branch is the subtle half:
     *  the first child that is not GONE counts as the BEGINNING, so a
     *  MIDDLE-only configuration must NOT put a divider in front of it even
     *  though it is not child 0. */
    protected boolean hasDividerBeforeChildAt(int childIndex) {
        if (childIndex == getVirtualChildCount()) {
            return (mShowDividers & SHOW_DIVIDER_END) != 0;
        }
        boolean allViewsAreGoneBefore = allViewsAreGoneBefore(childIndex);
        if (allViewsAreGoneBefore) {
            return (mShowDividers & SHOW_DIVIDER_BEGINNING) != 0;
        } else {
            return (mShowDividers & SHOW_DIVIDER_MIDDLE) != 0;
        }
    }

    private boolean allViewsAreGoneBefore(int childIndex) {
        for (int i = childIndex - 1; i >= 0; i--) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != View.GONE) return false;
        }
        return true;
    }

    private View getLastNonGoneChild() {
        for (int i = getVirtualChildCount() - 1; i >= 0; i--) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != View.GONE) return child;
        }
        return null;
    }

    @Override protected void onDraw(android.graphics.Canvas canvas) {
        if (mDivider == null) return;
        if (macapkOrientation == VERTICAL) {
            drawDividersVertical(canvas);
        } else {
            drawDividersHorizontal(canvas);
        }
    }

    void drawDividersVertical(android.graphics.Canvas canvas) {
        final int count = getVirtualChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != View.GONE) {
                if (hasDividerBeforeChildAt(i)) {
                    final ViewGroup.LayoutParams lp = child.getLayoutParams();
                    final int top = child.getTop() - topMargin(lp) - mDividerHeight;
                    drawHorizontalDivider(canvas, top);
                }
            }
        }
        if (hasDividerBeforeChildAt(count)) {
            final View child = getLastNonGoneChild();
            int bottom;
            if (child == null) {
                bottom = getHeight() - getPaddingBottom() - mDividerHeight;
            } else {
                bottom = child.getBottom() + bottomMargin(child.getLayoutParams());
            }
            drawHorizontalDivider(canvas, bottom);
        }
    }

    void drawDividersHorizontal(android.graphics.Canvas canvas) {
        final int count = getVirtualChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child != null && child.getVisibility() != View.GONE) {
                if (hasDividerBeforeChildAt(i)) {
                    final ViewGroup.LayoutParams lp = child.getLayoutParams();
                    drawVerticalDivider(canvas, child.getLeft() - leftMargin(lp) - mDividerWidth);
                }
            }
        }
        if (hasDividerBeforeChildAt(count)) {
            final View child = getLastNonGoneChild();
            int position;
            if (child == null) {
                position = getWidth() - getPaddingRight() - mDividerWidth;
            } else {
                position = child.getRight() + rightMargin(child.getLayoutParams());
            }
            drawVerticalDivider(canvas, position);
        }
    }

    void drawHorizontalDivider(android.graphics.Canvas canvas, int top) {
        mDivider.setBounds(getPaddingLeft() + mDividerPadding, top,
                getWidth() - getPaddingRight() - mDividerPadding, top + mDividerHeight);
        mDivider.draw(canvas);
    }

    void drawVerticalDivider(android.graphics.Canvas canvas, int left) {
        mDivider.setBounds(left, getPaddingTop() + mDividerPadding,
                left + mDividerWidth, getHeight() - getPaddingBottom() - mDividerPadding);
        mDivider.draw(canvas);
    }

    // ------------------------------------------------- virtual-child protocol
    // AOSP's measure/layout bodies never call getChildAt directly: TableRow
    // needs to DEFLECT an index (a collapsed column) and TableLayout needs to
    // account for a null slot. The defaults here are plain passthroughs, so
    // this is purely the seam AOSP has; TableRow already overrides the first
    // two in this tree.

    View getVirtualChildAt(int index) { return getChildAt(index); }
    int getVirtualChildCount() { return getChildCount(); }
    int measureNullChild(int childIndex) { return 0; }
    int getChildrenSkipCount(View child, int index) { return 0; }
    int getLocationOffset(View child) { return 0; }
    int getNextLocationOffset(View child) { return 0; }
    void measureChildBeforeLayout(View child, int childIndex, int widthMeasureSpec,
            int totalWidth, int heightMeasureSpec, int totalHeight) {
        measureChildWithMargins(child, widthMeasureSpec, totalWidth,
                heightMeasureSpec, totalHeight);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Tier 3 M5 is gated on MACAPK_REAL_LOOPER so the SHIPPED (flag-off)
        // build keeps the historical behavior exactly: LinearLayout inherited
        // the generic ViewGroup.onMeasure/onLayout and the ViewCanvasDriver
        // fixup hacks did the real stacking. Flag-on takes the faithful path.
        if (!android.os.Looper.MACAPK_REAL_LOOPER) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        if (macapkOrientation == VERTICAL) {
            measureVertical(widthMeasureSpec, heightMeasureSpec);
        } else {
            measureHorizontal(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!android.os.Looper.MACAPK_REAL_LOOPER) {
            super.onLayout(changed, l, t, r, b);
            return;
        }
        if (macapkOrientation == VERTICAL) {
            layoutVertical(l, t, r, b);
        } else {
            layoutHorizontal(l, t, r, b);
        }
    }

    // ---------------------------------------------------------------- measure

    private void measureVertical(int widthMeasureSpec, int heightMeasureSpec) {
        mTotalLength = 0;
        int maxWidth = 0;
        int childState = 0;
        int alternativeMaxWidth = 0;
        int weightedMaxWidth = 0;
        boolean allFillParent = true;
        float totalWeight = 0;
        boolean matchWidth = false;
        boolean skippedMeasure = false;
        int consumedExcessSpace = 0;
        int nonSkippedChildCount = 0;
        int largestChildHeight = Integer.MIN_VALUE;

        final int count = getVirtualChildCount();
        final int widthMode  = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final boolean useLargestChild = mUseLargestChild;
        // AOSP LinearLayout.java:788 (android11-release): captured once up
        // front so the mBaselineChildTop latch below always compares
        // against the value in effect for this whole measure pass.
        final int baselineChildIndex = macapkBaselineAlignedChildIndex;

        // First pass: measure each child along the major (height) axis.
        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child == null) {
                mTotalLength += measureNullChild(i);
                continue;
            }
            if (child.getVisibility() == View.GONE) {
                // A GONE child contributes NOTHING -- not its size, and not its
                // margins. AOSP continues before mTotalLength is touched
                // (LinearLayout.java:805); accumulating the margins of a GONE
                // child shifts everything after it and reads as a spacing bug.
                i += getChildrenSkipCount(child, i);
                continue;
            }

            nonSkippedChildCount++;
            if (hasDividerBeforeChildAt(i)) {
                mTotalLength += mDividerHeight;
            }

            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            final float weight = weightOf(lp);
            final int lpW = widthOf(lp), lpH = heightOf(lp);
            totalWeight += weight;

            final boolean useExcessSpace = (lpH == 0 && weight > 0);
            if (heightMode == MeasureSpec.EXACTLY && useExcessSpace) {
                // Laid out purely from excess space -- measure later.
                final int totalLength = mTotalLength;
                mTotalLength = Math.max(totalLength, totalLength + topMargin(lp) + bottomMargin(lp));
                skippedMeasure = true;
            } else {
                if (useExcessSpace) lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                final int usedHeight = totalWeight == 0 ? mTotalLength : 0;
                measureChildBeforeLayout(child, i, widthMeasureSpec, 0,
                        heightMeasureSpec, usedHeight);
                final int childHeight = child.getMeasuredHeight();
                if (useExcessSpace) { lp.height = 0; consumedExcessSpace += childHeight; }
                // AOSP's max() guard, not a plain +=: a child with a NEGATIVE
                // margin large enough to swallow itself must not shorten the
                // container below where it already reached.
                final int totalLength = mTotalLength;
                mTotalLength = Math.max(totalLength, totalLength + childHeight + topMargin(lp)
                        + bottomMargin(lp) + getNextLocationOffset(child));
                if (useLargestChild) {
                    largestChildHeight = Math.max(childHeight, largestChildHeight);
                }
            }

            // AOSP LinearLayout.java:865-869 (android11-release): once child
            // i's extent (incl. margins) is folded into mTotalLength above,
            // latch mBaselineChildTop when child i is the one immediately
            // BEFORE the baseline-designated child (i+1 == baselineChildIndex)
            // -- the running total up to but not including the baseline
            // child, which is exactly the "top of the baseline child" that
            // getBaseline() needs.
            if (baselineChildIndex >= 0 && baselineChildIndex == i + 1) {
                mBaselineChildTop = mTotalLength;
            }

            // AOSP :871-879: the bookkeeping above only works if no child
            // ABOVE the baseline child carries a weight (the weight-
            // redistribution pass further down rewrites mTotalLength, which
            // would make the latch stale). Fail fast rather than silently
            // mis-report a baseline. Inert unless setBaselineAlignedChildIndex
            // is actually used -- measured ZERO uses across the 15-app fleet
            // pair set (docs/fwprobe-window-view-divergences_2026-08-10.md).
            if (i < baselineChildIndex && weight > 0) {
                throw new RuntimeException("A child of LinearLayout with index "
                        + "less than mBaselineAlignedChildIndex has weight > 0, which "
                        + "won't work.  Either remove the weight, or don't set "
                        + "mBaselineAlignedChildIndex.");
            }

            boolean matchWidthLocally = false;
            if (widthMode != MeasureSpec.EXACTLY && lpW == ViewGroup.LayoutParams.MATCH_PARENT) {
                matchWidth = true;
                matchWidthLocally = true;
            }
            final int margin = leftMargin(lp) + rightMargin(lp);
            final int measuredWidth = child.getMeasuredWidth() + margin;
            maxWidth = Math.max(maxWidth, measuredWidth);
            childState = combineMeasuredStates(childState, child.getMeasuredState());
            allFillParent = allFillParent && (lpW == ViewGroup.LayoutParams.MATCH_PARENT);
            if (weight > 0) {
                // Widths of weighted views are bogus if we end up remeasuring.
                weightedMaxWidth = Math.max(weightedMaxWidth, matchWidthLocally ? margin : measuredWidth);
            } else {
                alternativeMaxWidth = Math.max(alternativeMaxWidth, matchWidthLocally ? margin : measuredWidth);
            }

            i += getChildrenSkipCount(child, i);
        }

        if (nonSkippedChildCount > 0 && hasDividerBeforeChildAt(count)) {
            mTotalLength += mDividerHeight;
        }

        // measureWithLargestChild: with no hard limit on our own height, every
        // child counts as being as tall as the tallest one (AOSP :916-939).
        if (useLargestChild
                && (heightMode == MeasureSpec.AT_MOST || heightMode == MeasureSpec.UNSPECIFIED)) {
            mTotalLength = 0;
            for (int i = 0; i < count; i++) {
                final View child = getVirtualChildAt(i);
                if (child == null) { mTotalLength += measureNullChild(i); continue; }
                if (child.getVisibility() == View.GONE) {
                    i += getChildrenSkipCount(child, i);
                    continue;
                }
                final ViewGroup.LayoutParams lp = child.getLayoutParams();
                final int totalLength = mTotalLength;
                mTotalLength = Math.max(totalLength, totalLength + largestChildHeight
                        + topMargin(lp) + bottomMargin(lp) + getNextLocationOffset(child));
            }
        }

        mTotalLength += getPaddingTop() + getPaddingBottom();
        int heightSize = Math.max(mTotalLength, getSuggestedMinimumHeight());
        final int heightSizeAndState = resolveSizeAndState(heightSize, heightMeasureSpec, 0);
        heightSize = heightSizeAndState & MEASURED_SIZE_MASK;

        // Distribute remaining (or deficit) space to weighted children.
        int remainingExcess = heightSize - mTotalLength
                + (mAllowInconsistentMeasurement ? 0 : consumedExcessSpace);
        if (skippedMeasure
                || ((sRemeasureWeightedChildren || remainingExcess != 0) && totalWeight > 0f)) {
            float remainingWeightSum = macapkWeightSum > 0f ? macapkWeightSum : totalWeight;
            mTotalLength = 0;
            for (int i = 0; i < count; i++) {
                final View child = getVirtualChildAt(i);
                if (child == null || child.getVisibility() == View.GONE) continue;
                final ViewGroup.LayoutParams lp = child.getLayoutParams();
                final float childWeight = weightOf(lp);
                final int lpW = widthOf(lp), lpH = heightOf(lp);
                if (childWeight > 0) {
                    // Integer-truncated PER CHILD with the remainder carried
                    // forward in remainingExcess, so the LAST weighted child
                    // absorbs the slack of an uneven division. Rounding each
                    // share independently, or dividing once up front, is the
                    // classic source of a few-pixel drift down a weighted row.
                    final int share = (int) (childWeight * remainingExcess / remainingWeightSum);
                    remainingExcess -= share;
                    remainingWeightSum -= childWeight;

                    final int childHeight;
                    if (mUseLargestChild && heightMode != MeasureSpec.EXACTLY) {
                        childHeight = largestChildHeight;
                    } else if (lpH == 0 && (!mAllowInconsistentMeasurement
                            || heightMode == MeasureSpec.EXACTLY)) {
                        // Laid out from scratch out of its share alone.
                        childHeight = share;
                    } else {
                        // Had intrinsic height; the share is ADDED to it. This
                        // is why height=0, WRAP_CONTENT and MATCH_PARENT with
                        // the same weight are three different results.
                        childHeight = child.getMeasuredHeight() + share;
                    }

                    final int childHeightSpec = MeasureSpec.makeMeasureSpec(
                            Math.max(0, childHeight), MeasureSpec.EXACTLY);
                    final int childWidthSpec = getChildMeasureSpec(widthMeasureSpec,
                            getPaddingLeft() + getPaddingRight() + leftMargin(lp) + rightMargin(lp), lpW);
                    child.measure(childWidthSpec, childHeightSpec);

                    // Child may now not fit in the vertical dimension.
                    childState = combineMeasuredStates(childState, child.getMeasuredState()
                            & (MEASURED_STATE_MASK >> MEASURED_HEIGHT_STATE_SHIFT));
                }
                final int margin = leftMargin(lp) + rightMargin(lp);
                final int measuredWidth = child.getMeasuredWidth() + margin;
                maxWidth = Math.max(maxWidth, measuredWidth);
                final boolean matchWidthLocally = widthMode != MeasureSpec.EXACTLY
                        && lpW == ViewGroup.LayoutParams.MATCH_PARENT;
                alternativeMaxWidth = Math.max(alternativeMaxWidth, matchWidthLocally ? margin : measuredWidth);
                allFillParent = allFillParent && (lpW == ViewGroup.LayoutParams.MATCH_PARENT);
                final int totalLength = mTotalLength;
                mTotalLength = Math.max(totalLength, totalLength + child.getMeasuredHeight()
                        + topMargin(lp) + bottomMargin(lp) + getNextLocationOffset(child));
            }
            mTotalLength += getPaddingTop() + getPaddingBottom();
        } else {
            alternativeMaxWidth = Math.max(alternativeMaxWidth, weightedMaxWidth);

            // No limit, so every weighted view becomes as tall as the largest
            // child; they have all been measured once already (AOSP :1029).
            if (useLargestChild && heightMode != MeasureSpec.EXACTLY) {
                for (int i = 0; i < count; i++) {
                    final View child = getVirtualChildAt(i);
                    if (child == null || child.getVisibility() == View.GONE) continue;
                    if (weightOf(child.getLayoutParams()) > 0) {
                        child.measure(
                                MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(), MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(largestChildHeight, MeasureSpec.EXACTLY));
                    }
                }
            }
        }

        if (!allFillParent && widthMode != MeasureSpec.EXACTLY) maxWidth = alternativeMaxWidth;
        maxWidth += getPaddingLeft() + getPaddingRight();
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                heightSizeAndState);

        if (matchWidth) forceUniformWidth(count, heightMeasureSpec);
    }

    /** Re-measure MATCH_PARENT-width children to the now-known exact width so a
     *  WRAP_CONTENT-width vertical LinearLayout still stretches its
     *  fill-width children (AOSP LinearLayout.forceUniformWidth). */
    private void forceUniformWidth(int count, int heightMeasureSpec) {
        final int uniformMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child == null || child.getVisibility() == View.GONE) continue;
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (widthOf(lp) == ViewGroup.LayoutParams.MATCH_PARENT) {
                final int oldHeight = lp.height;
                lp.height = child.getMeasuredHeight();
                measureChildWithMargins(child, uniformMeasureSpec, 0, heightMeasureSpec, 0);
                lp.height = oldHeight;
            }
        }
    }

    private void measureHorizontal(int widthMeasureSpec, int heightMeasureSpec) {
        mTotalLength = 0;
        int maxHeight = 0;
        int childState = 0;
        int alternativeMaxHeight = 0;
        int weightedMaxHeight = 0;
        boolean allFillParent = true;
        float totalWeight = 0;
        boolean matchHeight = false;
        boolean skippedMeasure = false;
        int consumedExcessSpace = 0;
        int nonSkippedChildCount = 0;
        int largestChildWidth = Integer.MIN_VALUE;

        final int count = getVirtualChildCount();
        final int widthMode  = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final boolean useLargestChild = mUseLargestChild;
        // AOSP LinearLayout.java:1133. measureHorizontal is NOT a transpose of
        // measureVertical: under an EXACTLY width it accumulates mTotalLength
        // with a plain += and skips the negative-margin max() guard (:1216).
        final boolean isExactly = widthMode == MeasureSpec.EXACTLY;

        final boolean baselineAligned = macapkBaselineAligned;
        if (mMaxAscent == null || mMaxDescent == null) {
            mMaxAscent = new int[VERTICAL_GRAVITY_COUNT];
            mMaxDescent = new int[VERTICAL_GRAVITY_COUNT];
        }
        final int[] maxAscent = mMaxAscent;
        final int[] maxDescent = mMaxDescent;
        maxAscent[0] = maxAscent[1] = maxAscent[2] = maxAscent[3] = -1;
        maxDescent[0] = maxDescent[1] = maxDescent[2] = maxDescent[3] = -1;

        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child == null) {
                mTotalLength += measureNullChild(i);
                continue;
            }
            if (child.getVisibility() == View.GONE) {
                i += getChildrenSkipCount(child, i);
                continue;
            }

            nonSkippedChildCount++;
            if (hasDividerBeforeChildAt(i)) {
                mTotalLength += mDividerWidth;
            }

            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            final float weight = weightOf(lp);
            final int lpW = widthOf(lp), lpH = heightOf(lp);
            totalWeight += weight;

            final boolean useExcessSpace = (lpW == 0 && weight > 0);
            if (widthMode == MeasureSpec.EXACTLY && useExcessSpace) {
                if (isExactly) {
                    mTotalLength += leftMargin(lp) + rightMargin(lp);
                } else {
                    final int totalLength = mTotalLength;
                    mTotalLength = Math.max(totalLength,
                            totalLength + leftMargin(lp) + rightMargin(lp));
                }
                // Baseline alignment needs a real measurement to get the
                // baseline offset, which DEFEATS the skip-measure optimisation
                // (AOSP :1180). So flipping baselineAligned changes the
                // measurement even when every child reports a baseline of -1.
                if (baselineAligned) {
                    final int freeWidthSpec = makeSafeMeasureSpec(
                            MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.UNSPECIFIED);
                    final int freeHeightSpec = makeSafeMeasureSpec(
                            MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED);
                    child.measure(freeWidthSpec, freeHeightSpec);
                } else {
                    skippedMeasure = true;
                }
            } else {
                if (useExcessSpace) lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                final int usedWidth = totalWeight == 0 ? mTotalLength : 0;
                measureChildBeforeLayout(child, i, widthMeasureSpec, usedWidth,
                        heightMeasureSpec, 0);
                final int childWidth = child.getMeasuredWidth();
                if (useExcessSpace) { lp.width = 0; consumedExcessSpace += childWidth; }
                if (isExactly) {
                    mTotalLength += childWidth + leftMargin(lp) + rightMargin(lp)
                            + getNextLocationOffset(child);
                } else {
                    final int totalLength = mTotalLength;
                    mTotalLength = Math.max(totalLength, totalLength + childWidth
                            + leftMargin(lp) + rightMargin(lp) + getNextLocationOffset(child));
                }
                if (useLargestChild) {
                    largestChildWidth = Math.max(childWidth, largestChildWidth);
                }
            }

            boolean matchHeightLocally = false;
            if (heightMode != MeasureSpec.EXACTLY && lpH == ViewGroup.LayoutParams.MATCH_PARENT) {
                matchHeight = true;
                matchHeightLocally = true;
            }
            final int margin = topMargin(lp) + bottomMargin(lp);
            final int measuredHeight = child.getMeasuredHeight() + margin;
            childState = combineMeasuredStates(childState, child.getMeasuredState());
            if (baselineAligned) {
                final int childBaseline = child.getBaseline();
                if (childBaseline != -1) {
                    final int index = baselineGravityIndex(lp);
                    maxAscent[index] = Math.max(maxAscent[index], childBaseline);
                    maxDescent[index] = Math.max(maxDescent[index], measuredHeight - childBaseline);
                }
            }
            maxHeight = Math.max(maxHeight, measuredHeight);
            allFillParent = allFillParent && (lpH == ViewGroup.LayoutParams.MATCH_PARENT);
            if (weight > 0) {
                weightedMaxHeight = Math.max(weightedMaxHeight, matchHeightLocally ? margin : measuredHeight);
            } else {
                alternativeMaxHeight = Math.max(alternativeMaxHeight, matchHeightLocally ? margin : measuredHeight);
            }

            i += getChildrenSkipCount(child, i);
        }

        if (nonSkippedChildCount > 0 && hasDividerBeforeChildAt(count)) {
            mTotalLength += mDividerWidth;
        }

        // AOSP: fold the per-bucket ascent/descent into the row's height
        // requirement (mMaxAscent[INDEX_TOP] checked first -- TOP is the
        // common case). No-op unless some child reported a real baseline.
        if (maxAscent[INDEX_TOP] != -1 || maxAscent[INDEX_CENTER_VERTICAL] != -1
                || maxAscent[INDEX_BOTTOM] != -1 || maxAscent[INDEX_FILL] != -1) {
            final int ascent = Math.max(maxAscent[INDEX_FILL], Math.max(maxAscent[INDEX_CENTER_VERTICAL],
                    Math.max(maxAscent[INDEX_TOP], maxAscent[INDEX_BOTTOM])));
            final int descent = Math.max(maxDescent[INDEX_FILL], Math.max(maxDescent[INDEX_CENTER_VERTICAL],
                    Math.max(maxDescent[INDEX_TOP], maxDescent[INDEX_BOTTOM])));
            maxHeight = Math.max(maxHeight, ascent + descent);
        }

        // measureWithLargestChild with no hard width limit (AOSP :1295-1322).
        if (useLargestChild
                && (widthMode == MeasureSpec.AT_MOST || widthMode == MeasureSpec.UNSPECIFIED)) {
            mTotalLength = 0;
            for (int i = 0; i < count; i++) {
                final View child = getVirtualChildAt(i);
                if (child == null) { mTotalLength += measureNullChild(i); continue; }
                if (child.getVisibility() == View.GONE) {
                    i += getChildrenSkipCount(child, i);
                    continue;
                }
                final ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (isExactly) {
                    mTotalLength += largestChildWidth + leftMargin(lp) + rightMargin(lp)
                            + getNextLocationOffset(child);
                } else {
                    final int totalLength = mTotalLength;
                    mTotalLength = Math.max(totalLength, totalLength + largestChildWidth
                            + leftMargin(lp) + rightMargin(lp) + getNextLocationOffset(child));
                }
            }
        }

        mTotalLength += getPaddingLeft() + getPaddingRight();
        int widthSize = Math.max(mTotalLength, getSuggestedMinimumWidth());
        final int widthSizeAndState = resolveSizeAndState(widthSize, widthMeasureSpec, 0);
        widthSize = widthSizeAndState & MEASURED_SIZE_MASK;

        int remainingExcess = widthSize - mTotalLength
                + (mAllowInconsistentMeasurement ? 0 : consumedExcessSpace);
        if (skippedMeasure
                || ((sRemeasureWeightedChildren || remainingExcess != 0) && totalWeight > 0f)) {
            float remainingWeightSum = macapkWeightSum > 0f ? macapkWeightSum : totalWeight;
            maxAscent[0] = maxAscent[1] = maxAscent[2] = maxAscent[3] = -1;
            maxDescent[0] = maxDescent[1] = maxDescent[2] = maxDescent[3] = -1;
            // AOSP resets maxHeight to -1 here (:1347): the second pass
            // recomputes it from scratch over the RE-measured children, so a
            // first-pass height that the re-measure invalidated must not
            // survive as a floor.
            maxHeight = -1;
            mTotalLength = 0;
            for (int i = 0; i < count; i++) {
                final View child = getVirtualChildAt(i);
                if (child == null || child.getVisibility() == View.GONE) continue;
                final ViewGroup.LayoutParams lp = child.getLayoutParams();
                final float childWeight = weightOf(lp);
                final int lpW = widthOf(lp), lpH = heightOf(lp);
                if (childWeight > 0) {
                    // Integer-truncated per child, remainder carried forward.
                    final int share = (int) (childWeight * remainingExcess / remainingWeightSum);
                    remainingExcess -= share;
                    remainingWeightSum -= childWeight;

                    final int childWidth;
                    if (mUseLargestChild && widthMode != MeasureSpec.EXACTLY) {
                        childWidth = largestChildWidth;
                    } else if (lpW == 0 && (!mAllowInconsistentMeasurement
                            || widthMode == MeasureSpec.EXACTLY)) {
                        childWidth = share;
                    } else {
                        childWidth = child.getMeasuredWidth() + share;
                    }

                    final int childWidthSpec = MeasureSpec.makeMeasureSpec(
                            Math.max(0, childWidth), MeasureSpec.EXACTLY);
                    final int childHeightSpec = getChildMeasureSpec(heightMeasureSpec,
                            getPaddingTop() + getPaddingBottom() + topMargin(lp) + bottomMargin(lp), lpH);
                    child.measure(childWidthSpec, childHeightSpec);

                    // Child may now not fit in the horizontal dimension.
                    childState = combineMeasuredStates(childState,
                            child.getMeasuredState() & MEASURED_STATE_MASK);
                }
                if (isExactly) {
                    mTotalLength += child.getMeasuredWidth() + leftMargin(lp) + rightMargin(lp)
                            + getNextLocationOffset(child);
                } else {
                    final int totalLength = mTotalLength;
                    mTotalLength = Math.max(totalLength, totalLength + child.getMeasuredWidth()
                            + leftMargin(lp) + rightMargin(lp) + getNextLocationOffset(child));
                }
                final int margin = topMargin(lp) + bottomMargin(lp);
                final int measuredHeight = child.getMeasuredHeight() + margin;
                if (baselineAligned) {
                    final int childBaseline = child.getBaseline();
                    if (childBaseline != -1) {
                        final int index = baselineGravityIndex(lp);
                        maxAscent[index] = Math.max(maxAscent[index], childBaseline);
                        maxDescent[index] = Math.max(maxDescent[index], measuredHeight - childBaseline);
                    }
                }
                maxHeight = Math.max(maxHeight, measuredHeight);
                final boolean matchHeightLocally = heightMode != MeasureSpec.EXACTLY
                        && lpH == ViewGroup.LayoutParams.MATCH_PARENT;
                alternativeMaxHeight = Math.max(alternativeMaxHeight, matchHeightLocally ? margin : measuredHeight);
                allFillParent = allFillParent && (lpH == ViewGroup.LayoutParams.MATCH_PARENT);
            }
            mTotalLength += getPaddingLeft() + getPaddingRight();
            if (maxAscent[INDEX_TOP] != -1 || maxAscent[INDEX_CENTER_VERTICAL] != -1
                    || maxAscent[INDEX_BOTTOM] != -1 || maxAscent[INDEX_FILL] != -1) {
                final int ascent = Math.max(maxAscent[INDEX_FILL], Math.max(maxAscent[INDEX_CENTER_VERTICAL],
                        Math.max(maxAscent[INDEX_TOP], maxAscent[INDEX_BOTTOM])));
                final int descent = Math.max(maxDescent[INDEX_FILL], Math.max(maxDescent[INDEX_CENTER_VERTICAL],
                        Math.max(maxDescent[INDEX_TOP], maxDescent[INDEX_BOTTOM])));
                maxHeight = Math.max(maxHeight, ascent + descent);
            }
        } else {
            alternativeMaxHeight = Math.max(alternativeMaxHeight, weightedMaxHeight);

            // No limit, so every weighted view becomes as wide as the largest
            // child; they have all been measured once already (AOSP :1449).
            if (useLargestChild && widthMode != MeasureSpec.EXACTLY) {
                for (int i = 0; i < count; i++) {
                    final View child = getVirtualChildAt(i);
                    if (child == null || child.getVisibility() == View.GONE) continue;
                    if (weightOf(child.getLayoutParams()) > 0) {
                        child.measure(
                                MeasureSpec.makeMeasureSpec(largestChildWidth, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(), MeasureSpec.EXACTLY));
                    }
                }
            }
        }

        if (!allFillParent && heightMode != MeasureSpec.EXACTLY) maxHeight = alternativeMaxHeight;
        maxHeight += getPaddingTop() + getPaddingBottom();
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());

        setMeasuredDimension(widthSizeAndState | (childState & MEASURED_STATE_MASK),
                resolveSizeAndState(maxHeight, heightMeasureSpec,
                        childState << MEASURED_HEIGHT_STATE_SHIFT));

        if (matchHeight) forceUniformHeight(count, widthMeasureSpec);
    }

    private void forceUniformHeight(int count, int widthMeasureSpec) {
        final int uniformMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child == null || child.getVisibility() == View.GONE) continue;
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (heightOf(lp) == ViewGroup.LayoutParams.MATCH_PARENT) {
                final int oldWidth = lp.width;
                lp.width = child.getMeasuredWidth();
                measureChildWithMargins(child, widthMeasureSpec, 0, uniformMeasureSpec, 0);
                lp.width = oldWidth;
            }
        }
    }

    // ----------------------------------------------------------------- layout

    private void layoutVertical(int left, int top, int right, int bottom) {
        final int paddingLeft = getPaddingLeft();
        final int width = right - left;
        final int childRight = width - getPaddingRight();
        final int childSpace = width - paddingLeft - getPaddingRight();
        final int count = getVirtualChildCount();

        final int majorGravity = macapkGravity & Gravity.VERTICAL_GRAVITY_MASK;
        // RTL-RESOLUTION-COUPLING (1 of 3 sites in this file; grep the tag).
        // RELATIVE, not plain HORIZONTAL: the container gravity may be
        // START/END, whose RELATIVE_LAYOUT_DIRECTION bit the plain mask drops,
        // silently turning END into RIGHT-under-LTR even in an RTL tree. This
        // and the two sites in layoutHorizontal only ever do anything when
        // getLayoutDirection() answers RTL, so they are correct exactly when
        // View's direction RESOLUTION is correct -- see the note on the
        // reversal site below.
        final int minorGravity = macapkGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;

        int childTop;
        switch (majorGravity) {
            case Gravity.BOTTOM:          childTop = getPaddingTop() + bottom - top - mTotalLength; break;
            case Gravity.CENTER_VERTICAL: childTop = getPaddingTop() + (bottom - top - mTotalLength) / 2; break;
            default:                      childTop = getPaddingTop(); break;   // TOP
        }

        for (int i = 0; i < count; i++) {
            final View child = getVirtualChildAt(i);
            if (child == null) {
                childTop += measureNullChild(i);
                continue;
            }
            if (child.getVisibility() == View.GONE) continue;
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            final int childWidth  = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            int gravity = childGravity(lp);
            if (gravity < 0) gravity = minorGravity;
            final int layoutDirection = getLayoutDirection();
            final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
            final int horiz = absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK;

            int childLeft;
            if (horiz == Gravity.CENTER_HORIZONTAL) {
                childLeft = paddingLeft + (childSpace - childWidth) / 2 + leftMargin(lp) - rightMargin(lp);
            } else if (horiz == Gravity.RIGHT) {
                childLeft = childRight - childWidth - rightMargin(lp);
            } else {
                childLeft = paddingLeft + leftMargin(lp);
            }

            if (hasDividerBeforeChildAt(i)) {
                childTop += mDividerHeight;
            }
            childTop += topMargin(lp);
            final int y = childTop + getLocationOffset(child);
            child.layout(childLeft, y, childLeft + childWidth, y + childHeight);
            childTop += childHeight + bottomMargin(lp) + getNextLocationOffset(child);

            i += getChildrenSkipCount(child, i);
        }
    }

    /** AOSP LinearLayout.onRtlPropertiesChanged (:1683): only a HORIZONTAL
     *  LinearLayout re-lays-out on a direction flip, because only its CHILD
     *  ORDER depends on the direction. */
    @Override public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        if (layoutDirection != macapkLayoutDirection) {
            macapkLayoutDirection = layoutDirection;
            if (macapkOrientation == HORIZONTAL) requestLayout();
        }
    }
    private int macapkLayoutDirection = -1;

    private void layoutHorizontal(int left, int top, int right, int bottom) {
        final boolean isLayoutRtl = isLayoutRtl();
        final int paddingTop = getPaddingTop();
        final int height = bottom - top;
        final int childBottom = height - getPaddingBottom();
        final int childSpace = height - paddingTop - getPaddingBottom();
        final int count = getVirtualChildCount();

        // RTL-RESOLUTION-COUPLING (2 of 3): START/END container gravity keeps
        // its relative bit so getAbsoluteGravity below can resolve it.
        final int majorGravity = macapkGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
        final int minorGravity = macapkGravity & Gravity.VERTICAL_GRAVITY_MASK;
        final boolean baselineAligned = macapkBaselineAligned;

        final int layoutDirection = getLayoutDirection();
        int childLeft;
        switch (Gravity.getAbsoluteGravity(majorGravity, layoutDirection)) {
            case Gravity.RIGHT:             childLeft = getPaddingLeft() + right - left - mTotalLength; break;
            case Gravity.CENTER_HORIZONTAL: childLeft = getPaddingLeft() + (right - left - mTotalLength) / 2; break;
            default:                        childLeft = getPaddingLeft(); break;   // LEFT
        }

        // RTL-RESOLUTION-COUPLING (3 of 3). AOSP :1747-1753: under RTL the row
        // is laid out from the LAST child backwards. This is the half a
        // reimplementation usually omits, and omitting it yields a
        // plausible-looking row that is simply mirrored wrong -- gravity
        // resolution alone moves the block, not the order.
        //
        // All three tagged sites are inert while getLayoutDirection() answers
        // LTR, which is every LTR-locale app. They become VISIBLE the moment
        // anything resolves to RTL, so they are only as right as View's
        // resolution is. AOSP resolves direction during the attach/layout
        // traversal, so a DETACHED view answers LTR no matter what
        // setLayoutDirection was told; Mac.apk's View currently resolves
        // eagerly, which makes a detached RTL tree behave RTL here where
        // Android would still be LTR.
        int start = 0;
        int dir = 1;
        if (isLayoutRtl) {
            start = count - 1;
            dir = -1;
        }

        for (int i = 0; i < count; i++) {
            final int childIndex = start + dir * i;
            final View child = getVirtualChildAt(childIndex);
            if (child == null) {
                childLeft += measureNullChild(childIndex);
                continue;
            }
            if (child.getVisibility() == View.GONE) continue;
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            final int childWidth  = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            // AOSP: a baseline offset only applies when the child isn't
            // stretched to MATCH_PARENT height (a stretched child has no
            // single well-defined baseline position to align to).
            int childBaseline = -1;
            if (baselineAligned && heightOf(lp) != ViewGroup.LayoutParams.MATCH_PARENT) {
                childBaseline = child.getBaseline();
            }

            int gravity = childGravity(lp);
            if (gravity < 0) gravity = minorGravity;
            final int vert = gravity & Gravity.VERTICAL_GRAVITY_MASK;

            int childTop;
            if (vert == Gravity.CENTER_VERTICAL) {
                // AOSP deliberately does NOT baseline-align CENTER_VERTICAL
                // children (removed for bug 1038483: a baseline offset here
                // could push a taller sibling outside the parent's bounds).
                childTop = paddingTop + (childSpace - childHeight) / 2 + topMargin(lp) - bottomMargin(lp);
            } else if (vert == Gravity.BOTTOM) {
                childTop = childBottom - childHeight - bottomMargin(lp);
                if (childBaseline != -1 && mMaxDescent != null) {
                    final int descent = child.getMeasuredHeight() - childBaseline;
                    childTop -= (mMaxDescent[INDEX_BOTTOM] - descent);
                }
            } else if (vert == Gravity.TOP) {
                childTop = paddingTop + topMargin(lp);
                if (childBaseline != -1 && mMaxAscent != null) {
                    childTop += mMaxAscent[INDEX_TOP] - childBaseline;
                }
            } else {
                // AOSP's `default:` arm (:1808) -- a gravity with NO vertical
                // bits at all pins to paddingTop and drops the top margin. It
                // looks like an AOSP oversight, but it is observable, so it is
                // mirrored rather than "corrected".
                childTop = paddingTop;
            }

            if (hasDividerBeforeChildAt(childIndex)) {
                childLeft += mDividerWidth;
            }
            childLeft += leftMargin(lp);
            final int x = childLeft + getLocationOffset(child);
            child.layout(x, childTop, x + childWidth, childTop + childHeight);
            childLeft += childWidth + rightMargin(lp) + getNextLocationOffset(child);

            i += getChildrenSkipCount(child, childIndex);
        }
    }

    // ----------------------------------------------- LayoutParams field access
    // Children of a LinearLayout get a LinearLayout.LayoutParams from the
    // inflater, but a programmatically-added child may carry a plain
    // ViewGroup.LayoutParams / MarginLayoutParams. These helpers read
    // weight/gravity/margins across whatever LP type is attached, falling back
    // to the universal macapkGravity field the inflater stamps on every LP.

    private static float weightOf(ViewGroup.LayoutParams lp) {
        return (lp instanceof LayoutParams llp) ? llp.weight : 0f;
    }
    private static int childGravity(ViewGroup.LayoutParams lp) {
        if (lp instanceof LayoutParams llp && llp.gravity >= 0) return llp.gravity;
        if (lp != null && lp.macapkGravity != 0) return lp.macapkGravity;
        return -1;
    }
    /** AOSP measureHorizontal: "(lp.gravity < 0 ? mGravity : lp.gravity) &
     *  Gravity.VERTICAL_GRAVITY_MASK" translated into a maxAscent/maxDescent
     *  bucket index (0..VERTICAL_GRAVITY_COUNT-1), so a mixed-gravity row
     *  (one child TOP, another BOTTOM) tracks each bucket's tallest
     *  ascent/descent separately instead of colliding. */
    private int baselineGravityIndex(ViewGroup.LayoutParams lp) {
        int g = childGravity(lp);
        if (g < 0) g = macapkGravity;
        g &= Gravity.VERTICAL_GRAVITY_MASK;
        return ((g >> Gravity.AXIS_Y_SHIFT) & ~Gravity.AXIS_SPECIFIED) >> 1;
    }
    private static int widthOf(ViewGroup.LayoutParams lp)  { return lp != null ? lp.width  : ViewGroup.LayoutParams.WRAP_CONTENT; }
    private static int heightOf(ViewGroup.LayoutParams lp) { return lp != null ? lp.height : ViewGroup.LayoutParams.WRAP_CONTENT; }
    private static int leftMargin(ViewGroup.LayoutParams lp)   { return (lp instanceof MarginLayoutParams m) ? m.leftMargin   : 0; }
    private static int rightMargin(ViewGroup.LayoutParams lp)  { return (lp instanceof MarginLayoutParams m) ? m.rightMargin  : 0; }
    private static int topMargin(ViewGroup.LayoutParams lp)    { return (lp instanceof MarginLayoutParams m) ? m.topMargin    : 0; }
    private static int bottomMargin(ViewGroup.LayoutParams lp) { return (lp instanceof MarginLayoutParams m) ? m.bottomMargin : 0; }

    @Override public LinearLayout.LayoutParams generateDefaultLayoutParams() {
        // Flag-off: keep the historical (inherited ViewGroup) default so the
        // shipped build is unchanged. Flag-on: AOSP LinearLayout default --
        // HORIZONTAL -> WRAP x WRAP, VERTICAL -> MATCH x WRAP (so an
        // addView-without-LP child fills the cross axis as on real Android).
        if (!android.os.Looper.MACAPK_REAL_LOOPER) {
            // Historical default (WRAP x WRAP), now typed as LinearLayout.LayoutParams
            // to match AOSP's covariant override (the demanded descriptor).
            return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        if (macapkOrientation == HORIZONTAL) {
            return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    /** v1.0.1643: AOSP checkLayoutParams, so addView converts a foreign
     *  LayoutParams into this container's own kind (ViewGroup.addView ->
     *  generateLayoutParams). Gate MACAPK_CHECK_LAYOUT_PARAMS=0 restores the old
     *  accept-anything answer for an A/B. */
    @Override
    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (!MACAPK_CHECK_LP) return true;
        return p instanceof LinearLayout.LayoutParams;
    }
    private static final boolean MACAPK_CHECK_LP =
        !"0".equals(System.getenv("MACAPK_CHECK_LAYOUT_PARAMS"));

    /** AOSP LinearLayout.generateLayoutParams(AttributeSet), android14-release
     *  LinearLayout.java:1977-1980:
     *      @Override
     *      public LayoutParams generateLayoutParams(AttributeSet attrs) {
     *          return new LinearLayout.LayoutParams(getContext(), attrs);
     *      }
     *  Without it LinearLayout inherited ViewGroup's `return new LayoutParams();`, a
     *  bare ViewGroup.LayoutParams that ignores its argument. LayoutInflater's
     *  <include> path adopts parent.generateLayoutParams(siteAttrs) whenever the
     *  include SITE declares width and height (AOSP parseInclude's contract), so
     *  every such include under a LinearLayout lost its layout_weight,
     *  layout_margin* and layout_gravity: a bare LP has nowhere to hold them, and
     *  addView's checkLayoutParams conversion (v1.0.1643) carries only width and
     *  height across. Before v1.0.1643 the bare LP stayed on the child and
     *  Wikipedia's History search card threw ClassCastException in the app's own
     *  post-runnable. Measured victims: Wikipedia's search card margins (0 where a
     *  device has 16/16/24dp), AntennaPod's swipe-preview episode (weight 0.7 lost,
     *  so zero width), deskclock's world-clock city column (weight 1 and marginEnd
     *  lost). Evidence: docs/wikipedia-pageactivity-classcastexception_2026-09-26.md;
     *  offline gate: debug/w5linearlp_gate.sh.
     *
     *  The declared return type is ViewGroup.LayoutParams, not AOSP's covariant
     *  LinearLayout.LayoutParams, for two reasons: the bisect gate must be able to
     *  hand back the base class's bare LP, and ActionMenuView extends this class
     *  with a ViewGroup.LayoutParams-returning override that a covariant parent
     *  would refuse to compile. No corpus app names the covariant descriptor
     *  (debug/apishape_census.py over ../Apks, 2026-09-26: 0 references), so
     *  nothing links against it today. Narrow it, together with RadioGroup /
     *  TableRow / TableLayout / ActionMenuView, when the gate retires.
     *
     *  Direct (non-include) XML children keep their old skeleton:
     *  LayoutInflater.buildLayoutParams now takes its "meaningful subclass" branch
     *  for them instead of its hardcoded `new LinearLayout.LayoutParams(WRAP_CONTENT,
     *  WRAP_CONTENT)` fallback, and the WRAP_CONTENT seed in
     *  LayoutParams(Context, AttributeSet) below makes the two identical.
     *
     *  BISECT GATE (default ON): MACAPK_LL_GENLP_ATTRS=0 restores the inherited base
     *  answer here AND the unseeded 0x0 LayoutParams(Context, AttributeSet). */
    @Override
    public ViewGroup.LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        if (!LayoutParams.MACAPK_GENLP_ATTRS) return super.generateLayoutParams(attrs);
        return new LayoutParams(getContext(), attrs);
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        /** The MACAPK_LL_GENLP_ATTRS bisect gate (see generateLayoutParams(AttributeSet)
         *  above). Lives here rather than on LinearLayout so constructing a
         *  LayoutParams never forces LinearLayout's own class initialisation. */
        static final boolean MACAPK_GENLP_ATTRS =
            !"0".equals(System.getenv("MACAPK_LL_GENLP_ATTRS"));

        public float weight = 0f;
        public int   gravity = -1;
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, float weight) { super(w, h); this.weight = weight; }
        public LayoutParams(LayoutParams src) { super(src); this.weight = src.weight; this.gravity = src.gravity; }
        public LayoutParams(ViewGroup.LayoutParams src) { super(src); }
        public LayoutParams(ViewGroup.MarginLayoutParams src) { super(src); }
        /** AOSP LinearLayout.LayoutParams(Context, AttributeSet), android14-release
         *  LinearLayout.java:2084-2093, reads width/height through
         *  ViewGroup.LayoutParams.setBaseAttributes and weight/gravity from
         *  R.styleable.LinearLayout_Layout. Ours does not parse: population is the
         *  inflater's attribute walk, as for FrameLayout and RelativeLayout, so the
         *  skeleton's defaults are what survives for any attribute the walk does not
         *  set. WRAP_CONTENT x WRAP_CONTENT is the skeleton buildLayoutParams gave
         *  every direct LinearLayout child before generateLayoutParams(AttributeSet)
         *  existed; seeding it keeps those children identical now that they come
         *  through this constructor. Measured without the seed: 19 Wikipedia
         *  dialog-title views (m3 / mtrl alert-dialog title panels) whose size comes
         *  from a style that does not resolve went from WRAP x WRAP to 0x0. AOSP
         *  would THROW there ("You must supply a layout_width attribute"); the
         *  forgiving default is ours, and it is the one we already had.
         *  MACAPK_LL_GENLP_ATTRS=0 restores the old 0x0. The trailing marker lets
         *  debug/w5linearlp_gate.sh build the seedless mutant that proves its digest
         *  can see this default move. */
        public LayoutParams(Context c, android.util.AttributeSet attrs) {
            super();
            if (MACAPK_GENLP_ATTRS) { width = WRAP_CONTENT; height = WRAP_CONTENT; } // w5linearlp-seed
        }

        /** AOSP LinearLayout.java:2146 -- the ViewGroup.LayoutParams.debug()
         *  override used by ViewDebug / hierarchy dumps.
         *  sizeToString is `protected static` on AOSP's
         *  ViewGroup.LayoutParams, which this tree's ViewGroup does not have
         *  (that is the android.view surface), so the same three-case body is
         *  inlined here. */
        public String debug(String output) {
            return output + "LinearLayout.LayoutParams={width=" + macapkSizeToString(width)
                    + ", height=" + macapkSizeToString(height) + " weight=" + weight + "}";
        }

        /** AOSP ViewGroup.LayoutParams.sizeToString(int). */
        static String macapkSizeToString(int size) {
            if (size == WRAP_CONTENT) return "wrap-content";
            if (size == MATCH_PARENT) return "match-parent";
            return String.valueOf(size);
        }
    }

    public void onProvideAutofillStructure(android.view.ViewStructure structure, int flags) { }
    public void onProvideAutofillVirtualStructure(android.view.ViewStructure structure, int flags) { }

    // ---- API-36 surface completion ------------------------------------
    /** AOSP LinearLayout.setHorizontalGravity/setVerticalGravity -- each
     *  replaces only ITS HALF of the gravity word and re-lays-out only when the
     *  half actually changed, so setting one does not clobber the other. */
    public void setHorizontalGravity(int horizontalGravity) {
        final int gravity = horizontalGravity & android.view.Gravity.HORIZONTAL_GRAVITY_MASK;
        if ((getGravity() & android.view.Gravity.HORIZONTAL_GRAVITY_MASK) != gravity) {
            setGravity((getGravity() & ~android.view.Gravity.HORIZONTAL_GRAVITY_MASK) | gravity);
        }
    }
    public void setVerticalGravity(int verticalGravity) {
        final int gravity = verticalGravity & android.view.Gravity.VERTICAL_GRAVITY_MASK;
        if ((getGravity() & android.view.Gravity.VERTICAL_GRAVITY_MASK) != gravity) {
            setGravity((getGravity() & ~android.view.Gravity.VERTICAL_GRAVITY_MASK) | gravity);
        }
    }

    /** AOSP LinearLayout.generateLayoutParams(ViewGroup.LayoutParams) --
     *  preserve the richest source type so weight/gravity (LinearLayout.
     *  LayoutParams) and margins (MarginLayoutParams) survive the conversion.
     *  It overrides ViewGroup.generateLayoutParams(ViewGroup.LayoutParams), which
     *  addView reaches through checkLayoutParams (v1.0.1643). (A comment here
     *  used to say ViewGroup did not declare that method; it has for a while.) */
    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        // AOSP gates the type-preserving conversion on
        // View.sPreserveMarginParamsInLayoutParamConversion, which is
        // `targetSdkVersion >= N` (View.java:5891). RelativeLayout.java:1131 is
        // AOSP's own instance of exactly this shape. Below N the margins are
        // DROPPED, because layouts authored before the API-24 fix were written
        // around the drop and double-space with it. The true branch is
        // literally the previous code path, and the flag defaults true, so
        // nothing changes for an app at or above 24 or for an unresolved one.
        if (android.view.View.sPreserveMarginParamsInLayoutParamConversion) {
            if (lp instanceof LayoutParams) return new LayoutParams((LayoutParams) lp);
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                return new LayoutParams((ViewGroup.MarginLayoutParams) lp);
            }
        }
        return new LayoutParams(lp);
    }
}
