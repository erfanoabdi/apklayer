// android.view.animation.PathInterpolator -- an Interpolator defined by a
// cubic (or quadratic) Bezier curve between (0,0) and (1,1). Mirrors AOSP
// frameworks/base/core/java/android/view/animation/PathInterpolator.java.
// Jetpack Compose + Material motion construct these with the 4-float
// cubic-bezier ctor (controlX1, controlY1, controlX2, controlY2); the old
// bare stub had only a no-arg ctor, so onCreate NoSuchMethodError'd building
// Reddit's Compose UI. We implement the real curve evaluation.
package android.view.animation;

// AOSP: `extends BaseInterpolator` (BaseInterpolator itself implements
// Interpolator). The interface-only form left BaseInterpolator out of the chain,
// so an app or library holding a BaseInterpolator-typed field could not take one
// of these, and AnimationUtils' XML-inflated change-interval bookkeeping had
// nowhere to live. frameworks/base/core/java/android/view/animation/PathInterpolator.java
public class PathInterpolator extends BaseInterpolator {
    // Sampled x->t lookup table precision (AOSP uses the same approach).
    private static final float PRECISION = 0.002f;

    // Not final: AOSP fills these from initQuad()/initCubic()/initPath(), and
    // the quadratic ctor genuinely cannot delegate to the cubic one (see below).
    private float[] mX;
    private float[] mY;

    public PathInterpolator() {
        // Identity (linear) when no curve is supplied.
        initCubic(0f, 0f, 1f, 1f);
    }

    /** Quadratic Bezier with a single control point.
     *
     *  AOSP (android16-release
     *  core/java/android/view/animation/PathInterpolator.java:76-78, 148-153):
     *
     *      public PathInterpolator(float controlX, float controlY) {
     *          initQuad(controlX, controlY);
     *      }
     *      private void initQuad(float controlX, float controlY) {
     *          Path path = new Path();
     *          path.moveTo(0, 0);
     *          path.quadTo(controlX, controlY, 1f, 1f);
     *          initPath(path);
     *      }
     *
     *  i.e. a GENUINE quadratic Bezier. This ctor used to read
     *  `this(controlX, controlY, controlX, controlY)`, which is a different
     *  curve, not a reparameterisation of the same one: with p0=0 and the far
     *  endpoint 1, quadratic B(t) = 2(1-t)t*c + t^2 while cubic with both
     *  control points at c is 3(1-t)^2*t*c + 3(1-t)*t^2*c + t^3. At c=1,
     *  t=0.5 those are 0.75 and 0.875 -- a 12.5 percentage-point error at the
     *  midpoint of the curve, on every `<pathInterpolator>` that declares only
     *  controlX1/controlY1 (AOSP's own initQuad branch, see
     *  parseInterpolatorFromTypeArray:132-135) and on every app that
     *  constructs the 2-float form directly.
     *
     *  (The correct cubic-equivalent of a quadratic would be
     *  c1 = 2q/3, c2 = 1/3 + 2q/3; we evaluate the quadratic directly instead,
     *  which is exact rather than an algebraic re-derivation.) */
    public PathInterpolator(float controlX, float controlY) {
        initQuad(controlX, controlY);
    }

    /** Cubic Bezier with two control points; endpoints fixed at (0,0),(1,1). */
    public PathInterpolator(float controlX1, float controlY1,
                            float controlX2, float controlY2) {
        initCubic(controlX1, controlY1, controlX2, controlY2);
    }

    // Path-based ctor: full arbitrary-path support isn't implemented; fall
    // back to a linear curve so callers still get a valid interpolator.
    public PathInterpolator(android.graphics.Path path) {
        initCubic(0f, 0f, 1f, 1f);
    }

    // SAMPLING NOTE. AOSP builds a real Path and calls Path.approximate(0.002f),
    // which walks the curve at uniform ARC LENGTH; we walk uniform t. Both
    // produce a piecewise-linear approximation of the SAME Bezier and both feed
    // the same binary-search-plus-lerp getInterpolation below, so the curve is
    // identical up to sampling error (501 samples on a curve of length <= ~2.4
    // keeps that well under a thousandth). The alternative -- constructing a
    // Path and implementing approximate() -- would add a graphics dependency to
    // a pure function for no measurable gain.
    private void initQuad(float controlX, float controlY) {
        int numPoints = (int) (1f / PRECISION) + 1;
        mX = new float[numPoints];
        mY = new float[numPoints];
        for (int i = 0; i < numPoints; i++) {
            float t = (float) i / (numPoints - 1);
            mX[i] = quad(t, controlX);
            mY[i] = quad(t, controlY);
        }
    }

    private void initCubic(float controlX1, float controlY1,
                           float controlX2, float controlY2) {
        int numPoints = (int) (1f / PRECISION) + 1;
        mX = new float[numPoints];
        mY = new float[numPoints];
        for (int i = 0; i < numPoints; i++) {
            float t = (float) i / (numPoints - 1);
            mX[i] = cubic(t, controlX1, controlX2);
            mY[i] = cubic(t, controlY1, controlY2);
        }
    }

    // Cubic Bezier component with implicit endpoints p0=0, p3=1:
    // B(t) = 3(1-t)^2 t c1 + 3(1-t) t^2 c2 + t^3
    private static float cubic(float t, float c1, float c2) {
        float u = 1f - t;
        return 3f * u * u * t * c1 + 3f * u * t * t * c2 + t * t * t;
    }

    // Quadratic Bezier component with implicit endpoints p0=0, p2=1:
    // B(t) = 2(1-t) t c + t^2
    private static float quad(float t, float c) {
        float u = 1f - t;
        return 2f * u * t * c + t * t;
    }

    @Override
    public float getInterpolation(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        // Find the segment of mX that brackets t, then linearly interpolate
        // the corresponding mY values (same scheme AOSP uses).
        int startIndex = 0;
        int endIndex = mX.length - 1;
        while (endIndex - startIndex > 1) {
            int midIndex = (startIndex + endIndex) / 2;
            if (t < mX[midIndex]) {
                endIndex = midIndex;
            } else {
                startIndex = midIndex;
            }
        }
        float xRange = mX[endIndex] - mX[startIndex];
        if (xRange == 0f) {
            return mY[startIndex];
        }
        float tInRange = t - mX[startIndex];
        float fraction = tInRange / xRange;
        float startY = mY[startIndex];
        float endY = mY[endIndex];
        return startY + (fraction * (endY - startY));
    }
}
