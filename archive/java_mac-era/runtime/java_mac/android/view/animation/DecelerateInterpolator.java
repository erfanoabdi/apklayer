package android.view.animation;
import android.content.Context;
import android.util.AttributeSet;

// AOSP: `extends BaseInterpolator` (BaseInterpolator itself implements
// Interpolator). The interface-only form left BaseInterpolator out of the chain,
// so an app or library holding a BaseInterpolator-typed field could not take one
// of these, and AnimationUtils' XML-inflated change-interval bookkeeping had
// nowhere to live. frameworks/base/core/java/android/view/animation/DecelerateInterpolator.java
public class DecelerateInterpolator extends BaseInterpolator {
    private final float factor;
    public DecelerateInterpolator() { factor = 1.0f; }
    public DecelerateInterpolator(float factor) { this.factor = factor; }
    public DecelerateInterpolator(Context context, AttributeSet attrs) { this.factor = 1.0f; }
    @Override public float getInterpolation(float input) {
        if (factor == 1.0f) return (float) (1.0f - (1.0f - input) * (1.0f - input));
        return (float) (1.0f - Math.pow(1.0f - input, 2 * factor));
    }
}
