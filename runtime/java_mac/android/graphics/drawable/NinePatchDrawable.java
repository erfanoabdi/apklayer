// android.graphics.drawable.NinePatchDrawable -- AOSP Drawable wrapper
// around a NinePatch (a stretchable bitmap with stretch+padding chunks).
// AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/NinePatchDrawable.java
// Inflated 9-patch backgrounds (dialogs, buttons, EditText frames) become
// NinePatchDrawable. The core NinePatch.draw blits the bitmap scaled to
// the bounds (stretch zones not yet decoded); this wrapper provides the
// Drawable surface so a 9-patch background renders rather than throwing
// NoClassDefFoundError or painting nothing.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Rect;

public class NinePatchDrawable extends Drawable {
    private NinePatch mNinePatch;
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect mPadding = new Rect();
    private int mTargetDensity = 160;

    public NinePatchDrawable(android.content.res.Resources res, android.graphics.Bitmap bitmap,
                             byte[] chunk, Rect padding, String srcName) {
        mNinePatch = new NinePatch(bitmap, chunk, srcName);
        if (padding != null) mPadding.set(padding);
    }
    /** AOSP's deprecated density-less constructor (frameworks/base graphics/
     *  java/android/graphics/drawable/NinePatchDrawable.java:93-102,
     *  android11-release): "Create drawable from raw nine-patch data, not
     *  dealing with density." AOSP delegates to the Resources-aware
     *  constructor's shared core with res = null. Our Resources-aware 5-arg
     *  overload just above never actually reads res either (mTargetDensity
     *  stays the fixed AOSP default, 160 -- we don't yet compute it from
     *  Resources.getDisplayMetrics()), so the two constructors are
     *  equivalent other than this one's missing Resources argument;
     *  delegating keeps that single implementation instead of duplicating
     *  the NinePatch construction + padding copy. */
    @Deprecated
    public NinePatchDrawable(android.graphics.Bitmap bitmap, byte[] chunk, Rect padding, String srcName) {
        this(null, bitmap, chunk, padding, srcName);
    }
    public NinePatchDrawable(android.content.res.Resources res, NinePatch patch) {
        mNinePatch = patch;
    }
    public NinePatchDrawable(NinePatch patch) {
        mNinePatch = patch;
    }

    public Paint getPaint() { return mPaint; }
    public void setTargetDensity(int density) { mTargetDensity = density; }
    public void setTargetDensity(android.util.DisplayMetrics metrics) {
        if (metrics != null) mTargetDensity = metrics.densityDpi;
    }

    /** AOSP NinePatchDrawable.draw() (graphics/java/android/graphics/drawable/
     *  NinePatchDrawable.java, android13-release): a tint stored via
     *  setTintList (or an XML android:tint, see Resources.macapkInflateBitmap)
     *  is turned into a (Blend/PorterDuff)ColorFilter and applied to mPaint
     *  ONLY when the caller has not already set an explicit filter of their
     *  own, then cleared back afterward:
     *      if (mBlendModeFilter != null && getPaint().getColorFilter() == null) {
     *          mPaint.setColorFilter(mBlendModeFilter);
     *          clearColorFilter = true;
     *      }
     *      ...
     *      state.mNinePatch.draw(canvas, bounds, mPaint);
     *      ...
     *      if (clearColorFilter) mPaint.setColorFilter(null);
     *  This class held mTintList/mTintMode on the Drawable base (setTint/
     *  setTintList already worked, and Drawable.isStateful/onStateChange
     *  already covered the storage half -- see Drawable.java) but never
     *  consulted them here, so a tinted 9-patch painted its raw un-tinted
     *  asset pixels regardless of the attribute or the current state. Same
     *  prevCf-guarded pattern ShapeDrawable.draw() already uses for
     *  macapkEffectiveTint(), used here for the same reason: honor an
     *  explicit setColorFilter() over the tint rather than clobbering it. */
    @Override public void draw(Canvas canvas) {
        if (mNinePatch == null) return;
        final ColorFilter prevCf = mPaint.getColorFilter();
        final int tint = macapkEffectiveTint();
        final boolean applied = prevCf == null && tint != 0;
        if (applied) {
            mPaint.setColorFilter(new android.graphics.PorterDuffColorFilter(tint, mTintMode));
        }
        mNinePatch.draw(canvas, getBounds(), mPaint);
        if (applied) {
            mPaint.setColorFilter(prevCf);
        }
    }

    @Override public void setAlpha(int alpha) { mPaint.setAlpha(alpha); invalidateSelf(); }
    @Override public int getAlpha() { return mPaint.getAlpha(); }
    @Override public void setColorFilter(ColorFilter cf) { mPaint.setColorFilter(cf); invalidateSelf(); }
    @Override public ColorFilter getColorFilter() { return mPaint.getColorFilter(); }
    @Override public int getOpacity() {
        return (mNinePatch != null && mNinePatch.hasAlpha()) || mPaint.getAlpha() < 0xFF
            ? -3 /* TRANSLUCENT */ : -1 /* OPAQUE */;
    }
    /** AOSP NinePatchDrawable.getPadding (android16-release graphics/java/android/
     *  graphics/drawable/NinePatchDrawable.java:270-277):
     *      padding.set(mPadding);
     *      return (padding.left | padding.top | padding.right | padding.bottom) != 0;
     *
     *  ⚠ This used to be `return !mPadding.isEmpty()`, which is a GEOMETRY test:
     *  Rect.isEmpty() is `left >= right || top >= bottom` (Rect.java:24). But this
     *  Rect holds INSETS, not coordinates -- Resources.java:1180-1182 says so and
     *  builds it as `new Rect(padLeft, padTop, padRight, padBottom)`. For the
     *  overwhelmingly common symmetric 9-patch, e.g. (16,8,16,8), `16 >= 16` makes
     *  isEmpty() true and we answered FALSE, so View.setBackgroundDrawable
     *  (View.java:5463 `if (d.getPadding(pad))`) skipped applying it and every
     *  9-patch background contributed no padding at all.
     *
     *  Every other getPadding override in this package already uses the union test
     *  (LayerDrawable:374, InsetDrawable:121, DrawableContainer:147,
     *  StateListDrawable:217, ShapeDrawable:80, DrawableWrapper:103, and
     *  ShapeDrawable.java:59-64 documents fixing this exact bug); this was the
     *  sole survivor, i.e. an isolated regression rather than a policy. */
    @Override public boolean getPadding(Rect padding) {
        if (padding != null) padding.set(mPadding);
        return (mPadding.left | mPadding.top | mPadding.right | mPadding.bottom) != 0;
    }
    @Override public int getIntrinsicWidth() {
        return mNinePatch != null ? mNinePatch.getWidth() : -1;
    }
    @Override public int getIntrinsicHeight() {
        return mNinePatch != null ? mNinePatch.getHeight() : -1;
    }
}
