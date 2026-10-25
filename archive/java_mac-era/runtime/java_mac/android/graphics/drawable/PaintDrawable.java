// android.graphics.drawable.PaintDrawable -- AOSP ShapeDrawable subclass
// that draws a solid color rounded-rect (or rect). AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/PaintDrawable.java
// setCornerRadius / setCornerRadii pick the shape; the inherited paint
// holds the color. Used as a simple themed background.
package android.graphics.drawable;

import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.RoundRectShape;

public class PaintDrawable extends ShapeDrawable {
    public PaintDrawable() {
        setShape(new RectShape());
    }
    public PaintDrawable(int color) {
        setShape(new RectShape());
        getPaint().setColor(color);
    }

    /** Round all four corners by radius. radius <= 0 reverts to a rect. */
    public void setCornerRadius(float radius) {
        if (radius <= 0) {
            setShape(new RectShape());
            return;
        }
        float[] radii = new float[8];
        for (int i = 0; i < 8; i++) radii[i] = radius;
        setShape(new RoundRectShape(radii, null, null));
    }

    /** Per-corner radii (8 values: rx,ry per corner TL,TR,BR,BL). */
    // AOSP returns void. frameworks/base/graphics/java/android/graphics/drawable/PaintDrawable.java
    public void setCornerRadii(float[] radii) {
        if (radii == null) {
            setShape(new RectShape());
        } else {
            setShape(new RoundRectShape(radii, null, null));
        }
    }
}
