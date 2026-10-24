// android.transition.TransitionSet -- an ordered group of child transitions
// played together or one after another. AutoTransition is a TransitionSet; apps
// also build their own. Was a bare stub. Mirrors
// frameworks/base/core/java/android/transition/TransitionSet.java for the public
// surface (addTransition / ORDERING_* / capture fan-out / play fan-out). The
// child transitions do the actual animating via their own createAnimator.
package android.transition;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

public class TransitionSet extends Transition {

    public static final int ORDERING_TOGETHER = 0;
    public static final int ORDERING_SEQUENTIAL = 1;

    // NOT final: clone() below must rebuild this with cloned children rather
    // than share the template's list (see clone()'s comment).
    private ArrayList<Transition> mTransitions = new ArrayList<Transition>();
    private int mPlayTogether = ORDERING_TOGETHER;
    // Aggregate start/end state for THIS set's own listeners -- see the top of
    // playTransition() for why this bookkeeping exists at all.
    private int mCurrentListeners;
    private boolean mSetStarted;

    public TransitionSet() {}

    /**
     * AOSP TransitionSet.java:103-110 -- the XML-inflation constructor. Reads
     * android:transitionOrdering (attrs.xml: together=0, sequential=1) and
     * defaults to ORDERING_TOGETHER.
     */
    public TransitionSet(Context context, AttributeSet attrs) {
        super(context, attrs);
        int ordering = Transition.getEnumAttr(attrs, "transitionOrdering",
                new String[] { "together", "sequential" },
                new int[] { ORDERING_TOGETHER, ORDERING_SEQUENTIAL },
                ORDERING_TOGETHER);
        setOrdering(ordering);
    }

    public TransitionSet setOrdering(int ordering) {
        mPlayTogether = ordering;
        return this;
    }
    public int getOrdering() { return mPlayTogether; }

    public TransitionSet addTransition(Transition transition) {
        if (transition != null) {
            mTransitions.add(transition);
            // AOSP TransitionSet.addTransitionInternal sets the child's parent so
            // Transition.getTransitionValues() can delegate up to the owning set.
            transition.mParent = this;
            // Propagate set-level timing to children that have none, matching AOSP.
            if (getDuration() >= 0) transition.setDuration(getDuration());
        }
        return this;
    }

    public TransitionSet removeTransition(Transition transition) {
        mTransitions.remove(transition);
        if (transition != null && transition.mParent == this) transition.mParent = null;
        return this;
    }

    // ---- Covariant overrides. -----------------------------------------------
    // AOSP redeclares each of these on TransitionSet with a TransitionSet return
    // type (see the android16 signature file, `public class TransitionSet`).
    // apidiff CANNOT see these missing: it tolerates a name+arity match against
    // the inherited Transition.addTarget(View) etc. But the return type is part
    // of the JVM descriptor, so an app compiled against the real SDK emits
    // `invokevirtual TransitionSet.addTarget:(Landroid/view/View;)Landroid/
    // transition/TransitionSet;` and gets a NoSuchMethodError against a jar that
    // only has the Transition-returning inherited form. That is the whole reason
    // these exist.

    @Override
    public TransitionSet addTarget(View target) { super.addTarget(target); return this; }

    @Override
    public TransitionSet addTarget(int targetId) { super.addTarget(targetId); return this; }

    @Override
    public TransitionSet addTarget(String targetName) { super.addTarget(targetName); return this; }

    @Override
    public TransitionSet addTarget(Class<?> targetType) { super.addTarget(targetType); return this; }

    @Override
    public TransitionSet removeTarget(View target) { super.removeTarget(target); return this; }

    @Override
    public TransitionSet removeTarget(int targetId) { super.removeTarget(targetId); return this; }

    @Override
    public TransitionSet removeTarget(String targetName) {
        super.removeTarget(targetName);
        return this;
    }

    @Override
    public TransitionSet removeTarget(Class<?> target) { super.removeTarget(target); return this; }

    @Override
    public TransitionSet addListener(TransitionListener listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public TransitionSet removeListener(TransitionListener listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public TransitionSet setStartDelay(long startDelay) {
        super.setStartDelay(startDelay);
        return this;
    }

    /**
     * AOSP Transition.getTransitionValues() delegates to its parent set; the SET
     * itself is the one holding the merged capture maps there. Here the CHILDREN
     * hold the captures (captureValues below fans out), so the set answers by
     * asking its children -- same observable answer for the caller, which is
     * what ChangeTransform-style "what did the parent view look like" lookups
     * need.
     */
    @Override
    public TransitionValues getTransitionValues(View view, boolean start) {
        for (int i = 0; i < mTransitions.size(); i++) {
            Transition child = mTransitions.get(i);
            // Ask the child DIRECTLY, not through its own getTransitionValues():
            // that would delegate straight back up to this set and recurse.
            TransitionValues values = child.findOwnTransitionValues(view, start);
            if (values != null) return values;
        }
        return null;
    }

    public int getTransitionCount() { return mTransitions.size(); }
    public Transition getTransitionAt(int index) {
        if (index < 0 || index >= mTransitions.size()) return null;
        return mTransitions.get(index);
    }

    @Override
    public TransitionSet setDuration(long duration) {
        super.setDuration(duration);
        for (Transition t : mTransitions) t.setDuration(duration);
        return this;
    }

    @Override
    public TransitionSet setInterpolator(android.animation.TimeInterpolator interpolator) {
        super.setInterpolator(interpolator);
        for (Transition t : mTransitions) t.setInterpolator(interpolator);
        return this;
    }

    // The set itself captures nothing; its children capture per their own props.
    @Override public void captureStartValues(TransitionValues transitionValues) {}
    @Override public void captureEndValues(TransitionValues transitionValues) {}

    // Fan out capture + play to children. TransitionManager calls our package
    // capture/play hooks (inherited), so we override them to drive children.
    @Override
    void captureValues(ViewGroup sceneRoot, boolean start) {
        for (Transition t : mTransitions) {
            // Push set-level targets onto children that have none would require
            // target plumbing; children default to the whole subtree like the set.
            t.captureValues(sceneRoot, start);
        }
    }

    @Override
    void playTransition(ViewGroup sceneRoot) {
        if (mTransitions.isEmpty()) {
            // AOSP TransitionSet.runAnimators(): "if (mTransitions.isEmpty()) {
            // start(); end(); return; }" -- an empty set still dispatches its OWN
            // start/end so a listener added directly to it is not stranded.
            // Silently returning here (the previous behavior) meant an empty
            // TransitionSet's onTransitionStart/onTransitionEnd never fired at all.
            dispatchStart();
            dispatchEnd();
            return;
        }
        // AOSP TransitionSet.setupStartEndListeners()/TransitionSetListener
        // (frameworks/base core/java/android/transition/TransitionSet.java):
        // every child is wrapped with a listener that promotes the FIRST child's
        // onTransitionStart into THIS set's own dispatchStart(), and calls this
        // set's own dispatchEnd() once EVERY child has ended (mCurrentListeners
        // decremented to 0, mirroring AOSP's field of the same name). Without
        // this, a TransitionListener added directly to a TransitionSet -- or to
        // the default AutoTransition, which every plain
        // TransitionManager.beginDelayedTransition(root) call plays, and which
        // IS a TransitionSet -- never received onTransitionStart/onTransitionEnd
        // at all: this method fans straight out to the children's own
        // playTransition() and previously never touched the set's own dispatch.
        mCurrentListeners = mTransitions.size();
        mSetStarted = false;
        TransitionListenerAdapter setListener = new TransitionListenerAdapter() {
            @Override public void onTransitionStart(Transition transition) {
                if (!mSetStarted) { mSetStarted = true; dispatchStart(); }
            }
            @Override public void onTransitionEnd(Transition transition) {
                transition.removeListener(this);
                if (--mCurrentListeners == 0) dispatchEnd();
            }
        };
        for (Transition t : mTransitions) t.addListener(setListener);

        if (mPlayTogether == ORDERING_SEQUENTIAL) {
            // AOSP TransitionSet.runAnimators() (frameworks/base core/java/
            // android/transition/TransitionSet.java) chains ORDERING_SEQUENTIAL
            // children by starting each one only once the previous child's
            // onTransitionEnd fires. AutoTransition -- the implicit default for
            // every plain TransitionManager.beginDelayedTransition(root) call --
            // is `new TransitionSet(); setOrdering(ORDERING_SEQUENTIAL);
            // addTransition(Fade(OUT)); addTransition(ChangeBounds());
            // addTransition(Fade(IN));` specifically so the exiting view finishes
            // fading, THEN bounds change, THEN the entering view fades in, not
            // all three at once. Every child already has its start/end
            // TransitionValues captured by the whole-set capture fan-out above
            // (captureValues), so calling playTransition(sceneRoot) on a later
            // child from the listener -- once its turn comes -- is safe.
            for (int i = 1; i < mTransitions.size(); i++) {
                final Transition next = mTransitions.get(i);
                Transition previous = mTransitions.get(i - 1);
                previous.addListener(new TransitionListenerAdapter() {
                    @Override public void onTransitionEnd(Transition transition) {
                        transition.removeListener(this);
                        next.playTransition(sceneRoot);
                    }
                });
            }
            mTransitions.get(0).playTransition(sceneRoot);
        } else {
            // ORDERING_TOGETHER (the default): every child already captured its
            // own start/end values above, so starting them all now runs them
            // concurrently.
            for (Transition t : mTransitions) {
                t.playTransition(sceneRoot);
            }
        }
    }

    @Override
    public void cancel() {
        for (Transition t : mTransitions) t.cancel();
        super.cancel();
    }

    // AOSP TransitionSet.clone() (frameworks/base core/java/android/transition/
    // TransitionSet.java):
    //   @Override
    //   public TransitionSet clone() {
    //       TransitionSet clone = (TransitionSet) super.clone();
    //       clone.mTransitions = new ArrayList<Transition>();
    //       int numTransitions = mTransitions.size();
    //       for (int i = 0; i < numTransitions; ++i) {
    //           clone.addTransitionInternal((Transition) mTransitions.get(i).clone());
    //       }
    //       return clone;
    //   }
    // This override was missing entirely, so Object.clone() (via the inherited
    // Transition.clone()) only shallow-copied the mTransitions list REFERENCE.
    // That is a serious shared-state bug specifically because
    // TransitionManager.sDefaultTransition is a single static AutoTransition
    // (itself a TransitionSet of 3 children) that EVERY plain
    // beginDelayedTransition(root)/go(scene) call clones -- so every default
    // transition anywhere in the app, including two overlapping ones, would
    // share the exact same 3 child Transition instances. A second overlapping
    // default transition would then reset those shared children's
    // mStartValuesList/mEndValuesList/mCurrentAnimators out from under the
    // first transition's still-in-flight run, which can manifest as the first
    // transition's onTransitionEnd never firing (its animators' end listeners
    // keep decrementing a mNumInstances the second run already reset).
    @Override
    public TransitionSet clone() {
        TransitionSet clone = (TransitionSet) super.clone();
        clone.mTransitions = new ArrayList<Transition>();
        int numTransitions = mTransitions.size();
        for (int i = 0; i < numTransitions; i++) {
            clone.mTransitions.add(mTransitions.get(i).clone());
        }
        return clone;
    }
}
