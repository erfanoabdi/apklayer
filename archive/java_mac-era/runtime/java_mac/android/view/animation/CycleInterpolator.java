package android.view.animation;
import android.content.Context;
import android.util.AttributeSet;

// AOSP CycleInterpolator: repeats the animation for a given number of cycles,
// following a sine wave. Formula verbatim from AOSP.
// AOSP: `extends BaseInterpolator` (BaseInterpolator itself implements
// Interpolator). The interface-only form left BaseInterpolator out of the chain,
// so an app or library holding a BaseInterpolator-typed field could not take one
// of these, and AnimationUtils' XML-inflated change-interval bookkeeping had
// nowhere to live. frameworks/base/core/java/android/view/animation/CycleInterpolator.java
public class CycleInterpolator extends BaseInterpolator {
    private final float mCycles;
    public CycleInterpolator(float cycles) { mCycles = cycles; }
    public CycleInterpolator(Context context, AttributeSet attrs) { mCycles = 1.0f; }
    @Override public float getInterpolation(float input) {
        return (float) Math.sin(2 * mCycles * Math.PI * input);
    }
}
