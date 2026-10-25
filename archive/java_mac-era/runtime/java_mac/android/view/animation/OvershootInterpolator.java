package android.view.animation;
import android.content.Context;
import android.util.AttributeSet;

// AOSP: `extends BaseInterpolator` (BaseInterpolator itself implements
// Interpolator). The interface-only form left BaseInterpolator out of the chain,
// so an app or library holding a BaseInterpolator-typed field could not take one
// of these, and AnimationUtils' XML-inflated change-interval bookkeeping had
// nowhere to live. frameworks/base/core/java/android/view/animation/OvershootInterpolator.java
public class OvershootInterpolator extends BaseInterpolator {
    private final float tension;
    public OvershootInterpolator() { tension = 2.0f; }
    public OvershootInterpolator(float tension) { this.tension = tension; }
    public OvershootInterpolator(Context context, AttributeSet attrs) { this.tension = 2.0f; }
    @Override public float getInterpolation(float t) {
        t -= 1.0f;
        return t * t * ((tension + 1) * t + tension) + 1.0f;
    }
}
