// android.graphics.drawable.ClipDrawable -- AOSP API-1 DrawableWrapper
// that clips its child to a fraction of its bounds driven by the level
// (0..10000) on the horizontal and/or vertical axis. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/ClipDrawable.java
// This is how a determinate ProgressBar's progress layer reveals: the
// track is a ClipDrawable whose level == progress, clipped HORIZONTAL so
// more of the child shows as progress climbs. At level 0 nothing draws.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;

public class ClipDrawable extends DrawableWrapper {
    public static final int HORIZONTAL = 1;
    public static final int VERTICAL = 2;

    private static final int MAX_LEVEL = 10000;

    private final int mOrientation;
    private final int mGravity;
    private final Rect mTmpRect = new Rect();

    public ClipDrawable(Drawable drawable, int gravity, int orientation) {
        super(drawable);
        this.mGravity = gravity;
        this.mOrientation = orientation;
    }

    /** Zero-arg ctor for inflation; child + params default. */
    public ClipDrawable() {
        super(null);
        this.mGravity = android.view.Gravity.LEFT;
        this.mOrientation = HORIZONTAL;
    }

    @Override public void draw(Canvas canvas) {
        final Drawable d = getDrawable();
        if (d == null) return;
        // AOSP: a level of 0 with any active clip axis draws nothing.
        if (getLevel() == 0) return;

        final Rect r = mTmpRect;
        final Rect bounds = getBounds();
        int level = getLevel();

        int w = bounds.width();
        if ((mOrientation & HORIZONTAL) != 0) {
            w -= (w * (MAX_LEVEL - level)) / MAX_LEVEL;
        }
        int h = bounds.height();
        if ((mOrientation & VERTICAL) != 0) {
            h -= (h * (MAX_LEVEL - level)) / MAX_LEVEL;
        }

        android.view.Gravity.apply(mGravity, w, h, bounds, r);

        if (w > 0 && h > 0) {
            final int saved = canvas.save();
            canvas.clipRect(r.left, r.top, r.right, r.bottom);
            d.draw(canvas);
            canvas.restoreToCount(saved);
        }
    }

    @Override public boolean onLevelChange(int level) {
        super.onLevelChange(level);
        invalidateSelf();
        return true;
    }

    /** AOSP ClipDrawable.java:161-175. The level is the whole answer: at level 0
     *  nothing is painted at all (TRANSPARENT), at full level the child's own
     *  opacity passes straight through, and only in between is the clip really
     *  hiding part of a non-transparent child. The flat TRANSLUCENT this
     *  replaces told every ProgressBar's track that it might show through even
     *  when full.
     *
     *  Deviation: AOSP dereferences the child with no null check. The zero-arg
     *  constructor exists here for <clip> inflation, where the child arrives
     *  later, so a null answers TRANSPARENT rather than throwing mid-inflate.
     *  No probe case constructs a child-less ClipDrawable. */
    @Override public int getOpacity() {
        final Drawable dr = getDrawable();
        if (dr == null) return android.graphics.PixelFormat.TRANSPARENT;
        final int opacity = dr.getOpacity();
        if (opacity == android.graphics.PixelFormat.TRANSPARENT || dr.getLevel() == 0) {
            return android.graphics.PixelFormat.TRANSPARENT;
        }
        if (getLevel() >= MAX_LEVEL) return opacity;
        // Some portion of a non-transparent drawable is hidden.
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
