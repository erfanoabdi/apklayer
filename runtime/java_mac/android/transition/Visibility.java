// android.transition.Visibility -- the base for transitions that animate a view
// appearing or disappearing (Fade, Slide, Explode). It captures the view's
// visibility + on-screen position at start and end, decides whether the view is
// appearing or disappearing, and delegates to onAppear/onDisappear. Was absent;
// Fade/Slide reference it as their superclass so it must exist.
// Mirrors frameworks/base/core/java/android/transition/Visibility.java (the
// observable appear/disappear dispatch; the parent-visibility edge cases AOSP
// handles for removed views are simplified).
package android.transition;

import android.util.AttributeSet;
import android.content.Context;
import android.animation.Animator;
import android.view.View;
import android.view.ViewGroup;

public abstract class Visibility extends Transition {

    public static final int MODE_IN = 0x1;
    public static final int MODE_OUT = 0x2;

    // Package-private (AOSP declares it the same way) so VisibilityPropagation can
    // reuse a visibility this transition already captured instead of re-reading it.
    static final String PROPNAME_VISIBILITY = "android:visibility:visibility";
    private static final String PROPNAME_PARENT = "android:visibility:parent";

    private int mMode = MODE_IN | MODE_OUT;

    public Visibility() {}

    /**
     * AOSP Visibility.java:99-107 -- the XML-inflation constructor. Reads
     * android:transitionVisibilityMode (attrs.xml declares it as FLAGS:
     * mode_in=1, mode_out=2) and only applies it when non-zero, so an absent
     * attribute leaves the MODE_IN|MODE_OUT default alone.
     */
    public Visibility(Context context, AttributeSet attrs) {
        super(context, attrs);
        int mode = Transition.getEnumAttr(attrs, "transitionVisibilityMode",
                new String[] { "mode_in", "mode_out" },
                new int[] { MODE_IN, MODE_OUT }, 0);
        if (mode != 0) {
            setMode(mode);
        }
    }

    public void setMode(int mode) { mMode = mode; }
    public int getMode() { return mMode; }

    @Override
    public String[] getTransitionProperties() {
        return new String[] { PROPNAME_VISIBILITY, PROPNAME_PARENT };
    }

    private void captureValues(TransitionValues transitionValues) {
        int visibility = transitionValues.view.getVisibility();
        transitionValues.values.put(PROPNAME_VISIBILITY, visibility);
        transitionValues.values.put(PROPNAME_PARENT, transitionValues.view.getParent());
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    public boolean isVisible(TransitionValues values) {
        if (values == null) return false;
        Object v = values.values.get(PROPNAME_VISIBILITY);
        Object parent = values.values.get(PROPNAME_PARENT);
        int visibility = (v instanceof Integer) ? (Integer) v : View.GONE;
        return visibility == View.VISIBLE && parent != null;
    }

    // The raw android:visibility int a capture recorded, or View.GONE when the
    // side was not captured at all -- the value AOSP's VisibilityInfo carries
    // into the five-argument onAppear/onDisappear below.
    private static int visibilityOf(TransitionValues values) {
        if (values == null) return View.GONE;
        Object v = values.values.get(PROPNAME_VISIBILITY);
        return (v instanceof Integer) ? ((Integer) v).intValue() : View.GONE;
    }

    @Override
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        boolean startVisible = isVisible(startValues);
        boolean endVisible = isVisible(endValues);
        // AOSP Visibility.java:247-264 dispatches through the FIVE-argument
        // onAppear/onDisappear, and those in turn call the four-argument ones.
        // Going straight to the four-argument form (what this did) means a
        // subclass that overrides only the five-argument hook -- the one AOSP's
        // own javadoc names first, and the form androidx's Visibility uses --
        // is never called at all.
        if (!startVisible && endVisible) {
            return onAppear(sceneRoot, startValues, visibilityOf(startValues),
                    endValues, visibilityOf(endValues));
        }
        if (startVisible && !endVisible) {
            return onDisappear(sceneRoot, startValues, visibilityOf(startValues),
                    endValues, visibilityOf(endValues));
        }
        return null;
    }

    /**
     * AOSP Visibility.java:285-304. The mode gate lives HERE, not in
     * createAnimator, and the default implementation forwards to the
     * four-argument onAppear with the END scene's view.
     */
    public Animator onAppear(ViewGroup sceneRoot, TransitionValues startValues,
            int startVisibility, TransitionValues endValues, int endVisibility) {
        if ((mMode & MODE_IN) != MODE_IN || endValues == null) {
            return null;
        }
        return onAppear(sceneRoot, endValues.view, startValues, endValues);
    }

    /**
     * AOSP Visibility.java:360-372. Note AOSP's own comment that startValues is
     * never null for a disappear, and its early return when it is.
     */
    public Animator onDisappear(ViewGroup sceneRoot, TransitionValues startValues,
            int startVisibility, TransitionValues endValues, int endVisibility) {
        if ((mMode & MODE_OUT) != MODE_OUT) {
            return null;
        }
        if (startValues == null) {
            // startValues (and startView) will never be null for a disappear transition.
            return null;
        }
        return onDisappear(sceneRoot, startValues.view, startValues, endValues);
    }

    public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        return null;
    }

    public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
            TransitionValues endValues) {
        return null;
    }
}
