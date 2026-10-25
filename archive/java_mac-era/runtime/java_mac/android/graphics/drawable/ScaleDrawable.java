// android.graphics.drawable.ScaleDrawable -- AOSP API-1 DrawableWrapper
// that draws its child scaled by the current level (0..10000). AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/ScaleDrawable.java
// At level 0 the child is invisible (per AOSP: a 0-level scale draws
// nothing); at level 10000 it draws at full size. The scale is applied
// independently per axis (scaleWidth/scaleHeight) anchored by a gravity.
// Used by SeekBar/ProgressBar thumb+track layered drawables.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;

public class ScaleDrawable extends DrawableWrapper {
    private static final int MAX_LEVEL = 10000;

    private final float mScaleWidth;
    private final float mScaleHeight;
    private final int mGravity;
    private final Rect mTmpRect = new Rect();

    public ScaleDrawable(Drawable drawable, int gravity, float scaleWidth, float scaleHeight) {
        super(drawable);
        this.mGravity = gravity;
        this.mScaleWidth = clamp01(scaleWidth);
        this.mScaleHeight = clamp01(scaleHeight);
    }

    /** Zero-arg ctor for inflation; child set later via setDrawable. */
    public ScaleDrawable() {
        super(null);
        this.mGravity = android.view.Gravity.LEFT;
        this.mScaleWidth = -1f;
        this.mScaleHeight = -1f;
    }

    @Override public void draw(Canvas canvas) {
        final Drawable d = getDrawable();
        if (d == null) return;
        // Recompute the scaled child bounds here: our base Drawable.setBounds
        // does not dispatch onBoundsChange, so rely on the draw path to keep
        // the child geometry current. AOSP draws nothing once the level
        // reaches 0 and a scale is active (a SeekBar at 0 progress is empty).
        applyScale(getBounds());
        if (d.getLevel() != 0 || getLevel() != 0) {
            d.draw(canvas);
        }
    }

    @Override public void onBoundsChange(Rect bounds) {
        applyScale(bounds);
    }

    private void applyScale(Rect bounds) {
        final Drawable d = getDrawable();
        if (d == null) return;
        final Rect r = mTmpRect;
        int level = getLevel();
        int w = bounds.width();
        if (mScaleWidth > 0) {
            w -= (int) (w * mScaleWidth * (MAX_LEVEL - level) / MAX_LEVEL);
        }
        int h = bounds.height();
        if (mScaleHeight > 0) {
            h -= (int) (h * mScaleHeight * (MAX_LEVEL - level) / MAX_LEVEL);
        }
        // Apply gravity to place the scaled rect inside bounds.
        android.view.Gravity.apply(mGravity, w, h, bounds, r);
        if (w > 0 && h > 0) d.setBounds(r.left, r.top, r.right, r.bottom);
    }

    @Override public boolean onLevelChange(int level) {
        super.onLevelChange(level);
        applyScale(getBounds());
        invalidateSelf();
        return true;
    }

    /** AOSP ScaleDrawable.java:200-212. Same shape as ClipDrawable's: a level-0
     *  child paints nothing, and an OPAQUE child that is not at full level only
     *  covers part of the bounds. Inherited DrawableWrapper.getOpacity returned
     *  the child's answer verbatim, which claimed a half-scaled opaque child
     *  covered the whole area (DRW.lvl.scale.opacity.0/.10000).
     *
     *  Deviation: the null guard, for the same <scale>-inflation reason as
     *  ClipDrawable. */
    @Override public int getOpacity() {
        final Drawable d = getDrawable();
        if (d == null) return android.graphics.PixelFormat.TRANSPARENT;
        if (d.getLevel() == 0) return android.graphics.PixelFormat.TRANSPARENT;
        final int opacity = d.getOpacity();
        if (opacity == android.graphics.PixelFormat.OPAQUE && d.getLevel() < MAX_LEVEL) {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
        return opacity;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
