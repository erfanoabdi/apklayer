// android.animation.LayoutTransition -- the object a ViewGroup consults to
// animate children appearing, disappearing, and repositioning.
// AOSP:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/animation/LayoutTransition.java
//
// This used to be a one-line auto-generated empty class, which meant every one
// of AOSP's ~25 public methods threw NoSuchMethodError on first call and the
// nested TransitionListener interface did not exist at all. That second part is
// the sharper failure: implementing an interface is resolved when the
// implementing class is DEFINED, so an app class carrying
// `implements LayoutTransition.TransitionListener` failed to load entirely.
//
// What is implemented here for real: the whole configuration surface (per-type
// durations, start delays, staggers, interpolators and animators, the enabled
// type set, the listener list), the AOSP default animators, and the
// APPEARING / DISAPPEARING driver hooks including their listener callbacks and
// running-animation bookkeeping (isRunning / isChangingLayout / cancel).
//
// What is NOT implemented, deliberately and visibly: the CHANGE_APPEARING /
// CHANGE_DISAPPEARING / CHANGING bounds animations. AOSP builds those in
// setupChangeAnimation() out of ViewGroup internals Mac.apk does not have --
// ViewGroup.requestTransitionStart() and ViewGroup.suppressLayout(), plus a
// live View.addOnLayoutChangeListener (real since MACAPK_LAYOUT_CHANGE_LISTENERS, 2026-09-26) and a ViewTreeObserver
// pre-draw pass. Faking the result would be worse than not running it: AOSP
// only fires the CHANGE_* listener callbacks from inside that layout-change
// listener (LayoutTransition.java:970), so "no child's bounds changed, so no
// CHANGE transition and no callbacks" is a legitimate AOSP outcome and is the
// one we produce. See the note in runChangeTransition below.
//
// The remaining gap is on the ViewGroup side, not here: ViewGroup
// .setLayoutTransition() must store the transition (today it discards it, so
// getLayoutTransition() returns null even right after a set) and call the
// addChild / removeChild / showChild / hideChild / layoutChange hooks below,
// and LayoutInflater must attach a default LayoutTransition when it sees
// android:animateLayoutChanges="true".
package android.animation;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class LayoutTransition {

    /** A flag indicating the animation that runs on those items that are
     *  changing due to a new item appearing in the container. */
    public static final int CHANGE_APPEARING = 0;
    /** A flag indicating the animation that runs on those items that are
     *  changing due to an item disappearing from the container. */
    public static final int CHANGE_DISAPPEARING = 1;
    /** A flag indicating the animation that runs on items that are appearing
     *  in the container. */
    public static final int APPEARING = 2;
    /** A flag indicating the animation that runs on items that are
     *  disappearing from the container. */
    public static final int DISAPPEARING = 3;
    /** A flag indicating the animation that runs on items that are changing
     *  due to a layout change not caused by items being added or removed. */
    public static final int CHANGING = 4;

    // Bit fields backing mTransitionTypes.
    private static final int FLAG_APPEARING           = 0x01;
    private static final int FLAG_DISAPPEARING        = 0x02;
    private static final int FLAG_CHANGE_APPEARING    = 0x04;
    private static final int FLAG_CHANGE_DISAPPEARING = 0x08;
    private static final int FLAG_CHANGING            = 0x10;

    // The animations currently in use for each transition type. Set to the
    // defaults below unless the app calls setAnimator().
    private Animator mDisappearingAnim = null;
    private Animator mAppearingAnim = null;
    private Animator mChangingAppearingAnim = null;
    private Animator mChangingDisappearingAnim = null;
    private Animator mChangingAnim = null;

    // The shared default animations, built once on first construction.
    private static ObjectAnimator defaultChange;
    private static ObjectAnimator defaultChangeIn;
    private static ObjectAnimator defaultChangeOut;
    private static ObjectAnimator defaultFadeIn;
    private static ObjectAnimator defaultFadeOut;

    private static long DEFAULT_DURATION = 300;

    private long mChangingAppearingDuration = DEFAULT_DURATION;
    private long mChangingDisappearingDuration = DEFAULT_DURATION;
    private long mChangingDuration = DEFAULT_DURATION;
    private long mAppearingDuration = DEFAULT_DURATION;
    private long mDisappearingDuration = DEFAULT_DURATION;

    // The appearing item waits a full duration by default so the surrounding
    // items finish moving before it fades in; symmetrically the change-on-
    // disappear animation waits for the item to fade out first.
    private long mAppearingDelay = DEFAULT_DURATION;
    private long mDisappearingDelay = 0;
    private long mChangingAppearingDelay = 0;
    private long mChangingDisappearingDelay = DEFAULT_DURATION;
    private long mChangingDelay = 0;

    private long mChangingAppearingStagger = 0;
    private long mChangingDisappearingStagger = 0;
    private long mChangingStagger = 0;

    // Stateless, so shared across instances. Identity comparison against these
    // is how AOSP decides whether the app overrode an interpolator.
    private static TimeInterpolator ACCEL_DECEL_INTERPOLATOR =
            new AccelerateDecelerateInterpolator();
    private static TimeInterpolator DECEL_INTERPOLATOR = new DecelerateInterpolator();
    private static TimeInterpolator sAppearingInterpolator = ACCEL_DECEL_INTERPOLATOR;
    private static TimeInterpolator sDisappearingInterpolator = ACCEL_DECEL_INTERPOLATOR;
    private static TimeInterpolator sChangingAppearingInterpolator = DECEL_INTERPOLATOR;
    private static TimeInterpolator sChangingDisappearingInterpolator = DECEL_INTERPOLATOR;
    private static TimeInterpolator sChangingInterpolator = DECEL_INTERPOLATOR;

    private TimeInterpolator mAppearingInterpolator = sAppearingInterpolator;
    private TimeInterpolator mDisappearingInterpolator = sDisappearingInterpolator;
    private TimeInterpolator mChangingAppearingInterpolator = sChangingAppearingInterpolator;
    private TimeInterpolator mChangingDisappearingInterpolator = sChangingDisappearingInterpolator;
    private TimeInterpolator mChangingInterpolator = sChangingInterpolator;

    // Running animations, keyed by the view they act on. These back
    // isRunning() / isChangingLayout() / cancel(), which apps poll to decide
    // whether it is safe to mutate the container. LinkedHashMap because AOSP
    // depends on insertion order when it walks them.
    private final LinkedHashMap<View, Animator> currentChangingAnimations =
            new LinkedHashMap<View, Animator>();
    private final LinkedHashMap<View, Animator> currentAppearingAnimations =
            new LinkedHashMap<View, Animator>();
    private final LinkedHashMap<View, Animator> currentDisappearingAnimations =
            new LinkedHashMap<View, Animator>();

    /** Default enabled set: appearing / disappearing and the change animations
     *  tied to them, but NOT CHANGING. */
    private int mTransitionTypes = FLAG_CHANGE_APPEARING | FLAG_CHANGE_DISAPPEARING
            | FLAG_APPEARING | FLAG_DISAPPEARING;

    private ArrayList<TransitionListener> mListeners;

    private boolean mAnimateParentHierarchy = true;

    /**
     * Constructs a LayoutTransition wired with the default animation for each
     * transition type: an alpha fade for APPEARING / DISAPPEARING and a
     * left/top/right/bottom/scrollX/scrollY bounds animator for the CHANGE_*
     * types (the property values are placeholders; AOSP fills in the real ones
     * per child at transition time).
     */
    public LayoutTransition() {
        if (defaultChangeIn == null) {
            // "left" etc. are placeholders; real properties/values are supplied
            // when a change transition is actually set up.
            PropertyValuesHolder pvhLeft = PropertyValuesHolder.ofInt("left", 0, 1);
            PropertyValuesHolder pvhTop = PropertyValuesHolder.ofInt("top", 0, 1);
            PropertyValuesHolder pvhRight = PropertyValuesHolder.ofInt("right", 0, 1);
            PropertyValuesHolder pvhBottom = PropertyValuesHolder.ofInt("bottom", 0, 1);
            PropertyValuesHolder pvhScrollX = PropertyValuesHolder.ofInt("scrollX", 0, 1);
            PropertyValuesHolder pvhScrollY = PropertyValuesHolder.ofInt("scrollY", 0, 1);
            defaultChangeIn = ObjectAnimator.ofPropertyValuesHolder((Object) null,
                    pvhLeft, pvhTop, pvhRight, pvhBottom, pvhScrollX, pvhScrollY);
            defaultChangeIn.setDuration(DEFAULT_DURATION);
            defaultChangeIn.setStartDelay(mChangingAppearingDelay);
            defaultChangeIn.setInterpolator(mChangingAppearingInterpolator);
            defaultChangeOut = (ObjectAnimator) defaultChangeIn.clone();
            defaultChangeOut.setStartDelay(mChangingDisappearingDelay);
            defaultChangeOut.setInterpolator(mChangingDisappearingInterpolator);
            defaultChange = (ObjectAnimator) defaultChangeIn.clone();
            defaultChange.setStartDelay(mChangingDelay);
            defaultChange.setInterpolator(mChangingInterpolator);

            defaultFadeIn = ObjectAnimator.ofFloat(null, "alpha", 0f, 1f);
            defaultFadeIn.setDuration(DEFAULT_DURATION);
            defaultFadeIn.setStartDelay(mAppearingDelay);
            defaultFadeIn.setInterpolator(mAppearingInterpolator);
            defaultFadeOut = ObjectAnimator.ofFloat(null, "alpha", 1f, 0f);
            defaultFadeOut.setDuration(DEFAULT_DURATION);
            defaultFadeOut.setStartDelay(mDisappearingDelay);
            defaultFadeOut.setInterpolator(mDisappearingInterpolator);
        }
        mChangingAppearingAnim = defaultChangeIn;
        mChangingDisappearingAnim = defaultChangeOut;
        mChangingAnim = defaultChange;
        mAppearingAnim = defaultFadeIn;
        mDisappearingAnim = defaultFadeOut;
    }

    /** Sets the duration used by ALL of this transition's animations. */
    public void setDuration(long duration) {
        mChangingAppearingDuration = duration;
        mChangingDisappearingDuration = duration;
        mChangingDuration = duration;
        mAppearingDuration = duration;
        mDisappearingDuration = duration;
    }

    /** Enables one transition type. Note AOSP's asymmetry: enabling CHANGING
     *  also forces the parent hierarchy to animate, because a CHANGING
     *  transition on a wrap_content container is otherwise clipped. */
    public void enableTransitionType(int transitionType) {
        switch (transitionType) {
            case APPEARING:
                mTransitionTypes |= FLAG_APPEARING;
                break;
            case DISAPPEARING:
                mTransitionTypes |= FLAG_DISAPPEARING;
                break;
            case CHANGE_APPEARING:
                mTransitionTypes |= FLAG_CHANGE_APPEARING;
                break;
            case CHANGE_DISAPPEARING:
                mTransitionTypes |= FLAG_CHANGE_DISAPPEARING;
                break;
            case CHANGING:
                mTransitionTypes |= FLAG_CHANGING;
                break;
        }
    }

    /** Disables one transition type. */
    public void disableTransitionType(int transitionType) {
        switch (transitionType) {
            case APPEARING:
                mTransitionTypes &= ~FLAG_APPEARING;
                break;
            case DISAPPEARING:
                mTransitionTypes &= ~FLAG_DISAPPEARING;
                break;
            case CHANGE_APPEARING:
                mTransitionTypes &= ~FLAG_CHANGE_APPEARING;
                break;
            case CHANGE_DISAPPEARING:
                mTransitionTypes &= ~FLAG_CHANGE_DISAPPEARING;
                break;
            case CHANGING:
                mTransitionTypes &= ~FLAG_CHANGING;
                break;
        }
    }

    public boolean isTransitionTypeEnabled(int transitionType) {
        switch (transitionType) {
            case APPEARING:
                return (mTransitionTypes & FLAG_APPEARING) == FLAG_APPEARING;
            case DISAPPEARING:
                return (mTransitionTypes & FLAG_DISAPPEARING) == FLAG_DISAPPEARING;
            case CHANGE_APPEARING:
                return (mTransitionTypes & FLAG_CHANGE_APPEARING) == FLAG_CHANGE_APPEARING;
            case CHANGE_DISAPPEARING:
                return (mTransitionTypes & FLAG_CHANGE_DISAPPEARING) == FLAG_CHANGE_DISAPPEARING;
            case CHANGING:
                return (mTransitionTypes & FLAG_CHANGING) == FLAG_CHANGING;
        }
        return false;
    }

    public void setStartDelay(int transitionType, long delay) {
        switch (transitionType) {
            case CHANGE_APPEARING:
                mChangingAppearingDelay = delay;
                break;
            case CHANGE_DISAPPEARING:
                mChangingDisappearingDelay = delay;
                break;
            case CHANGING:
                mChangingDelay = delay;
                break;
            case APPEARING:
                mAppearingDelay = delay;
                break;
            case DISAPPEARING:
                mDisappearingDelay = delay;
                break;
        }
    }

    public long getStartDelay(int transitionType) {
        switch (transitionType) {
            case CHANGE_APPEARING:
                return mChangingAppearingDelay;
            case CHANGE_DISAPPEARING:
                return mChangingDisappearingDelay;
            case CHANGING:
                return mChangingDelay;
            case APPEARING:
                return mAppearingDelay;
            case DISAPPEARING:
                return mDisappearingDelay;
        }
        return 0;
    }

    public void setDuration(int transitionType, long duration) {
        switch (transitionType) {
            case CHANGE_APPEARING:
                mChangingAppearingDuration = duration;
                break;
            case CHANGE_DISAPPEARING:
                mChangingDisappearingDuration = duration;
                break;
            case CHANGING:
                mChangingDuration = duration;
                break;
            case APPEARING:
                mAppearingDuration = duration;
                break;
            case DISAPPEARING:
                mDisappearingDuration = duration;
                break;
        }
    }

    public long getDuration(int transitionType) {
        switch (transitionType) {
            case CHANGE_APPEARING:
                return mChangingAppearingDuration;
            case CHANGE_DISAPPEARING:
                return mChangingDisappearingDuration;
            case CHANGING:
                return mChangingDuration;
            case APPEARING:
                return mAppearingDuration;
            case DISAPPEARING:
                return mDisappearingDuration;
        }
        return 0;
    }

    /** The inter-animation delay applied to successive children of a CHANGE_*
     *  transition, so they cascade instead of moving in lockstep. */
    public void setStagger(int transitionType, long duration) {
        switch (transitionType) {
            case CHANGE_APPEARING:
                mChangingAppearingStagger = duration;
                break;
            case CHANGE_DISAPPEARING:
                mChangingDisappearingStagger = duration;
                break;
            case CHANGING:
                mChangingStagger = duration;
                break;
            // noop other cases: APPEARING and DISAPPEARING animate a single
            // view, so there is nothing to stagger.
        }
    }

    public long getStagger(int transitionType) {
        switch (transitionType) {
            case CHANGE_APPEARING:
                return mChangingAppearingStagger;
            case CHANGE_DISAPPEARING:
                return mChangingDisappearingStagger;
            case CHANGING:
                return mChangingStagger;
        }
        return 0;
    }

    public void setInterpolator(int transitionType, TimeInterpolator interpolator) {
        switch (transitionType) {
            case CHANGE_APPEARING:
                mChangingAppearingInterpolator = interpolator;
                break;
            case CHANGE_DISAPPEARING:
                mChangingDisappearingInterpolator = interpolator;
                break;
            case CHANGING:
                mChangingInterpolator = interpolator;
                break;
            case APPEARING:
                mAppearingInterpolator = interpolator;
                break;
            case DISAPPEARING:
                mDisappearingInterpolator = interpolator;
                break;
        }
    }

    public TimeInterpolator getInterpolator(int transitionType) {
        switch (transitionType) {
            case CHANGE_APPEARING:
                return mChangingAppearingInterpolator;
            case CHANGE_DISAPPEARING:
                return mChangingDisappearingInterpolator;
            case CHANGING:
                return mChangingInterpolator;
            case APPEARING:
                return mAppearingInterpolator;
            case DISAPPEARING:
                return mDisappearingInterpolator;
        }
        return null;
    }

    /** Replaces the animation for one transition type. A null animator
     *  disables that transition (AOSP checks for null and skips straight to
     *  the endTransition callback). */
    public void setAnimator(int transitionType, Animator animator) {
        switch (transitionType) {
            case CHANGE_APPEARING:
                mChangingAppearingAnim = animator;
                break;
            case CHANGE_DISAPPEARING:
                mChangingDisappearingAnim = animator;
                break;
            case CHANGING:
                mChangingAnim = animator;
                break;
            case APPEARING:
                mAppearingAnim = animator;
                break;
            case DISAPPEARING:
                mDisappearingAnim = animator;
                break;
        }
    }

    public Animator getAnimator(int transitionType) {
        switch (transitionType) {
            case CHANGE_APPEARING:
                return mChangingAppearingAnim;
            case CHANGE_DISAPPEARING:
                return mChangingDisappearingAnim;
            case CHANGING:
                return mChangingAnim;
            case APPEARING:
                return mAppearingAnim;
            case DISAPPEARING:
                return mDisappearingAnim;
        }
        return null;
    }

    /** Whether change animations also animate the parent hierarchy. AOSP
     *  defaults this to true to stop wrap_content ancestors snapping to their
     *  end size at transition start. */
    public void setAnimateParentHierarchy(boolean animateParentHierarchy) {
        mAnimateParentHierarchy = animateParentHierarchy;
    }

    /**
     * Sets up and runs the CHANGE_APPEARING / CHANGE_DISAPPEARING / CHANGING
     * bounds animations.
     *
     * NOT IMPLEMENTED, on purpose. AOSP's setupChangeAnimation() works by
     * (a) attaching a View.OnLayoutChangeListener to every child, (b) asking
     * ViewGroup to defer the transition start via requestTransitionStart(),
     * (c) calling ViewGroup.suppressLayout() while the bounds animate, and
     * (d) cleaning the listeners up from a ViewTreeObserver pre-draw pass.
     * Mac.apk's View.addOnLayoutChangeListener fires since
     * MACAPK_LAYOUT_CHANGE_LISTENERS (2026-09-26), but ViewGroup has neither
     * requestTransitionStart nor suppressLayout, so there is still no way to
     * hold layout still while animating a bounds change.
     *
     * Doing nothing here is the AOSP-consistent outcome rather than a lie:
     * AOSP fires the CHANGE_* startTransition/endTransition callbacks only
     * from inside that layout-change listener, so a container in which no
     * child's bounds actually changed runs no CHANGE transition and delivers
     * no callbacks either. Wiring this up properly requires the ViewGroup-side
     * support above to land first.
     */
    private void runChangeTransition(ViewGroup parent, View newView, int changeReason) {
        // Intentionally empty. See the javadoc above.
    }

    /** Starts the animations queued for a CHANGING transition. */
    public void startChangingAnimations() {
        LinkedHashMap<View, Animator> currentAnimCopy =
                new LinkedHashMap<View, Animator>(currentChangingAnimations);
        for (Animator anim : currentAnimCopy.values()) {
            if (anim instanceof ObjectAnimator) {
                ((ObjectAnimator) anim).setCurrentPlayTime(0);
            }
            anim.start();
        }
    }

    /** Variant of startChangingAnimations used when the window is not visible:
     *  snap the targets to their end state so no mid-transition state (such as
     *  isRunning()) is left behind. */
    public void endChangingAnimations() {
        LinkedHashMap<View, Animator> currentAnimCopy =
                new LinkedHashMap<View, Animator>(currentChangingAnimations);
        for (Animator anim : currentAnimCopy.values()) {
            anim.start();
            anim.end();
        }
        // The listeners should have drained the map already, but be sure.
        currentChangingAnimations.clear();
    }

    /** True while CHANGE_APPEARING or CHANGE_DISAPPEARING animations run, i.e.
     *  while layout-related properties are being animated. */
    public boolean isChangingLayout() {
        return (currentChangingAnimations.size() > 0);
    }

    /** True while any animation of this transition is running. */
    public boolean isRunning() {
        return (currentChangingAnimations.size() > 0 || currentAppearingAnimations.size() > 0
                || currentDisappearingAnimations.size() > 0);
    }

    /** Cancels the running transition. Note the asymmetry AOSP relies on: the
     *  changing animations are cancel()ed so views stay where they are, but the
     *  visibility animations are end()ed so views land on their final alpha. */
    public void cancel() {
        if (currentChangingAnimations.size() > 0) {
            LinkedHashMap<View, Animator> currentAnimCopy =
                    new LinkedHashMap<View, Animator>(currentChangingAnimations);
            for (Animator anim : currentAnimCopy.values()) {
                anim.cancel();
            }
            currentChangingAnimations.clear();
        }
        if (currentAppearingAnimations.size() > 0) {
            LinkedHashMap<View, Animator> currentAnimCopy =
                    new LinkedHashMap<View, Animator>(currentAppearingAnimations);
            for (Animator anim : currentAnimCopy.values()) {
                anim.end();
            }
            currentAppearingAnimations.clear();
        }
        if (currentDisappearingAnimations.size() > 0) {
            LinkedHashMap<View, Animator> currentAnimCopy =
                    new LinkedHashMap<View, Animator>(currentDisappearingAnimations);
            for (Animator anim : currentAnimCopy.values()) {
                anim.end();
            }
            currentDisappearingAnimations.clear();
        }
    }

    /** Cancels one transition type, with the same cancel-vs-end asymmetry as
     *  cancel(). */
    public void cancel(int transitionType) {
        switch (transitionType) {
            case CHANGE_APPEARING:
            case CHANGE_DISAPPEARING:
            case CHANGING:
                if (currentChangingAnimations.size() > 0) {
                    LinkedHashMap<View, Animator> currentAnimCopy =
                            new LinkedHashMap<View, Animator>(currentChangingAnimations);
                    for (Animator anim : currentAnimCopy.values()) {
                        anim.cancel();
                    }
                    currentChangingAnimations.clear();
                }
                break;
            case APPEARING:
                if (currentAppearingAnimations.size() > 0) {
                    LinkedHashMap<View, Animator> currentAnimCopy =
                            new LinkedHashMap<View, Animator>(currentAppearingAnimations);
                    for (Animator anim : currentAnimCopy.values()) {
                        anim.end();
                    }
                    currentAppearingAnimations.clear();
                }
                break;
            case DISAPPEARING:
                if (currentDisappearingAnimations.size() > 0) {
                    LinkedHashMap<View, Animator> currentAnimCopy =
                            new LinkedHashMap<View, Animator>(currentDisappearingAnimations);
                    for (Animator anim : currentAnimCopy.values()) {
                        anim.end();
                    }
                    currentDisappearingAnimations.clear();
                }
                break;
        }
    }

    /** Runs the animation that makes an added item appear. */
    private void runAppearingTransition(final ViewGroup parent, final View child) {
        Animator currentAnimation = currentDisappearingAnimations.get(child);
        if (currentAnimation != null) {
            currentAnimation.cancel();
        }
        if (mAppearingAnim == null) {
            // A null animator means "no appearing animation"; AOSP still owes
            // the listener its endTransition so the pair stays balanced.
            if (hasListeners()) {
                ArrayList<TransitionListener> listeners =
                        new ArrayList<TransitionListener>(mListeners);
                for (TransitionListener listener : listeners) {
                    listener.endTransition(LayoutTransition.this, parent, child, APPEARING);
                }
            }
            return;
        }
        Animator anim = mAppearingAnim.clone();
        anim.setTarget(child);
        anim.setStartDelay(mAppearingDelay);
        anim.setDuration(mAppearingDuration);
        if (mAppearingInterpolator != sAppearingInterpolator) {
            anim.setInterpolator(mAppearingInterpolator);
        }
        if (anim instanceof ObjectAnimator) {
            ((ObjectAnimator) anim).setCurrentPlayTime(0);
        }
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                currentAppearingAnimations.remove(child);
                if (hasListeners()) {
                    ArrayList<TransitionListener> listeners =
                            new ArrayList<TransitionListener>(mListeners);
                    for (TransitionListener listener : listeners) {
                        listener.endTransition(LayoutTransition.this, parent, child, APPEARING);
                    }
                }
            }
        });
        currentAppearingAnimations.put(child, anim);
        anim.start();
    }

    /** Runs the animation that makes a removed item disappear. */
    private void runDisappearingTransition(final ViewGroup parent, final View child) {
        Animator currentAnimation = currentAppearingAnimations.get(child);
        if (currentAnimation != null) {
            currentAnimation.cancel();
        }
        if (mDisappearingAnim == null) {
            if (hasListeners()) {
                ArrayList<TransitionListener> listeners =
                        new ArrayList<TransitionListener>(mListeners);
                for (TransitionListener listener : listeners) {
                    listener.endTransition(LayoutTransition.this, parent, child, DISAPPEARING);
                }
            }
            return;
        }
        Animator anim = mDisappearingAnim.clone();
        anim.setStartDelay(mDisappearingDelay);
        anim.setDuration(mDisappearingDuration);
        if (mDisappearingInterpolator != sDisappearingInterpolator) {
            anim.setInterpolator(mDisappearingInterpolator);
        }
        anim.setTarget(child);
        // The view survives the transition (it is only hidden or detached), so
        // the alpha the fade-out drove has to be restored at the end.
        final float preAnimAlpha = child.getAlpha();
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                currentDisappearingAnimations.remove(child);
                child.setAlpha(preAnimAlpha);
                if (hasListeners()) {
                    ArrayList<TransitionListener> listeners =
                            new ArrayList<TransitionListener>(mListeners);
                    for (TransitionListener listener : listeners) {
                        listener.endTransition(LayoutTransition.this, parent, child, DISAPPEARING);
                    }
                }
            }
        });
        if (anim instanceof ObjectAnimator) {
            ((ObjectAnimator) anim).setCurrentPlayTime(0);
        }
        currentDisappearingAnimations.put(child, anim);
        anim.start();
    }

    /**
     * @param changesLayout false when the child is only becoming VISIBLE from
     * INVISIBLE, which occupies the same space and therefore cannot move any
     * sibling; the CHANGE_* transitions are skipped in that case.
     */
    private void addChild(ViewGroup parent, View child, boolean changesLayout) {
        if (parent.getWindowVisibility() != View.VISIBLE) {
            return;
        }
        if ((mTransitionTypes & FLAG_APPEARING) == FLAG_APPEARING) {
            // Let any disappearing animation finish before we proceed.
            cancel(DISAPPEARING);
        }
        if (changesLayout && (mTransitionTypes & FLAG_CHANGE_APPEARING) == FLAG_CHANGE_APPEARING) {
            // Cancel changing animations so new ones start from current positions.
            cancel(CHANGE_APPEARING);
            cancel(CHANGING);
        }
        if (hasListeners() && (mTransitionTypes & FLAG_APPEARING) == FLAG_APPEARING) {
            ArrayList<TransitionListener> listeners =
                    new ArrayList<TransitionListener>(mListeners);
            for (TransitionListener listener : listeners) {
                listener.startTransition(this, parent, child, APPEARING);
            }
        }
        if (changesLayout && (mTransitionTypes & FLAG_CHANGE_APPEARING) == FLAG_CHANGE_APPEARING) {
            runChangeTransition(parent, child, APPEARING);
        }
        if ((mTransitionTypes & FLAG_APPEARING) == FLAG_APPEARING) {
            runAppearingTransition(parent, child);
        }
    }

    private boolean hasListeners() {
        return mListeners != null && mListeners.size() > 0;
    }

    /** Called by ViewGroup on every layout() of the container. Sets up a
     *  CHANGING transition only when one is enabled and nothing else is
     *  already animating, since add/remove/hide/show have their own. */
    public void layoutChange(ViewGroup parent) {
        if (parent.getWindowVisibility() != View.VISIBLE) {
            return;
        }
        if ((mTransitionTypes & FLAG_CHANGING) == FLAG_CHANGING && !isRunning()) {
            runChangeTransition(parent, null, CHANGING);
        }
    }

    /** Called by ViewGroup when a child is about to be added. */
    public void addChild(ViewGroup parent, View child) {
        addChild(parent, child, true);
    }

    /** @deprecated Use {@link #showChild(ViewGroup, View, int)}. */
    @Deprecated
    public void showChild(ViewGroup parent, View child) {
        addChild(parent, child, true);
    }

    /** Called by ViewGroup when a child is about to become visible.
     *  @param oldVisibility the child's previous visibility, GONE or INVISIBLE. */
    public void showChild(ViewGroup parent, View child, int oldVisibility) {
        addChild(parent, child, oldVisibility == View.GONE);
    }

    /**
     * @param changesLayout false when the child is only becoming INVISIBLE,
     * which keeps its space and therefore moves no sibling.
     */
    private void removeChild(ViewGroup parent, View child, boolean changesLayout) {
        if (parent.getWindowVisibility() != View.VISIBLE) {
            return;
        }
        if ((mTransitionTypes & FLAG_DISAPPEARING) == FLAG_DISAPPEARING) {
            cancel(APPEARING);
        }
        if (changesLayout
                && (mTransitionTypes & FLAG_CHANGE_DISAPPEARING) == FLAG_CHANGE_DISAPPEARING) {
            cancel(CHANGE_DISAPPEARING);
            cancel(CHANGING);
        }
        if (hasListeners() && (mTransitionTypes & FLAG_DISAPPEARING) == FLAG_DISAPPEARING) {
            ArrayList<TransitionListener> listeners =
                    new ArrayList<TransitionListener>(mListeners);
            for (TransitionListener listener : listeners) {
                listener.startTransition(this, parent, child, DISAPPEARING);
            }
        }
        if (changesLayout
                && (mTransitionTypes & FLAG_CHANGE_DISAPPEARING) == FLAG_CHANGE_DISAPPEARING) {
            runChangeTransition(parent, child, DISAPPEARING);
        }
        if ((mTransitionTypes & FLAG_DISAPPEARING) == FLAG_DISAPPEARING) {
            runDisappearingTransition(parent, child);
        }
    }

    /** Called by ViewGroup when a child is about to be removed. */
    public void removeChild(ViewGroup parent, View child) {
        removeChild(parent, child, true);
    }

    /** @deprecated Use {@link #hideChild(ViewGroup, View, int)}. */
    @Deprecated
    public void hideChild(ViewGroup parent, View child) {
        removeChild(parent, child, true);
    }

    /** Called by ViewGroup when a child is about to be hidden.
     *  @param newVisibility the child's new visibility, GONE or INVISIBLE. */
    public void hideChild(ViewGroup parent, View child, int newVisibility) {
        removeChild(parent, child, newVisibility == View.GONE);
    }

    public void addTransitionListener(TransitionListener listener) {
        if (mListeners == null) {
            mListeners = new ArrayList<TransitionListener>();
        }
        mListeners.add(listener);
    }

    public void removeTransitionListener(TransitionListener listener) {
        if (mListeners == null) {
            return;
        }
        mListeners.remove(listener);
    }

    /** AOSP returns the live list, which is null until the first listener is
     *  added; callers are expected to null-check. */
    public List<TransitionListener> getTransitionListeners() {
        return mListeners;
    }

    /** Listener for the start and end of each transition animation. This must
     *  exist as a TYPE even where the transitions do not run: an app class
     *  declaring `implements LayoutTransition.TransitionListener` fails to
     *  LOAD without it, which takes down whatever touched that class. */
    public interface TransitionListener {

        /** Sent when any type of transition animation begins.
         *  @param transitionType APPEARING, DISAPPEARING, CHANGE_APPEARING,
         *  CHANGE_DISAPPEARING or CHANGING. */
        public void startTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType);

        /** Sent when any type of transition animation ends. */
        public void endTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType);
    }
}
