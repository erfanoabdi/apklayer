// android.animation.TypeEvaluator -- interpolates between two values
// of type T. Used by ValueAnimator.ofObject. AOSP has ArgbEvaluator,
// FloatEvaluator, IntEvaluator implementations.
package android.animation;

public interface TypeEvaluator<T> {
    T evaluate(float fraction, T startValue, T endValue);
}
