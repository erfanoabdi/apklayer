package android.view.animation;
import android.content.Context;
import android.util.AttributeSet;

// AOSP frameworks/base/core/java/android/view/animation/BounceInterpolator:
// the change bounces near the end. Formula + constants verbatim from AOSP.
// AOSP: `extends BaseInterpolator` (BaseInterpolator itself implements
// Interpolator). The interface-only form left BaseInterpolator out of the chain,
// so an app or library holding a BaseInterpolator-typed field could not take one
// of these, and AnimationUtils' XML-inflated change-interval bookkeeping had
// nowhere to live. frameworks/base/core/java/android/view/animation/BounceInterpolator.java
public class BounceInterpolator extends BaseInterpolator {
    public BounceInterpolator() {}
    public BounceInterpolator(Context context, AttributeSet attrs) {}
    private static float bounce(float t) { return t * t * 8.0f; }
    @Override public float getInterpolation(float t) {
        t *= 1.1226f;
        if (t < 0.3535f)      return bounce(t);
        else if (t < 0.7408f) return bounce(t - 0.54719f) + 0.7f;
        else if (t < 0.9644f) return bounce(t - 0.8526f) + 0.9f;
        else                  return bounce(t - 1.0435f) + 0.95f;
    }
}
