// android.transition.TransitionManager -- the entry point apps use to run scene
// transitions: beginDelayedTransition(root [, transition]) captures the current
// scene, lets the caller mutate the view tree, and animates to the new layout on
// the next layout pass; go(scene [, transition]) swaps to a Scene. Was a bare
// stub, so every beginDelayedTransition() call silently did nothing (no animation,
// abrupt layout). Mirrors the observable contract of
// frameworks/base/core/java/android/transition/TransitionManager.java.
//
// AOSP defers the end-capture to an OnPreDraw of the scene root. We do the same:
// capture start values immediately, register a one-shot pre-draw listener via the
// ViewTreeObserver, and on that callback capture end values and play the
// transition. This is what makes "set the new state, then it animates" work.
package android.transition;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.WeakHashMap;

public class TransitionManager {

    private static final Transition sDefaultTransition = new AutoTransition();

    // Pending transitions keyed by scene root, so a second beginDelayedTransition
    // on the same root before the pre-draw fires replaces the first (AOSP behavior).
    private static final WeakHashMap<ViewGroup, Transition> sPendingTransitions =
            new WeakHashMap<ViewGroup, Transition>();

    public TransitionManager() {}

    public static void beginDelayedTransition(final ViewGroup sceneRoot) {
        beginDelayedTransition(sceneRoot, null);
    }

    public static void beginDelayedTransition(final ViewGroup sceneRoot, Transition transition) {
        if (sceneRoot == null) return;
        final Transition t = (transition != null) ? transition : sDefaultTransition.clone();

        // Capture the BEFORE state now.
        t.captureValues(sceneRoot, true);
        sPendingTransitions.put(sceneRoot, t);

        // Defer the AFTER capture + play to the next pre-draw, by which point the
        // caller has mutated the tree and a layout pass has positioned everything.
        final ViewTreeObserver observer = sceneRoot.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                ViewTreeObserver obs = sceneRoot.getViewTreeObserver();
                if (obs.isAlive()) obs.removeOnPreDrawListener(this);
                Transition pending = sPendingTransitions.remove(sceneRoot);
                if (pending != null) {
                    pending.captureValues(sceneRoot, false);
                    pending.playTransition(sceneRoot);
                }
                return true;
            }
        });
    }

    public static void go(Scene scene) {
        go(scene, sDefaultTransition.clone());
    }

    public static void go(Scene scene, Transition transition) {
        if (scene == null) return;
        ViewGroup root = scene.getSceneRoot();
        Transition t = (transition != null) ? transition : sDefaultTransition.clone();
        if (root != null) {
            t.captureValues(root, true);
        }
        scene.enter();
        if (root != null) {
            // Capture the post-enter layout on the next pre-draw, then play.
            final ViewGroup sceneRoot = root;
            final Transition pending = t;
            ViewTreeObserver observer = sceneRoot.getViewTreeObserver();
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    ViewTreeObserver obs = sceneRoot.getViewTreeObserver();
                    if (obs.isAlive()) obs.removeOnPreDrawListener(this);
                    pending.captureValues(sceneRoot, false);
                    pending.playTransition(sceneRoot);
                    return true;
                }
            });
        }
    }

    private final ArrayList<Scene> mScenes = new ArrayList<Scene>();

    // Instance API (setTransition + transitionTo) is rarely used vs the static
    // helpers, but apps that build a TransitionManager in XML reference it.
    public void setTransition(Scene scene, Transition transition) {}
    public void setTransition(Scene fromScene, Scene toScene, Transition transition) {}

    public void transitionTo(Scene scene) {
        go(scene);
    }

    public static void endTransitions(ViewGroup sceneRoot) {
        Transition pending = sPendingTransitions.remove(sceneRoot);
        if (pending != null) {
            try { pending.cancel(); } catch (Throwable ignored) {}
        }
    }
}
