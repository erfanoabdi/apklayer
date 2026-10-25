package android.view.animation;
import android.content.Context;
import android.util.AttributeSet;

// AOSP: `extends BaseInterpolator` (BaseInterpolator itself implements
// Interpolator). The interface-only form left BaseInterpolator out of the chain,
// so an app or library holding a BaseInterpolator-typed field could not take one
// of these, and AnimationUtils' XML-inflated change-interval bookkeeping had
// nowhere to live. frameworks/base/core/java/android/view/animation/AccelerateInterpolator.java
public class AccelerateInterpolator extends BaseInterpolator {
    private final double factor;
    public AccelerateInterpolator() { factor = 1.0; }
    public AccelerateInterpolator(float factor) { this.factor = factor; }
    public AccelerateInterpolator(Context context, AttributeSet attrs) { this.factor = 1.0; }
    @Override public float getInterpolation(float input) {
        return (float) Math.pow(input, 2.0 * factor);
    }
}
