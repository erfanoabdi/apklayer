// android.animation.ValueAnimator -- Tier 3 M7: a REAL, Choreographer-driven
// value pump (was a snap-to-end stub). On start() it registers a frame callback
// on the MAIN-loop Choreographer; each true vsync (driven by ViewCanvasDriver via
// Choreographer.macapkDriveMainFrame) it computes fraction = elapsed/duration,
// applies the interpolator, evaluates the animated value, fires update listeners,
// and re-posts itself until the animation completes (honoring startDelay,
// repeatCount/INFINITE, and RESTART/REVERSE). Material transitions, RecyclerView
// ItemAnimator, ViewPropertyAnimator, and ObjectAnimator all ride on this.
//
// Gated on MACAPK_REAL_LOOPER: flag-OFF keeps the historical snap-to-end (the
// shipped build is byte-for-byte unchanged -- start() jumps to fraction 1 and
// fires the lifecycle once), so this only animates on the experimental path that
// has the real main loop + vsync Choreographer (M1-M7) to drive it.
//
// AOSP source: cs.android.com/.../frameworks/base/core/java/android/animation/ValueAnimator.java
package android.animation;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

// AOSP fidelity: the unchecked usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("unchecked")
public class ValueAnimator extends Animator {
    public static final int RESTART = 1;
    public static final int REVERSE = 2;
    public static final int INFINITE = -1;

    private long durationMs = 300L;
    private long startDelayMs = 0L;
    private int  repeatCount = 0;
    private int  repeatMode = RESTART;
    private float currentFraction;
    private android.animation.TimeInterpolator interpolator;
    private ArrayList<AnimatorUpdateListener> updateListeners;

    // Value spec.
    private static final int T_NONE = 0, T_FLOAT = 1, T_INT = 2, T_ARGB = 3, T_OBJECT = 4;
    private int valueType = T_NONE;
    private float[]  floatValues;
    private int[]    intValues;
    private Object[] objectValues;
    private android.animation.TypeEvaluator evaluator;
    private Object animatedValue;
    // AOSP setValues(PropertyValuesHolder...)/getValues(): takes precedence
    // over the ofInt/ofFloat/ofArgb/ofObject spec above when set (see
    // computeAnimatedValue). The common idiom is reconfiguring a pooled/
    // reused animator's targets without constructing a fresh instance.
    private PropertyValuesHolder[] mValues;

    // Running state (real path).
    private boolean mRunning;
    private boolean mStartedFired;
    // AOSP reverse(): one-time-per-run direction flip, reset to forward by the
    // next start() (see below). Consulted by doAnimationFrame's flip-XOR and
    // by end()'s terminal-fraction choice.
    private boolean mReversing;
    private long mStartTimeNanos = -1L;
    // NOT final: clone() below must rebuild this bound to the CLONE, not just
    // shallow-copy the reference (see clone()'s comment for why that matters).
    private android.view.Choreographer.FrameCallback mFrameCallback =
        new android.view.Choreographer.FrameCallback() {
            @Override public void doFrame(long frameTimeNanos) { doAnimationFrame(frameTimeNanos); }
        };

    public ValueAnimator() {}

    public static ValueAnimator ofInt(int... values)   { ValueAnimator a = new ValueAnimator(); a.setIntValues(values); return a; }
    public static ValueAnimator ofFloat(float... values) { ValueAnimator a = new ValueAnimator(); a.setFloatValues(values); return a; }
    public static ValueAnimator ofArgb(int... values)  { ValueAnimator a = new ValueAnimator(); a.setIntValues(values); a.valueType = T_ARGB; return a; }
    /**
     * AOSP ValueAnimator.ofPropertyValuesHolder(PropertyValuesHolder...):
     *     ValueAnimator anim = new ValueAnimator();
     *     anim.setValues(values);
     *     return anim;
     * This was the one ValueAnimator factory missing. It is how an
     * AnimatorInflater-built or hand-built multi-property value animator is
     * created without a target, so the absence was a NoSuchMethodError at the
     * construction call.
     */
    public static ValueAnimator ofPropertyValuesHolder(PropertyValuesHolder... values) {
        ValueAnimator anim = new ValueAnimator();
        anim.setValues(values);
        return anim;
    }
    public static ValueAnimator ofObject(android.animation.TypeEvaluator evaluator, Object... values) {
        ValueAnimator a = new ValueAnimator(); a.evaluator = evaluator; a.objectValues = values; a.valueType = T_OBJECT; return a;
    }

    @Override public ValueAnimator setDuration(long d) { this.durationMs = Math.max(0, d); return this; }
    @Override public long getDuration() { return durationMs; }
    @Override public void setStartDelay(long d) { this.startDelayMs = Math.max(0, d); }
    @Override public long getStartDelay() { return startDelayMs; }

    // AOSP ValueAnimator.java (android11-release) verbatim:
    //   public long getTotalDuration() {
    //       if (mRepeatCount == INFINITE) { return DURATION_INFINITE; }
    //       else { return mStartDelay + (mDuration * (mRepeatCount + 1)); }
    //   }
    // Repeats participate in the total, and an INFINITE repeat answers
    // DURATION_INFINITE. AnimatorSet's dependency graph consumes this value
    // per child, which is how one infinite child makes the whole set's
    // total infinite.
    @Override public long getTotalDuration() {
        if (repeatCount == INFINITE) return DURATION_INFINITE;
        return startDelayMs + durationMs * (repeatCount + 1);
    }

    public void setRepeatCount(int v) { this.repeatCount = v; }
    public int  getRepeatCount()      { return repeatCount; }
    public void setRepeatMode(int v)  { this.repeatMode = v; }
    public int  getRepeatMode()       { return repeatMode; }

    public void setIntValues(int... values)   { this.intValues = values; if (valueType != T_ARGB) valueType = T_INT; }
    public void setFloatValues(float... values) { this.floatValues = values; valueType = T_FLOAT; }
    public void setObjectValues(Object... values) { this.objectValues = values; valueType = T_OBJECT; }
    public void setValues(PropertyValuesHolder... values) { this.mValues = values; }
    public PropertyValuesHolder[] getValues() { return mValues; }
    // Package accessors so ObjectAnimator can capture the START value from the
    // target when only an end value was supplied (T_FLOAT=1, T_INT=2, T_ARGB=3).
    float[] macapkFloatValues() { return floatValues; }
    int[]   macapkIntValues()   { return intValues; }
    int     macapkValueType()   { return valueType; }
    /** Tier 3 M7 (subclass helpers): set ARGB-color or generic-object value specs. */
    protected void setArgbValues(int[] values) { this.intValues = values; this.valueType = T_ARGB; }
    protected void setObjectEvaluator(android.animation.TypeEvaluator ev, Object[] values) {
        this.evaluator = ev; this.objectValues = values; this.valueType = T_OBJECT;
    }

    public void setCurrentPlayTime(long playTime) {
        float f = durationMs > 0 ? (float) playTime / durationMs : 1f;
        setCurrentFraction(f);
    }
    public void setCurrentFraction(float fraction) {
        fraction = Math.max(0f, Math.min(1f, fraction));
        currentFraction = applyInterpolator(fraction);
        computeAnimatedValue(currentFraction);
        applyAnimatedValue(animatedValue, currentFraction);
        fireUpdate();
    }
    public long getCurrentPlayTime() { return (long) (currentFraction * durationMs); }
    public float getAnimatedFraction() { return currentFraction; }
    public Object getAnimatedValue() { return animatedValue; }
    public Object getAnimatedValue(String propertyName) { return animatedValue; }

    @Override public void setInterpolator(android.animation.TimeInterpolator value) { this.interpolator = value; }
    @Override public android.animation.TimeInterpolator getInterpolator() { return interpolator; }

    public void addUpdateListener(AnimatorUpdateListener listener) {
        if (listener == null) return;
        if (updateListeners == null) updateListeners = new ArrayList<>();
        updateListeners.add(listener);
    }
    public void removeUpdateListener(AnimatorUpdateListener listener) {
        if (updateListeners != null) updateListeners.remove(listener);
    }
    public void removeAllUpdateListeners() { if (updateListeners != null) updateListeners.clear(); }

    @Override public boolean isRunning() { return mRunning; }
    @Override public boolean isStarted() { return mRunning; }

    // ------------------------------------------------------------------
    // pause() / resume(). Animator.pause() set a flag that NOTHING read:
    // doAnimationFrame kept re-posting itself and the animation ran to
    // completion, so an app that paused an animator (onPause, a drag taking
    // over, a Lottie-style scrubbing control) saw it finish anyway and any
    // state it restores in onAnimationEnd fired at the wrong moment.
    //
    // AOSP ValueAnimator (android16-release
    // core/java/android/animation/ValueAnimator.java):
    //     public void pause() { boolean previouslyPaused = mPaused;
    //         super.pause();
    //         if (!previouslyPaused && mPaused) { mPauseTime = -1; mResumed = false; } }
    //     public void resume() { if (mPaused && !mResumed) { mResumed = true;
    //         if (mPauseTime > 0) addAnimationCallback(0); } super.resume(); }
    // and in doAnimationFrame:
    //     if (mPaused) { mPauseTime = frameTime; removeAnimationCallback(); return false; }
    //     else if (mResumed) { mResumed = false;
    //         if (mPauseTime > 0) mStartTime += (frameTime - mPauseTime); }
    //
    // The mStartTime shift is the load-bearing half: without it a resumed
    // animation would jump forward by however long it sat paused instead of
    // continuing where it stopped.
    // ------------------------------------------------------------------
    private long mPauseTimeNanos = -1L;
    private boolean mResumed;

    @Override public void pause() {
        boolean previouslyPaused = isPaused();
        super.pause();
        if (!previouslyPaused && isPaused()) {
            mPauseTimeNanos = -1L;
            mResumed = false;
        }
    }

    @Override public void resume() {
        if (isPaused() && !mResumed) {
            mResumed = true;
            // AOSP addAnimationCallback(0). Our callback is one-shot (each
            // frame re-posts), and doAnimationFrame deliberately stops
            // re-posting while paused, so resume() is what puts it back.
            // mPauseTime > 0 means a frame actually observed the pause; if none
            // did, the still-pending callback will simply see !mPaused.
            if (mPauseTimeNanos > 0L) {
                android.view.Choreographer.getInstance().postFrameCallback(mFrameCallback);
            }
        }
        super.resume();
    }

    @Override public void start() {
        if (!android.os.Looper.MACAPK_REAL_LOOPER) {
            // Shipped path: snap-to-end so the lifecycle fires in order (no pump).
            mReversing = false;
            currentFraction = 1f;
            computeAnimatedValue(1f);
            applyAnimatedValue(animatedValue, 1f);
            fireUpdate();
            super.start();
            return;
        }
        // Real path: drive the value frame-by-frame off the vsync Choreographer.
        // AOSP: start() always plays forward -- reverse() sets a one-time
        // direction for the CURRENT run only ("future playing of the animation
        // will use the default behavior of playing forward").
        mReversing = false;
        mRunning = true;
        mStartedFired = false;
        mStartTimeNanos = -1L;
        currentFraction = 0f;
        computeAnimatedValue(0f);
        android.view.Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    // AOSP ValueAnimator.reverse(): plays backward from wherever this run has
    // reached; if not currently running, starts from the end and plays toward
    // the start. See the mReversing field comment for the one-time-per-run
    // contract.
    public void reverse() {
        if (!android.os.Looper.MACAPK_REAL_LOOPER) {
            // Shipped snap-to-end path has no time axis to run backward across;
            // mirror the observable contract by snapping to the opposite
            // endpoint instead of always 1.
            mReversing = !mReversing;
            float f = mReversing ? 0f : 1f;
            currentFraction = f;
            computeAnimatedValue(f);
            applyAnimatedValue(animatedValue, f);
            fireUpdate();
            super.start();
            return;
        }
        if (mRunning) {
            if (mStartTimeNanos >= 0L) {
                // Rebase mStartTimeNanos so the fraction doAnimationFrame
                // computes on the NEXT tick matches the fraction shown just
                // before this call (no visual jump); the flipped mReversing
                // then carries playback the other way from there. Derivation:
                // solve for newStart such that, evaluated at "now", the
                // flipped formula reproduces the pre-flip fraction.
                double scaledDurationMs = durationMs * (double) sDurationScale;
                double scaledDelayMs = startDelayMs * (double) sDurationScale;
                long nowNanos = System.nanoTime();
                double elapsedMs = (nowNanos - mStartTimeNanos) / 1_000_000.0 - scaledDelayMs;
                double timeLeftMs = scaledDurationMs - elapsedMs;
                mStartTimeNanos = nowNanos - (long) ((timeLeftMs + scaledDelayMs) * 1_000_000.0);
            }
            mReversing = !mReversing;
        } else {
            // Not running: start (virtual dispatch reaches ObjectAnimator's
            // captureStartValue override, exactly as a forward start() would,
            // so the implicit start endpoint is captured correctly), then flip
            // to reverse and re-prime the initial value at fraction 1.
            start();
            mReversing = true;
            currentFraction = 1f;
            computeAnimatedValue(1f);
        }
    }

    @Override public void cancel() {
        if (!android.os.Looper.MACAPK_REAL_LOOPER) { super.cancel(); return; }
        if (!mRunning) return;
        mRunning = false;
        android.view.Choreographer.getInstance().removeFrameCallback(mFrameCallback);
        notifyCancelListeners();
        notifyEndListeners();
    }

    @Override public void end() {
        if (!android.os.Looper.MACAPK_REAL_LOOPER) { super.end(); return; }
        android.view.Choreographer.getInstance().removeFrameCallback(mFrameCallback);
        // Jump to the terminal value in the CURRENT playback direction:
        // fraction 1 normally, fraction 0 if reverse() flipped this run.
        float endFraction = mReversing ? 0f : 1f;
        currentFraction = applyInterpolator(endFraction);
        computeAnimatedValue(currentFraction);
        applyAnimatedValue(animatedValue, currentFraction);
        fireUpdate();
        boolean was = mRunning;
        mRunning = false;
        if (was || !mStartedFired) notifyEndListeners();
    }

    // AOSP ValueAnimator.clone() (frameworks/base/core/java/android/animation/
    // ValueAnimator.java) resets every per-run field to a fresh, unstarted state
    // and deep-copies mUpdateListeners / mValues, so a clone is an independent
    // animator rather than a continuation of whatever the template was doing:
    //   final ValueAnimator anim = (ValueAnimator) super.clone();
    //   if (mUpdateListeners != null) {
    //       anim.mUpdateListeners = new ArrayList<AnimatorUpdateListener>(mUpdateListeners);
    //   }
    //   anim.mReversing = false; ... anim.mStarted = false; anim.mRunning = false;
    //   ... anim.mStartTime = -1; ...
    //   PropertyValuesHolder[] oldValues = mValues;
    //   if (oldValues != null) {
    //       anim.mValues = new PropertyValuesHolder[oldValues.length];
    //       for (int i = 0; i < oldValues.length; ++i) {
    //           anim.mValues[i] = oldValues[i].clone();
    //       }
    //   }
    // We mirror that AND fix a bug AOSP's own architecture does not have: real
    // AOSP's ValueAnimator directly IMPLEMENTS AnimationHandler.AnimationFrameCallback
    // (dispatch is virtual on `this`, always correct after a clone). Ours instead
    // holds mFrameCallback, a `final`-until-now field pointing at an ANONYMOUS
    // INNER CLASS instance created when the object doing `new ValueAnimator()`/
    // `new ObjectAnimator()` was constructed. Object.clone() (what super.clone()
    // bottoms out to) copies that field's REFERENCE, not a fresh instance, and
    // the referenced object's implicit outer-instance pointer (this$0) is set
    // ONCE, at ITS construction, forever pointing at the ORIGINAL. Verified
    // empirically with an isolated repro (a class holding
    // `final Callback cb = new Callback(){ void fire(){ real(); } }`): after
    // `Impl clone = (Impl) template.clone();`, `clone.cb == template.cb` is TRUE,
    // and calling `clone.cb.fire()` runs `real()` on the TEMPLATE, not the clone.
    // Concretely: the CLONE's start() would postFrameCallback(mFrameCallback),
    // and every subsequent vsync would drive doAnimationFrame() on the TEMPLATE
    // forever -- the clone's own mRunning never clears, its own listeners
    // (including onAnimationEnd) never fire, and its own applyAnimatedValue()
    // never runs past the one synchronous computeAnimatedValue(0f) inside
    // start(). This is not a hypothetical: android.animation.LayoutTransition
    // (runAppearingTransition/runDisappearingTransition) calls
    // mAppearingAnim.clone()/mDisappearingAnim.clone() on EVERY child add/remove,
    // so every app using the one-line android:animateLayoutChanges="true" opt-in
    // hits this on every layout change. Rebuilding the callback here --
    // referencing the local `anim` explicitly rather than relying on implicit
    // outer-instance capture -- fixes it for that case and for any app code that
    // clones an Animator directly (a documented AOSP idiom for running several
    // concurrent copies of one configured animator).
    @Override
    public ValueAnimator clone() {
        final ValueAnimator anim = (ValueAnimator) super.clone();
        anim.mFrameCallback = new android.view.Choreographer.FrameCallback() {
            @Override public void doFrame(long frameTimeNanos) { anim.doAnimationFrame(frameTimeNanos); }
        };
        anim.mRunning = false;
        anim.mStartedFired = false;
        anim.mStartTimeNanos = -1L;
        anim.mReversing = false;
        anim.currentFraction = 0f;
        anim.animatedValue = null;
        if (updateListeners != null) anim.updateListeners = new ArrayList<>(updateListeners);
        // setValues(PropertyValuesHolder...) holders cache their own evaluated
        // state across setXxxValues() calls; give the clone independent copies so
        // a later setValues()/mutation on one instance cannot reach the other.
        // floatValues/intValues/objectValues are intentionally left shared: they
        // are plain immutable-after-construction arrays (only ever READ by
        // computeAnimatedValue), so sharing them is safe and deep-copying them
        // would just be churn.
        if (mValues != null) {
            PropertyValuesHolder[] copy = new PropertyValuesHolder[mValues.length];
            for (int i = 0; i < mValues.length; i++) {
                copy[i] = (mValues[i] != null) ? mValues[i].clone() : null;
            }
            anim.mValues = copy;
        }
        return anim;
    }

    private void doAnimationFrame(long frameTimeNanos) {
        if (!mRunning) return;
        // AOSP doAnimationFrame's first act after the running check. Returning
        // WITHOUT re-posting is our equivalent of removeAnimationCallback():
        // the pump stops here and resume() restarts it.
        if (isPaused()) {
            mPauseTimeNanos = frameTimeNanos;
            return;
        } else if (mResumed) {
            mResumed = false;
            if (mPauseTimeNanos > 0L && mStartTimeNanos >= 0L) {
                // Offset by the duration the animation was paused, so it picks
                // up exactly where it left off rather than jumping ahead.
                mStartTimeNanos += (frameTimeNanos - mPauseTimeNanos);
            }
        }
        if (mStartTimeNanos < 0L) mStartTimeNanos = frameTimeNanos;
        // AOSP getScaledDuration(): mDuration/mStartDelay both scale by the
        // system-wide Animator duration scale (0 == animations disabled,
        // handled below by the scaledDurationMs<=0 branch). sDurationScale is
        // 1.0f by default so this is numerically a no-op unless setDurationScale
        // was called.
        double scaledDelayMs = startDelayMs * (double) sDurationScale;
        double elapsedMs = (frameTimeNanos - mStartTimeNanos) / 1_000_000.0 - scaledDelayMs;
        if (elapsedMs < 0) { android.view.Choreographer.getInstance().postFrameCallback(mFrameCallback); return; }
        if (!mStartedFired) { mStartedFired = true; notifyStartListeners(); }

        boolean done;
        double iterFraction;
        double scaledDurationMs = durationMs * (double) sDurationScale;
        if (scaledDurationMs <= 0) {
            iterFraction = 1.0; done = true;
        } else {
            double f = elapsedMs / scaledDurationMs;   // total progress, can exceed 1 for repeats
            int totalIters = repeatCount == INFINITE ? Integer.MAX_VALUE : repeatCount;
            if (repeatCount != INFINITE && f >= totalIters + 1) {
                iterFraction = 1.0; done = true;
            } else {
                int iter = (int) f;
                iterFraction = f - iter;
                // reverse() flips the WHOLE run's direction; repeatMode=REVERSE
                // flips alternate iterations. Compose via XOR so both can apply
                // (a reversed run of a REVERSE-repeat animator still alternates,
                // just starting from the other end).
                boolean flip = (repeatMode == REVERSE && (iter % 2 == 1));
                if (mReversing) flip = !flip;
                if (flip) iterFraction = 1.0 - iterFraction;
                done = false;
            }
        }
        float clamped = (float) Math.max(0.0, Math.min(1.0, iterFraction));
        currentFraction = applyInterpolator(clamped);
        computeAnimatedValue(currentFraction);
        applyAnimatedValue(animatedValue, currentFraction);
        fireUpdate();

        if (done) {
            mRunning = false;
            notifyEndListeners();
        } else {
            android.view.Choreographer.getInstance().postFrameCallback(mFrameCallback);
        }
    }

    private float applyInterpolator(float f) {
        if (interpolator != null) {
            try { return interpolator.getInterpolation(f); } catch (Throwable ignored) {}
        }
        // AOSP default: AccelerateDecelerateInterpolator.
        return (float) (Math.cos((f + 1) * Math.PI) / 2.0) + 0.5f;
    }

    // AOSP public API: attach a custom TypeEvaluator (the common idiom is
    // ValueAnimator.ofInt(c1,c2).setEvaluator(new ArgbEvaluator()) for a color
    // animation). AOSP sets it on the first PropertyValuesHolder; we keep the
    // single evaluator field and honor it in computeAnimatedValue for the
    // primitive value types. Missing before -> NoSuchMethodError on the call.
    public void setEvaluator(android.animation.TypeEvaluator value) { this.evaluator = value; }

    private void computeAnimatedValue(float fraction) {
        // setValues(PropertyValuesHolder...) takes precedence over every other
        // value spec (AOSP: mValues, once set, is THE value spec). Only the
        // first holder drives the base ValueAnimator's single animatedValue;
        // ObjectAnimator.ofPropertyValuesHolder uses its own separate `holders`
        // array (applied per-property in ObjectAnimator.applyAnimatedValue) and
        // does not go through this field, so it is unaffected.
        if (mValues != null && mValues.length > 0) {
            animatedValue = mValues[0].macapkValueAt(fraction);
            return;
        }
        // A custom evaluator set via setEvaluator() on an ofInt/ofFloat/ofArgb
        // animator takes precedence: route the endpoints through it (e.g. an
        // ArgbEvaluator on ofInt produces color interpolation). evaluator is
        // non-null with a non-OBJECT type ONLY via setEvaluator(), so normal
        // ofInt/ofFloat/ofArgb animations are unaffected.
        if (evaluator != null && valueType != T_OBJECT) {
            Object a = null, b = null;
            if ((valueType == T_INT || valueType == T_ARGB) && intValues != null && intValues.length > 0) {
                a = Integer.valueOf(intValues[0]);
                b = Integer.valueOf(intValues[intValues.length - 1]);
            } else if (valueType == T_FLOAT && floatValues != null && floatValues.length > 0) {
                a = Float.valueOf(floatValues[0]);
                b = Float.valueOf(floatValues[floatValues.length - 1]);
            }
            if (a != null) { animatedValue = evaluator.evaluate(fraction, a, b); return; }
        }
        switch (valueType) {
            case T_FLOAT:
                if (floatValues != null && floatValues.length > 0)
                    animatedValue = lerpFloat(floatValues, fraction);
                break;
            case T_INT:
                if (intValues != null && intValues.length > 0)
                    animatedValue = Math.round(lerpFloatFromInt(intValues, fraction));
                break;
            case T_ARGB:
                if (intValues != null && intValues.length >= 2)
                    animatedValue = lerpArgb(intValues, fraction);
                else if (intValues != null && intValues.length == 1)
                    animatedValue = intValues[0];
                break;
            case T_OBJECT:
                if (evaluator != null && objectValues != null && objectValues.length >= 2)
                    animatedValue = evaluator.evaluate(fraction, objectValues[0], objectValues[objectValues.length - 1]);
                break;
            default: break;
        }
    }

    private static float lerpFloat(float[] v, float f) {
        if (v.length == 1) return v[0] * f;
        float scaled = f * (v.length - 1);
        int i = (int) Math.floor(scaled);
        if (i >= v.length - 1) return v[v.length - 1];
        if (i < 0) return v[0];
        float local = scaled - i;
        return v[i] + (v[i + 1] - v[i]) * local;
    }
    private static float lerpFloatFromInt(int[] v, float f) {
        if (v.length == 1) return v[0] * f;
        float scaled = f * (v.length - 1);
        int i = (int) Math.floor(scaled);
        if (i >= v.length - 1) return v[v.length - 1];
        if (i < 0) return v[0];
        float local = scaled - i;
        return v[i] + (v[i + 1] - v[i]) * local;
    }
    private static int lerpArgb(int[] v, float f) {
        float scaled = f * (v.length - 1);
        int i = (int) Math.floor(scaled);
        if (i >= v.length - 1) i = v.length - 2;
        if (i < 0) i = 0;
        float local = scaled - i;
        int s = v[i], e = v[i + 1];
        int a = (int) (((s >> 24) & 0xff) + (((e >> 24) & 0xff) - ((s >> 24) & 0xff)) * local);
        int r = (int) (((s >> 16) & 0xff) + (((e >> 16) & 0xff) - ((s >> 16) & 0xff)) * local);
        int g = (int) (((s >> 8) & 0xff)  + (((e >> 8) & 0xff)  - ((s >> 8) & 0xff))  * local);
        int b = (int) ((s & 0xff)         + ((e & 0xff)         - (s & 0xff))         * local);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Hook for subclasses (ObjectAnimator) to push the per-frame value onto a
     *  target property. Base ValueAnimator has no target. */
    protected void applyAnimatedValue(Object value, float fraction) {}

    private void fireUpdate() {
        if (updateListeners == null) return;
        for (AnimatorUpdateListener l : new ArrayList<>(updateListeners)) {
            try { l.onAnimationUpdate(this); } catch (Throwable ignored) {}
        }
    }

    public interface AnimatorUpdateListener {
        void onAnimationUpdate(ValueAnimator animation);
    }

    public static long getFrameDelay() { return 16; }
    public static void setFrameDelay(long frameDelay) {}

    // AOSP: system-wide Animator duration scale (Settings.Global.
    // ANIMATOR_DURATION_SCALE / the accessibility "remove animations" toggle).
    // 0 disables animations -- every running animator's scaledDurationMs
    // collapses to 0, so doAnimationFrame's scaledDurationMs<=0 branch snaps
    // it to the end on the very next frame. Consulted in doAnimationFrame
    // above; 1.0f default keeps every existing animation's timing unchanged.
    private static float sDurationScale = 1.0f;
    private static final ArrayList<WeakReference<DurationScaleChangeListener>> sDurationScaleChangeListeners =
            new ArrayList<WeakReference<DurationScaleChangeListener>>();

    public static float getDurationScale() { return sDurationScale; }

    public static void setDurationScale(float durationScale) {
        sDurationScale = durationScale;
        ArrayList<WeakReference<DurationScaleChangeListener>> listenerCopy;
        synchronized (sDurationScaleChangeListeners) {
            listenerCopy = new ArrayList<WeakReference<DurationScaleChangeListener>>(sDurationScaleChangeListeners);
        }
        for (WeakReference<DurationScaleChangeListener> ref : listenerCopy) {
            DurationScaleChangeListener l = ref.get();
            if (l != null) { try { l.onChanged(durationScale); } catch (Throwable ignored) {} }
        }
    }

    // AOSP: reuses a dead (GC'd) weak slot if one exists, else appends; returns
    // false if this exact listener is already registered.
    public static boolean registerDurationScaleChangeListener(DurationScaleChangeListener listener) {
        synchronized (sDurationScaleChangeListeners) {
            int deadSlot = -1;
            for (int i = 0; i < sDurationScaleChangeListeners.size(); i++) {
                DurationScaleChangeListener existing = sDurationScaleChangeListeners.get(i).get();
                if (existing == null) { if (deadSlot == -1) deadSlot = i; }
                else if (existing == listener) return false;
            }
            if (deadSlot != -1) {
                sDurationScaleChangeListeners.set(deadSlot, new WeakReference<DurationScaleChangeListener>(listener));
                return true;
            }
            return sDurationScaleChangeListeners.add(new WeakReference<DurationScaleChangeListener>(listener));
        }
    }

    public static boolean unregisterDurationScaleChangeListener(DurationScaleChangeListener listener) {
        synchronized (sDurationScaleChangeListeners) {
            WeakReference<DurationScaleChangeListener> toRemove = null;
            for (WeakReference<DurationScaleChangeListener> ref : sDurationScaleChangeListeners) {
                if (ref.get() == listener) { toRemove = ref; break; }
            }
            return toRemove != null && sDurationScaleChangeListeners.remove(toRemove);
        }
    }

    public interface DurationScaleChangeListener {
        void onChanged(float scale);
    }

    public static boolean areAnimatorsEnabled() { return true; }
}
