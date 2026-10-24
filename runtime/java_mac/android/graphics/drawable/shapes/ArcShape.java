// android.graphics.drawable.shapes.ArcShape -- AOSP RectShape that draws
// an arc / pie slice spanning sweepAngle degrees from startAngle. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/shapes/ArcShape.java
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public class ArcShape extends RectShape {
    private final float mStart;
    private final float mSweep;

    public ArcShape(float startAngle, float sweepAngle) {
        mStart = startAngle;
        mSweep = sweepAngle;
    }

    public final float getStartAngle() { return mStart; }
    public final float getSweepAngle() { return mSweep; }

    @Override public void draw(Canvas canvas, Paint paint) {
        final RectF r = rect();
        canvas.drawArc(r, mStart, mSweep, true, paint);
    }
}
