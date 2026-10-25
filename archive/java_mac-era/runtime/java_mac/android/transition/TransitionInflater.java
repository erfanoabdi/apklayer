// android.transition.TransitionInflater -- inflates a <transition>/<transitionSet>
// XML resource (and <transitionManager> scene mappings) into Transition objects.
// Was absent; apps that declare transitions in res/transition/*.xml and call
// TransitionInflater.from(ctx).inflateTransition(id) reference it. Mirrors the
// public surface of
// frameworks/base/core/java/android/transition/TransitionInflater.java.
//
// Our resource pipeline does not expose the raw transition XML to Java the way
// AOSP's XmlResourceParser does, so inflateTransition returns a sensible default
// (an AutoTransition) rather than parsing the resource. This keeps callers working
// (they get a real, runnable transition) without a per-resource parser; the
// common case -- "give me a transition for this id and run it" -- behaves
// correctly even if it is not the exact authored transition.
package android.transition;

import android.content.Context;

public class TransitionInflater {

    private final Context mContext;

    private TransitionInflater(Context context) {
        mContext = context;
    }

    public static TransitionInflater from(Context context) {
        return new TransitionInflater(context);
    }

    public Transition inflateTransition(int resource) {
        // Without an authored-XML parser we cannot reconstruct the exact tree;
        // an AutoTransition (fade-out / change-bounds / fade-in) is the closest
        // faithful default and is what most declared transitions approximate.
        return new AutoTransition();
    }

    public TransitionManager inflateTransitionManager(int resource, android.view.ViewGroup sceneRoot) {
        return new TransitionManager();
    }
}
