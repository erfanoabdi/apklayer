// android.view.ViewPropertyAnimator -- Tier 3 M7: the fluent View.animate()
// builder, now REAL. Each chained call (translationX/alpha/rotation/...) records
// a target; start() captures the view's current value for each, runs a
// Choreographer-driven ValueAnimator(0->1), and on every vsync sets each property
// = start + (target - start) * interpolatedFraction. Translation/scale/rotation
// are rendered via the View 2D transform (ViewGroup.dispatchDraw); alpha updates
// the View's alpha. Gated on MACAPK_REAL_LOOPER: flag-OFF keeps the historical
// snap-to-end (apply final values + run start/end actions immediately), so the
// shipped build is unchanged.
package android.view;

import java.util.ArrayList;

public class ViewPropertyAnimator {
    private final View target;
    private long durationMs = 300L;          // AOSP ViewPropertyAnimator default
    private long startDelayMs = 0L;
    // AOSP declares this android.animation.TimeInterpolator (NOT the narrower
    // android.view.animation.Interpolator every app compiles against via
    // View.animate().setInterpolator(...)); javac resolves call sites to the
    // TimeInterpolator descriptor regardless of the static type of the
    // argument passed, so a narrower parameter type here is a NoSuchMethodError
    // at every call site, not just a type restriction.
    private android.animation.TimeInterpolator interpolator;
    private Runnable startAction, endAction;
    private android.animation.Animator.AnimatorListener listener;
    private android.animation.ValueAnimator.AnimatorUpdateListener updateListener;
    private android.animation.ValueAnimator anim;

    // kind: 0 x, 1 y, 2 translationX, 3 translationY, 4 scaleX, 5 scaleY, 6 alpha,
    // 7 rotation, 8 rotationX, 9 rotationY, 10 translationZ, 11 z
    private static final class Prop {
        final int kind; final float value; final boolean by;
        Prop(int k, float v, boolean b) { kind = k; value = v; by = b; }
    }
    private final ArrayList<Prop> props = new ArrayList<>();

    public ViewPropertyAnimator() { this.target = null; }
    public ViewPropertyAnimator(View target) { this.target = target; }

    private ViewPropertyAnimator add(int kind, float v, boolean by) { props.add(new Prop(kind, v, by)); return this; }
    public ViewPropertyAnimator x(float v)               { return add(0, v, false); }
    public ViewPropertyAnimator y(float v)               { return add(1, v, false); }
    public ViewPropertyAnimator xBy(float v)             { return add(0, v, true); }
    public ViewPropertyAnimator yBy(float v)             { return add(1, v, true); }
    public ViewPropertyAnimator translationX(float v)    { return add(2, v, false); }
    public ViewPropertyAnimator translationY(float v)    { return add(3, v, false); }
    public ViewPropertyAnimator translationXBy(float v)  { return add(2, v, true); }
    public ViewPropertyAnimator translationYBy(float v)  { return add(3, v, true); }
    public ViewPropertyAnimator scaleX(float v)          { return add(4, v, false); }
    public ViewPropertyAnimator scaleY(float v)          { return add(5, v, false); }
    public ViewPropertyAnimator scaleXBy(float v)        { return add(4, v, true); }
    public ViewPropertyAnimator scaleYBy(float v)        { return add(5, v, true); }
    public ViewPropertyAnimator alpha(float v)           { return add(6, v, false); }
    public ViewPropertyAnimator alphaBy(float v)         { return add(6, v, true); }
    public ViewPropertyAnimator rotation(float v)        { return add(7, v, false); }
    public ViewPropertyAnimator rotationBy(float v)      { return add(7, v, true); }
    public ViewPropertyAnimator rotationX(float v)       { return add(8, v, false); }
    public ViewPropertyAnimator rotationXBy(float v)     { return add(8, v, true); }
    public ViewPropertyAnimator rotationY(float v)       { return add(9, v, false); }
    public ViewPropertyAnimator rotationYBy(float v)     { return add(9, v, true); }
    public ViewPropertyAnimator translationZ(float v)    { return add(10, v, false); }
    public ViewPropertyAnimator translationZBy(float v)  { return add(10, v, true); }
    public ViewPropertyAnimator z(float v)               { return add(11, v, false); }
    public ViewPropertyAnimator zBy(float v)              { return add(11, v, true); }
    public ViewPropertyAnimator setDuration(long ms)     { durationMs = Math.max(0, ms); return this; }
    public ViewPropertyAnimator setStartDelay(long ms)   { startDelayMs = Math.max(0, ms); return this; }
    public ViewPropertyAnimator setInterpolator(android.animation.TimeInterpolator i) { interpolator = i; return this; }
    public android.animation.TimeInterpolator getInterpolator() { return interpolator; }
    public ViewPropertyAnimator setListener(android.animation.Animator.AnimatorListener l) { listener = l; return this; }
    public ViewPropertyAnimator setUpdateListener(android.animation.ValueAnimator.AnimatorUpdateListener l) { updateListener = l; return this; }
    public ViewPropertyAnimator withStartAction(Runnable r) { startAction = r; return this; }
    public ViewPropertyAnimator withEndAction(Runnable r)   { endAction = r; return this; }
    // AOSP: sets LAYER_TYPE_HARDWARE for the animation's duration and restores
    // it afterward, so translucency/rotation composite cleanly through a GL
    // layer. Mac.apk already composites every View through Metal/ANGLE
    // regardless of layer type, so there is no rendering-cost model here for
    // the hint to change -- an honest no-op keeps the fluent chain working
    // without inventing behavior Mac.apk can't back up.
    public ViewPropertyAnimator withLayer() { return this; }

    private float getProp(int kind) {
        switch (kind) {
            case 0: return target.getX();
            case 1: return target.getY();
            case 2: return target.getTranslationX();
            case 3: return target.getTranslationY();
            case 4: return target.getScaleX();
            case 5: return target.getScaleY();
            case 6: return target.getAlpha();
            case 7: return target.getRotation();
            case 8: return target.getRotationX();
            case 9: return target.getRotationY();
            case 10: return target.getTranslationZ();
            case 11: return target.getZ();
            default: return 0f;
        }
    }
    private void setProp(int kind, float v) {
        switch (kind) {
            case 0: target.setX(v); break;
            case 1: target.setY(v); break;
            case 2: target.setTranslationX(v); break;
            case 3: target.setTranslationY(v); break;
            case 4: target.setScaleX(v); break;
            case 5: target.setScaleY(v); break;
            case 6: target.setAlpha(v); break;
            case 7: target.setRotation(v); break;
            case 8: target.setRotationX(v); break;
            case 9: target.setRotationY(v); break;
            case 10: target.setTranslationZ(v); break;
            case 11: target.setZ(v); break;
            default: break;
        }
    }

    public void start() {
        final int n = props.size();
        final float[] starts = new float[n];
        final float[] ends = new float[n];
        if (target != null) {
            for (int i = 0; i < n; i++) {
                Prop p = props.get(i);
                float s = getProp(p.kind);
                starts[i] = s;
                ends[i] = p.by ? s + p.value : p.value;
            }
        }
        if (target == null || !android.os.Looper.MACAPK_REAL_LOOPER) {
            // Shipped path: snap to the final values and run actions immediately.
            if (startAction != null) try { startAction.run(); } catch (Throwable ignored) {}
            for (int i = 0; i < n; i++) setProp(props.get(i).kind, ends[i]);
            if (endAction != null) try { endAction.run(); } catch (Throwable ignored) {}
            // setListener/setUpdateListener: AOSP fires the lifecycle callbacks
            // even for an instantaneous/duration-0 completion. Reuse
            // ValueAnimator's own defensive per-listener notify loop (Animator
            // .start() -> onAnimationStart then onAnimationEnd, synchronously,
            // when MACAPK_REAL_LOOPER is off) via a throwaway instance instead
            // of hand-rolling the same dispatch here. Kept local (never
            // assigned to the `anim` field) so cancel() keeps its existing
            // no-op behavior on this path, and gated on listener/updateListener
            // being non-null so every pre-existing call site (which could not
            // have set either, since the methods did not exist before) takes
            // the exact same path as before, unchanged.
            if (listener != null || updateListener != null) {
                try {
                    android.animation.ValueAnimator snap = android.animation.ValueAnimator.ofFloat(0f, 1f);
                    snap.setDuration(durationMs);
                    if (updateListener != null) snap.addUpdateListener(updateListener);
                    if (listener != null) snap.addListener(listener);
                    snap.start();
                } catch (Throwable ignored) {}
            }
            return;
        }
        if (startAction != null) try { startAction.run(); } catch (Throwable ignored) {}
        anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(durationMs);
        anim.setStartDelay(startDelayMs);
        if (interpolator != null) {
            anim.setInterpolator(interpolator);
        }
        anim.addUpdateListener(a -> {
            float f = a.getAnimatedFraction();
            for (int i = 0; i < n; i++) setProp(props.get(i).kind, starts[i] + (ends[i] - starts[i]) * f);
        });
        if (updateListener != null) anim.addUpdateListener(updateListener);
        anim.addListener(new android.animation.Animator.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                for (int i = 0; i < n; i++) setProp(props.get(i).kind, ends[i]);
                if (endAction != null) try { endAction.run(); } catch (Throwable ignored) {}
            }
        });
        if (listener != null) anim.addListener(listener);
        anim.start();
    }

    public void cancel() { if (anim != null) anim.cancel(); }
    public long getDuration() { return durationMs; }
    public long getStartDelay() { return startDelayMs; }
}
