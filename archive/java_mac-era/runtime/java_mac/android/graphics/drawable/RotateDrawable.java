// android.graphics.drawable.RotateDrawable -- AOSP API-1 DrawableWrapper
// that rotates its child around a pivot, with the rotation angle
// interpolated from the current level (0..10000) between fromDegrees and
// toDegrees. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/RotateDrawable.java
// Used by indeterminate ProgressBar (a rotating spinner is a
// RotateDrawable whose level animates 0->10000 on a loop).
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;

public class RotateDrawable extends DrawableWrapper {
    private static final int MAX_LEVEL = 10000;

    private float mFromDegrees = 0f;
    private float mToDegrees = 360f;
    private float mPivotX = 0.5f;
    private float mPivotY = 0.5f;
    private boolean mPivotXRel = true;
    private boolean mPivotYRel = true;
    private float mCurrentDegrees = 0f;

    public RotateDrawable() { super(null); }
    public RotateDrawable(Drawable child) { super(child); }

    public void setFromDegrees(float fromDegrees) { mFromDegrees = fromDegrees; invalidateSelf(); }
    public float getFromDegrees() { return mFromDegrees; }
    public void setToDegrees(float toDegrees) { mToDegrees = toDegrees; invalidateSelf(); }
    public float getToDegrees() { return mToDegrees; }

    public void setPivotX(float pivotX) { mPivotX = pivotX; invalidateSelf(); }
    public float getPivotX() { return mPivotX; }
    public void setPivotY(float pivotY) { mPivotY = pivotY; invalidateSelf(); }
    public float getPivotY() { return mPivotY; }
    public void setPivotXRelative(boolean relative) { mPivotXRel = relative; invalidateSelf(); }
    public boolean isPivotXRelative() { return mPivotXRel; }
    public void setPivotYRelative(boolean relative) { mPivotYRel = relative; invalidateSelf(); }
    public boolean isPivotYRelative() { return mPivotYRel; }

    @Override public void draw(Canvas canvas) {
        final Drawable d = getDrawable();
        if (d == null) return;
        final Rect bounds = getBounds();
        final int w = bounds.width();
        final int h = bounds.height();
        final float px = mPivotXRel ? (w * mPivotX) : mPivotX;
        final float py = mPivotYRel ? (h * mPivotY) : mPivotY;

        final int saved = canvas.save();
        canvas.rotate(mCurrentDegrees, px + bounds.left, py + bounds.top);
        d.draw(canvas);
        canvas.restoreToCount(saved);
    }

    @Override public boolean onLevelChange(int level) {
        super.onLevelChange(level);
        float value = level / (float) MAX_LEVEL;
        mCurrentDegrees = mFromDegrees + (mToDegrees - mFromDegrees) * value;
        invalidateSelf();
        return true;
    }
}
