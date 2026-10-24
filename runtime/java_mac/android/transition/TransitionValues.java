// android.transition.TransitionValues -- the per-view property bag a Transition
// captures at scene start and scene end. A Transition compares the start vs end
// bag for each view and builds an Animator from the difference. Was absent; the
// concrete transitions (Fade/ChangeBounds/Slide) and Transition.captureValues
// reference it in their signatures, so it must exist on the bootclasspath.
// Mirrors frameworks/base/core/java/android/transition/TransitionValues.java.
package android.transition;

import android.view.View;
import java.util.HashMap;
import java.util.Map;

public class TransitionValues {
    // AOSP exposes `view` as a public field and `values` as a public final Map.
    public View view;
    public final Map<String, Object> values = new HashMap<String, Object>();

    public TransitionValues() {}

    public TransitionValues(View view) {
        this.view = view;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof TransitionValues) {
            if (view == ((TransitionValues) other).view) {
                if (values.equals(((TransitionValues) other).values)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = view == null ? 0 : view.hashCode();
        result = 31 * result + values.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TransitionValues@")
                .append(Integer.toHexString(hashCode())).append(":\n");
        sb.append("    view = ").append(view).append("\n");
        sb.append("    values:");
        for (String s : values.keySet()) {
            sb.append("    ").append(s).append(": ").append(values.get(s)).append("\n");
        }
        return sb.toString();
    }
}
