// android.animation.AnimatorSet -- composes multiple Animator instances to
// play in sequence, together, or in an arbitrary dependency order. Material
// widgets / MotionLayout / androidx transitions use this for state changes.
//
// Tier 3 M17: real dependency-graph sequencing. The previous stub added every
// child to one list and start() started them ALL AT ONCE, so playSequentially
// behaved identically to playTogether (and Builder.with/before/after ignored
// ordering). Now playSequentially/play().before()/after() build a predecessor
// graph and start() runs each child only once all its predecessors have ENDED
// (chained via the M7 Choreographer-driven ValueAnimator's onAnimationEnd).
//
// Gated on MACAPK_REAL_LOOPER: flag-OFF keeps the legacy "start all at once"
// behavior (children snap to end there anyway, so the end state is identical),
// so the shipped build is byte-for-byte unchanged; the real sequencing runs
// only on the experimental real-loop path where the child animators actually
// tick over time.
package android.animation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AnimatorSet extends Animator {
    private final ArrayList<Animator> nodes = new ArrayList<>();
    // node -> the set of animators that must END before this node may start.
    private final Map<Animator, Set<Animator>> deps = new HashMap<>();
    private boolean durationSet = false;

    private Animator ensureNode(Animator a) {
        if (a != null && !deps.containsKey(a)) {
            nodes.add(a);
            deps.put(a, new LinkedHashSet<>());
            if (durationSet) a.setDuration(getDuration());
        }
        return a;
    }

    public void playTogether(Animator... items) {
        if (items != null) for (Animator a : items) ensureNode(a);   // no deps -> all start together
    }
    public void playTogether(Collection<Animator> items) {
        if (items != null) for (Animator a : items) ensureNode(a);
    }
    public void playSequentially(Animator... items) {
        if (items != null) playSequentially(Arrays.asList(items));
    }
    public void playSequentially(java.util.List<Animator> items) {
        if (items == null) return;
        Animator prev = null;
        for (Animator a : items) {
            ensureNode(a);
            if (prev != null && a != null) deps.get(a).add(prev);   // a waits for prev to end
            prev = a;
        }
    }
    public Builder play(Animator anim) { ensureNode(anim); return new Builder(anim); }
    public ArrayList<Animator> getChildAnimations() { return nodes; }

    @Override public AnimatorSet setDuration(long duration) {
        super.setDuration(duration);
        if (duration >= 0) {
            durationSet = true;
            // AOSP: AnimatorSet.setDuration overrides every child's duration.
            for (Animator a : nodes) if (a != null) a.setDuration(duration);
        }
        return this;
    }
    @Override public void setStartDelay(long delay) { super.setStartDelay(delay); }

    /** AOSP AnimatorSet.getTotalDuration() (android11-release):
     *      updateAnimatorsDuration();
     *      createDependencyGraph();
     *      return mTotalDuration;
     *  updateAnimatorsDuration() re-applies a set-level duration override to
     *  every child and sets the root delay node's length to the SET's own
     *  startDelay (mDelayAnim.setDuration(mStartDelay)). The graph gives every
     *  parentless node the root as parent; updatePlayTime then computes each
     *  child's start as its LATEST parent's end ("if (parent.mEndTime >=
     *  child.mStartTime) child.mStartTime = parent.mEndTime") and its end as
     *  start + its own getTotalDuration(), with DURATION_INFINITE poisoning
     *  the node and every successor. mTotalDuration is the LAST event time,
     *  and sortAnimationEvents deliberately sorts DURATION_INFINITE events
     *  last -- i.e. the max end over the DAG, INFINITE winning.
     *
     *  This is the ONE public answer that distinguishes playTogether from
     *  playSequentially (three 100ms children: 100 vs 300); the inherited
     *  base impl answered 0 for both. */
    @Override public long getTotalDuration() {
        if (durationSet) {
            for (Animator a : nodes) if (a != null) a.setDuration(getDuration());
        }
        long total = getStartDelay();
        Map<Animator, Long> memo = new HashMap<>();
        for (Animator n : nodes) {
            long e = macapkNodeEnd(n, memo, new LinkedHashSet<>());
            if (e == DURATION_INFINITE) return DURATION_INFINITE;
            if (e > total) total = e;
        }
        return total;
    }
    /** End time of `n` in the dependency graph: max(set startDelay, latest
     *  predecessor end) + n.getTotalDuration(). A dependency cycle answers
     *  DURATION_INFINITE, matching updatePlayTime's cycle branch (every node
     *  of a cycle gets mStartTime = DURATION_INFINITE). */
    private long macapkNodeEnd(Animator n, Map<Animator, Long> memo, Set<Animator> visiting) {
        Long m = memo.get(n);
        if (m != null) return m;
        if (!visiting.add(n)) return DURATION_INFINITE;
        long start = getStartDelay();
        for (Animator p : deps.get(n)) {
            if (!deps.containsKey(p)) continue;   // predecessor not in this set
            long pe = macapkNodeEnd(p, memo, visiting);
            if (pe == DURATION_INFINITE) { memo.put(n, DURATION_INFINITE); return DURATION_INFINITE; }
            if (pe > start) start = pe;
        }
        visiting.remove(n);
        long own = n.getTotalDuration();
        long e = (own == DURATION_INFINITE) ? DURATION_INFINITE : start + own;
        memo.put(n, e);
        return e;
    }

    private int remaining;   // children not yet ended (real-loop path)

    // Wall clock of the last start(), for getCurrentPlayTime(). -1 means "never
    // started", which AOSP reports as a play time of 0.
    private long mStartTimeMillis = -1;

    /**
     * AOSP AnimatorSet.getCurrentPlayTime() (AnimatorSet.java:1122-1137): 0 when
     * the set has not started or is still inside its start delay, otherwise the
     * elapsed play time measured from the first frame, with the start delay
     * subtracted and the global animator duration scale divided out.
     *
     * AOSP measures from mLastFrameTime, i.e. the last frame the set actually
     * ticked; we measure from the wall clock at start(), because our set drives
     * its children rather than ticking a frame of its own. For a running set the
     * two agree to within one frame; for a set whose children have all ended,
     * AOSP freezes at the last frame while this keeps counting -- so read this
     * as a play time, not as a completion test (use isRunning() for that).
     */
    public long getCurrentPlayTime() {
        if (mStartTimeMillis < 0) {
            // Not yet started or during start delay.
            return 0;
        }
        long elapsed = android.os.SystemClock.uptimeMillis() - mStartTimeMillis - getStartDelay();
        return elapsed < 0 ? 0 : elapsed;
    }

    /**
     * AOSP AnimatorSet.setupStartValues()/setupEndValues() (AnimatorSet.java:
     * 643-663) fan the call out to every child node. AOSP skips its internal
     * mRootNode (a zero-length delay animator that does not exist here), so
     * every entry in `nodes` is a real child.
     */
    @Override public void setupStartValues() {
        for (Animator a : new ArrayList<>(nodes)) {
            if (a != null) {
                try { a.setupStartValues(); } catch (Throwable ignored) {}
            }
        }
    }

    @Override public void setupEndValues() {
        for (Animator a : new ArrayList<>(nodes)) {
            if (a != null) {
                try { a.setupEndValues(); } catch (Throwable ignored) {}
            }
        }
    }

    @Override public void start() {
        mStartTimeMillis = android.os.SystemClock.uptimeMillis();
        if (!android.os.Looper.MACAPK_REAL_LOOPER || nodes.isEmpty()) {
            // Legacy / flag-off: start every child (they snap to end). The end
            // state is identical to the graph walk, so flag-off is unchanged.
            for (Animator a : new ArrayList<>(nodes)) if (a != null) {
                try { a.start(); } catch (Throwable ignored) {}
            }
            super.start();
            return;
        }
        // Real-loop path: dependency-graph sequencing.
        if (durationSet) for (Animator a : nodes) if (a != null) a.setDuration(getDuration());
        notifyStartListeners();
        // Invert deps -> successors, and count each node's unmet predecessors.
        final Map<Animator, Integer> unmet = new HashMap<>();
        final Map<Animator, List<Animator>> succ = new HashMap<>();
        for (Animator n : nodes) { unmet.put(n, 0); succ.put(n, new ArrayList<>()); }
        for (Animator n : nodes) {
            for (Animator p : deps.get(n)) {
                if (!unmet.containsKey(p)) continue;   // predecessor not in this set -> ignore
                unmet.put(n, unmet.get(n) + 1);
                succ.get(p).add(n);
            }
        }
        remaining = nodes.size();
        final AnimatorListener chain = new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator child) {
                child.removeListener(this);
                for (Animator s : succ.get(child)) {
                    int u = unmet.get(s) - 1;
                    unmet.put(s, u);
                    if (u == 0) startChild(s, this);
                }
                if (--remaining <= 0) AnimatorSet.this.notifyEndListeners();
            }
        };
        // Start every root (no unmet predecessors). Snapshot first so a
        // synchronous end (a child that completes inside start()) can't mutate
        // the iteration.
        final List<Animator> roots = new ArrayList<>();
        for (Animator n : nodes) if (unmet.get(n) == 0) roots.add(n);
        for (Animator n : roots) startChild(n, chain);
    }
    private void startChild(Animator a, AnimatorListener chain) {
        if (a == null) return;
        a.addListener(chain);
        try { a.start(); } catch (Throwable t) { /* end-listener still advances the chain */ }
    }
    @Override public void cancel() {
        // AOSP AnimatorSet.cancel() (frameworks/base/core/java/android/animation/
        // AnimatorSet.java): cancels every playing child, fires onAnimationCancel
        // to the SET's own listeners, then unconditionally calls its private
        // endAnimation(), which fires onAnimationEnd to the set's own listeners
        // too -- "if (isStarted()) { ...cancel children...; endAnimation(); }".
        // Verbatim from that file:
        //   private void endAnimation() {
        //       ...
        //       if (mListeners != null) {
        //           ...
        //           tmpListeners.get(i).onAnimationEnd(this, mReversing);
        //       }
        //       ...
        //   }
        // notifyEndListeners() was missing here, so a listener added directly to
        // an AnimatorSet (StateListAnimator's press/elevation AnimatorSets are a
        // mainstream Material Components example: setState() calls
        // runningAnimator.cancel() on every state change) never saw
        // onAnimationEnd after a cancel -- exactly the "animation that never
        // ends" failure shape: whatever cleanup a caller gated on the set's own
        // end callback (re-enable a button, clear an "isAnimating" flag) never ran.
        for (Animator a : new ArrayList<>(nodes)) if (a != null) {
            try { a.cancel(); } catch (Throwable ignored) {}
        }
        notifyCancelListeners();
        notifyEndListeners();
    }
    @Override public void end() {
        for (Animator a : new ArrayList<>(nodes)) if (a != null) {
            try { a.end(); } catch (Throwable ignored) {}
        }
        notifyEndListeners();
    }

    /** AOSP AnimatorSet.Builder: play(a).with(b)/before(b)/after(b) wires the
     *  dependency graph relative to the anchor `a` passed to play(). */
    // AOSP AnimatorSet.Builder (core/java/android/animation/AnimatorSet.java,
    // android16-release) is NOT final. Ours was, so an app subclassing it failed
    // at resolution -- and `-Xverify:none` (jvm_loader.cpp:163) does NOT suppress
    // a final-superclass violation, so it is a hard failure, not a silent one.
    // Deferred from the v1.0.1040 final-modifier sweep because this file was
    // owned by another lane at the time; closed here.
    public class Builder {
        private final Animator anchor;
        Builder(Animator a) { anchor = a; }
        public Builder with(Animator anim) {
            // b plays together with the anchor: same predecessors.
            if (anim != null && anchor != null) {
                ensureNode(anim);
                deps.get(anim).addAll(deps.get(anchor));
            }
            return this;
        }
        public Builder before(Animator anim) {
            // anchor plays before b: b waits for the anchor.
            if (anim != null && anchor != null) { ensureNode(anim); deps.get(anim).add(anchor); }
            return this;
        }
        public Builder after(Animator anim) {
            // anchor plays after b: the anchor waits for b.
            if (anim != null && anchor != null) { ensureNode(anim); deps.get(anchor).add(anim); }
            return this;
        }
        public Builder after(long delay) {
            if (anchor != null) anchor.setStartDelay(anchor.getStartDelay() + delay);
            return this;
        }
    }

    public void setCurrentPlayTime(long playTime) { }

    public void reverse() { }
}


