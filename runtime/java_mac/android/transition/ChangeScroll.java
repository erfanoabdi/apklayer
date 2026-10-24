// android.transition.ChangeScroll -- animates a view's scroll position between
// scenes. Was absent. We capture scrollX/scrollY and tween via scrollTo, driving
// the existing View scroll path. Mirrors
// frameworks/base/core/java/android/transition/ChangeScroll.java.
package android.transition;

import android.util.AttributeSet;

import android.content.Context;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

public class ChangeScroll extends Transition {

    private static final String PROP_X = "android:changeScroll:x";
    private static final String PROP_Y = "android:changeScroll:y";

    public ChangeScroll() {}

    /** AOSP ChangeScroll(Context, AttributeSet) -- the XML-inflation constructor; all of
     *  its configuration lives on the superclass, so it just chains up. */
    public ChangeScroll(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void capture(TransitionValues values) {
        View v = values.view;
        if (v == null) return;
        values.values.put(PROP_X, v.getScrollX());
        values.values.put(PROP_Y, v.getScrollY());
    }

    @Override public void captureStartValues(TransitionValues transitionValues) { capture(transitionValues); }
    @Override public void captureEndValues(TransitionValues transitionValues) { capture(transitionValues); }

    private static int i(TransitionValues v, String key) {
        Object o = v.values.get(key);
        return (o instanceof Integer) ? (Integer) o : 0;
    }

    @Override
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null || endValues == null) return null;
        final View view = endValues.view;
        final int sx = i(startValues, PROP_X), ex = i(endValues, PROP_X);
        final int sy = i(startValues, PROP_Y), ey = i(endValues, PROP_Y);
        if (sx == ex && sy == ey) return null;

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float p = a.getAnimatedFraction();
                view.scrollTo(Math.round(sx + (ex - sx) * p), Math.round(sy + (ey - sy) * p));
            }
        });
        return anim;
    }
}
