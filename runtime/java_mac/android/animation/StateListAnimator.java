// android.animation.StateListAnimator -- maps view drawable-states to
// Animators (e.g. a Material button's elevation lift-on-press, or
// AppBarLayout's lift-on-scroll). M23 (flag-gated on MACAPK_REAL_LOOPER):
// a REAL driver. setState(int[]) finds the first tuple whose state spec
// matches (StateSet.stateSetMatches) and starts its Animator on the
// target; off-flag it stays an inert container (the legacy behavior), so
// the shipped build is unchanged. Public surface mirrors AOSP
// frameworks/base core/java/android/animation/StateListAnimator.java.
package android.animation;

import java.util.ArrayList;

public class StateListAnimator implements Cloneable {
    private static final class Tuple {
        final int[] specs;
        final Animator animator;
        Tuple(int[] s, Animator a) { specs = s; animator = a; }
    }

    private ArrayList<Tuple> tuples = new ArrayList<>();
    private java.lang.ref.WeakReference<android.view.View> target;
    private Tuple lastMatch;
    private Animator runningAnimator;

    public StateListAnimator() {}

    public void addState(int[] specs, Animator animator) {
        tuples.add(new Tuple(specs, animator));
    }

    /** AOSP: View.setStateListAnimator sets the target so the animators
     *  have something to apply their property changes to. */
    public void setTarget(android.view.View view) {
        android.view.View current = (target == null) ? null : target.get();
        if (current == view) return;
        if (current != null) clearTarget();
        if (view != null) {
            for (int i = 0; i < tuples.size(); i++) {
                Animator a = tuples.get(i).animator;
                if (a != null) {
                    try { a.setTarget(view); } catch (Throwable ignored) {}
                }
            }
        }
        target = (view == null) ? null : new java.lang.ref.WeakReference<>(view);
    }

    public android.view.View getTarget() { return target == null ? null : target.get(); }

    private void clearTarget() {
        if (runningAnimator != null) {
            try { runningAnimator.cancel(); } catch (Throwable ignored) {}
            runningAnimator = null;
        }
        lastMatch = null;
    }

    /** AOSP: called by View.drawableStateChanged. Find the first tuple
     *  whose spec matches the new state and run its animator (only when a
     *  DIFFERENT tuple wins than last time, so a state change that keeps
     *  the same winning entry doesn't restart it). */
    public void setState(int[] state) {
        if (!android.os.Looper.MACAPK_REAL_LOOPER) return;
        Tuple match = null;
        for (int i = 0; i < tuples.size(); i++) {
            if (android.util.StateSet.stateSetMatches(tuples.get(i).specs, state)) {
                match = tuples.get(i);
                break;
            }
        }
        if (match == lastMatch) return;
        if (runningAnimator != null) {
            try { runningAnimator.cancel(); } catch (Throwable ignored) {}
            runningAnimator = null;
        }
        lastMatch = match;
        if (match != null && match.animator != null) {
            android.view.View v = getTarget();
            if (v != null) {
                try {
                    match.animator.setTarget(v);
                    match.animator.start();
                    runningAnimator = match.animator;
                } catch (Throwable ignored) {}
            }
        }
    }

    public Animator getCurrentAnimation() { return runningAnimator; }

    /** AOSP: snap the current state's animator straight to its end value
     *  (no animation), used during attach. */
    public void jumpToCurrentState() {
        if (runningAnimator != null) {
            try { runningAnimator.end(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public StateListAnimator clone() {
        try {
            StateListAnimator c = (StateListAnimator) super.clone();
            c.tuples = new ArrayList<>(tuples);
            c.target = null;
            c.lastMatch = null;
            c.runningAnimator = null;
            return c;
        } catch (CloneNotSupportedException e) {
            return new StateListAnimator();
        }
    }
}
