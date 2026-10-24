// Mac.apk -- android.graphics.DashPathEffect.
//
// AOSP public surface: DashPathEffect(float[] intervals, float phase)
// draws a stroked path as a dashed line. The intervals array holds the
// on/off lengths (must be even-count, >= 2); phase shifts where the
// dash pattern starts. See
// frameworks/base/graphics/java/android/graphics/DashPathEffect.java.
//
// Our Canvas is AWT-backed (software). The accessors below let the
// stroke path map this effect onto java.awt.BasicStroke, whose dash
// array uses the same on/off-length convention and whose dash_phase
// matches AOSP's phase. We expose macapk* getters rather than reading
// fields directly so the mapping site stays decoupled from storage.
package android.graphics;

public class DashPathEffect extends PathEffect {
    // Defensive copies: AOSP copies the array into its native peer, so
    // a caller mutating its array after construction must not affect us.
    private final float[] mIntervals;
    private final float mPhase;

    public DashPathEffect(float[] intervals, float phase) {
        // AOSP requires an even count of at least 2; mirror that so
        // callers see the same IllegalArgumentException contract.
        if (intervals == null || intervals.length < 2) {
            throw new IllegalArgumentException("intervals array must have >= 2 entries");
        }
        if ((intervals.length & 1) != 0) {
            throw new IllegalArgumentException("intervals array must have even number of entries");
        }
        mIntervals = intervals.clone();
        mPhase = phase;
    }

    // Accessors for our AWT BasicStroke mapping. Return a copy of the
    // dash array so the Canvas stroke site can hand it straight to
    // BasicStroke without risking external mutation of our state.
    public float[] macapkIntervals() {
        return mIntervals.clone();
    }

    public float macapkPhase() {
        return mPhase;
    }
}
