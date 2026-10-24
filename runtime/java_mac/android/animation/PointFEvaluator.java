// android.animation.PointFEvaluator -- interpolates between two PointF values.
// Was absent. Used to animate a position vector. Mirrors
// frameworks/base/core/java/android/animation/PointFEvaluator.java.
package android.animation;

import android.graphics.PointF;

public class PointFEvaluator implements TypeEvaluator<PointF> {
    private PointF mPoint;

    public PointFEvaluator() {}
    public PointFEvaluator(PointF reusePoint) { mPoint = reusePoint; }

    @Override
    public PointF evaluate(float fraction, PointF startValue, PointF endValue) {
        float x = startValue.x + (fraction * (endValue.x - startValue.x));
        float y = startValue.y + (fraction * (endValue.y - startValue.y));
        if (mPoint != null) {
            mPoint.x = x;
            mPoint.y = y;
            return mPoint;
        }
        return new PointF(x, y);
    }
}
