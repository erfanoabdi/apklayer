// android.graphics.drawable.shapes.OvalShape -- AOSP RectShape that
// draws an oval inscribed in its bounds. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/shapes/OvalShape.java
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;

public class OvalShape extends RectShape {
    public OvalShape() {}

    @Override public void draw(Canvas canvas, Paint paint) {
        canvas.drawOval(rect(), paint);
    }

    @Override public void getOutline(Outline outline) {
        final android.graphics.RectF r = rect();
        outline.setOval((int) Math.ceil(r.left), (int) Math.ceil(r.top),
                        (int) Math.floor(r.right), (int) Math.floor(r.bottom));
    }
}
