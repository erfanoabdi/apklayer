package android.graphics.drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * AOSP-faithful android.graphics.drawable.AnimatedStateListDrawable (API 21).
 * A StateListDrawable whose state changes can animate via transition drawables.
 * We do not run the transition animations (no per-frame drawable invalidation
 * pump for arbitrary drawables), so we behave as a plain StateListDrawable:
 * states are added by id and selected on setState, transitions are recorded but
 * resolve instantly to their destination. Mirrors
 * frameworks/base/graphics/java/android/graphics/drawable/AnimatedStateListDrawable.java.
 */
public class AnimatedStateListDrawable extends StateListDrawable {
    private final Map<Integer, int[]> mStateById = new HashMap<>();

    public AnimatedStateListDrawable() {}

    /** AOSP keyed addState: also records the id so transitions can reference it. */
    public void addState(int[] stateSet, Drawable drawable, int id) {
        mStateById.put(id, stateSet);
        super.addState(stateSet, drawable);
    }

    /**
     * Record a transition drawable between two keyframe states. We store it so
     * the API round-trips, but since we have no animation pump the state list
     * simply snaps to the destination drawable on the next setState.
     */
    public <T extends Drawable & Animatable> void addTransition(int fromId, int toId,
            T transition, boolean reversible) {
        int[] fromSet = mStateById.get(fromId);
        int[] toSet = mStateById.get(toId);
        // Register the transition under the union of from/to so it is selectable
        // if the state list ever resolves to it; behavior stays correct because
        // setState matches the most specific concrete state first.
        if (toSet != null) {
            super.addState(toSet, transition);
        } else if (fromSet != null) {
            super.addState(fromSet, transition);
        }
    }
}
