// android.transition.ChangeClipBounds -- animates a view's clip-bounds rect
// between scenes. Was absent. We capture View.getClipBounds() and tween the rect,
// applying via setClipBounds each frame. Mirrors
// frameworks/base/core/java/android/transition/ChangeClipBounds.java. (Our
// setClipBounds is currently a no-op in View, so the clip does not visually apply
// yet, but the capture/diff/animator lifecycle and listener callbacks are
// faithful -- when View gains real clipping this transition animates for free.)
package android.transition;

import android.util.AttributeSet;

import android.content.Context;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

public class ChangeClipBounds extends Transition {

    private static final String PROP_CLIP = "android:clipBounds:clip";

    public ChangeClipBounds() {}

    /** AOSP ChangeClipBounds(Context, AttributeSet) -- the XML-inflation constructor; all of
     *  its configuration lives on the superclass, so it just chains up. */
    public ChangeClipBounds(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void capture(TransitionValues values) {
        View v = values.view;
        if (v == null) return;
        Rect clip = v.getClipBounds();
        values.values.put(PROP_CLIP, clip);
    }

    @Override public void captureStartValues(TransitionValues transitionValues) { capture(transitionValues); }
    @Override public void captureEndValues(TransitionValues transitionValues) { capture(transitionValues); }

    @Override
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null || endValues == null) return null;
        Rect start = (Rect) startValues.values.get(PROP_CLIP);
        Rect end = (Rect) endValues.values.get(PROP_CLIP);
        if (start == null || end == null || start.equals(end)) return null;

        final View view = endValues.view;
        final int sl = start.left, st = start.top, sr = start.right, sb = start.bottom;
        final int el = end.left, et = end.top, er = end.right, eb = end.bottom;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float p = a.getAnimatedFraction();
                view.setClipBounds(new Rect(
                        Math.round(sl + (el - sl) * p),
                        Math.round(st + (et - st) * p),
                        Math.round(sr + (er - sr) * p),
                        Math.round(sb + (eb - sb) * p)));
            }
        });
        return anim;
    }
}
