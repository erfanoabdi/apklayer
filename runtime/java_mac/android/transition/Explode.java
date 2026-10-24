// android.transition.Explode -- moves views in/out toward the scene edges from an
// epicenter (a Visibility transition). Was absent. We implement a faithful
// appear/disappear via translation toward the nearest edge; without a real
// epicenter callback we use the scene center, which is the AOSP default when no
// epicenter is set. Mirrors
// frameworks/base/core/java/android/transition/Explode.java's observable motion.
package android.transition;

import android.util.AttributeSet;

import android.content.Context;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.ViewGroup;

public class Explode extends Visibility {

    public Explode() {}

    /** AOSP Explode(Context, AttributeSet) -- the XML-inflation constructor; all of
     *  its configuration lives on the superclass, so it just chains up. */
    public Explode(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private float[] calculateOut(ViewGroup sceneRoot, View view) {
        float cx = sceneRoot != null ? sceneRoot.getWidth() / 2f : 0f;
        float cy = sceneRoot != null ? sceneRoot.getHeight() / 2f : 0f;
        float vx = view.getLeft() + view.getWidth() / 2f;
        float vy = view.getTop() + view.getHeight() / 2f;
        float dx = vx - cx;
        float dy = vy - cy;
        if (dx == 0 && dy == 0) { dx = 0; dy = -(sceneRoot != null ? sceneRoot.getHeight() : view.getHeight()); }
        return new float[] { dx, dy };
    }

    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        if (view == null) return null;
        float[] o = calculateOut(sceneRoot, view);
        view.setTranslationX(o[0]);
        view.setTranslationY(o[1]);
        // Animate translationX to 0; snap Y at end (single-axis-dominant tween).
        final View v = view;
        Animator anim = ObjectAnimator.ofFloat(view, "translationX", o[0], 0f);
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { v.setTranslationY(0f); }
        });
        return anim;
    }

    @Override
    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        if (view == null) return null;
        float[] o = calculateOut(sceneRoot, view);
        final View v = view;
        final float ty = o[1];
        Animator anim = ObjectAnimator.ofFloat(view, "translationX", 0f, o[0]);
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { v.setTranslationY(ty); }
        });
        return anim;
    }
}
