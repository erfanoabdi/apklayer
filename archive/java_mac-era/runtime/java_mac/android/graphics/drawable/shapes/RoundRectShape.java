// android.graphics.drawable.shapes.RoundRectShape -- AOSP RectShape that
// draws a rounded rectangle, optionally with an inner rounded-rect cutout
// (a ring). AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/shapes/RoundRectShape.java
// The outerRadii array holds 8 floats (rx,ry for each of the 4 corners,
// TL,TR,BR,BL). inset[] insets an inner cutout; innerRadii its corners.
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

public class RoundRectShape extends RectShape {
    private float[] mOuterRadii;
    private RectF mInset;
    private float[] mInnerRadii;

    private RectF mInnerRect;
    private Path mPath;

    public RoundRectShape(float[] outerRadii, RectF inset, float[] innerRadii) {
        if (outerRadii != null && outerRadii.length < 8) {
            throw new ArrayIndexOutOfBoundsException("outer radii must have >= 8 values");
        }
        if (innerRadii != null && innerRadii.length < 8) {
            throw new ArrayIndexOutOfBoundsException("inner radii must have >= 8 values");
        }
        mOuterRadii = outerRadii;
        mInset = inset;
        mInnerRadii = innerRadii;
        if (inset != null) mInnerRect = new RectF();
        mPath = new Path();
    }

    @Override public void draw(Canvas canvas, Paint paint) {
        canvas.drawPath(mPath, paint);
    }

    @Override public void getOutline(Outline outline) {
        if (mInnerRect != null) return; // ring -> no simple outline
        final RectF rect = rect();
        if (mOuterRadii == null) {
            outline.setRect((int) Math.ceil(rect.left), (int) Math.ceil(rect.top),
                            (int) Math.floor(rect.right), (int) Math.floor(rect.bottom));
            return;
        }
        // AOSP requires all 8 radii equal for a fast-path outline.
        float r0 = mOuterRadii[0];
        boolean uniform = true;
        for (float v : mOuterRadii) if (v != r0) { uniform = false; break; }
        if (uniform) {
            outline.setRoundRect((int) Math.ceil(rect.left), (int) Math.ceil(rect.top),
                                 (int) Math.floor(rect.right), (int) Math.floor(rect.bottom), r0);
        }
    }

    @Override protected void onResize(float w, float h) {
        super.onResize(w, h);
        RectF r = rect();
        mPath.reset();
        if (mOuterRadii != null) {
            mPath.addRoundRect(r, mOuterRadii, Path.Direction.CW);
        } else {
            mPath.addRect(r, Path.Direction.CW);
        }
        if (mInnerRect != null) {
            mInnerRect.set(r.left + mInset.left, r.top + mInset.top,
                           r.right - mInset.right, r.bottom - mInset.bottom);
            if (mInnerRect.width() < w && mInnerRect.height() < h) {
                if (mInnerRadii != null) {
                    mPath.addRoundRect(mInnerRect, mInnerRadii, Path.Direction.CCW);
                } else {
                    mPath.addRect(mInnerRect, Path.Direction.CCW);
                }
            }
        }
    }

    @Override public RoundRectShape clone() throws CloneNotSupportedException {
        RoundRectShape s = (RoundRectShape) super.clone();
        s.mOuterRadii = mOuterRadii != null ? mOuterRadii.clone() : null;
        s.mInnerRadii = mInnerRadii != null ? mInnerRadii.clone() : null;
        s.mInset = mInset != null ? new RectF(mInset) : null;
        s.mInnerRect = mInnerRect != null ? new RectF(mInnerRect) : null;
        s.mPath = new Path(mPath);
        return s;
    }
}
