// android.transition.ChangeTransform -- animates a view's scale, rotation and
// translation between scenes (the non-bounds half of a shared-element move). Was
// absent. We capture translationX/Y, scaleX/Y and rotation and tween each that
// differs, driving the existing View transform setters. Mirrors the observable
// behavior of frameworks/base/core/java/android/transition/ChangeTransform.java
// (the matrix-parenting reparent edge cases AOSP handles are simplified).
package android.transition;

import android.util.AttributeSet;
import android.content.Context;
import android.animation.Animator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

public class ChangeTransform extends Transition {

    private static final String PROP_TX = "android:changeTransform:translationX";
    private static final String PROP_TY = "android:changeTransform:translationY";
    private static final String PROP_SX = "android:changeTransform:scaleX";
    private static final String PROP_SY = "android:changeTransform:scaleY";
    private static final String PROP_ROT = "android:changeTransform:rotation";

    private boolean mReparent = true;
    private boolean mUseOverlay = true;

    public ChangeTransform() {}

    /**
     * AOSP ChangeTransform.java:103-109 -- the XML-inflation constructor. Reads
     * android:reparentWithOverlay and android:reparent, both defaulting to true.
     */
    public ChangeTransform(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            mUseOverlay = attrs.getAttributeBooleanValue(
                    Transition.ANDROID_NS, "reparentWithOverlay", true);
            mReparent = attrs.getAttributeBooleanValue(
                    Transition.ANDROID_NS, "reparent", true);
        }
    }

    public boolean getReparent() { return mReparent; }
    public void setReparent(boolean reparent) { mReparent = reparent; }
    public boolean getReparentWithOverlay() { return mUseOverlay; }
    public void setReparentWithOverlay(boolean reparentWithOverlay) { mUseOverlay = reparentWithOverlay; }

    private void capture(TransitionValues values) {
        View v = values.view;
        if (v == null) return;
        values.values.put(PROP_TX, v.getTranslationX());
        values.values.put(PROP_TY, v.getTranslationY());
        values.values.put(PROP_SX, v.getScaleX());
        values.values.put(PROP_SY, v.getScaleY());
        values.values.put(PROP_ROT, v.getRotation());
    }

    @Override public void captureStartValues(TransitionValues transitionValues) { capture(transitionValues); }
    @Override public void captureEndValues(TransitionValues transitionValues) { capture(transitionValues); }

    private static float f(TransitionValues v, String key) {
        Object o = v.values.get(key);
        return (o instanceof Float) ? (Float) o : 0f;
    }

    @Override
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null || endValues == null) return null;
        final View view = endValues.view;
        final float stx = f(startValues, PROP_TX), etx = f(endValues, PROP_TX);
        final float sty = f(startValues, PROP_TY), ety = f(endValues, PROP_TY);
        final float ssx = f(startValues, PROP_SX), esx = f(endValues, PROP_SX);
        final float ssy = f(startValues, PROP_SY), esy = f(endValues, PROP_SY);
        final float srot = f(startValues, PROP_ROT), erot = f(endValues, PROP_ROT);
        if (stx == etx && sty == ety && ssx == esx && ssy == esy && srot == erot) return null;

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float p = a.getAnimatedFraction();
                view.setTranslationX(stx + (etx - stx) * p);
                view.setTranslationY(sty + (ety - sty) * p);
                view.setScaleX(ssx + (esx - ssx) * p);
                view.setScaleY(ssy + (esy - ssy) * p);
                view.setRotation(srot + (erot - srot) * p);
            }
        });
        return anim;
    }
}
