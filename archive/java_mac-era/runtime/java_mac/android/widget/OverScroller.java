// android.widget.OverScroller -- variant of Scroller that supports
// overscrolling (the springy bounce-back when the user flings past
// the content edge). RecyclerView, NestedScrollView, ViewPager, and
// most modern scrolling containers depend on this.
//
// Real SplineOverScroller-equivalent fling physics (was: computeScrollOffset
// unconditionally `return false`, fling() didn't even compute a resting
// DISTANCE from the launch velocity). OverScroller's own math is pure
// elapsed-wall-clock-time (AnimationUtils.currentAnimationTimeMillis()) --
// it does not itself depend on Choreographer, matching AOSP. The caller's
// standard idiom (RecyclerView's ViewFlinger: `if (scroller.
// computeScrollOffset()) { scrollTo(currX, currY); postOnAnimation(this); }`)
// drives the per-frame polling via View.postOnAnimation, so fling() now
// produces real, decelerating motion instead of a silent freeze.
//
// Scope: the SPLINE deceleration curve -- the "does a fling coast smoothly
// to a stop" behavior every scrolling container needs on every fling -- is
// ported faithfully from AOSP, formulas and physical constants verbatim
// (SplineOverScroller: SPLINE_POSITION/SPLINE_TIME tables, DECELERATION_RATE,
// getSplineFlingDistance/Duration, mPhysicalCoeff). The startScroll()
// SCROLL_MODE path (a fixed-duration animated move, e.g. smoothScrollTo) is
// ported too, using the same viscous-fluid default interpolator AOSP uses.
//
// NOT ported: AOSP's BALLISTIC/CUBIC states -- the springy bounce-back
// animation that plays when a fling's target lands outside [min,max] (or
// notifyHorizontalEdgeReached/notifyVerticalEdgeReached is called directly).
// A fling that would overshoot is instead clamped to stop exactly at the
// boundary; springBack() snaps instantly into range instead of animating
// the bounce. This mirrors the audit's own scoping: EdgeEffect (the
// companion overscroll "glow" visual) is a separately-tracked, lower-
// priority cosmetic gap, and a bounce-back animation has no visible
// consumer without it.
//
// AOSP source mirror: frameworks/base/core/java/android/widget/
// OverScroller.java (SplineOverScroller nested class) and Scroller.java
// (ViscousFluidInterpolator). Every formula/constant below is copied
// verbatim from that source; see the comment above each one.
package android.widget;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.ViewConfiguration;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

public class OverScroller {
    private static final int DEFAULT_DURATION = 250;
    private static final int SCROLL_MODE = 0;
    private static final int FLING_MODE = 1;

    private int mode = SCROLL_MODE;
    private final SplineScroller scrollerX;
    private final SplineScroller scrollerY;
    private final Interpolator interpolator;
    private final boolean flywheel;

    public OverScroller(Context context) {
        this(context, null);
    }
    public OverScroller(Context context, Interpolator interpolator) {
        this(context, interpolator, true);
    }
    /** AOSP-deprecated overloads: bounce coefficients "no longer supported,
     *  this coefficient has no effect" even on real Android. Kept only for
     *  source compatibility with callers that pass them. */
    public OverScroller(Context context, Interpolator interpolator,
                         float bounceCoefficientX, float bounceCoefficientY) {
        this(context, interpolator, true);
    }
    public OverScroller(Context context, Interpolator interpolator,
                         float bounceCoefficientX, float bounceCoefficientY,
                         boolean flywheel) {
        this(context, interpolator, flywheel);
    }
    public OverScroller(Context context, Interpolator interpolator, boolean flywheel) {
        this.interpolator = (interpolator != null) ? interpolator : new ViscousFluidInterpolator();
        this.flywheel = flywheel;
        final float physicalCoeff = computePhysicalCoeff(context);
        this.scrollerX = new SplineScroller(physicalCoeff);
        this.scrollerY = new SplineScroller(physicalCoeff);
    }

    /** AOSP SplineOverScroller ctor: mPhysicalCoeff = GRAVITY_EARTH *
     *  inch/meter * ppi * 0.84 (look-and-feel tuning), ppi = density * 160.
     *  Falls back to xhdpi (density 2.0, matching DisplayMetrics' own
     *  default) if the context can't resolve a display yet -- callers here
     *  historically passed context straight through to a constructor that
     *  ignored it entirely, so a defensive fallback avoids turning a
     *  previously-harmless null/half-attached Context into a new crash. */
    private static float computePhysicalCoeff(Context context) {
        float density = android.util.DisplayMetrics.macapkDeviceDensity();
        try {
            if (context != null && context.getResources() != null) {
                density = context.getResources().getDisplayMetrics().density;
            }
        } catch (Throwable ignored) {}
        final float ppi = density * 160.0f;
        return SensorManager.GRAVITY_EARTH  // g (m/s^2)
                * 39.37f                     // inch/meter
                * ppi
                * 0.84f;                      // look and feel tuning
    }

    /** Whether the scroller has finished its animation. */
    public final boolean isFinished() { return scrollerX.finished && scrollerY.finished; }
    /** Force the finished field to a particular value. Does NOT move to the
     *  final position (contrast abortAnimation()). */
    public final void forceFinished(boolean finished) { scrollerX.finished = scrollerY.finished = finished; }

    public final int getCurrX() { return scrollerX.currentPosition; }
    public final int getCurrY() { return scrollerY.currentPosition; }
    public final int getStartX() { return scrollerX.start; }
    public final int getStartY() { return scrollerY.start; }
    public final int getFinalX() { return scrollerX.finalPos; }
    public final int getFinalY() { return scrollerY.finalPos; }
    /** AOSP: norm of the X/Y current-velocity vector. */
    public final float getCurrVelocity() { return (float) Math.hypot(scrollerX.currVelocity, scrollerY.currVelocity); }

    /** The amount of friction applied to flings. Default is
     *  ViewConfiguration#getScrollFriction(). */
    public void setFriction(float friction) {
        scrollerX.flingFriction = friction;
        scrollerY.flingFriction = friction;
    }

    /** Snap immediately to the destination (no animation). */
    public void startScroll(int startX, int startY, int dx, int dy) {
        startScroll(startX, startY, dx, dy, DEFAULT_DURATION);
    }
    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        mode = SCROLL_MODE;
        scrollerX.startScroll(startX, dx, duration);
        scrollerY.startScroll(startY, dy, duration);
    }

    /** Fling -- the velocity-based scroll. Computes a friction-based
     *  resting distance/duration from the launch velocity (AOSP
     *  SplineOverScroller.fling) instead of settling on the first poll. */
    public void fling(int startX, int startY, int velocityX, int velocityY,
                       int minX, int maxX, int minY, int maxY) {
        fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY, 0, 0);
    }
    public void fling(int startX, int startY, int velocityX, int velocityY,
                       int minX, int maxX, int minY, int maxY,
                       int overX, int overY) {
        // AOSP flywheel: a fling launched while a previous one is still
        // coasting in the SAME direction adds onto its current velocity
        // instead of restarting from rest.
        if (flywheel && !isFinished()) {
            float oldVelocityX = scrollerX.currVelocity;
            float oldVelocityY = scrollerY.currVelocity;
            if (Math.signum(velocityX) == Math.signum(oldVelocityX)
                    && Math.signum(velocityY) == Math.signum(oldVelocityY)) {
                // Compound assignment on an int += float implicitly adds as
                // float then narrows the SUM back to int (matches AOSP's
                // identical `velocityX += oldVelocityX;`), not a
                // pre-truncate-then-add.
                velocityX += oldVelocityX;
                velocityY += oldVelocityY;
            }
        }
        mode = FLING_MODE;
        // overX/overY (the allowed overshoot band) only feed AOSP's
        // BALLISTIC bounce, which this port doesn't implement -- see file
        // header. fling() always clamps to [min,max] instead.
        scrollerX.fling(startX, velocityX, minX, maxX);
        scrollerY.fling(startY, velocityY, minY, maxY);
    }

    /** Bounce-back when overscrolled. This port has no CUBIC bounce state
     *  (see file header), so it snaps straight to the boundary instead of
     *  animating the spring. Returns false if no scroll is needed. */
    public boolean springBack(int startX, int startY,
                               int minX, int maxX, int minY, int maxY) {
        mode = FLING_MODE;
        final boolean changedX = scrollerX.springBackClamp(startX, minX, maxX);
        final boolean changedY = scrollerY.springBackClamp(startY, minY, maxY);
        return changedX || changedY;
    }

    /** Each frame the caller polls this to advance the animation. Mirrors
     *  AOSP's SCROLL_MODE (interpolated move) / FLING_MODE (spline
     *  deceleration) dispatch. */
    public boolean computeScrollOffset() {
        if (isFinished()) return false;

        switch (mode) {
            case SCROLL_MODE: {
                final long time = AnimationUtils.currentAnimationTimeMillis();
                // Either scroller's clock works: both started together.
                final long elapsedTime = time - scrollerX.startTime;
                final int duration = scrollerX.duration;
                if (elapsedTime < duration) {
                    final float q = interpolator.getInterpolation(elapsedTime / (float) duration);
                    final float q2 = interpolator.getInterpolation((elapsedTime - 1) / (float) duration);
                    scrollerX.updateScroll(q, q2);
                    scrollerY.updateScroll(q, q2);
                } else {
                    abortAnimation();
                }
                break;
            }
            case FLING_MODE:
                if (!scrollerX.finished && !scrollerX.update()) {
                    scrollerX.onSplineDurationElapsed();
                }
                if (!scrollerY.finished && !scrollerY.update()) {
                    scrollerY.onSplineDurationElapsed();
                }
                break;
        }
        return true;
    }

    /** Notify the scroller that we've reached a boundary outside of a
     *  fling()'s own min/max clamp. AOSP transitions into a BALLISTIC
     *  bounce here, which this port doesn't implement (see file header);
     *  there is no lighter-weight faithful fallback than leaving the
     *  current animation as-is. */
    public void notifyHorizontalEdgeReached(int startX, int finalX, int overX) {}
    public void notifyVerticalEdgeReached(int startY, int finalY, int overY) {}

    /** AOSP: true while a post-edge bounce-back is animating. This port
     *  never enters that state (see file header), so the honest answer is
     *  always false. */
    public boolean isOverScrolled() { return false; }

    /** AOSP: extends a currently-running fling's duration and re-targets
     *  its end (paired with the @hide setFinalX/setFinalY, which is why
     *  they aren't ported below -- no public/reflectable caller can reach
     *  them without also having a way to call those). Real AOSP signature
     *  is void; the previous stub here incorrectly declared a boolean
     *  return, which would NoSuchMethodError against a direct invokevirtual
     *  compiled against the real platform method. */
    public void extendDuration(int extend) {
        scrollerX.extendDuration(extend);
        scrollerY.extendDuration(extend);
    }

    public void abortAnimation() {
        scrollerX.finish();
        scrollerY.finish();
    }

    // ------------------------------------------------------------- Axis math

    /** One axis (X or Y) of the AOSP SplineOverScroller. Field names drop
     *  the AOSP "m" prefix to match this file's existing style; formulas
     *  are copied verbatim from frameworks/base's OverScroller.java. */
    private static final class SplineScroller {
        int start;
        int currentPosition;
        int finalPos;
        float currVelocity;
        long startTime;
        int duration;
        // Duration to complete the spline component of the animation (the
        // UNCLAMPED duration a fling's velocity implies); duration itself
        // may be shorter than this when fling() clamped the target into
        // [min,max] (see adjustDuration).
        int splineDuration;
        int splineDistance;
        boolean finished = true;
        float flingFriction = ViewConfiguration.getScrollFriction();
        final float physicalCoeff;

        // AOSP SplineOverScroller: DECELERATION_RATE = ln(0.78)/ln(0.9); the
        // spline's control points/table are built once, shared by every
        // instance (the shape doesn't depend on friction/physicalCoeff --
        // those only scale TIME and DISTANCE, applied in
        // getSplineFlingDistance/Duration and update() below).
        private static final float DECELERATION_RATE = (float) (Math.log(0.78) / Math.log(0.9));
        private static final float INFLEXION = 0.35f; // Tension lines cross at (INFLEXION, 1)
        private static final float START_TENSION = 0.5f;
        private static final float END_TENSION = 1.0f;
        private static final float P1 = START_TENSION * INFLEXION;
        private static final float P2 = 1.0f - END_TENSION * (1.0f - INFLEXION);
        private static final int NB_SAMPLES = 100;
        private static final float[] SPLINE_POSITION = new float[NB_SAMPLES + 1];
        private static final float[] SPLINE_TIME = new float[NB_SAMPLES + 1];
        static {
            float x_min = 0.0f;
            float y_min = 0.0f;
            for (int i = 0; i < NB_SAMPLES; i++) {
                final float alpha = (float) i / NB_SAMPLES;

                float x_max = 1.0f;
                float x, tx, coef;
                while (true) {
                    x = x_min + (x_max - x_min) / 2.0f;
                    coef = 3.0f * x * (1.0f - x);
                    tx = coef * ((1.0f - x) * P1 + x * P2) + x * x * x;
                    if (Math.abs(tx - alpha) < 1E-5) break;
                    if (tx > alpha) x_max = x; else x_min = x;
                }
                SPLINE_POSITION[i] = coef * ((1.0f - x) * START_TENSION + x) + x * x * x;

                float y_max = 1.0f;
                float y, dy;
                while (true) {
                    y = y_min + (y_max - y_min) / 2.0f;
                    coef = 3.0f * y * (1.0f - y);
                    dy = coef * ((1.0f - y) * START_TENSION + y) + y * y * y;
                    if (Math.abs(dy - alpha) < 1E-5) break;
                    if (dy > alpha) y_max = y; else y_min = y;
                }
                SPLINE_TIME[i] = coef * ((1.0f - y) * P1 + y * P2) + y * y * y;
            }
            SPLINE_POSITION[NB_SAMPLES] = SPLINE_TIME[NB_SAMPLES] = 1.0f;
        }

        SplineScroller(float physicalCoeff) { this.physicalCoeff = physicalCoeff; }

        void startScroll(int startPos, int distance, int dur) {
            finished = false;
            currentPosition = start = startPos;
            finalPos = startPos + distance;
            startTime = AnimationUtils.currentAnimationTimeMillis();
            duration = dur;
        }

        void updateScroll(float q, float q2) {
            final int distance = finalPos - start;
            currentPosition = start + Math.round(q * distance);
            // q2 is 1ms before q.
            currVelocity = 1000f * (q - q2) * distance;
        }

        void finish() {
            currentPosition = finalPos;
            finished = true;
        }

        void extendDuration(int extend) {
            final long time = AnimationUtils.currentAnimationTimeMillis();
            final int elapsedTime = (int) (time - startTime);
            duration = splineDuration = elapsedTime + extend;
            finished = false;
        }

        /** AOSP springback(), CUBIC-bounce replaced by an instant clamp --
         *  see file header. Returns true if startPos was out of range. */
        boolean springBackClamp(int startPos, int min, int max) {
            finished = true;
            currentPosition = start = finalPos = startPos;
            startTime = AnimationUtils.currentAnimationTimeMillis();
            duration = 0;
            if (startPos < min) { currentPosition = finalPos = min; return true; }
            if (startPos > max) { currentPosition = finalPos = max; return true; }
            return false;
        }

        void fling(int startPos, int velocity, int min, int max) {
            finished = false;
            currVelocity = velocity;
            duration = splineDuration = 0;
            startTime = AnimationUtils.currentAnimationTimeMillis();
            currentPosition = start = startPos;

            if (startPos > max || startPos < min) {
                // AOSP startAfterEdge()->fitOnBounceCurve(): a BALLISTIC
                // bounce off whichever edge we're already past. Not
                // implemented (see file header) -- land at that edge
                // immediately instead of bouncing off it.
                currentPosition = start = finalPos = (startPos < min) ? min : max;
                finished = true;
                return;
            }

            double totalDistance = 0.0;
            if (velocity != 0) {
                duration = splineDuration = getSplineFlingDuration(velocity);
                totalDistance = getSplineFlingDistance(velocity);
            }

            splineDistance = (int) (totalDistance * Math.signum(velocity));
            finalPos = startPos + splineDistance;

            // Clamp to a valid final position (adjustDuration shortens
            // `duration` to match, along the SAME spline, so the clamped
            // fling still decelerates smoothly instead of jump-cutting).
            if (finalPos < min) {
                adjustDuration(start, finalPos, min);
                finalPos = min;
            }
            if (finalPos > max) {
                adjustDuration(start, finalPos, max);
                finalPos = max;
            }
        }

        private double getSplineDeceleration(int velocity) {
            return Math.log(INFLEXION * Math.abs(velocity) / (flingFriction * physicalCoeff));
        }

        private double getSplineFlingDistance(int velocity) {
            final double l = getSplineDeceleration(velocity);
            final double decelMinusOne = DECELERATION_RATE - 1.0;
            return flingFriction * physicalCoeff * Math.exp(DECELERATION_RATE / decelMinusOne * l);
        }

        /** Returns the duration, expressed in milliseconds. */
        private int getSplineFlingDuration(int velocity) {
            final double l = getSplineDeceleration(velocity);
            final double decelMinusOne = DECELERATION_RATE - 1.0;
            return (int) (1000.0 * Math.exp(l / decelMinusOne));
        }

        /** Modifies `duration` to the time it takes to reach `newFinal`
         *  (some point strictly between `startPos` and the original,
         *  unclamped `oldFinal`) along the spline already computed for the
         *  full fling. */
        private void adjustDuration(int startPos, int oldFinal, int newFinal) {
            final int oldDistance = oldFinal - startPos;
            final int newDistance = newFinal - startPos;
            final float x = Math.abs((float) newDistance / oldDistance);
            final int index = (int) (NB_SAMPLES * x);
            if (index < NB_SAMPLES) {
                final float x_inf = (float) index / NB_SAMPLES;
                final float x_sup = (float) (index + 1) / NB_SAMPLES;
                final float t_inf = SPLINE_TIME[index];
                final float t_sup = SPLINE_TIME[index + 1];
                final float timeCoef = t_inf + (x - x_inf) / (x_sup - x_inf) * (t_sup - t_inf);
                duration *= timeCoef;
            }
        }

        /** Advance currentPosition/currVelocity to the current wall-clock
         *  time along the spline. Returns false once `duration` has
         *  elapsed (mirrors AOSP SplineOverScroller.update(), SPLINE case
         *  only -- see file header for the omitted CUBIC/BALLISTIC cases). */
        boolean update() {
            final long time = AnimationUtils.currentAnimationTimeMillis();
            final long currentTime = time - startTime;

            if (currentTime == 0) {
                // Skip work but report "still going" if duration is nonzero.
                return duration > 0;
            }
            if (currentTime > duration) return false;

            final float t = (float) currentTime / splineDuration;
            final int index = (int) (NB_SAMPLES * t);
            float distanceCoef = 1.f;
            float velocityCoef = 0.f;
            if (index < NB_SAMPLES) {
                final float t_inf = (float) index / NB_SAMPLES;
                final float t_sup = (float) (index + 1) / NB_SAMPLES;
                final float d_inf = SPLINE_POSITION[index];
                final float d_sup = SPLINE_POSITION[index + 1];
                velocityCoef = (d_sup - d_inf) / (t_sup - t_inf);
                distanceCoef = d_inf + (t - t_inf) * velocityCoef;
            }

            final double distance = distanceCoef * splineDistance;
            currVelocity = velocityCoef * splineDistance / splineDuration * 1000.0f;
            currentPosition = start + (int) Math.round(distance);
            return true;
        }

        /** AOSP continueWhenFinished()'s SPLINE case, snap-to-final only
         *  (no BALLISTIC bounce transition -- see file header). */
        void onSplineDurationElapsed() {
            if (duration < splineDuration) {
                // fling() clamped us to a boundary: the table-interpolated
                // position at the shortened duration is only an
                // approximation of that exact boundary (the same
                // numerical gap AOSP's own implementation corrects for
                // here), so snap onto it precisely instead of the last,
                // slightly-short sample.
                currentPosition = start = finalPos;
            }
            finished = true;
        }
    }

    /** AOSP Scroller.ViscousFluidInterpolator: the default fling/
     *  startScroll easing when the caller doesn't supply one. Ported
     *  verbatim (our android.widget.Scroller has the same curve inlined as
     *  a private method, not exposed as a reusable Interpolator). */
    private static final class ViscousFluidInterpolator implements Interpolator {
        private static final float VISCOUS_FLUID_SCALE = 8.0f;
        private static final float VISCOUS_FLUID_NORMALIZE;
        private static final float VISCOUS_FLUID_OFFSET;
        static {
            VISCOUS_FLUID_NORMALIZE = 1.0f / viscousFluid(1.0f);
            VISCOUS_FLUID_OFFSET = 1.0f - VISCOUS_FLUID_NORMALIZE * viscousFluid(1.0f);
        }
        private static float viscousFluid(float x) {
            x *= VISCOUS_FLUID_SCALE;
            if (x < 1.0f) {
                x -= (1.0f - (float) Math.exp(-x));
            } else {
                final float start = 0.36787944117f;   // 1/e == exp(-1)
                x = 1.0f - (float) Math.exp(1.0f - x);
                x = start + x * (1.0f - start);
            }
            return x;
        }
        @Override public float getInterpolation(float input) {
            final float interpolated = VISCOUS_FLUID_NORMALIZE * viscousFluid(input);
            if (interpolated > 0) return interpolated + VISCOUS_FLUID_OFFSET;
            return interpolated;
        }
    }
}
