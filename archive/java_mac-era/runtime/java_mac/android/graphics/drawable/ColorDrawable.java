// android.graphics.drawable.ColorDrawable -- uniform color fill. AOSP:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r48/graphics/java/android/graphics/drawable/ColorDrawable.java
// Glide's DrawableCrossFadeTransition uses `new ColorDrawable(0)` as
// the from-drawable in its alpha-tween; we draw the single color into
// the current bounds.
//
// v1.0.923 -- the alpha model was WRONG in a way no pixel diff could catch.
// setAlpha(a) does not STORE a. AOSP (ColorDrawable.java:173-184):
//
//     alpha += alpha >> 7;                                   // widen 0..255 -> 0..256
//     final int baseAlpha = mColorState.mBaseColor >>> 24;
//     final int useAlpha  = baseAlpha * alpha >> 8;           // MODULATE the base
//     final int useColor  = (mColorState.mBaseColor << 8 >>> 8) | (useAlpha << 24);
//
// so setAlpha is scale-by-fraction against the colour the drawable was built
// with, it does NOT compound across calls, and getAlpha() reads back the
// MODULATED alpha (mUseColor >>> 24), not the argument. A straight assignment
// agrees on 0 and 255 over an opaque base and is off by one or by half
// everywhere else: setAlpha(127) on an opaque base is 126, setAlpha(255) on a
// 0x80 base is 128. Measured by DRW.cd.setAlphaOnOpaque.* / setAlphaOnHalf.*.
//
// The base/use split also has to live in the ConstantState, because that is
// what siblings share: two ColorDrawables from one ConstantState see each
// other's setAlpha until one of them calls mutate() (DRW.cs.cd.*).
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;

public class ColorDrawable extends Drawable {
    private ColorState mColorState;
    private final Paint mPaint = new Paint();
    private boolean mMutated;

    public ColorDrawable() { mColorState = new ColorState(); }
    public ColorDrawable(int color) { mColorState = new ColorState(); setColor(color); }
    private ColorDrawable(ColorState state) { mColorState = state; }

    /** AOSP getColor() returns mUseColor -- the colour AFTER setAlpha's
     *  modulation, not the base (ColorDrawable.java:130-133). */
    public int getColor() { return mColorState.mUseColor; }

    /** AOSP setColor "will clobber the results of prior calls to setAlpha"
     *  (:139-151): both the base and the modulated colour are reset. */
    public void setColor(int color) {
        if (mColorState.mBaseColor != color || mColorState.mUseColor != color) {
            mColorState.mBaseColor = mColorState.mUseColor = color;
            invalidateSelf();
        }
    }

    @Override public void draw(Canvas canvas) {
        if (canvas == null) return;
        final ColorFilter colorFilter = mPaint.getColorFilter();
        if ((mColorState.mUseColor >>> 24) == 0 && colorFilter == null && !hasTintFilter()) return;
        android.graphics.Rect bnd = getBounds();
        // The explicit colour filter on one flat colour (MACAPK_DRAWABLE_COLOR_FILTER).
        // AOSP draw (android14-release ColorDrawable.java:113-127) paints mUseColor
        // through the paint's filter, which Skia applies to that colour -- so
        // SRC_IN keeps the drawable's alpha (a = as * ad). Our Canvas resolves a
        // paint filter with solidColorForFilter, which answers SRC / SRC_IN /
        // SRC_ATOP / SRC_OVER with the filter colour VERBATIM and drops it:
        // `new ColorDrawable(0x80FF0000).setColorFilter(0xFF00FF00, SRC_IN)`
        // draws 0x8000FF00 on a device and drew opaque green here. So the colour
        // is filtered once, exactly, by the same Drawable.macapkFilterColor the
        // other flat-colour leaves use, and painted with the paint's filter
        // cleared for that one draw (restored after, as AOSP restores its own);
        // a filter it cannot model stays on the paint for Canvas to resolve.
        // This only became reachable from the common int form with the same
        // change (Drawable.setColorFilter(int, Mode) was a no-op), which is why
        // it shares that gate; =0 is the exact pre-change draw below.
        if (MACAPK_DRAWABLE_COLOR_FILTER && colorFilter != null) {
            final Integer f = macapkFilterColor(colorFilter, mColorState.mUseColor);
            if (f != null) {
                if ((f >>> 24) == 0) return;
                mPaint.setColorFilter(null);
                try {
                    mPaint.setColor(f);
                    canvas.drawRect(bnd.left, bnd.top, bnd.right, bnd.bottom, mPaint);
                } finally {
                    mPaint.setColorFilter(colorFilter);
                }
                return;
            }
        }
        mPaint.setColor(mColorState.mUseColor);
        canvas.drawRect(bnd.left, bnd.top, bnd.right, bnd.bottom, mPaint);
    }

    /** AOSP ColorDrawable.java:173-184, verbatim arithmetic -- see the header. */
    @Override public void setAlpha(int alpha) {
        alpha += alpha >> 7;   // make it 0..256
        final int baseAlpha = mColorState.mBaseColor >>> 24;
        final int useAlpha = baseAlpha * alpha >> 8;
        final int useColor = (mColorState.mBaseColor << 8 >>> 8) | (useAlpha << 24);
        if (mColorState.mUseColor != useColor) {
            mColorState.mUseColor = useColor;
            invalidateSelf();
        }
    }
    /** AOSP :160-164 -- the MODULATED alpha, which is not the setAlpha argument. */
    @Override public int getAlpha() { return mColorState.mUseColor >>> 24; }

    @Override public void setColorFilter(ColorFilter f) { mPaint.setColorFilter(f); }
    /** AOSP :200-203 returns the PAINT's filter. setTint installs a separate
     *  mBlendModeColorFilter, so after setTint this is still null even though
     *  getOpacity() has flipped to TRANSLUCENT -- DRW.cd.tint.colorFilter=0
     *  against DRW.cd.tint.opacity=TRANSLUCENT. Collapsing the two is the
     *  reimplementation slip the pair exists to catch. */
    @Override public ColorFilter getColorFilter() { return mPaint.getColorFilter(); }

    /** Stands in for AOSP's mBlendModeColorFilter field: updateBlendModeFilter
     *  produces a filter exactly when a tint list AND a blend mode are both set,
     *  and null otherwise, so its non-nullness is this predicate. */
    private boolean hasTintFilter() { return mTintList != null && mTintMode != null; }

    /** AOSP :265-278. A tint or an explicit colour filter forces TRANSLUCENT
     *  before the colour is even consulted; otherwise only a 255 or 0 alpha is
     *  definite. */
    @Override public int getOpacity() {
        if (hasTintFilter() || mPaint.getColorFilter() != null) return PixelFormat.TRANSLUCENT;
        switch (mColorState.mUseColor >>> 24) {
            case 255: return PixelFormat.OPAQUE;
            case 0:   return PixelFormat.TRANSPARENT;
        }
        return PixelFormat.TRANSLUCENT;
    }

    /** AOSP :280-284: the bounds rect at the drawable's own alpha -- NOT the
     *  base class's alpha-0 "I have no meaningful outline" sentinel. */
    @Override public void getOutline(android.graphics.Outline outline) {
        outline.setRect(getBounds());
        outline.setAlpha(getAlpha() / 255.0f);
    }

    /** AOSP :237-239: only a STATEFUL tint makes a ColorDrawable stateful, even
     *  though onStateChange (inherited from our base) answers true for any tint. */
    @Override public boolean isStateful() {
        return mTintList != null && mTintList.isStateful();
    }

    @Override public ConstantState getConstantState() {
        mColorState.mChangingConfigurations = getChangingConfigurations();
        return mColorState;
    }

    /** AOSP :91-101: mutate is idempotent and returns THIS; it forks the shared
     *  state so later setAlpha/setColor stop leaking to siblings. */
    @Override public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mColorState = new ColorState(mColorState);
            mMutated = true;
        }
        return this;
    }

    static final class ColorState extends ConstantState {
        int mBaseColor;   // base color, independent of setAlpha()
        int mUseColor;    // basecolor modulated by setAlpha()
        int mChangingConfigurations;

        ColorState() { }
        ColorState(ColorState orig) {
            mBaseColor = orig.mBaseColor;
            mUseColor = orig.mUseColor;
            mChangingConfigurations = orig.mChangingConfigurations;
        }

        /** AOSP hands back a NEW ColorDrawable that SHARES this state, which is
         *  what makes an un-mutated sibling see the original's setAlpha. */
        @Override public Drawable newDrawable() { return new ColorDrawable(this); }
        @Override public int getChangingConfigurations() { return mChangingConfigurations; }
    }
}
