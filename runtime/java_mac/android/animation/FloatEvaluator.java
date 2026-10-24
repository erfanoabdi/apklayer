package android.animation;

// AOSP android.animation.FloatEvaluator: linear interpolation between two
// floats. Used by ValueAnimator.ofObject for Float properties.
public class FloatEvaluator implements TypeEvaluator<Number> {
    @Override
    public Float evaluate(float fraction, Number startValue, Number endValue) {
        float start = startValue.floatValue();
        return start + fraction * (endValue.floatValue() - start);
    }
}
