// android.graphics.drawable.InsetDrawable -- AOSP API-1 Drawable that
// wraps another Drawable and applies a fixed inset on each side.
// Used by MaterialCardView internally to inset its content-area
// background drawable; absent the class, MaterialCardViewHelper's
// <clinit> fails with NoClassDefFoundError. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/InsetDrawable.java
//
// AOSP declares `public class InsetDrawable extends DrawableWrapper`, and
// that inheritance is load-bearing rather than cosmetic: setDrawable,
// setTintList, setTintMode, isStateful, setState, onStateChange,
// setAutoMirrored and jumpToCurrentState are NOT redeclared here, they are
// inherited from DrawableWrapper, which forwards each of them to the
// wrapped drawable (the one that actually paints). Extending Drawable
// directly, as this class used to, meant an InsetDrawable stored its tint
// and state on itself and drew the child untinted / stuck in the old
// state, and offered no setDrawable at all. Sibling wrappers
// (ScaleDrawable / RotateDrawable / ClipDrawable) already extend
// DrawableWrapper; this class was the odd one out.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;

public class InsetDrawable extends DrawableWrapper {
    // AOSP models each side as an InsetValue { final float mFraction; int
    // mDimension } resolved against the bound size:
    //     getDimension(boundSize) = (int)(boundSize * mFraction) + mDimension
    // so a fractional inset scales with the drawable's bounds. The old code
    // truncated the float-fraction constructor's argument straight to an int
    // pixel count, which collapsed every fractional inset (0 < f < 1) to 0.
    private float mInsetLeftFraction;
    private float mInsetTopFraction;
    private float mInsetRightFraction;
    private float mInsetBottomFraction;
    private int mInsetLeftDim;
    private int mInsetTopDim;
    private int mInsetRightDim;
    private int mInsetBottomDim;

    private final Rect mTmpRect = new Rect();
    private final Rect mTmpInsetRect = new Rect();

    /** Zero-arg ctor for <inset> inflation; child set later via setDrawable. */
    public InsetDrawable() { super(null); }

    public InsetDrawable(Drawable drawable, int inset) {
        this(drawable, inset, inset, inset, inset);
    }

    public InsetDrawable(Drawable drawable, int insetLeft, int insetTop,
                         int insetRight, int insetBottom) {
        super(drawable);
        mInsetLeftDim = insetLeft;
        mInsetTopDim = insetTop;
        mInsetRightDim = insetRight;
        mInsetBottomDim = insetBottom;
    }

    public InsetDrawable(Drawable drawable, float inset) {
        this(drawable, inset, inset, inset, inset);
    }

    public InsetDrawable(Drawable drawable, float insetLeftFraction, float insetTopFraction,
                         float insetRightFraction, float insetBottomFraction) {
        super(drawable);
        mInsetLeftFraction = insetLeftFraction;
        mInsetTopFraction = insetTopFraction;
        mInsetRightFraction = insetRightFraction;
        mInsetBottomFraction = insetBottomFraction;
    }

    /** AOSP getInsets(Rect): resolve every side against the current bounds. */
    private void getInsets(Rect out) {
        final Rect b = getBounds();
        final int w = b.width();
        final int h = b.height();
        out.left = (int) (w * mInsetLeftFraction) + mInsetLeftDim;
        out.right = (int) (w * mInsetRightFraction) + mInsetRightDim;
        out.top = (int) (h * mInsetTopFraction) + mInsetTopDim;
        out.bottom = (int) (h * mInsetBottomFraction) + mInsetBottomDim;
    }

    private void applyInsets(Rect bounds) {
        final Drawable d = getDrawable();
        if (d == null) return;
        final Rect r = mTmpRect;
        r.set(bounds);
        r.left += (int) (bounds.width() * mInsetLeftFraction) + mInsetLeftDim;
        r.top += (int) (bounds.height() * mInsetTopFraction) + mInsetTopDim;
        r.right -= (int) (bounds.width() * mInsetRightFraction) + mInsetRightDim;
        r.bottom -= (int) (bounds.height() * mInsetBottomFraction) + mInsetBottomDim;
        d.setBounds(r.left, r.top, r.right, r.bottom);
    }

    // AOSP does the inset purely in onBoundsChange and inherits draw() from
    // DrawableWrapper. Our base Drawable.setBounds does not dispatch
    // onBoundsChange, so (like ScaleDrawable in this package) recompute the
    // child geometry on the draw path as well, otherwise the child would be
    // painted at the wrapper's full, un-inset bounds.
    @Override public void draw(Canvas canvas) {
        final Drawable d = getDrawable();
        if (d == null) return;
        applyInsets(getBounds());
        d.draw(canvas);
    }

    @Override public void onBoundsChange(Rect bounds) {
        applyInsets(bounds);
    }

    // AOSP: the child's own padding PLUS the inset, not the inset alone.
    //
    // The missing zero-fill is deliberate. AOSP calls super.getPadding (which is
    // `mDrawable != null && mDrawable.getPadding(padding)`), so with NO child the
    // caller's rect is left EXACTLY AS PASSED IN and the insets are added on top
    // of whatever was already there. DRW.id.nullChild.padret pins that: the probe
    // poisons the rect with 9,9,9,9, and real Android answers "1 rect=13,13,13,13"
    // -- 9+4 on every side. Zeroing here answered 4,4,4,4 and hid the fact that a
    // null-child InsetDrawable does not own the rect.
    @Override public boolean getPadding(Rect padding) {
        if (padding == null) return false;
        final Drawable d = getDrawable();
        final boolean pad = (d != null) && d.getPadding(padding);
        getInsets(mTmpInsetRect);
        padding.left += mTmpInsetRect.left;
        padding.right += mTmpInsetRect.right;
        padding.top += mTmpInsetRect.top;
        padding.bottom += mTmpInsetRect.bottom;
        return pad || (mTmpInsetRect.left | mTmpInsetRect.right
                | mTmpInsetRect.top | mTmpInsetRect.bottom) != 0;
    }

    // AOSP: an opaque child seen through a non-zero inset is translucent,
    // because the inset band itself shows whatever is behind the wrapper.
    //
    // AOSP dereferences getDrawable() with no null check, so a child-less
    // InsetDrawable throws NullPointerException here and in getIntrinsicWidth /
    // getIntrinsicHeight below. That is the answer DRW.id.nullChild.opacity and
    // .iw record, and the leniency it replaces was not free: a caller that gets
    // TRANSPARENT/-1 instead of the platform's NPE lays out around a drawable
    // real Android would have refused to measure.
    @Override public int getOpacity() {
        final Drawable d = getDrawable();
        final int opacity = d.getOpacity();
        getInsets(mTmpInsetRect);
        if (opacity == PixelFormat.OPAQUE
                && (mTmpInsetRect.left > 0 || mTmpInsetRect.top > 0
                    || mTmpInsetRect.right > 0 || mTmpInsetRect.bottom > 0)) {
            return PixelFormat.TRANSLUCENT;
        }
        return opacity;
    }

    // AOSP grows the child's intrinsic size by the fixed dimensions and
    // divides out the fractional part (the child occupies 1 - fraction of the
    // total), reporting "no intrinsic size" when the child has none or the
    // fractions leave no room.
    @Override public int getIntrinsicWidth() {
        final Drawable d = getDrawable();
        final int childWidth = d.getIntrinsicWidth();
        final float fraction = mInsetLeftFraction + mInsetRightFraction;
        if (childWidth < 0 || fraction >= 1) return -1;
        return (int) (childWidth / (1 - fraction)) + mInsetLeftDim + mInsetRightDim;
    }

    @Override public int getIntrinsicHeight() {
        final Drawable d = getDrawable();
        final int childHeight = d.getIntrinsicHeight();
        final float fraction = mInsetTopFraction + mInsetBottomFraction;
        if (childHeight < 0 || fraction >= 1) return -1;
        return (int) (childHeight / (1 - fraction)) + mInsetTopDim + mInsetBottomDim;
    }
}
