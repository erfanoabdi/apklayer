package android.view.animation;
import android.content.Context;
import android.util.AttributeSet;

// AOSP: `extends BaseInterpolator` (BaseInterpolator itself implements
// Interpolator). The interface-only form left BaseInterpolator out of the chain,
// so an app or library holding a BaseInterpolator-typed field could not take one
// of these, and AnimationUtils' XML-inflated change-interval bookkeeping had
// nowhere to live. frameworks/base/core/java/android/view/animation/AccelerateDecelerateInterpolator.java
public class AccelerateDecelerateInterpolator extends BaseInterpolator {
    public AccelerateDecelerateInterpolator() {}
    public AccelerateDecelerateInterpolator(Context context, AttributeSet attrs) {}
    @Override public float getInterpolation(float input) {
        return (float) (Math.cos((input + 1) * Math.PI) / 2.0f) + 0.5f;
    }
}
