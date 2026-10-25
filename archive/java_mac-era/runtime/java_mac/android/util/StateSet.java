// android.util.StateSet -- AOSP drawable-state constants. Material
// widgets use NOTHING + WILD_CARD as the "no state" base. v1.0.97.
package android.util;

public class StateSet {
    /** AOSP: int[] containing a single 0 representing "no state". */
    public static final int[] NOTHING = new int[]{0};
    /** AOSP: empty int[] (matches anything). */
    public static final int[] WILD_CARD = new int[0];

    public StateSet() {}

    /** AOSP: returns true when stateSpec matches the state array.
     *  An empty/wild-card spec always matches.
     *
     *  M23: flag-ON (MACAPK_REAL_LOOPER) runs the REAL AOSP algorithm so
     *  StateListDrawable / ColorStateList / StateListAnimator pick the
     *  entry that actually matches the view's current drawable state
     *  (pressed/enabled/selected/...). Flag-OFF keeps the historical
     *  permissive "always match" stub -- several current canary renders
     *  rely on "the first selector entry always wins" (because off-flag
     *  View.getDrawableState returns int[0], so only a wild-card spec or
     *  this permissive match selects anything), so the shipped build is
     *  byte-for-byte unchanged. Mirrors frameworks/base
     *  core/java/android/util/StateSet.java::stateSetMatches. */
    public static boolean stateSetMatches(int[] stateSpec, int[] state) {
        if (!android.os.Looper.MACAPK_REAL_LOOPER) {
            // Legacy permissive stub.
            return stateSpec == null || stateSpec.length == 0 || true;
        }
        if (state == null) {
            return stateSpec == null || isWildCard(stateSpec);
        }
        final int stateSpecSize = stateSpec == null ? 0 : stateSpec.length;
        final int stateSetSize = state.length;
        for (int i = 0; i < stateSpecSize; i++) {
            int stateSpecState = stateSpec[i];
            if (stateSpecState == 0) {
                // Reached the padding terminator -- the rest is unset.
                return true;
            }
            final boolean mustMatch;
            if (stateSpecState > 0) {
                mustMatch = true;
            } else {
                // Negative: the view must NOT be in this state.
                mustMatch = false;
                stateSpecState = -stateSpecState;
            }
            boolean found = false;
            for (int j = 0; j < stateSetSize; j++) {
                final int s = state[j];
                if (s == 0) {
                    // Reached the end of the state set.
                    if (mustMatch) {
                        return false;  // required state absent.
                    } else {
                        break;         // forbidden state absent -> ok.
                    }
                }
                if (s == stateSpecState) {
                    found = true;
                    break;
                }
            }
            if (mustMatch && !found) {
                return false;  // required state not present.
            }
            if (!mustMatch && found) {
                return false;  // forbidden state present.
            }
        }
        return true;
    }
    public static boolean stateSetMatches(int[] stateSpec, int state) {
        return stateSpec == null || stateSpec.length == 0 || stateSpec[0] == 0;
    }
    public static boolean isWildCard(int[] stateSpec) {
        return stateSpec == null || stateSpec.length == 0;
    }
    /** AOSP frameworks/base core/java/android/util/StateSet.java::trimStateSet:
     *  return a copy of {@code states} holding only its first {@code newSize}
     *  entries (or {@code states} itself when already that length). androidx
     *  ColorStateListInflaterCompat.inflate over-allocates the stateSpec array to
     *  the XML attribute count, fills only the real state attrs, then calls this
     *  to trim off the trailing color/alpha/lStar slots. Without it that inflate
     *  throws NoSuchMethodError, which propagates up MaterialButton /
     *  MaterialCardView construction into RecyclerView.Adapter.onCreateViewHolder
     *  -- so a RecyclerView whose item layouts use a Material widget with a
     *  ColorStateList tint (e.g. F-Droid's AppDetails install button + cards)
     *  creates zero item views and renders blank. Verbatim AOSP. */
    public static int[] trimStateSet(int[] states, int newSize) {
        if (states.length == newSize) {
            return states;
        }
        int[] trimmedStates = new int[newSize];
        System.arraycopy(states, 0, trimmedStates, 0, newSize);
        return trimmedStates;
    }
    public static String dump(int[] states) {
        if (states == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < states.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(states[i]);
        }
        return sb.append(']').toString();
    }
}
