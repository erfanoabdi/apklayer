// android.view.VelocityTracker -- computes pointer velocity from a movement
// history. This was a 13-line hard no-op (getXVelocity() always 0), which
// silently killed EVERY fling/swipe in the platform: RecyclerView's ViewFlinger,
// ViewPager paging, BottomSheet drag-to-dismiss, NestedScroll fling, and
// Compose's velocity-based gestures all ask a VelocityTracker for the release
// velocity and got 0 -> nothing ever flung.
//
// AOSP backs this with a native estimator (LeastSquaresVelocityTrackerStrategy /
// ImpulseVelocityTrackerStrategy in frameworks/native). We cannot call that, so
// we compute velocity in Java from the recorded (time,position) samples. The
// chosen strategy mirrors AOSP's default "impulse" estimator closely enough that
// the px/sec values land in the same range scrollers expect:
//   - keep a short ring of recent samples per pointer (last ~200 ms),
//   - estimate instantaneous velocity as the slope over that window using a
//     weighted average of adjacent-sample slopes (recent samples weighted more),
//   - report in the caller's requested units (computeCurrentVelocity(units)),
//   - clamp to the supplied max magnitude.
// AOSP ref: frameworks/base/core/java/android/view/VelocityTracker.java +
// frameworks/native/libs/input/VelocityTracker.cpp (impulse strategy).
package android.view;

import java.util.ArrayList;
import java.util.HashMap;

public final class VelocityTracker {
    // AOSP's LSQ2 strategy horizon is 100 ms, not 200 -- `frameworks/native/libs/input/
    // VelocityTracker.cpp` (android-11.0.0_r48) `LeastSquaresVelocityTrackerStrategy::HORIZON`.
    // A 200 ms window keeps samples from before a direction change and biases release velocity
    // toward stale motion.
    private static final long HORIZON_NS = 100_000_000L;
    /** AOSP `ASSUME_POINTER_STOPPED_TIME = 40ms` (same file, :47). If more than this elapses
     *  between movements the pointer is considered to have STOPPED and the history is reset, so a
     *  drag-pause-lift reports ~0 velocity instead of flinging on stale samples. Without it a user
     *  who drags, pauses and releases still gets a fling -- which is the single most-felt symptom
     *  of a wrong velocity tracker. */
    private static final long ASSUME_POINTER_STOPPED_NS = 40_000_000L;
    private static final int MAX_SAMPLES = 20;

    // One sample: a timestamp (ns) plus the position of each active pointer at
    // that instant, keyed by pointer id.
    private static final class Sample {
        long timeNs;
        final HashMap<Integer, float[]> pos = new HashMap<>(); // id -> {x,y}
    }

    private final ArrayList<Sample> mSamples = new ArrayList<>();
    // Computed velocities (px per `units` ms), keyed by pointer id, filled by
    // computeCurrentVelocity().
    private final HashMap<Integer, float[]> mVelocity = new HashMap<>(); // id -> {vx,vy}

    private VelocityTracker() {}

    // AOSP exposes obtain()/recycle() over a small pool. A fresh instance per
    // obtain() is observably identical (the pool is purely an allocation
    // optimization); recycle() drops our references.
    public static VelocityTracker obtain() { return new VelocityTracker(); }

    public void recycle() { clear(); }

    public void clear() {
        mSamples.clear();
        mVelocity.clear();
    }

    public void addMovement(MotionEvent ev) {
        if (ev == null) return;
        int action = ev.getActionMasked();
        // A new gesture starts fresh; AOSP clears history on ACTION_DOWN.
        if (action == MotionEvent.ACTION_DOWN) {
            clear();
        }
        Sample s = new Sample();
        s.timeNs = ev.getEventTime() * 1_000_000L; // eventTime is ms in our impl
        // POINTER-STOPPED RESET (AOSP `ASSUME_POINTER_STOPPED_TIME`, VelocityTracker.cpp:47).
        // If more than 40ms elapsed since the last movement the pointer is treated as having
        // STOPPED and the history is discarded, so the velocity computed at release reflects only
        // motion since the pause. Without this a user who drags, HOLDS STILL, then lifts still
        // gets a fling from samples taken before the pause -- the most-felt symptom of a wrong
        // tracker, and it happens on every list in every app.
        if (!mSamples.isEmpty()) {
            long gap = s.timeNs - mSamples.get(mSamples.size() - 1).timeNs;
            if (gap > ASSUME_POINTER_STOPPED_NS) mSamples.clear();
        }
        int n = ev.getPointerCount();
        for (int i = 0; i < n; i++) {
            int id = ev.getPointerId(i);
            s.pos.put(id, new float[]{ ev.getX(i), ev.getY(i) });
        }
        mSamples.add(s);
        // Drop samples older than the horizon relative to the newest, and bound
        // the ring so a long drag doesn't grow without limit.
        long cutoff = s.timeNs - HORIZON_NS;
        while (!mSamples.isEmpty() && mSamples.get(0).timeNs < cutoff) {
            mSamples.remove(0);
        }
        while (mSamples.size() > MAX_SAMPLES) {
            mSamples.remove(0);
        }
    }

    // AOSP: 1 unit = px/ms. Callers pass 1000 to get px/sec (the usual unit for
    // fling thresholds in ViewConfiguration). With no max, velocity is uncapped.
    public void computeCurrentVelocity(int units) {
        computeCurrentVelocity(units, Float.MAX_VALUE);
    }

    public void computeCurrentVelocity(int units, float maxVelocity) {
        mVelocity.clear();
        if (mSamples.size() < 2) return;
        float maxMag = Math.abs(maxVelocity);

        // Estimate per pointer id present in the most recent sample.
        Sample last = mSamples.get(mSamples.size() - 1);
        for (Integer id : last.pos.keySet()) {
            float vx = estimateAxis(id, 0);
            float vy = estimateAxis(id, 1);
            // estimateAxis returns px/ms; scale to the requested unit window.
            vx *= units;
            vy *= units;
            vx = clamp(vx, maxMag);
            vy = clamp(vy, maxMag);
            mVelocity.put(id, new float[]{ vx, vy });
        }
    }

    /** AOSP's DEFAULT strategy is LSQ2 -- a degree-2 least-squares fit, weighted, over the 100 ms
     *  horizon (`VelocityTracker.cpp:112 DEFAULT_STRATEGY = "lsq2"`), and the reported velocity is
     *  the FIRST DERIVATIVE OF THAT FIT AT THE NEWEST SAMPLE. We previously averaged adjacent
     *  slopes weighted by recency, which is a different estimator: it lags a real flick (the newest
     *  interval is only one term among many) and it cannot represent acceleration at all, so a
     *  finger speeding up into the lift reads slower than the device reports.
     *
     *  Solved with plain Gaussian elimination on the 3x3 normal equations, degrading to degree 1
     *  and then 0 when there are too few samples or the system is singular -- which is what AOSP's
     *  own solver does when the fit is underdetermined. Returns px/ms. */
    private float estimateAxis(int id, int axis) {
        ArrayList<long[]> times = new ArrayList<>();
        ArrayList<Float> positions = new ArrayList<>();
        for (Sample s : mSamples) {
            float[] p = s.pos.get(id);
            if (p == null) continue;
            times.add(new long[]{ s.timeNs });
            positions.add(p[axis]);
        }
        int m = positions.size();
        if (m < 2) return 0f;
        long t0 = times.get(m - 1)[0];          // newest sample is the origin, as AOSP does
        // Degree 2 needs 3 points; fall back the way AOSP's solver does.
        int degree = (m >= 3) ? 2 : 1;
        // Weighted normal equations for sum w*(a0 + a1*t + a2*t^2 - x)^2, t in ms (negative, past).
        int n = degree + 1;
        double[][] a = new double[n][n];
        double[] b = new double[n];
        for (int i = 0; i < m; i++) {
            double t = (times.get(i)[0] - t0) / 1_000_000.0;   // ms, <= 0
            double x = positions.get(i);
            // AOSP weights by recency and by sample spacing; a simple recency weight reproduces
            // its shape without the API-level-specific tuning constants.
            double w = 1.0 + (double) i / m;
            double[] pow = new double[n];
            pow[0] = 1.0;
            for (int k = 1; k < n; k++) pow[k] = pow[k - 1] * t;
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) a[r][c] += w * pow[r] * pow[c];
                b[r] += w * pow[r] * x;
            }
        }
        double[] coef = solve(a, b, n);
        if (coef == null) {
            if (degree == 2) {   // singular: retry linear before giving up
                return estimateAxisLinear(times, positions, m, t0);
            }
            return 0f;
        }
        // velocity = d/dt at t = 0 (the newest sample) = a1
        double v = coef[1];
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0f;
        return (float) v;
    }

    private float estimateAxisLinear(ArrayList<long[]> times, ArrayList<Float> positions,
                                     int m, long t0) {
        double sw = 0, st = 0, sx = 0, stt = 0, stx = 0;
        for (int i = 0; i < m; i++) {
            double t = (times.get(i)[0] - t0) / 1_000_000.0;
            double x = positions.get(i), w = 1.0 + (double) i / m;
            sw += w; st += w * t; sx += w * x; stt += w * t * t; stx += w * t * x;
        }
        double den = sw * stt - st * st;
        if (Math.abs(den) < 1e-9) return 0f;
        double v = (sw * stx - st * sx) / den;
        return (Double.isNaN(v) || Double.isInfinite(v)) ? 0f : (float) v;
    }

    /** Gaussian elimination with partial pivoting; null when singular. */
    private static double[] solve(double[][] a, double[] b, int n) {
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) piv = r;
            if (Math.abs(a[piv][col]) < 1e-12) return null;
            double[] tmp = a[col]; a[col] = a[piv]; a[piv] = tmp;
            double tb = b[col]; b[col] = b[piv]; b[piv] = tb;
            for (int r = col + 1; r < n; r++) {
                double f = a[r][col] / a[col][col];
                if (f == 0) continue;
                for (int c = col; c < n; c++) a[r][c] -= f * a[col][c];
                b[r] -= f * b[col];
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double sum = b[r];
            for (int c = r + 1; c < n; c++) sum -= a[r][c] * x[c];
            x[r] = sum / a[r][r];
        }
        return x;
    }

    private static float clamp(float v, float maxMag) {
        if (v > maxMag) return maxMag;
        if (v < -maxMag) return -maxMag;
        return v;
    }

    // AOSP: getXVelocity()/getYVelocity() return the active (first) pointer's
    // velocity. The id overloads return a specific pointer's; 0 if unknown.
    public float getXVelocity() {
        float[] v = anyVelocity();
        return v == null ? 0f : v[0];
    }
    public float getYVelocity() {
        float[] v = anyVelocity();
        return v == null ? 0f : v[1];
    }
    public float getXVelocity(int id) {
        float[] v = mVelocity.get(id);
        return v == null ? 0f : v[0];
    }
    public float getYVelocity(int id) {
        float[] v = mVelocity.get(id);
        return v == null ? 0f : v[1];
    }

    // API 34: getAxisVelocity(axis[, id]) generalizes getXVelocity/
    // getYVelocity to an arbitrary MotionEvent axis constant, and
    // isAxisSupported reports which axes a tracker can answer. AOSP's
    // native estimator can track other axes (e.g. AXIS_SCROLL for
    // rotary/wheel velocity) when the input events carry them; our
    // addMovement() above only ever records AXIS_X/AXIS_Y samples, so we
    // route those two through the existing estimator and honestly report
    // every other axis as unsupported/0 instead of fabricating a velocity
    // we never measured. id == ACTIVE_POINTER_ID means "the active
    // pointer", matching the no-arg getXVelocity()/getYVelocity() (which
    // resolve the active pointer via anyVelocity()) rather than a literal
    // pointer id -1 lookup.
    // AOSP ref: frameworks/base/core/java/android/view/VelocityTracker.java
    public static final int ACTIVE_POINTER_ID = -1;

    public float getAxisVelocity(int axis, int id) {
        if (axis == MotionEvent.AXIS_X) {
            return id == ACTIVE_POINTER_ID ? getXVelocity() : getXVelocity(id);
        }
        if (axis == MotionEvent.AXIS_Y) {
            return id == ACTIVE_POINTER_ID ? getYVelocity() : getYVelocity(id);
        }
        return 0f;
    }

    public float getAxisVelocity(int axis) {
        return getAxisVelocity(axis, ACTIVE_POINTER_ID);
    }

    public boolean isAxisSupported(int axis) {
        return axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y;
    }

    private float[] anyVelocity() {
        if (mVelocity.isEmpty()) return null;
        // Prefer the pointer in the most recent sample (the active finger).
        if (!mSamples.isEmpty()) {
            Sample last = mSamples.get(mSamples.size() - 1);
            for (Integer id : last.pos.keySet()) {
                float[] v = mVelocity.get(id);
                if (v != null) return v;
            }
        }
        return mVelocity.values().iterator().next();
    }
}
