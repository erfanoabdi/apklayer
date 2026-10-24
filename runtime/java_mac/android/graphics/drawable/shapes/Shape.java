// android.graphics.drawable.shapes.Shape -- AOSP base class for a
// graphical shape that draws itself into a Canvas with a Paint. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/shapes/Shape.java
// ShapeDrawable holds a Shape and forwards draw() to it. Subclasses
// (Rect/Oval/RoundRect/Arc/Path) implement the actual primitive.
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;

public abstract class Shape implements Cloneable {
    private float mWidth;
    private float mHeight;

    public Shape() {}

    public final float getWidth()  { return mWidth; }
    public final float getHeight() { return mHeight; }

    /** Draw this shape into the canvas using the supplied paint. */
    public abstract void draw(Canvas canvas, Paint paint);

    /** Resize the shape; triggers onResize when the size actually changes. */
    public final void resize(float width, float height) {
        if (width < 0) width = 0;
        if (height < 0) height = 0;
        if (mWidth != width || mHeight != height) {
            mWidth = width;
            mHeight = height;
            onResize(width, height);
        }
    }

    /** Subclass hook invoked when the shape is resized. */
    protected void onResize(float width, float height) {}

    /** True if the shape may render with non-opaque (alpha) pixels. AOSP
     *  default is true (conservative). */
    public boolean hasAlpha() { return true; }

    /** Compute the outline of this shape into outline. Default: no-op. */
    public void getOutline(Outline outline) {}

    @Override public Shape clone() throws CloneNotSupportedException {
        return (Shape) super.clone();
    }
}
