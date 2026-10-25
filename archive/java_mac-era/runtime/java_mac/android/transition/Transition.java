// android.transition.Transition -- the base of the scene-transition framework
// (Fade/Slide/ChangeBounds/AutoTransition/TransitionSet). Was a bare stub
// (empty body), so beginDelayedTransition()/TransitionManager.go() did nothing
// and any caller invoking addTarget/setDuration/createAnimator NoSuchMethodError'd.
//
// This is a real, faithful subset of the AOSP contract
// (frameworks/base/core/java/android/transition/Transition.java): a Transition
// captures per-view property values at the start scene and the end scene, then
// builds an Animator from the difference. We implement the public surface
// (targets, timing, interpolator, listeners, captureStartValues/captureEndValues/
// createAnimator, the playTransition driver) so concrete transitions animate and
// listeners fire. The capture is driven by TransitionManager around a scene
// change; our renderer applies the resulting per-view property animations through
// the existing View setters (setAlpha/setTranslationX/layout/...).
package android.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.StringTokenizer;

public abstract class Transition implements Cloneable {

    // AOSP Transition.java:131-151 (android16-release). setMatchOrder(int...)
    // takes these; they are compile-time constants so javac inlines them into
    // the caller, but a declared `int[] order = {Transition.MATCH_ID}` still
    // needs the field to exist at COMPILE time for any code we build, and
    // reflection/annotation readers observe them at runtime.
    public static final int MATCH_INSTANCE = 0x1;
    public static final int MATCH_NAME = 0x2;
    public static final int MATCH_ID = 0x3;
    public static final int MATCH_ITEM_ID = 0x4;

    private static final int MATCH_FIRST = MATCH_INSTANCE;
    private static final int MATCH_LAST = MATCH_ITEM_ID;

    // AOSP Transition.java:162 DEFAULT_MATCH_ORDER.
    private static final int[] DEFAULT_MATCH_ORDER = {
            MATCH_NAME, MATCH_INSTANCE, MATCH_ID, MATCH_ITEM_ID,
    };

    private static final String MATCH_INSTANCE_STR = "instance";
    private static final String MATCH_NAME_STR = "name";
    private static final String MATCH_VIEW_NAME_STR = "viewName";
    private static final String MATCH_ID_STR = "id";
    private static final String MATCH_ITEM_ID_STR = "itemId";

    public interface TransitionListener {
        void onTransitionStart(Transition transition);
        void onTransitionEnd(Transition transition);
        void onTransitionCancel(Transition transition);
        void onTransitionPause(Transition transition);
        void onTransitionResume(Transition transition);
    }

    // AOSP exposes EpicenterCallback (used by Explode/Slide for ripple-origin
    // propagation). Surface it so callers that set one compile.
    public abstract static class EpicenterCallback {
        public abstract android.graphics.Rect onGetEpicenter(Transition transition);
    }

    private String mName = getClass().getName();
    private long mDuration = -1;
    private long mStartDelay = -1;
    private TimeInterpolator mInterpolator;

    // Target selection. AOSP supports id/view/name/type include+exclude lists; we
    // keep the common include-by-view and include-by-id sets, which is what
    // beginDelayedTransition (whole-scene) and addTarget(view) exercise.
    private final ArrayList<Integer> mTargetIds = new ArrayList<Integer>();
    private final ArrayList<View> mTargets = new ArrayList<View>();
    private final ArrayList<Class<?>> mTargetTypes = new ArrayList<Class<?>>();
    // AOSP declares this lazily-null (distinct from the eager lists above) --
    // addTarget(String)/getTargetNames() below mirror that so a caller probing
    // "was addTarget(String) ever called" via a null check sees the same
    // answer as on real Android.
    private ArrayList<String> mTargetNames;

    // Exclusion lists. AOSP Transition.java:188-194 -- four "this target is not
    // animated at all" lists and three "do not descend INTO this target" lists.
    // Both kinds are honoured in the capture walk below (AOSP captureHierarchy,
    // Transition.java:1560-1662): a target exclude skips capturing that view, a
    // child exclude stops the recursion at that view but still captures the view
    // itself. Left null until first use, exactly as AOSP does, because
    // getTargetNames()-style null probes exist for these too.
    private ArrayList<Integer> mTargetIdExcludes;
    private ArrayList<View> mTargetExcludes;
    private ArrayList<Class<?>> mTargetTypeExcludes;
    private ArrayList<String> mTargetNameExcludes;
    private ArrayList<Integer> mTargetIdChildExcludes;
    private ArrayList<View> mTargetChildExcludes;
    private ArrayList<Class<?>> mTargetTypeChildExcludes;

    // AOSP Transition.java:198.
    private int[] mMatchOrder = DEFAULT_MATCH_ORDER;

    // AOSP Transition.java:2158-2174.
    private TransitionPropagation mPropagation;

    // AOSP Transition.java:2204-2210. True for a scene-change transition, false
    // for the activity-transition (window) case where the framework must not
    // remove views. Nothing in Mac.apk drives the false case yet, so the getter
    // reports AOSP's default.
    private boolean mCanRemoveViews = true;

    // Set by TransitionSet when this transition is added to it, so
    // getTransitionValues() can delegate to the owning set the way AOSP does
    // (Transition.java:1666-1672). Null for a standalone transition.
    Transition mParent;

    private ArrayList<TransitionListener> mListeners;
    private EpicenterCallback mEpicenterCallback;
    private PathMotion mPathMotion = PathMotion.STRAIGHT_PATH_MOTION;

    // Captured values. AOSP holds these in TransitionValuesMaps; a flat list keyed
    // by view (via TransitionValues.view) is enough for our diff.
    // NOT final: these are per-run mutable state that clone() below must reset
    // to fresh, independent lists (AOSP's clone() does the same to its
    // equivalent fields) so two clones of one declared Transition can run
    // overlapping scene changes without one run's capture/animator bookkeeping
    // clobbering the other's. mTargetIds/mTargets/mTargetTypes/mTargetNames/
    // mListeners above are configuration, not per-run state, so AOSP leaves
    // them shared across a clone and we do too.
    private ArrayList<TransitionValues> mStartValuesList = new ArrayList<TransitionValues>();
    private ArrayList<TransitionValues> mEndValuesList = new ArrayList<TransitionValues>();

    private ArrayList<Animator> mCurrentAnimators = new ArrayList<Animator>();
    private int mNumInstances = 0;
    private boolean mEnded = false;

    public Transition() {}

    /**
     * AOSP Transition.java:271-291 -- the constructor every transition inflated
     * from res/transition/*.xml goes through, and the one a subclass declared in
     * XML (or constructed by TransitionInflater) must chain to. It reads
     * android:duration / android:startDelay / android:interpolator /
     * android:matchOrder off the attribute set.
     *
     * AOSP resolves those through R.styleable.Transition +
     * TypedArray.getInt/getString. Mac.apk has no com.android.internal.R, so we
     * read the SAME four attributes by name out of the android namespace on the
     * AttributeSet, which is the same data the TypedArray would have projected.
     * The observable result -- a transition whose duration/startDelay/
     * interpolator/matchOrder came from XML -- matches; what we cannot do is
     * follow a theme-attribute or style indirection (`?attr/...`), because that
     * resolution lives in the TypedArray path.
     *
     * A null AttributeSet leaves every property at its default, matching
     * obtainStyledAttributes(null, ...).
     */
    public Transition(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        long duration = attrs.getAttributeIntValue(ANDROID_NS, "duration", -1);
        if (duration >= 0) {
            setDuration(duration);
        }
        long startDelay = attrs.getAttributeIntValue(ANDROID_NS, "startDelay", -1);
        if (startDelay > 0) {
            setStartDelay(startDelay);
        }
        int resID = attrs.getAttributeResourceValue(ANDROID_NS, "interpolator", 0);
        if (resID > 0 && context != null) {
            setInterpolator(android.view.animation.AnimationUtils.loadInterpolator(context, resID));
        }
        String matchOrder = attrs.getAttributeValue(ANDROID_NS, "matchOrder");
        if (matchOrder != null) {
            setMatchOrder(parseMatchOrder(matchOrder));
        }
    }

    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /**
     * Read an android-namespace attribute that AOSP declares as an XML
     * &lt;enum&gt;/&lt;flag&gt; (slideEdge, fadingMode, transitionVisibilityMode,
     * transitionOrdering, ...).
     *
     * aapt2 resolves an enum name to its int in a COMPILED binary XML, so a real
     * APK's resource carries the number and getAttributeIntValue answers it. But
     * Mac.apk's XML path is apktool-DECODED text (com.macapk.loader.
     * DomXmlResourceParser), where the attribute is still the symbolic name and
     * getAttributeIntValue would silently fall back to the default. `names`/
     * `values` carry the mapping straight out of AOSP core/res/res/values/
     * attrs.xml, so both shapes resolve to the same number a device would use.
     */
    static int getEnumAttr(AttributeSet attrs, String name, String[] names, int[] values,
            int defValue) {
        if (attrs == null) return defValue;
        String raw = attrs.getAttributeValue(ANDROID_NS, name);
        if (raw == null) return defValue;
        raw = raw.trim();
        if (raw.isEmpty()) return defValue;
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(raw)) return values[i];
        }
        // A flag attribute (transitionVisibilityMode) may arrive as "mode_in|mode_out".
        if (raw.indexOf('|') >= 0) {
            int acc = 0;
            boolean any = false;
            for (String part : raw.split("\\|")) {
                String p = part.trim();
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(p)) { acc |= values[i]; any = true; break; }
                }
            }
            if (any) return acc;
        }
        return attrs.getAttributeIntValue(ANDROID_NS, name, defValue);
    }

    // AOSP Transition.java:293-322 parseMatchOrder(String). An empty token
    // SHRINKS the array (AOSP's odd but deliberate behaviour), and an unknown
    // token is an InflateException on a device -- we mirror both.
    private static int[] parseMatchOrder(String matchOrderString) {
        StringTokenizer st = new StringTokenizer(matchOrderString, ",");
        int[] matches = new int[st.countTokens()];
        int index = 0;
        while (st.hasMoreTokens()) {
            String token = st.nextToken().trim();
            if (MATCH_ID_STR.equalsIgnoreCase(token)) {
                matches[index] = MATCH_ID;
            } else if (MATCH_INSTANCE_STR.equalsIgnoreCase(token)) {
                matches[index] = MATCH_INSTANCE;
            } else if (MATCH_NAME_STR.equalsIgnoreCase(token)) {
                matches[index] = MATCH_NAME;
            } else if (MATCH_VIEW_NAME_STR.equalsIgnoreCase(token)) {
                matches[index] = MATCH_NAME;
            } else if (MATCH_ITEM_ID_STR.equalsIgnoreCase(token)) {
                matches[index] = MATCH_ITEM_ID;
            } else if (token.isEmpty()) {
                int[] smallerMatches = new int[matches.length - 1];
                System.arraycopy(matches, 0, smallerMatches, 0, index);
                matches = smallerMatches;
                index--;
            } else {
                throw new android.view.InflateException(
                        "Unknown match type in matchOrder: '" + token + "'");
            }
            index++;
        }
        return matches;
    }

    /**
     * AOSP Transition.java:498-513. Sets the order in which start values are
     * matched to end values. Validation (unknown value / duplicate value ->
     * IllegalArgumentException) is part of the observable contract, so it is
     * reproduced here even though our matching walk is instance-only today.
     *
     * ⚠ KNOWN PARTIAL: we STORE the order and validate it, but our
     * start/end pairing (playTransition below) matches on view INSTANCE only,
     * which is MATCH_INSTANCE -- the first entry AOSP falls back to for a view
     * that is the same object in both scenes, and the only kind of match a
     * beginDelayedTransition() (same hierarchy, same instances) can produce.
     * Honouring MATCH_ID / MATCH_NAME / MATCH_ITEM_ID would mean rebuilding the
     * capture into AOSP's TransitionValuesMaps (four keyed maps) and changing
     * how every existing transition pairs its values, which is a semantic
     * change to a working path -- see the lane report.
     */
    public void setMatchOrder(int... matches) {
        if (matches == null || matches.length == 0) {
            mMatchOrder = DEFAULT_MATCH_ORDER;
        } else {
            for (int i = 0; i < matches.length; i++) {
                int match = matches[i];
                if (!isValidMatch(match)) {
                    throw new IllegalArgumentException("matches contains invalid value");
                }
                if (alreadyContains(matches, i)) {
                    throw new IllegalArgumentException("matches contains a duplicate value");
                }
            }
            mMatchOrder = matches.clone();
        }
    }

    // AOSP Transition.java:515-527.
    private static boolean isValidMatch(int match) {
        return (match >= MATCH_FIRST && match <= MATCH_LAST);
    }

    private static boolean alreadyContains(int[] array, int searchIndex) {
        int value = array[searchIndex];
        for (int i = 0; i < searchIndex; i++) {
            if (array[i] == value) {
                return true;
            }
        }
        return false;
    }

    public Transition setDuration(long duration) {
        mDuration = duration;
        return this;
    }
    public long getDuration() { return mDuration; }

    public Transition setStartDelay(long startDelay) {
        mStartDelay = startDelay;
        return this;
    }
    public long getStartDelay() { return mStartDelay; }

    public Transition setInterpolator(TimeInterpolator interpolator) {
        mInterpolator = interpolator;
        return this;
    }
    public TimeInterpolator getInterpolator() { return mInterpolator; }

    public String getName() { return mName; }

    public Transition addTarget(View target) {
        if (target != null && !mTargets.contains(target)) mTargets.add(target);
        return this;
    }
    public Transition addTarget(int targetId) {
        if (targetId != 0) mTargetIds.add(targetId);
        return this;
    }
    public Transition addTarget(Class<?> targetType) {
        if (targetType != null) mTargetTypes.add(targetType);
        return this;
    }
    public Transition addTarget(String targetName) {
        if (targetName != null) {
            if (mTargetNames == null) mTargetNames = new ArrayList<String>();
            mTargetNames.add(targetName);
        }
        return this;
    }

    public Transition removeTarget(View target) {
        if (target != null) mTargets.remove(target);
        return this;
    }
    public Transition removeTarget(int targetId) {
        if (targetId != 0) mTargetIds.remove(Integer.valueOf(targetId));
        return this;
    }
    public Transition removeTarget(String targetName) {
        if (targetName != null && mTargetNames != null) mTargetNames.remove(targetName);
        return this;
    }
    // AOSP Transition.java:1071-1076 removeTarget(Class). apidiff cannot see
    // this one -- it tolerates a name+arity match against removeTarget(View) --
    // but a caller that removes a TYPE target would NoSuchMethodError without it.
    public Transition removeTarget(Class<?> target) {
        if (target != null) mTargetTypes.remove(target);
        return this;
    }

    // ---- Exclusions. AOSP Transition.java:1108-1290. -------------------------
    // Each returns `this` for chaining and is a no-op for a null target, exactly
    // as AOSP's shared excludeObject()/excludeId() helpers behave.
    // excludeTarget(...) removes the target from the transition entirely;
    // excludeChildren(...) keeps the target but stops the capture walk from
    // descending into it.

    public Transition excludeTarget(int targetId, boolean exclude) {
        if (targetId >= 0) {
            mTargetIdExcludes = excludeObject(mTargetIdExcludes, targetId, exclude);
        }
        return this;
    }

    public Transition excludeTarget(String targetName, boolean exclude) {
        mTargetNameExcludes = excludeObject(mTargetNameExcludes, targetName, exclude);
        return this;
    }

    public Transition excludeTarget(View target, boolean exclude) {
        mTargetExcludes = excludeObject(mTargetExcludes, target, exclude);
        return this;
    }

    public Transition excludeTarget(Class<?> type, boolean exclude) {
        mTargetTypeExcludes = excludeObject(mTargetTypeExcludes, type, exclude);
        return this;
    }

    public Transition excludeChildren(int targetId, boolean exclude) {
        if (targetId >= 0) {
            mTargetIdChildExcludes = excludeObject(mTargetIdChildExcludes, targetId, exclude);
        }
        return this;
    }

    public Transition excludeChildren(View target, boolean exclude) {
        mTargetChildExcludes = excludeObject(mTargetChildExcludes, target, exclude);
        return this;
    }

    public Transition excludeChildren(Class<?> type, boolean exclude) {
        mTargetTypeChildExcludes = excludeObject(mTargetTypeChildExcludes, type, exclude);
        return this;
    }

    // AOSP Transition.java:1229-1238 excludeObject() + its ArrayListManager
    // (Transition.java:2380+), which lazily allocates on add and drops the list
    // back to null when the last element is removed. The null-when-empty part is
    // observable through AOSP's own `mTargetIdExcludes != null` fast paths, so
    // it is reproduced rather than simplified to an always-allocated list.
    private static <T> ArrayList<T> excludeObject(ArrayList<T> list, T target, boolean exclude) {
        if (target != null) {
            if (exclude) {
                if (list == null) list = new ArrayList<T>();
                if (!list.contains(target)) list.add(target);
            } else if (list != null) {
                list.remove(target);
                if (list.isEmpty()) list = null;
            }
        }
        return list;
    }

    // AOSP captureHierarchy (Transition.java:1565-1580): a view whose id, identity
    // or type is on an exclude list is skipped outright.
    private boolean isTargetExcluded(View view) {
        if (view == null) return true;
        if (mTargetIdExcludes != null && mTargetIdExcludes.contains(view.getId())) return true;
        if (mTargetExcludes != null && mTargetExcludes.contains(view)) return true;
        if (mTargetTypeExcludes != null) {
            for (int i = 0; i < mTargetTypeExcludes.size(); i++) {
                if (mTargetTypeExcludes.get(i).isInstance(view)) return true;
            }
        }
        return false;
    }

    // AOSP checks the NAME excludes in isValidTarget (Transition.java:830-834),
    // NOT in captureHierarchy -- so unlike the three excludes above, a
    // name-excluded view does not prune its own subtree. Kept separate for
    // exactly that reason.
    private boolean isNameExcluded(View view) {
        return mTargetNameExcludes != null && view != null && view.getTransitionName() != null
                && mTargetNameExcludes.contains(view.getTransitionName());
    }

    // AOSP captureHierarchy (Transition.java:1595-1612): "Don't traverse child
    // hierarchy if there are any child-excludes on this view".
    private boolean areChildrenExcluded(View view) {
        if (view == null) return true;
        if (mTargetIdChildExcludes != null && mTargetIdChildExcludes.contains(view.getId())) {
            return true;
        }
        if (mTargetChildExcludes != null && mTargetChildExcludes.contains(view)) return true;
        if (mTargetTypeChildExcludes != null) {
            for (int i = 0; i < mTargetTypeChildExcludes.size(); i++) {
                if (mTargetTypeChildExcludes.get(i).isInstance(view)) return true;
            }
        }
        return false;
    }

    /**
     * AOSP Transition.java:2158-2174. A TransitionPropagation staggers the
     * per-target start delays (Explode's "views nearest the epicenter move
     * first"). We store and return it, and playTransition() below applies the
     * delay it computes.
     */
    public void setPropagation(TransitionPropagation transitionPropagation) {
        mPropagation = transitionPropagation;
    }

    public TransitionPropagation getPropagation() {
        return mPropagation;
    }

    /** AOSP Transition.java:2204-2210. */
    public boolean canRemoveViews() {
        return mCanRemoveViews;
    }

    void setCanRemoveViews(boolean canRemoveViews) {
        mCanRemoveViews = canRemoveViews;
    }

    /**
     * AOSP Transition.java:1666-1672. Lets a transition query the before/after
     * state of a RELATED view mid-run (ChangeTransform does this to find its
     * parent's values). Delegates to the owning TransitionSet when there is one,
     * because in AOSP the set owns the merged capture maps.
     */
    public TransitionValues getTransitionValues(View view, boolean start) {
        if (mParent != null) {
            return mParent.getTransitionValues(view, start);
        }
        return findOwnTransitionValues(view, start);
    }

    /** The non-delegating half of getTransitionValues -- see TransitionSet's
     *  override, which calls this on each child to avoid recursing back up. */
    TransitionValues findOwnTransitionValues(View view, boolean start) {
        ArrayList<TransitionValues> lookIn = start ? mStartValuesList : mEndValuesList;
        for (int i = 0; i < lookIn.size(); i++) {
            TransitionValues values = lookIn.get(i);
            if (values != null && values.view == view) return values;
        }
        return null;
    }

    /**
     * AOSP Transition.java:1799-1824. "Did anything this transition cares about
     * actually change?" -- the hook a subclass overrides to force (or suppress)
     * an animation. Default: true when any captured property differs.
     */
    public boolean isTransitionRequired(TransitionValues startValues, TransitionValues endValues) {
        boolean valuesChanged = false;
        // if startValues null, then transition didn't care to stash values,
        // and won't get canceled
        if (startValues != null && endValues != null) {
            String[] properties = getTransitionProperties();
            if (properties != null) {
                for (int i = 0; i < properties.length; i++) {
                    if (isValueChanged(startValues, endValues, properties[i])) {
                        valuesChanged = true;
                        break;
                    }
                }
            } else {
                for (String key : startValues.values.keySet()) {
                    if (isValueChanged(startValues, endValues, key)) {
                        valuesChanged = true;
                        break;
                    }
                }
            }
        }
        return valuesChanged;
    }

    // AOSP Transition.java:1826-1848 isValueChanged(). Note the deliberate
    // "present in neither / present in only one" handling: a key the transition
    // did not capture on BOTH sides is NOT a change.
    private static boolean isValueChanged(TransitionValues oldValues, TransitionValues newValues,
            String key) {
        if (oldValues.values.containsKey(key) != newValues.values.containsKey(key)) {
            // The transition didn't care about this particular value, so we don't care, either.
            return false;
        }
        Object oldValue = oldValues.values.get(key);
        Object newValue = newValues.values.get(key);
        boolean changed;
        if (oldValue == null && newValue == null) {
            // both are null
            changed = false;
        } else if (oldValue == null || newValue == null) {
            // one is null
            changed = true;
        } else {
            // neither is null
            changed = !oldValue.equals(newValue);
        }
        return changed;
    }

    public java.util.List<Integer> getTargetIds() { return mTargetIds; }
    public java.util.List<View> getTargets() { return mTargets; }
    public java.util.List<String> getTargetNames() { return mTargetNames; }
    public java.util.List<Class<?>> getTargetTypes() { return mTargetTypes; }

    public Transition addListener(TransitionListener listener) {
        if (mListeners == null) mListeners = new ArrayList<TransitionListener>();
        mListeners.add(listener);
        return this;
    }
    public Transition removeListener(TransitionListener listener) {
        if (mListeners != null) {
            mListeners.remove(listener);
            if (mListeners.isEmpty()) mListeners = null;
        }
        return this;
    }

    public void setEpicenterCallback(EpicenterCallback epicenterCallback) {
        mEpicenterCallback = epicenterCallback;
    }
    public EpicenterCallback getEpicenterCallback() { return mEpicenterCallback; }
    public android.graphics.Rect getEpicenter() {
        return mEpicenterCallback == null ? null : mEpicenterCallback.onGetEpicenter(this);
    }

    public void setPathMotion(PathMotion pathMotion) {
        mPathMotion = (pathMotion == null) ? PathMotion.STRAIGHT_PATH_MOTION : pathMotion;
    }
    public PathMotion getPathMotion() { return mPathMotion; }

    // AOSP: subclasses MUST implement captureStartValues + captureEndValues to
    // record the properties they animate, and createAnimator to build the tween.
    public abstract void captureStartValues(TransitionValues transitionValues);
    public abstract void captureEndValues(TransitionValues transitionValues);

    // Subclasses override; default builds nothing (a Transition with no animatable
    // delta produces no animator, matching AOSP's null return).
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
            TransitionValues endValues) {
        return null;
    }

    // Properties this transition reads -- subclasses may override to advertise the
    // keys they capture. Not load-bearing for our diff but part of the API.
    public String[] getTransitionProperties() { return null; }

    // Internal capture entry points driven by TransitionManager. We walk the
    // target set (or the whole subtree when no explicit targets) and ask the
    // subclass to fill a TransitionValues for each.
    void captureValues(ViewGroup sceneRoot, boolean start) {
        ArrayList<TransitionValues> list = start ? mStartValuesList : mEndValuesList;
        list.clear();
        ArrayList<View> views = collectTargets(sceneRoot);
        for (View v : views) {
            TransitionValues values = new TransitionValues(v);
            if (start) captureStartValues(values); else captureEndValues(values);
            // captureStartValues uses transitionValues.view, set above.
            capturePropagationValues(values);
            list.add(values);
        }
    }

    // AOSP Transition.java:2176-2197 capturePropagationValues(). Only asks the
    // propagation to capture when the transition itself captured something AND
    // the propagation's own keys are not already present, which is what stops a
    // VisibilityPropagation from re-capturing over a Visibility transition.
    void capturePropagationValues(TransitionValues transitionValues) {
        if (mPropagation != null && !transitionValues.values.isEmpty()) {
            String[] propertyNames = mPropagation.getPropagationProperties();
            if (propertyNames == null) {
                return;
            }
            boolean containsAll = true;
            for (int i = 0; i < propertyNames.length; i++) {
                if (!transitionValues.values.containsKey(propertyNames[i])) {
                    containsAll = false;
                    break;
                }
            }
            if (!containsAll) {
                mPropagation.captureValues(transitionValues);
            }
        }
    }

    private ArrayList<View> collectTargets(ViewGroup sceneRoot) {
        ArrayList<View> out = new ArrayList<View>();
        boolean hasTargetNames = mTargetNames != null && !mTargetNames.isEmpty();
        if (!mTargets.isEmpty() || !mTargetIds.isEmpty() || !mTargetTypes.isEmpty() || hasTargetNames) {
            for (View v : mTargets) out.add(v);
            gatherByIdTypeAndName(sceneRoot, out);
        } else if (sceneRoot != null) {
            gatherAll(sceneRoot, out);
        }
        return out;
    }

    private void gatherByIdTypeAndName(View v, ArrayList<View> out) {
        if (v == null) return;
        // AOSP captureHierarchy (Transition.java:1565-1580): an excluded target
        // is skipped, and its subtree is skipped with it.
        if (isTargetExcluded(v)) return;
        if (!isNameExcluded(v)) {
            if (mTargetIds.contains(v.getId()) && !out.contains(v)) out.add(v);
            // getTransitionName() is null on most views (only set by callers that
            // opt into name-based targeting); mTargetNames never contains null
            // (addTarget(String) guards against it), so this is a safe no-match.
            if (mTargetNames != null && mTargetNames.contains(v.getTransitionName())
                    && !out.contains(v)) {
                out.add(v);
            }
            for (Class<?> t : mTargetTypes) {
                if (t.isInstance(v) && !out.contains(v)) { out.add(v); break; }
            }
        }
        if (v instanceof ViewGroup) {
            // AOSP captureHierarchy (Transition.java:1595-1612): a child-exclude
            // keeps the view but stops the walk from descending into it.
            if (areChildrenExcluded(v)) return;
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) gatherByIdTypeAndName(g.getChildAt(i), out);
        }
    }

    private void gatherAll(View v, ArrayList<View> out) {
        if (v == null) return;
        if (isTargetExcluded(v)) return;
        if (!isNameExcluded(v)) out.add(v);
        if (v instanceof ViewGroup) {
            if (areChildrenExcluded(v)) return;
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) gatherAll(g.getChildAt(i), out);
        }
    }

    private TransitionValues findEndValuesFor(View view) {
        for (TransitionValues tv : mEndValuesList) {
            if (tv.view == view) return tv;
        }
        return null;
    }

    // Build + run all animators from the start/end diff. Called by
    // TransitionManager after both captures. Mirrors AOSP playTransition /
    // createAnimators + runAnimators.
    void playTransition(ViewGroup sceneRoot) {
        mCurrentAnimators.clear();
        // Per-animator propagation delays, collected alongside mCurrentAnimators
        // and normalized after the loop -- AOSP Transition.java:774-795 stores the
        // raw delays, tracks the minimum, then rewrites every one as
        // (delay - minStartDelay + animator.getStartDelay()) so the earliest
        // target starts at its own start delay and the rest fan out behind it.
        final java.util.ArrayList<Long> propagationDelays = new java.util.ArrayList<Long>();
        long minStartDelay = Long.MAX_VALUE;
        for (TransitionValues startValues : mStartValuesList) {
            TransitionValues endValues = findEndValuesFor(startValues.view);
            Animator animator = createAnimator(sceneRoot, startValues, endValues);
            if (animator != null) {
                if (mDuration >= 0) animator.setDuration(mDuration);
                if (mStartDelay >= 0) animator.setStartDelay(mStartDelay);
                if (mInterpolator != null) animator.setInterpolator(mInterpolator);
                if (mPropagation != null) {
                    long delay = mPropagation.getStartDelay(sceneRoot, this, startValues, endValues);
                    propagationDelays.add(Long.valueOf(delay));
                    minStartDelay = Math.min(delay, minStartDelay);
                } else {
                    propagationDelays.add(null);
                }
                mCurrentAnimators.add(animator);
            }
        }
        // Handle views present only in the end scene (e.g. Fade-in of an appearing
        // view) -- AOSP runs createAnimator with null startValues for those.
        for (TransitionValues endValues : mEndValuesList) {
            boolean seen = false;
            for (TransitionValues sv : mStartValuesList) {
                if (sv.view == endValues.view) { seen = true; break; }
            }
            if (!seen) {
                Animator animator = createAnimator(sceneRoot, null, endValues);
                if (animator != null) {
                    if (mDuration >= 0) animator.setDuration(mDuration);
                    if (mStartDelay >= 0) animator.setStartDelay(mStartDelay);
                    if (mInterpolator != null) animator.setInterpolator(mInterpolator);
                    if (mPropagation != null) {
                        long delay = mPropagation.getStartDelay(sceneRoot, this, null, endValues);
                        propagationDelays.add(Long.valueOf(delay));
                        minStartDelay = Math.min(delay, minStartDelay);
                    } else {
                        propagationDelays.add(null);
                    }
                    mCurrentAnimators.add(animator);
                }
            }
        }
        if (mPropagation != null && minStartDelay != Long.MAX_VALUE) {
            for (int i = 0; i < mCurrentAnimators.size(); i++) {
                Long delay = propagationDelays.get(i);
                if (delay == null) continue;
                Animator animator = mCurrentAnimators.get(i);
                animator.setStartDelay(delay.longValue() - minStartDelay + animator.getStartDelay());
            }
        }
        runAnimators();
    }

    /** MACAPK_TRACE_TRANSITION=1: one line per run / animator end / end /
     *  cancel of every transition (diagnostic). */
    static final boolean sTraceTransition = "1".equals(System.getenv("MACAPK_TRACE_TRANSITION"));
    /** A listener that throws is the APP's bug on a device too (AOSP does not
     *  catch here), so we keep the other listeners running but never hide it:
     *  a swallowed throw in a TransitionListener left leanback's headers
     *  transition half-finished with no trace. */
    void macapkListenerThrew(String what, Throwable t) {
        StringBuilder sb = new StringBuilder("transition listener ").append(what).append(" threw: ").append(t);
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(8, st.length); i++) sb.append("\n    at ").append(st[i]);
        com.macapk.loader.NativeLog.w("Mac.apk.Transition", sb.toString());
    }
    void macapkTraceT(String what) {
        if (sTraceTransition) com.macapk.loader.NativeLog.i("Mac.apk.Transition", what + " "
            + getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(this))
            + " animators=" + mCurrentAnimators.size() + " pending=" + mNumInstances);
    }
    void runAnimators() {
        macapkTraceT("run");
        mEnded = false;
        if (mCurrentAnimators.isEmpty()) {
            // Nothing to animate -- still fire the start/end lifecycle so callers
            // depending on the listener (e.g. a "remove view when done" handler)
            // are not stranded. AOSP fires end immediately for an empty set.
            dispatchStart();
            dispatchEnd();
            return;
        }
        dispatchStart();
        mNumInstances = mCurrentAnimators.size();
        for (Animator anim : mCurrentAnimators) {
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    --mNumInstances;
                    macapkTraceT("animator-end");
                    if (mNumInstances == 0) dispatchEnd();
                }
            });
            anim.start();
        }
    }

    // Package-private (was private): TransitionSet.playTransition() needs to
    // drive the SET's own start/end dispatch directly -- see that method for why.
    void dispatchStart() {
        if (mListeners != null) {
            for (TransitionListener l : new ArrayList<TransitionListener>(mListeners)) {
                try { l.onTransitionStart(this); } catch (Throwable t) { macapkListenerThrew("onTransitionStart", t); }
            }
        }
    }

    void dispatchEnd() {
        macapkTraceT(mEnded ? "end(again)" : "end");
        if (mEnded) return;
        mEnded = true;
        if (mListeners != null) {
            for (TransitionListener l : new ArrayList<TransitionListener>(mListeners)) {
                try { l.onTransitionEnd(this); } catch (Throwable t) { macapkListenerThrew("onTransitionEnd", t); }
            }
        }
    }

    public void cancel() {
        macapkTraceT("cancel");
        for (Animator a : new ArrayList<Animator>(mCurrentAnimators)) {
            try { a.cancel(); } catch (Throwable ignored) {}
        }
        if (mListeners != null) {
            for (TransitionListener l : new ArrayList<TransitionListener>(mListeners)) {
                try { l.onTransitionCancel(this); } catch (Throwable t) { macapkListenerThrew("onTransitionCancel", t); }
            }
        }
    }

    @Override
    public Transition clone() {
        try {
            Transition clone = (Transition) super.clone();
            // AOSP's clone() resets only the per-run capture/animator state
            // (its mAnimators/mStartValues/mEndValues equivalents) to fresh
            // containers; Object.clone()'s field copy would otherwise give the
            // clone the SAME list references as the original, so a second
            // captureValues()/playTransition() run on either instance clobbers
            // the other's in-flight start/end diff. Target/listener
            // configuration (mTargetIds/mTargets/mTargetTypes/mTargetNames/
            // mListeners) stays shared by reference, matching AOSP.
            clone.mStartValuesList = new ArrayList<TransitionValues>();
            clone.mEndValuesList = new ArrayList<TransitionValues>();
            clone.mCurrentAnimators = new ArrayList<Animator>();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    }
}
