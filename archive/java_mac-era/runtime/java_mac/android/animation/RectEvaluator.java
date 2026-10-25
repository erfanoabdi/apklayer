// android.animation.RectEvaluator -- interpolates between two Rect values
// edge-by-edge. Was absent. Used to animate a bounds rect. Mirrors
// frameworks/base/core/java/android/animation/RectEvaluator.java.
package android.animation;

import android.graphics.Rect;

public class RectEvaluator implements TypeEvaluator<Rect> {
    private Rect mRect;

    public RectEvaluator() {}
    public RectEvaluator(Rect reuseRect) { mRect = reuseRect; }

    @Override
    public Rect evaluate(float fraction, Rect startValue, Rect endValue) {
        int left = startValue.left + (int) ((endValue.left - startValue.left) * fraction);
        int top = startValue.top + (int) ((endValue.top - startValue.top) * fraction);
        int right = startValue.right + (int) ((endValue.right - startValue.right) * fraction);
        int bottom = startValue.bottom + (int) ((endValue.bottom - startValue.bottom) * fraction);
        if (mRect != null) {
            mRect.set(left, top, right, bottom);
            return mRect;
        }
        return new Rect(left, top, right, bottom);
    }
}
