// android.view.animation.LinearInterpolator -- identity interpolator.
// Material widgets, AppCompat transitions, RecyclerView ItemAnimator all
// instantiate this; minimal stub returns input fraction unchanged.
package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;

// AOSP: `extends BaseInterpolator` (BaseInterpolator itself implements
// Interpolator). The interface-only form left BaseInterpolator out of the chain,
// so an app or library holding a BaseInterpolator-typed field could not take one
// of these, and AnimationUtils' XML-inflated change-interval bookkeeping had
// nowhere to live. frameworks/base/core/java/android/view/animation/LinearInterpolator.java
public class LinearInterpolator extends BaseInterpolator {
    public LinearInterpolator() {}
    public LinearInterpolator(Context context, AttributeSet attrs) {}
    @Override public float getInterpolation(float input) { return input; }
}
