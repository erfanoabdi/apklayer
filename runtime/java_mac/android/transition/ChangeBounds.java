// android.transition.ChangeBounds -- animates a view's layout bounds (position +
// size) between scenes. The workhorse of shared-element-style and "expand a card"
// transitions, and a default member of AutoTransition. Was absent. Mirrors
// frameworks/base/core/java/android/transition/ChangeBounds.java: capture the
// view's (left,top,right,bottom) at start and end, then tween the bounds and
// re-layout the view each frame.
package android.transition;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class ChangeBounds extends Transition {

    private static final String PROPNAME_BOUNDS = "android:changeBounds:bounds";

    private boolean mResizeClip = false;
    private boolean mReparent = false;

    public ChangeBounds() {}

    /**
     * AOSP ChangeBounds.java:177-184 -- the XML-inflation constructor. Reads
     * android:resizeClip (boolean, default false).
     */
    public ChangeBounds(Context context, AttributeSet attrs) {
        super(context, attrs);
        boolean resizeClip = (attrs != null)
                && attrs.getAttributeBooleanValue(Transition.ANDROID_NS, "resizeClip", false);
        setResizeClip(resizeClip);
    }

    /**
     * AOSP ChangeBounds.java:205-219. When true, ChangeBounds resizes the view
     * by animating its CLIP BOUNDS instead of its layout dimensions.
     *
     * ⚠ KNOWN PARTIAL, and deliberately so: the flag is stored and reported
     * back, but createAnimator below always animates layout bounds. Honouring
     * it requires View.setClipBounds, which is an empty stub in this tree
     * (android/view/View.java) and belongs to another package. Wiring
     * resizeClip to it today would move the view straight to its END bounds
     * with NO clip ever applied -- visibly worse than ignoring the flag. When
     * View.setClipBounds becomes real, the branch belongs here.
     */
    public void setResizeClip(boolean resizeClip) {
        mResizeClip = resizeClip;
    }

    public boolean getResizeClip() {
        return mResizeClip;
    }

    /**
     * AOSP ChangeBounds.java:234-236 (@Deprecated there too). Tells ChangeBounds
     * to track each target's before/after PARENT and animate a parent change.
     *
     * ⚠ KNOWN PARTIAL: stored and reported, but our captureValues does not
     * record the parent, so a reparent is not animated. AOSP deprecated this
     * precisely because it only works when the two scenes share parent
     * instances or ids; ChangeTransform is the supported replacement and IS
     * implemented here.
     */
    @Deprecated
    public void setReparent(boolean reparent) {
        mReparent = reparent;
    }

    @Override
    public String[] getTransitionProperties() {
        return new String[] { PROPNAME_BOUNDS };
    }

    private void captureValues(TransitionValues values) {
        View view = values.view;
        if (view == null) return;
        Rect bounds = new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
        values.values.put(PROPNAME_BOUNDS, bounds);
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        if (startValues == null || endValues == null) return null;
        final View view = endValues.view;
        Rect start = (Rect) startValues.values.get(PROPNAME_BOUNDS);
        Rect end = (Rect) endValues.values.get(PROPNAME_BOUNDS);
        if (start == null || end == null || start.equals(end)) return null;

        final int sl = start.left, st = start.top, sr = start.right, sb = start.bottom;
        final int el = end.left, et = end.top, er = end.right, eb = end.bottom;

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float f = a.getAnimatedFraction();
                int l = Math.round(sl + (el - sl) * f);
                int t = Math.round(st + (et - st) * f);
                int r = Math.round(sr + (er - sr) * f);
                int b = Math.round(sb + (eb - sb) * f);
                view.layout(l, t, r, b);
            }
        });
        return anim;
    }
}
