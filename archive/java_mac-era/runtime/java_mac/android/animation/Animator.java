// android.animation.Animator -- base class of the AOSP property animator
// hierarchy (ObjectAnimator, ValueAnimator, AnimatorSet). Most modern
// android UI libraries (Material, AppCompat, RecyclerView's ItemAnimator)
// instantiate these for transitions. Without the class on the classpath,
// the JVM throws NoClassDefFoundError at first reference -- enough to
// abort Activity inflation. We provide a no-op stub: animations don't
// run, but the callsites can construct, start, and cancel without
// crashing. The UI just snaps to its final state.
//
// AOSP source mirror: cs.android.com/.../frameworks/base/core/java/
// android/animation/Animator.java. Signatures track the API 33 surface
// since modern androidx libraries assume that level.
package android.animation;

import java.util.ArrayList;

public abstract class Animator implements Cloneable {

    /**
     * AOSP Animator.java:495-517. Tell the animator to gather the START values
     * for its animation from whatever it is animating -- an ObjectAnimator reads
     * them off its target, an AnimatorSet fans the call out to its children, and
     * a plain ValueAnimator has no target so it ignores the request. The base
     * implementation is EMPTY on AOSP too; the overrides carry the behaviour.
     *
     * LayoutTransition and the Transition framework call these around a scene
     * change, so a missing method here is a NoSuchMethodError in the middle of
     * an animation setup rather than at construction.
     */
    public void setupStartValues() {
    }

    /** AOSP Animator.java:507-517 -- the end-value counterpart of
     *  setupStartValues(). Empty on the base class. */
    public void setupEndValues() {
    }
    public static final long DURATION_INFINITE = -1L;

    private ArrayList<AnimatorListener> listeners;
    private ArrayList<AnimatorPauseListener> pauseListeners;
    protected long startDelay;
    protected long duration;
    // Package-visible: ValueAnimator's pause()/resume() overrides read it.
    boolean paused;
    private boolean started;

    public void start() {
        started = true;
        // AOSP ValueAnimator.start(boolean) sets `mPaused = false` as part of
        // starting a run, so a paused-then-restarted animator is not born
        // frozen. Now that the pause flag actually gates the frame pump
        // (ValueAnimator.doAnimationFrame), forgetting this would be a hang.
        paused = false;
        if (listeners != null) {
            for (AnimatorListener l : new ArrayList<>(listeners)) l.onAnimationStart(this);
            // Snap-to-finished: fire end immediately since we don't animate.
            for (AnimatorListener l : new ArrayList<>(listeners)) l.onAnimationEnd(this);
        }
        started = false;
    }
    public void cancel() {
        // AOSP Animator.cancel() javadoc (frameworks/base/core/java/android/
        // animation/Animator.java): "causes the animation to stop in its tracks,
        // sending an onAnimationCancel() to its listeners, followed by an
        // onAnimationEnd() message." The onAnimationEnd was missing here, so any
        // caller relying on cancel() completing the AOSP-documented pair (a very
        // common idiom: reveal-a-view / enable-a-button / start-the-next-screen
        // work sitting in onAnimationEnd) never ran when a listener depended on
        // this base no-op path being reached (e.g. via ValueAnimator.cancel()'s
        // super.cancel() fallback on the MACAPK_REAL_LOOPER=0 escape hatch).
        if (listeners != null) {
            for (AnimatorListener l : new ArrayList<>(listeners)) {
                try { l.onAnimationCancel(this); } catch (Throwable ignored) {}
            }
            for (AnimatorListener l : new ArrayList<>(listeners)) {
                try { l.onAnimationEnd(this); } catch (Throwable ignored) {}
            }
        }
        started = false;
    }
    public void end() {
        if (listeners != null) {
            // v1.0.98: defensively iterate via Object so a stray non-
            // AnimatorListener in the list doesn't CCE the entire end()
            // and abort the binder chain. The APK occasionally adds
            // listeners via raw types under different classloader views.
            for (Object o : new ArrayList<>(listeners)) {
                try {
                    if (o instanceof AnimatorListener al) al.onAnimationEnd(this);
                } catch (Throwable ignored) {}
            }
        }
        started = false;
    }
    /** AOSP Animator.pause():
     *      if (isStarted() && !mPaused) { mPaused = true; notifyPauseListeners(ON_PAUSE); }
     *  The isStarted() guard is AOSP's, and it is why "Pausing an animator
     *  before it is started has no effect" is the documented contract. The
     *  !mPaused guard is why a second pause() does not re-notify. */
    public void pause() {
        if (isStarted() && !paused) {
            paused = true;
            if (pauseListeners != null) {
                for (AnimatorPauseListener l : new ArrayList<>(pauseListeners)) {
                    try { l.onAnimationPause(this); } catch (Throwable ignored) {}
                }
            }
        }
    }

    /** AOSP Animator.resume():
     *      if (mPaused) { mPaused = false; notifyPauseListeners(ON_RESUME); } */
    public void resume() {
        if (paused) {
            paused = false;
            if (pauseListeners != null) {
                for (AnimatorPauseListener l : new ArrayList<>(pauseListeners)) {
                    try { l.onAnimationResume(this); } catch (Throwable ignored) {}
                }
            }
        }
    }

    public boolean isStarted() { return started; }
    public boolean isRunning() { return started; }
    public boolean isPaused()  { return paused; }

    public long getStartDelay() { return startDelay; }
    public void setStartDelay(long d) { this.startDelay = d; }
    public long getDuration() { return duration; }
    public Animator setDuration(long duration) { this.duration = duration; return this; }
    // AOSP Animator.java (android11-release) verbatim:
    //   long duration = getDuration();
    //   if (duration == DURATION_INFINITE) { return DURATION_INFINITE; }
    //   else { return getStartDelay() + duration; }
    // Reading through the VIRTUAL getters is load-bearing: ValueAnimator keeps
    // its own durationMs/startDelayMs storage, so the old direct field read
    // (startDelay + duration) answered 0 for EVERY ValueAnimator no matter
    // what setDuration()/setStartDelay() had been called with.
    public long getTotalDuration() {
        long dur = getDuration();
        if (dur == DURATION_INFINITE) return DURATION_INFINITE;
        return getStartDelay() + dur;
    }

    public void setInterpolator(android.animation.TimeInterpolator value) {}
    public android.animation.TimeInterpolator getInterpolator() { return null; }

    public void setTarget(Object target) {}

    public void addListener(AnimatorListener listener) {
        if (listener == null) return;
        if (listeners == null) listeners = new ArrayList<>();
        listeners.add(listener);
    }
    public void removeListener(AnimatorListener listener) {
        if (listeners != null) listeners.remove(listener);
    }
    public ArrayList<AnimatorListener> getListeners() { return listeners; }
    public void removeAllListeners() { if (listeners != null) listeners.clear(); }

    // Tier 3 M7: lifecycle-notify helpers for the real (Choreographer-driven)
    // ValueAnimator -- fire each listener defensively (a stray non-listener or a
    // throwing callback must not abort the animation pump). Used instead of the
    // base snap-to-end start()/end() when MACAPK_REAL_LOOPER drives real frames.
    protected void notifyStartListeners() {
        if (listeners == null) return;
        for (Object o : new ArrayList<>(listeners)) {
            try { if (o instanceof AnimatorListener al) al.onAnimationStart(this); } catch (Throwable ignored) {}
        }
    }
    protected void notifyEndListeners() {
        if (listeners == null) return;
        for (Object o : new ArrayList<>(listeners)) {
            try { if (o instanceof AnimatorListener al) al.onAnimationEnd(this); } catch (Throwable ignored) {}
        }
    }
    protected void notifyCancelListeners() {
        if (listeners == null) return;
        for (Object o : new ArrayList<>(listeners)) {
            try { if (o instanceof AnimatorListener al) al.onAnimationCancel(this); } catch (Throwable ignored) {}
        }
    }
    protected void notifyRepeatListeners() {
        if (listeners == null) return;
        for (Object o : new ArrayList<>(listeners)) {
            try { if (o instanceof AnimatorListener al) al.onAnimationRepeat(this); } catch (Throwable ignored) {}
        }
    }

    public void addPauseListener(AnimatorPauseListener listener) {
        if (listener == null) return;
        if (pauseListeners == null) pauseListeners = new ArrayList<>();
        pauseListeners.add(listener);
    }
    public void removePauseListener(AnimatorPauseListener listener) {
        if (pauseListeners != null) pauseListeners.remove(listener);
    }

    @Override public Animator clone() {
        try {
            Animator copy = (Animator) super.clone();
            copy.listeners = null;
            copy.pauseListeners = null;
            return copy;
        } catch (CloneNotSupportedException e) { throw new AssertionError(e); }
    }

    public interface AnimatorListener {
        void onAnimationStart(Animator animation);
        void onAnimationEnd(Animator animation);
        void onAnimationCancel(Animator animation);
        void onAnimationRepeat(Animator animation);
        default void onAnimationStart(Animator animation, boolean isReverse) { onAnimationStart(animation); }
        default void onAnimationEnd(Animator animation, boolean isReverse)   { onAnimationEnd(animation); }
    }
    public interface AnimatorPauseListener {
        void onAnimationPause(Animator animation);
        void onAnimationResume(Animator animation);
    }

    /** Adapter providing empty implementations of the four required
     *  AnimatorListener methods so callers can override only what they
     *  care about. Used heavily by Material widgets. */
    public abstract static class AnimatorListenerAdapter implements AnimatorListener, AnimatorPauseListener {
        @Override public void onAnimationStart(Animator animation) {}
        @Override public void onAnimationEnd(Animator animation) {}
        @Override public void onAnimationCancel(Animator animation) {}
        @Override public void onAnimationRepeat(Animator animation) {}
        @Override public void onAnimationPause(Animator animation) {}
        @Override public void onAnimationResume(Animator animation) {}
    }
}
