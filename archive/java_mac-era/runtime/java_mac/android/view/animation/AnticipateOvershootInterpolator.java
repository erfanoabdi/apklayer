package android.view.animation;
import android.content.Context;
import android.util.AttributeSet;

// AOSP AnticipateOvershootInterpolator: the change starts backward, flings
// forward, overshoots the end, and settles. Formula verbatim from AOSP;
// default tension 2.0 with extraTension 1.5.
// AOSP: `extends BaseInterpolator` (BaseInterpolator itself implements
// Interpolator). The interface-only form left BaseInterpolator out of the chain,
// so an app or library holding a BaseInterpolator-typed field could not take one
// of these, and AnimationUtils' XML-inflated change-interval bookkeeping had
// nowhere to live. frameworks/base/core/java/android/view/animation/AnticipateOvershootInterpolator.java
public class AnticipateOvershootInterpolator extends BaseInterpolator {
    private final float mTension;
    public AnticipateOvershootInterpolator() { mTension = 2.0f * 1.5f; }
    public AnticipateOvershootInterpolator(float tension) { mTension = tension * 1.5f; }
    public AnticipateOvershootInterpolator(float tension, float extraTension) {
        mTension = tension * extraTension;
    }
    public AnticipateOvershootInterpolator(Context context, AttributeSet attrs) { mTension = 2.0f * 1.5f; }
    private static float a(float t, float s) { return t * t * ((s + 1) * t - s); }
    private static float o(float t, float s) { return t * t * ((s + 1) * t + s); }
    @Override public float getInterpolation(float t) {
        if (t < 0.5f) return 0.5f * a(t * 2.0f, mTension);
        else          return 0.5f * (o(t * 2.0f - 2.0f, mTension) + 2.0f);
    }
}
