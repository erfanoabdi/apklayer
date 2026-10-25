package android.view.animation;
import android.content.Context;
import android.util.AttributeSet;

// AOSP AnticipateInterpolator: the change starts backward then flings forward.
// Formula verbatim from AOSP; default tension 2.0.
// AOSP: `extends BaseInterpolator` (BaseInterpolator itself implements
// Interpolator). The interface-only form left BaseInterpolator out of the chain,
// so an app or library holding a BaseInterpolator-typed field could not take one
// of these, and AnimationUtils' XML-inflated change-interval bookkeeping had
// nowhere to live. frameworks/base/core/java/android/view/animation/AnticipateInterpolator.java
public class AnticipateInterpolator extends BaseInterpolator {
    private final float mTension;
    public AnticipateInterpolator() { mTension = 2.0f; }
    public AnticipateInterpolator(float tension) { mTension = tension; }
    public AnticipateInterpolator(Context context, AttributeSet attrs) { mTension = 2.0f; }
    @Override public float getInterpolation(float t) {
        // a(t) = t * t * ((tension + 1) * t - tension)
        return t * t * ((mTension + 1) * t - mTension);
    }
}
