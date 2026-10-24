package android.animation;

// AOSP android.animation.IntEvaluator: linear interpolation between two ints.
// Used by ValueAnimator.ofObject for Integer properties.
public class IntEvaluator implements TypeEvaluator<Integer> {
    @Override
    public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
        int start = startValue;
        return (int) (start + fraction * (endValue - start));
    }
}
