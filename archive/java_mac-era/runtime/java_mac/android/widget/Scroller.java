// android.widget.Scroller -- computes a scroll/fling animation over time.
// Was a bare `extends Object` stub with no methods; widgets (ViewPager,
// older custom scrollers, GestureDetector consumers) poll
// computeScrollOffset/getCurrX/getCurrY each frame to animate. This is the
// real AOSP scroll math: a viscous-fluid easing for startScroll and an
// exponential deceleration model for fling, both time-driven off
// SystemClock. AOSP:
//   frameworks/base/core/java/android/widget/Scroller.java
//   public class Scroller
package android.widget;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;

public class Scroller {
    private int mMode;
    private int mStartX, mStartY;
    private int mFinalX, mFinalY;
    private int mCurrX, mCurrY;
    private long mStartTime;
    private int mDuration;
    private float mDurationReciprocal;
    private float mDeltaX, mDeltaY;
    private boolean mFinished;
    private final Interpolator mInterpolator;
    private boolean mFlywheel;

    private float mVelocity;
    private float mCurrVelocity;
    private int mDistance;

    private float mFlingFriction = ViewConfiguration.getScrollFriction();
    private float mDeceleration;
    private final float mPpi;

    private static final int DEFAULT_DURATION = 250;
    private static final int SCROLL_MODE = 0;
    private static final int FLING_MODE = 1;

    private static final float DECELERATION_RATE =
            (float) (Math.log(0.78) / Math.log(0.9));
    private static final float INFLEXION = 0.35f;
    private static final float START_TENSION = 0.5f;
    private static final float END_TENSION = 1.0f;
    private static final float P1 = START_TENSION * INFLEXION;
    private static final float P2 = 1.0f - END_TENSION * (1.0f - INFLEXION);

    private static final int NB_SAMPLES = 100;
    private static final float[] SPLINE_POSITION = new float[NB_SAMPLES + 1];
    private static final float[] SPLINE_TIME = new float[NB_SAMPLES + 1];

    private float mPhysicalCoeff;

    static {
        float xMin = 0.0f;
        float yMin = 0.0f;
        for (int i = 0; i < NB_SAMPLES; i++) {
            final float alpha = (float) i / NB_SAMPLES;
            float xMax = 1.0f, x, tx, coef;
            while (true) {
                x = xMin + (xMax - xMin) / 2.0f;
                coef = 3.0f * x * (1.0f - x);
                tx = coef * ((1.0f - x) * P1 + x * P2) + x * x * x;
                if (Math.abs(tx - alpha) < 1E-5) break;
                if (tx > alpha) xMax = x; else xMin = x;
            }
            SPLINE_POSITION[i] = coef * ((1.0f - x) * START_TENSION + x) + x * x * x;
            float yMax = 1.0f, y, dy;
            while (true) {
                y = yMin + (yMax - yMin) / 2.0f;
                coef = 3.0f * y * (1.0f - y);
                dy = coef * ((1.0f - y) * START_TENSION + y) + y * y * y;
                if (Math.abs(dy - alpha) < 1E-5) break;
                if (dy > alpha) yMax = y; else yMin = y;
            }
            SPLINE_TIME[i] = coef * ((1.0f - y) * P1 + y * P2) + y * y * y;
        }
        SPLINE_POSITION[NB_SAMPLES] = SPLINE_TIME[NB_SAMPLES] = 1.0f;
    }

    public Scroller(Context context) { this(context, null); }

    public Scroller(Context context, Interpolator interpolator) {
        this(context, interpolator,
                context.getApplicationInfo() == null
                        || context.getApplicationInfo().targetSdkVersion >= 11);
    }

    public Scroller(Context context, Interpolator interpolator, boolean flywheel) {
        mFinished = true;
        mInterpolator = interpolator;
        mPpi = context.getResources() != null
                ? context.getResources().getDisplayMetrics().density * 160.0f
                : 160.0f;
        mDeceleration = computeDeceleration(ViewConfiguration.getScrollFriction());
        mFlywheel = flywheel;
        mPhysicalCoeff = computeDeceleration(0.84f);
    }

    public final void setFriction(float friction) {
        mDeceleration = computeDeceleration(friction);
        mFlingFriction = friction;
    }

    private float computeDeceleration(float friction) {
        return SensorManager.GRAVITY_EARTH * 39.37f * mPpiSafe() * friction;
    }

    private float mPpiSafe() { return mPpi <= 0 ? 160.0f : mPpi; }

    public final boolean isFinished() { return mFinished; }
    public final void forceFinished(boolean finished) { mFinished = finished; }
    public final int getDuration() { return mDuration; }
    public final int getCurrX() { return mCurrX; }
    public final int getCurrY() { return mCurrY; }

    public float getCurrVelocity() {
        return mMode == FLING_MODE ? mCurrVelocity
                : mVelocity - mDeceleration * timePassed() / 2000.0f;
    }

    public final int getStartX() { return mStartX; }
    public final int getStartY() { return mStartY; }
    public final int getFinalX() { return mFinalX; }
    public final int getFinalY() { return mFinalY; }

    public boolean computeScrollOffset() {
        if (mFinished) return false;

        int timePassed = (int) (now() - mStartTime);

        if (timePassed < mDuration) {
            switch (mMode) {
                case SCROLL_MODE:
                    float x = timePassed * mDurationReciprocal;
                    if (mInterpolator == null) x = viscousFluid(x);
                    else x = mInterpolator.getInterpolation(x);
                    mCurrX = mStartX + Math.round(x * mDeltaX);
                    mCurrY = mStartY + Math.round(x * mDeltaY);
                    break;
                case FLING_MODE:
                    final float t = (float) timePassed / mDuration;
                    final int index = (int) (NB_SAMPLES * t);
                    float distanceCoef = 1.f;
                    float velocityCoef = 0.f;
                    if (index < NB_SAMPLES) {
                        final float tInf = (float) index / NB_SAMPLES;
                        final float tSup = (float) (index + 1) / NB_SAMPLES;
                        final float dInf = SPLINE_POSITION[index];
                        final float dSup = SPLINE_POSITION[index + 1];
                        velocityCoef = (dSup - dInf) / (tSup - tInf);
                        distanceCoef = dInf + (t - tInf) * velocityCoef;
                    }
                    mCurrVelocity = velocityCoef * mDistance / mDuration * 1000.0f;
                    mCurrX = mStartX + Math.round(distanceCoef * (mFinalX - mStartX));
                    mCurrX = Math.min(mCurrX, max(mStartX, mFinalX));
                    mCurrX = Math.max(mCurrX, min(mStartX, mFinalX));
                    mCurrY = mStartY + Math.round(distanceCoef * (mFinalY - mStartY));
                    mCurrY = Math.min(mCurrY, max(mStartY, mFinalY));
                    mCurrY = Math.max(mCurrY, min(mStartY, mFinalY));
                    if (mCurrX == mFinalX && mCurrY == mFinalY) mFinished = true;
                    break;
            }
        } else {
            mCurrX = mFinalX;
            mCurrY = mFinalY;
            mFinished = true;
        }
        return true;
    }

    public void startScroll(int startX, int startY, int dx, int dy) {
        startScroll(startX, startY, dx, dy, DEFAULT_DURATION);
    }

    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        mMode = SCROLL_MODE;
        mFinished = false;
        mDuration = duration;
        mStartTime = now();
        mStartX = startX;
        mStartY = startY;
        mFinalX = startX + dx;
        mFinalY = startY + dy;
        mDeltaX = dx;
        mDeltaY = dy;
        mDurationReciprocal = 1.0f / (float) mDuration;
        mCurrX = startX;
        mCurrY = startY;
    }

    public void fling(int startX, int startY, int velocityX, int velocityY,
            int minX, int maxX, int minY, int maxY) {
        if (mFlywheel && !mFinished) {
            float oldVel = getCurrVelocity();
            float dx = (float) (mFinalX - mStartX);
            float dy = (float) (mFinalY - mStartY);
            float hyp = (float) Math.hypot(dx, dy);
            float ndx = dx / hyp, ndy = dy / hyp;
            float oldVelocityX = ndx * oldVel, oldVelocityY = ndy * oldVel;
            if (Math.signum(velocityX) == Math.signum(oldVelocityX)
                    && Math.signum(velocityY) == Math.signum(oldVelocityY)) {
                velocityX += oldVelocityX;
                velocityY += oldVelocityY;
            }
        }
        mMode = FLING_MODE;
        mFinished = false;
        float velocity = (float) Math.hypot(velocityX, velocityY);
        mVelocity = velocity;
        mDuration = getSplineFlingDuration(velocity);
        mStartTime = now();
        mStartX = startX;
        mStartY = startY;
        float coeffX = velocity == 0 ? 1.0f : velocityX / velocity;
        float coeffY = velocity == 0 ? 1.0f : velocityY / velocity;
        double totalDistance = getSplineFlingDistance(velocity);
        mDistance = (int) (totalDistance * Math.signum(velocity));
        int dX = (int) Math.round(totalDistance * coeffX);
        int dY = (int) Math.round(totalDistance * coeffY);
        mFinalX = startX + dX;
        mFinalX = Math.min(mFinalX, maxX);
        mFinalX = Math.max(mFinalX, minX);
        mFinalY = startY + dY;
        mFinalY = Math.min(mFinalY, maxY);
        mFinalY = Math.max(mFinalY, minY);
    }

    private double getSplineDeceleration(float velocity) {
        return Math.log(INFLEXION * Math.abs(velocity)
                / (mFlingFriction * mPhysicalCoeff));
    }

    private int getSplineFlingDuration(float velocity) {
        final double l = getSplineDeceleration(velocity);
        final double decelMinusOne = DECELERATION_RATE - 1.0;
        return (int) (1000.0 * Math.exp(l / decelMinusOne));
    }

    private double getSplineFlingDistance(float velocity) {
        final double l = getSplineDeceleration(velocity);
        final double decelMinusOne = DECELERATION_RATE - 1.0;
        return mFlingFriction * mPhysicalCoeff
                * Math.exp(DECELERATION_RATE / decelMinusOne * l);
    }

    private static float viscousFluid(float x) {
        final float VISCOUS_FLUID_SCALE = 8.0f;
        x *= VISCOUS_FLUID_SCALE;
        if (x < 1.0f) {
            x -= (1.0f - (float) Math.exp(-x));
        } else {
            float start = 0.36787944117f; // 1/e
            x = 1.0f - (float) Math.exp(1.0f - x);
            x = start + x * (1.0f - start);
        }
        return x * 1.131625f; // normalize
    }

    public void abortAnimation() {
        mCurrX = mFinalX;
        mCurrY = mFinalY;
        mFinished = true;
    }

    public void extendDuration(int extend) {
        int passed = timePassed();
        mDuration = passed + extend;
        mDurationReciprocal = 1.0f / mDuration;
        mFinished = false;
    }

    public int timePassed() { return (int) (now() - mStartTime); }

    public void setFinalX(int newX) {
        mFinalX = newX;
        mDeltaX = mFinalX - mStartX;
        mFinished = false;
    }
    public void setFinalY(int newY) {
        mFinalY = newY;
        mDeltaY = mFinalY - mStartY;
        mFinished = false;
    }

    public boolean isScrollingInDirection(float xvel, float yvel) {
        final int dx = mFinalX - mStartX;
        final int dy = mFinalY - mStartY;
        return !mFinished && Math.signum(xvel) == Math.signum(dx)
                && Math.signum(yvel) == Math.signum(dy);
    }

    private static long now() { return android.os.SystemClock.uptimeMillis(); }
    private static int max(int a, int b) { return Math.max(a, b); }
    private static int min(int a, int b) { return Math.min(a, b); }
}
