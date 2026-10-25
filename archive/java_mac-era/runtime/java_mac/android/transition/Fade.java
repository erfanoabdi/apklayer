// android.transition.Fade -- fades a view's alpha in (on appear) or out (on
// disappear). The default member of AutoTransition for added/removed views, and a
// very common explicit transition (dialog/sheet content swaps, Fragment
// FADE transitions). Was absent. Mirrors
// frameworks/base/core/java/android/transition/Fade.java: it animates the view's
// TRANSITION alpha (0->1 on appear, captured->0 on disappear), never View.alpha.
package android.transition;

import android.util.AttributeSet;
import android.content.Context;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.ViewGroup;

public class Fade extends Visibility {

    public static final int IN = MODE_IN;
    public static final int OUT = MODE_OUT;

    public Fade() {}

    public Fade(int fadingMode) {
        setMode(fadingMode);
    }

    /**
     * AOSP Fade.java:101-107 -- the XML-inflation constructor. Reads
     * android:fadingMode (attrs.xml: fade_in=1 / fade_out=2 / fade_in_out=3,
     * i.e. Visibility's MODE_IN / MODE_OUT bits) and defaults to the mode the
     * superclass already has.
     */
    public Fade(Context context, AttributeSet attrs) {
        super(context, attrs);
        int fadingMode = Transition.getEnumAttr(attrs, "fadingMode",
                new String[] { "fade_in", "fade_out", "fade_in_out" },
                new int[] { MODE_IN, MODE_OUT, MODE_IN | MODE_OUT },
                getMode());
        setMode(fadingMode);
    }

    static final String PROPNAME_TRANSITION_ALPHA = "android:fade:transitionAlpha";

    /** AOSP Fade.captureStartValues (android14 :110-114): the view's
     *  TRANSITION alpha, so an interrupted fade resumes from where it was. */
    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        super.captureStartValues(transitionValues);
        if (transitionValues != null && transitionValues.view != null) {
            transitionValues.values.put(PROPNAME_TRANSITION_ALPHA,
                    transitionValues.view.getTransitionAlpha());
        }
    }

    /** AOSP Fade.onAppear (:141-154): transitionAlpha from the captured start
     *  (0 when none, or when it was already fully in) to 1.
     *
     *  ⚠ Ours animated the view's OWN alpha from 0 to "whatever alpha it had
     *  now". An interrupted fade leaves alpha mid-way, so the next appear
     *  animated 0 -> that fraction and parked there for good: leanback's
     *  headers sidebar (lb_browse_headers_in/out both carry Fades, run on every
     *  show/hide) drew every header at alpha ~0.05, i.e. a blank panel. AOSP
     *  never touches View.alpha here; transitionAlpha is a separate multiplier
     *  (ViewGroup draws alpha * transitionAlpha) that always ends at 1. */
    @Override
    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        if (view == null) return null;
        float startAlpha = getStartAlpha(startValues, 0f);
        if (startAlpha == 1f) startAlpha = 0f;
        return createAnimation(view, startAlpha, 1f);
    }

    /** AOSP Fade.onDisappear (:157-161): transitionAlpha from the captured
     *  start (1 when none) to 0; the listener puts it back to 1 at the end. */
    @Override
    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        if (view == null) return null;
        return createAnimation(view, getStartAlpha(startValues, 1f), 0f);
    }

    private static float getStartAlpha(TransitionValues startValues, float fallbackValue) {
        if (startValues != null) {
            Object f = startValues.values.get(PROPNAME_TRANSITION_ALPHA);
            if (f instanceof Float) return (Float) f;
        }
        return fallbackValue;
    }

    /** AOSP Fade.createAnimation (:119-139). */
    private Animator createAnimation(final View view, float startAlpha, float endAlpha) {
        if (startAlpha == endAlpha) return null;
        view.setTransitionAlpha(startAlpha);
        final ObjectAnimator anim = ObjectAnimator.ofFloat(view, "transitionAlpha", startAlpha, endAlpha);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                view.setTransitionAlpha(1f);
            }
        });
        addListener(new TransitionListenerAdapter() {
            @Override public void onTransitionEnd(Transition transition) {
                view.setTransitionAlpha(1f);
                transition.removeListener(this);
            }
        });
        return anim;
    }
}
