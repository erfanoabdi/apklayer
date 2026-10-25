// android.graphics.drawable.shapes.RectShape -- AOSP Shape that draws a
// rectangle filling the shape's bounds. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/shapes/RectShape.java
// Superclass of RoundRectShape; subclasses draw into the protected
// rect() RectF kept in sync with the shape size.
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;

public class RectShape extends Shape {
    private RectF mRect = new RectF();

    public RectShape() {}

    @Override public void draw(Canvas canvas, Paint paint) {
        canvas.drawRect(mRect, paint);
    }

    @Override protected void onResize(float width, float height) {
        mRect.set(0, 0, width, height);
    }

    @Override public void getOutline(Outline outline) {
        final RectF r = rect();
        outline.setRect((int) Math.ceil(r.left), (int) Math.ceil(r.top),
                        (int) Math.floor(r.right), (int) Math.floor(r.bottom));
    }

    /** The current rectangle bounds (subclasses draw into this). */
    protected final RectF rect() { return mRect; }

    @Override public RectShape clone() throws CloneNotSupportedException {
        RectShape s = (RectShape) super.clone();
        s.mRect = new RectF(mRect);
        return s;
    }
}
