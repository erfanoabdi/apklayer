// FragmentInflaterFactory -- Tier 3 Milestone 0: fragment-instantiation-from-layout.
//
// THE GAP (root-caused in Tier 2 M4, docs/fragment_instantiation_gap.md): an
// app layout that declares a fragment in XML -- either a `<fragment>` tag or a
// `FragmentContainerView android:name="..."` -- expects the platform to turn it
// into a REAL Fragment object: instantiate the class, run an add transaction on
// the activity's FragmentManager, and let the manager drive the fragment's
// lifecycle so onAttach/onCreate/onCreateView/onViewCreated fire. Real Android
// does this via a LayoutInflater factory:
//   * androidx FragmentActivity overrides Activity.onCreateView(...) (the
//     LayoutInflater PRIVATE factory) -> FragmentController.onCreateView ->
//     FragmentManager.getLayoutInflaterFactory().onCreateView(...) =
//     FragmentLayoutInflaterFactory.
//   * That factory handles `FragmentContainerView.class.getName()` (via the
//     internal 3-arg ctor) and the `"fragment"` tag (instantiate + addFragment).
// Mac.apk never ran any of that: (a) our LayoutInflater did not consult ANY
// factory, and (b) even if we delegated to the app's own factory it would fail,
// because that factory reads android:name via obtainStyledAttributes(R.styleable
// .Fragment / FragmentContainerView) and our obtainStyledAttributes cannot map
// framework attr ids (0x0101....) back to the DOM (the long-standing gap noted
// at Resources.java:651). So the fragment's VIEW got inflated as a plain child
// with NO backing Fragment -- getFragments() returned 0 -- and onViewCreated
// (which wires e.g. the Calculator toolbar/menu/preview) never ran.
//
// THE FIX (mirrors what the app's own FragmentLayoutInflaterFactory does, but
// reads android:name/id/tag straight from the DOM-backed AttributeSet so it
// does NOT depend on obtainStyledAttributes): install this as the inflater's
// private factory (LayoutInflater.from, gated on MACAPK_REAL_LOOPER). For a
// `<fragment>` / FragmentContainerView[android:name] tag we instantiate the
// named Fragment via the app's FragmentFactory (or the app classloader) and run
// a real add transaction (beginTransaction().add(...).commitNowAllowingStateLoss())
// on the app's OWN FragmentManager. The heavy lifting -- onCreateView,
// onViewCreated, STARTED/RESUMED -- is then done by the app's real FragmentManager
// state machine (commitNow moves the fragment to the FM's current state, and
// Tier 2 M4 FragmentLifecycle.driveToResumed advances it to RESUMED at the end
// of performLaunch).
//
// R8 discipline (the Reddit/Calculator constraint): the app's androidx/support
// classes are renamed, so we NEVER hardcode their class names. We locate the
// FragmentManager by public method shape (FragmentLifecycle.getHostFragmentManager),
// and we locate the transaction add() method by SHAPE -- a 3-arg method whose
// fragment parameter .isInstance(frag) -- not by name, so it survives renaming.
// (M4 already proved executePendingTransactions/getFragments resolve by name on
// Calculator's FM, so beginTransaction/commitNow-by-name are safe there too; the
// by-shape add() is belt-and-suspenders.)
//
// Generic + safe: it keys ONLY on the tag ("fragment" / *.FragmentContainerView)
// + the presence of android:name -- never on a package name. It returns null for
// every other tag (so normal inflation proceeds) and is a no-op when no host
// FragmentManager is found (GL games, fragment-less apps).
package com.macapk.loader;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class FragmentInflaterFactory implements LayoutInflater.Factory2 {
    private static final String TAG = "Mac.apk.FragmentInflaterFactory";

    /** Stateless singleton -- it resolves the host FragmentManager from the
     *  currently-running Activity (ActivityHost.peek()) at call time, so one
     *  instance serves every inflater. */
    public static final FragmentInflaterFactory INSTANCE = new FragmentInflaterFactory();
    private FragmentInflaterFactory() {}

    /** One-shot guard for the "no android:name" trace so a per-frame
     *  re-inflation path can't spam the log. */
    private static volatile boolean sLoggedNoName = false;

    @Override public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    @Override public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        try {
            boolean isFragmentTag = "fragment".equals(name);
            boolean isContainer = !isFragmentTag && name != null
                && (name.equals("FragmentContainerView") || name.endsWith(".FragmentContainerView"));
            if (!isFragmentTag && !isContainer) return null;   // not ours -> normal inflation

            // android:name (or class=) names the Fragment subclass to instantiate.
            // Read straight from the DOM-backed AttributeSet -- works where
            // obtainStyledAttributes(R.styleable.Fragment_android_name) does not.
            String fname = attrs.getAttributeValue(null, "name");
            if (fname == null || fname.isEmpty()) fname = attrs.getClassAttribute();
            if (fname == null || fname.isEmpty()) {
                // A fragment-ish tag with no android:name/class. Common case: a
                // FragmentContainerView whose fragment is added PROGRAMMATICALLY
                // (e.g. Google Calculator). Nothing to instantiate from layout;
                // let default inflation build the container. Trace at most once
                // (a per-frame re-inflation path must not spam this).
                if (!sLoggedNoName) {
                    sLoggedNoName = true;
                    NativeLog.i(TAG, "M0: <" + name + "> has no android:name -- programmatic host, not instantiating from layout");
                }
                return null;
            }

            int id = attrs.getAttributeResourceValue(null, "id", View.NO_ID);
            String tag = attrs.getAttributeValue(null, "tag");

            Activity activity = ActivityHost.peek();
            if (activity == null) return null;

            // WHICH MANAGER SHOULD HOST THIS? Real Android answers by TYPE, not
            // by classloader, and it tries androidx first with the framework as
            // the fallback (androidx FragmentActivity.onCreateView):
            //     final View v = dispatchFragmentsOnCreateView(parent, name, context, attrs);
            //     if (v == null) { return super.onCreateView(parent, name, context, attrs); }
            //     return v;
            // ...where `super` is android.app.Activity.onCreateView ->
            // mFragments.onCreateView -> the FRAMEWORK FragmentManagerImpl. That
            // framework manager lives on the BOOT classloader on a real device,
            // so "the manager I found is not app-loaded" is the NORMAL state for
            // a framework `<fragment>` tag, not a disqualification.
            //
            // A class extending android.app.Fragment can ONLY be hosted by the
            // framework manager (androidx's FragmentFactory.instantiate casts to
            // androidx.fragment.app.Fragment and would fail), so route by that.
            // Anuto TD is the measured case: zero androidx in the APK, every
            // fragment extends android.app.Fragment, five `<fragment>` tags in
            // activity_game.xml. The old classloader guard turned all five into
            // FrameLayout placeholders and the game rendered chrome only.
            if (isFragmentTag && namesFrameworkFragment(fname, context)) {
                // Hand off to the framework FragmentManagerImpl, which
                // instantiates, onInflate()s, adds and creates the view, then
                // returns the FRAGMENT'S OWN view (AOSP FragmentManager.java
                // :3603) -- returned here unwrapped, with the tag's android:id
                // already stamped on it.
                //
                // Deliberately the manager DIRECTLY, not activity.onCreateView():
                // that call dispatches VIRTUALLY, and on an androidx
                // FragmentActivity the override would route back into
                // dispatchFragmentsOnCreateView -> the androidx manager, which
                // cannot host an android.app.Fragment and is the path we just
                // decided against. Going straight to the framework manager is
                // the same destination AOSP reaches via `super.onCreateView`,
                // minus a pointless round trip through a factory that will fail.
                return frameworkOnCreateView(activity, parent, name, context, attrs);
            }

            Object fm = FragmentLifecycle.getHostFragmentManager(activity);
            if (fm == null) return null;   // fragment-less app / no support FM
            // Host on the APP's own (androidx/support) FragmentManager. If all we
            // can find is our framework android.app manager, this tag is not an
            // androidx one we can place there -- mirror FragmentActivity's
            // fallback and let the framework manager try, which reports honestly
            // if it cannot instantiate the named class.
            if (fm.getClass().getClassLoader() != activity.getClass().getClassLoader()) {
                if (isFragmentTag) {
                    NativeLog.i(TAG, "no app-loaded FM for <fragment " + fname
                        + "> -- falling back to the framework FragmentManager"
                        + " (AOSP FragmentActivity.onCreateView super path)");
                    return frameworkOnCreateView(activity, parent, name, context, attrs);
                }
                NativeLog.w(TAG, "host FM for <" + name + "> is not app-loaded ("
                    + fm.getClass().getName() + ") -- skipping instantiate (likely R8-renamed app FM not findable by name)");
                return null;
            }

            // PREFERRED PATH for a FragmentContainerView[android:name] tag: call
            // its OWN 3-arg constructor with the REAL, unstripped attrs and the
            // live FragmentManager -- exactly what AOSP's own
            // FragmentManager$FragmentLayoutInflaterFactory does
            // (LayoutInflater.createView(FragmentContainerView.class, ..., fm)).
            // See tryRealContainerCtor's own comment for why this is more robust
            // than buildContainer+hostFragment below, not just an alternative.
            if (isContainer) {
                View real = tryRealContainerCtor(name, context, attrs, fm);
                if (real != null) {
                    NativeLog.i(TAG, "M0: hosted <" + name + "> via its OWN 3-arg ctor("
                        + fm.getClass().getName() + ") -- ran AOSP's real"
                        + " FragmentContainerView code, not our reconstruction");
                    return real;
                }
            }

            ViewGroup container = buildContainer(isContainer, name, context, attrs, id);
            // `isFragmentTag` is the mFromLayout gate (see hostFragment). ONLY the
            // `<fragment>` tag is a from-layout fragment in androidx; a
            // FragmentContainerView[android:name] is an ordinary add, and its own
            // 3-arg ctor (tryRealContainerCtor above) is the path that handles it.
            boolean added = hostFragment(fm, container, fname, id, tag, context, attrs, isFragmentTag);
            if (added && isFragmentTag && FRAGMENT_TAG_OWN_VIEW) {
                View own = unwrapFromLayout(lastHostedFragment, container, parent, id, tag, fname);
                if (own != null) return own;
            }
            if (!added) {
                NativeLog.w(TAG, "could not host fragment " + fname + " (tag <" + name + ">)");
                // Still return the container so the layout tree is intact; the
                // fragment just isn't attached (no worse than the old behavior).
            } else {
                NativeLog.i(TAG, "M0: hosted fragment " + fname + " in <" + name
                    + "> id=0x" + Integer.toHexString(id == View.NO_ID ? container.getId() : id)
                    + (tag != null ? " tag=" + tag : ""));
            }
            return container;
        } catch (Throwable t) {
            NativeLog.w(TAG, "onCreateView('" + name + "') failed: " + t);
            return null;   // fall back to normal inflation
        }
    }

    /** Hand a `<fragment>` tag to the FRAMEWORK android.app.FragmentManagerImpl,
     *  the host AOSP reaches through Activity.onCreateView ->
     *  mFragments.onCreateView (Activity.java:7355). Returns the fragment's own
     *  view, or null if it could not be hosted (the inflater then substitutes
     *  its placeholder, and FragmentManagerImpl has already logged why). */
    private static View frameworkOnCreateView(Activity activity, View parent, String name,
                                              Context context, AttributeSet attrs) {
        android.app.FragmentManager fm = activity.getFragmentManager();
        if (!(fm instanceof android.app.FragmentManagerImpl)) return null;
        return ((android.app.FragmentManagerImpl) fm).onCreateView(parent, name, context, attrs);
    }

    /** True if `fname` names a class that extends the FRAMEWORK
     *  android.app.Fragment, i.e. one only the framework FragmentManager can
     *  host. Loads WITHOUT initialization (`Class.forName(n, false, cl)`) so the
     *  type test cannot run a `<clinit>` that the real instantiation path would
     *  otherwise own. Unloadable/unknown -> false, which routes the tag down the
     *  androidx path first exactly as AOSP's chain does. */
    private static boolean namesFrameworkFragment(String fname, Context context) {
        try {
            ClassLoader cl = context != null ? context.getClassLoader() : null;
            if (cl == null) return false;
            Class<?> cls = Class.forName(fname, false, cl);
            return android.app.Fragment.class.isAssignableFrom(cls);
        } catch (Throwable t) {
            return false;
        }
    }

    /** androidx FragmentLayoutInflaterFactory calls
     *  `fragment.onInflate(mFragmentManager.getHost().getContext(), attrs,
     *  fragment.mSavedFragmentState)` on BOTH its branches (new and retained),
     *  and it is the only point at which a fragment can read its own tag's
     *  attributes. Omitting it is why a NavHostFragment declared in XML never
     *  receives its `app:navGraph` and comes up with no graph.
     *
     *  R8 discipline: `onInflate` can be renamed, so find it by SHAPE -- the
     *  3-arg method taking (Context, AttributeSet, Bundle) -- rather than by
     *  name. Best-effort: a fragment that does not override it has nothing to
     *  read, so a miss is harmless. */
    private void callOnInflate(Object frag, Context context, AttributeSet attrs) {
        try {
            for (Method m : frag.getClass().getMethods()) {
                if (m.getParameterCount() != 3) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p[0] == Context.class && p[1] == AttributeSet.class
                        && p[2] == android.os.Bundle.class) {
                    m.setAccessible(true);
                    m.invoke(frag, context, attrs, null);
                    return;
                }
            }
        } catch (Throwable t) {
            NativeLog.w(TAG, "onInflate on " + frag.getClass().getName() + " threw " + t);
        }
    }

    /** Host a FragmentContainerView[android:name] tag by calling ITS OWN 3-arg
     *  constructor (Context, AttributeSet, FragmentManager) with the REAL,
     *  unstripped attrs and the discovered live FragmentManager -- i.e. do
     *  exactly what AOSP's own FragmentManager$FragmentLayoutInflaterFactory
     *  does. That constructor is real, un-reflected library bytecode: it reads
     *  android:name/android:id/android:tag itself, instantiates the Fragment via
     *  the app's own FragmentFactory, sets mFragmentId/mContainerId/mTag/
     *  mFragmentManager/mHost directly (AOSP's doAddOp), builds its OWN
     *  BackStackRecord and enqueues the add -- so NONE of it depends on
     *  beginTransaction/add/addOp/setReorderingAllowed/commitNow surviving R8
     *  under any recognisable name, unlike buildContainer+hostFragment below,
     *  which has to guess at each of those.
     *
     *  Verified 2026-07-30 by disassembling AnkiDroid's own (fully R8-renamed:
     *  every androidx.fragment.app class down to single/double letters EXCEPT
     *  FragmentContainerView itself) converted classes.jar: the constructor's
     *  bytecode is byte-for-byte AOSP's algorithm with every internal symbol
     *  renamed -- which is exactly why CALLING it, instead of reflecting into
     *  its innards by guessed names, sidesteps the rename entirely. This is also
     *  why View's (Context, AttributeSet) constructor was taught to resolve
     *  android:id synchronously (see View.java): this constructor reads
     *  ((View) this).getId() on itself before returning, the same way real
     *  AOSP's View(Context, AttributeSet) does, and needs the real id already in
     *  place -- our post-construction LayoutInflater.applyAttributes pass runs
     *  too late for a class we call directly like this one.
     *
     *  Returns null (never throws) if the class has no such constructor, or the
     *  constructor itself refuses (e.g. AOSP's own "must have an android:id"
     *  IllegalStateException) -- the caller then falls back to buildContainer/
     *  hostFragment, so an app this doesn't fit is no worse off than before. */
    private View tryRealContainerCtor(String name, Context context, AttributeSet attrs, Object fm) {
        try {
            Class<?> cls = Class.forName(name, true, context.getClassLoader());
            if (!ViewGroup.class.isAssignableFrom(cls)) return null;
            for (Constructor<?> c : cls.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length != 3) continue;
                if (!Context.class.isAssignableFrom(p[0])) continue;
                if (!AttributeSet.class.isAssignableFrom(p[1])) continue;
                if (!p[2].isInstance(fm)) continue;
                c.setAccessible(true);
                Object v = c.newInstance(context, attrs, fm);
                if (v instanceof View) return (View) v;
            }
        } catch (Throwable t) {
            Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException
                               && t.getCause() != null) ? t.getCause() : t;
            NativeLog.w(TAG, "real 3-arg FragmentContainerView ctor for <" + name
                + "> failed (" + cause.getClass().getName() + ": " + cause.getMessage()
                + ") -- falling back to manual hosting");
        }
        return null;
    }

    /** Build the container ViewGroup that hosts the fragment's view. For a
     *  FragmentContainerView tag we try to instantiate the app's REAL
     *  FragmentContainerView (so findViewById casts succeed) via its 2-arg
     *  (Context, AttributeSet) ctor, but with android:name/class STRIPPED so its
     *  ctor does not throw UnsupportedOperationException ("FragmentContainerView
     *  must be within a FragmentActivity to use android:name") -- we add the
     *  fragment ourselves, so the ctor's own name handling is unwanted. This is
     *  the FALLBACK now that tryRealContainerCtor above is tried first; it stays
     *  for containers whose class has no matching 3-arg ctor (a custom
     *  FragmentContainerView subclass) or where that ctor itself declined. Falls
     *  back further to a plain FrameLayout. */
    private ViewGroup buildContainer(boolean isContainer, String name, Context context,
                                     AttributeSet attrs, int id) {
        ViewGroup container = null;
        if (isContainer) {
            try {
                Class<?> cls = Class.forName(name, true, context.getClassLoader());
                if (ViewGroup.class.isAssignableFrom(cls)) {
                    AttributeSet stripped = new NameStrippedAttributeSet(attrs);
                    Constructor<?> c2 = cls.getConstructor(Context.class, AttributeSet.class);
                    c2.setAccessible(true);
                    Object v = c2.newInstance(context, stripped);
                    if (v instanceof ViewGroup) container = (ViewGroup) v;
                }
            } catch (Throwable t) {
                NativeLog.w(TAG, "FragmentContainerView instantiate failed (" + t
                    + "); using FrameLayout");
            }
        }
        if (container == null) container = new android.widget.FrameLayout(context);
        if (id != View.NO_ID) container.setId(id);
        return container;
    }

    /** Instantiate the named Fragment, run a real add transaction on the app's
     *  FragmentManager, and commit it now (synchronously). Returns true if the
     *  fragment was instantiated AND added to a transaction that committed.
     *
     *  `fromLayout` is true only for the `<fragment>` tag; see markFromLayout. */
    private boolean hostFragment(Object fm, ViewGroup container, String fname, int id,
                                 String tag, Context context, AttributeSet attrs,
                                 boolean fromLayout) {
        Object frag = instantiateFragment(fm, fname, context.getClassLoader());
        if (frag == null) return false;   // instantiateFragment already logged why

        // BEFORE onInflate, matching the app's own factory (see markFromLayout for
        // the measured bytecode order: mFromLayout/mInLayout are written at bci
        // 550/594, onInflate is inlined at bci 620-656).
        boolean marked = fromLayout && markFromLayout(frag, container);

        // androidx calls onInflate BEFORE addFragment, so the fragment sees its
        // own tag's attributes before any lifecycle work. NavHostFragment reads
        // app:navGraph here.
        callOnInflate(frag, context, attrs);

        // BY NAME FIRST, THEN BY RETURN TYPE. The header above already commits to
        // "locate the add() method by SHAPE, not by name, so it survives
        // renaming" -- but beginTransaction itself was still looked up by name,
        // and that is exactly where Aegis 3.4.2 stopped. MEASURED 2026-07-30:
        // once FragmentLifecycle's type-based discovery started returning the
        // REAL androidx.fragment.app.FragmentManagerImpl instead of an R8-merged
        // FragmentStore, the error moved from
        //     no usable beginTransaction() on androidx.emoji2.text.MetadataRepo
        // to
        //     no usable beginTransaction() on androidx.fragment.app.FragmentManagerImpl
        // -- i.e. we finally had the right object and then failed to call it,
        // because R8 renamed the method. The string `beginTransaction` does not
        // appear anywhere in Aegis's converted jar.
        //
        // The same asymmetry that fixed discovery fixes the call: R8 renames
        // METHODS but the androidx.fragment.app CLASS NAMES survive, so the
        // no-arg method whose RETURN TYPE is a FragmentTransaction/BackStackRecord
        // is unambiguous even when its name is gone.
        Object tx = invoke0(fm, "beginTransaction");
        if (tx == null) tx = beginTransactionByReturnType(fm);
        if (tx == null) tx = beginTransactionByConstruction(fm);
        if (tx == null) {
            // SILENT before. invoke0 swallows, so "the FM has no beginTransaction
            // we can find" (R8 shape we do not recognise) was indistinguishable
            // from every other hostFragment failure.
            NativeLog.e(TAG, "no usable beginTransaction() on "
                + fm.getClass().getName() + " for <fragment " + fname + ">");
            return false;
        }

        // Mirror androidx FragmentContainerView's add path: reordering allowed.
        Method setReorder = findMethod(tx.getClass(), "setReorderingAllowed", boolean.class);
        if (setReorder != null) {
            try { setReorder.invoke(tx, Boolean.TRUE); } catch (Throwable ignored) {}
        }

        if (!addToTransaction(tx, container, frag, tag)) {
            // SILENT before, and this is the gap that cost a whole diagnostic
            // cycle on Aegis 3.4.2 (2026-07-30): addToTransaction only logs when
            // a MATCHING add() throws. When no overload matches by shape it just
            // returns false, so instrumenting commitNow (the suspected culprit)
            // produced no output at all and the failure looked identical to
            // before. Dump the 3-arg add() candidates we actually saw so the
            // shape mismatch is visible rather than inferred.
            StringBuilder cands = new StringBuilder();
            try {
                for (Method m : tx.getClass().getMethods()) {
                    if (m.getParameterCount() != 3 || !"add".equals(m.getName())) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (cands.length() > 0) cands.append(", ");
                    cands.append("add(").append(p[0].getSimpleName()).append(',')
                         .append(p[1].getSimpleName()).append(',')
                         .append(p[2].getSimpleName()).append(')');
                }
            } catch (Throwable ignored) {}
            NativeLog.e(TAG, "no usable 3-arg add() on " + tx.getClass().getName()
                + " for <fragment " + fname + "> (fragment is a "
                + frag.getClass().getName() + ", container "
                + (container == null ? "null" : container.getClass().getSimpleName()
                   + " id=0x" + Integer.toHexString(container.getId()))
                + "); 3-arg add candidates seen: "
                + (cands.length() == 0 ? "NONE" : cands.toString()));
            return false;
        }
        if (commitNow(fm, tx)) {
            // MANDATORY COMPANION to markFromLayout -- never one without the
            // other. See attachInflatedView.
            if (fromLayout) attachInflatedView(frag, container, context, fname, marked);
            lastHostedFragment = frag;
            return true;
        }
        // The commit failed, so moveToExpectedState never ran and no view was
        // built. Put the from-layout marks back so we do not leave the fragment
        // in a half-configured state that would confuse a later add by the app
        // itself (a from-layout fragment that is not actually in a layout is
        // pinned below STARTED by computeExpectedState). Safe to restore to the
        // constructor defaults unconditionally because instantiateFragment always
        // returns a FRESHLY constructed Fragment.
        if (fromLayout) unmarkFromLayout(frag);
        // Say WHY the commit failed. commitNow used to route every attempt
        // through tryInvoke0, which catches Throwable and returns a bare false,
        // so an androidx IllegalStateException thrown out of commit was
        // DISCARDED and the caller could only say "could not host". Measured on
        // Aegis 3.4.2 (2026-07-30): <fragment EntryListView> failed here, and
        // the only evidence left in the log was java.lang.IllegalStateException
        // appearing in the class-load trace -- the message itself, which names
        // the actual condition ("FragmentManager has not been attached to a
        // host" vs "already added" vs "already executing transactions"), was
        // gone. The app then NPE'd in its own onCreate on the null fragment,
        // several hundred lines away from the real cause. Same reasoning as the
        // LayoutInflater placeholder [W]-vs-[E] split.
        NativeLog.e(TAG, "commit failed for <fragment " + fname + ">: "
            + (lastCommitFail == null || lastCommitFail.isEmpty()
               ? "no commit* method found on " + tx.getClass().getName()
               : lastCommitFail));
        return false;
    }

    /** The fragment the most recent successful hostFragment() committed, for
     *  unwrapFromLayout. Inflation runs on the main thread, as lastCommitFail. */
    private Object lastHostedFragment;

    /** BISECT GATE (2026-10-20, default ON): a `<fragment>` tag inflates to the
     *  FRAGMENT'S OWN VIEW, not to a FrameLayout holding it. androidx
     *  FragmentLayoutInflaterFactory.onCreateView ends
     *      if (id != 0) fragment.mView.setId(id);
     *      if (fragment.mView.getTag() == null) fragment.mView.setTag(tag);
     *      return fragment.mView;
     *  with fragment.mContainer = the inflating parent. Our wrapper carried the
     *  tag's android:id as well, so activity.findViewById(id) found the bare
     *  FrameLayout before the fragment's view, and anything the fragment hangs on
     *  its own view was invisible through the id. Victim: every Navigation app
     *  with <fragment android:name="...NavHostFragment"> that calls
     *  findNavController(activity, R.id.nav_host_fragment) in onCreate
     *  (NavHostFragment.onViewCreated tags its root view with the NavController):
     *  Rangskat threw "does not have a NavController set", its toolbar title and
     *  drawer were never wired. MACAPK_FRAGMENT_TAG_OWN_VIEW=0 restores the wrapper. */
    static final boolean FRAGMENT_TAG_OWN_VIEW =
        !"0".equals(System.getenv("MACAPK_FRAGMENT_TAG_OWN_VIEW"));

    /** Hand the inflater the from-layout fragment's own view, as androidx's
     *  factory does: take it back out of the transaction container hostFragment
     *  committed into, stamp the tag's android:id / android:tag on it, and point
     *  mContainer / mContainerId at the inflating parent (androidx writes
     *  fragment.mContainer = (ViewGroup) parent and mContainerId = parent's id),
     *  so a later removal takes the view out of the real parent. The inflater
     *  then generates the tag's layout params and adds the view to that parent,
     *  which also lifts computeExpectedState's "unparented from-layout view"
     *  VIEW_CREATED cap, exactly as on a device.
     *  Returns null (caller keeps the container) when the view is not the one
     *  attachInflatedView put in the container. */
    private View unwrapFromLayout(Object frag, ViewGroup container, View parent,
                                  int id, String tag, String fname) {
        if (frag == null || container == null) return null;
        Object mv = getFieldIfPresent(frag, "mView");
        if (!(mv instanceof View)) return null;
        View v = (View) mv;
        if (v.getParent() != container) return null;
        try {
            container.removeView(v);
        } catch (Throwable t) {
            NativeLog.w(TAG, "<fragment " + fname + "> could not leave its container: " + t);
            return null;
        }
        if (id != View.NO_ID) v.setId(id);
        if (v.getTag() == null && tag != null) v.setTag(tag);
        ViewGroup p = (parent instanceof ViewGroup) ? (ViewGroup) parent : null;
        setFieldIfPresent(frag, "mContainer", p);
        setFieldIfPresent(frag, "mContainerId", p != null ? p.getId() : 0);
        NativeLog.i(TAG, "M0: <fragment " + fname + "> inflates to its own view "
            + v.getClass().getName() + " id=0x" + Integer.toHexString(v.getId())
            + " (androidx FragmentLayoutInflaterFactory returns fragment.mView)");
        return v;
    }

    /** Why the most recent commitNow() failed -- every attempted commit* method
     *  and the cause it threw, joined. Null/empty means no commit* method was
     *  found at all (an R8 shape we do not recognise) rather than one throwing. */
    private String lastCommitFail;

    // ---- the from-layout (`<fragment>` tag) contract ----
    //
    // THE GAP THIS CLOSES: a fragment declared with a `<fragment>` tag must have
    // its VIEW in place by the time Activity.onCreate returns, because the
    // activity's own onCreate reaches into it immediately (setContentView ->
    // findFragmentById -> touch the fragment's widgets). We ran a plain
    // add + commitNow and never told the manager this was a from-layout
    // fragment, so its expected state was just the manager's state (CREATED)
    // during onCreate and the view did not exist until something later drove the
    // fragment to RESUMED. Measured on Aegis 3.4.2: MainActivity.onCreate NPE'd
    // on a null RecyclerView inside its fragment, which aborted the REST of
    // onCreate (ActivityHost swallows the throw and continues), so everything
    // onCreate would have built after that point -- including a whole
    // add-entry FAB menu inflated and addContentView'd much later in the same
    // method -- silently never happened.
    //
    // THE CONTRACT, quoted from androidx FragmentStateManager.computeExpectedState:
    //   https://github.com/androidx/androidx/blob/androidx-main/fragment/fragment/
    //     src/main/java/androidx/fragment/app/FragmentStateManager.java
    //     if (mFragment.mFromLayout) {
    //         if (mFragment.mInLayout) {
    //             maxState = Math.max(mFragmentManagerState, Fragment.VIEW_CREATED);
    //             if (mFragment.mView != null && mFragment.mView.getParent() == null) {
    //                 maxState = Math.min(maxState, Fragment.VIEW_CREATED);
    //             }
    //         } else { ... }
    //     }
    // so mFromLayout+mInLayout FLOOR the fragment at VIEW_CREATED no matter what
    // state the manager is in -- which is exactly the "view exists during
    // onCreate" guarantee -- and moveToExpectedState() then reaches
    // ensureInflatedView(), whose own guard is
    // `mFromLayout && mInLayout && !mPerformedCreateView` and which builds the
    // view with a NULL container. createView() early-returns for a from-layout
    // fragment, so androidx NEVER parents that view itself.
    //
    // Verified against Aegis 3.4.2's OWN converted classes.jar (not just
    // androidx-main), because the whole point of an R8'd app is that upstream
    // source is a hypothesis about it:
    //   FragmentStateManager.computeExpectedState  bci 279-348 -- the two clauses
    //     above, byte for byte, with VIEW_CREATED as the literal 2.
    //   FragmentStateManager.moveToExpectedState   bci 572/576 -- calls
    //     ensureInflatedView() then createView().
    //   FragmentStateManager.ensureInflatedView    bci 52-76 -- the three-way
    //     mFromLayout/mInLayout/mPerformedCreateView guard.
    //   FragmentLayoutInflaterFactory.onCreateView bci 550/594/1010/1015 -- writes
    //     mFromLayout, mInLayout and mContainer, then moveToExpectedState().
    //   Fragment's mFromLayout / mInLayout / mContainer / mView / mAdded fields and
    //     public performCreateView(LayoutInflater, ViewGroup, Bundle) all survive
    //     R8 under those names. getView() does NOT, which is why mView is read as
    //     a FIELD below rather than through the getter.
    //
    // AND THE CATCH, which is why markFromLayout and attachInflatedView are one
    // change and not two: the second clause CAPS an unparented from-layout
    // fragment at VIEW_CREATED forever. androidx gets past it because its factory
    // returns fragment.mView to the LayoutInflater, which parents it. We commit
    // through an add transaction into a container, so WE do the parenting first;
    // since 2026-10-20 (FRAGMENT_TAG_OWN_VIEW) unwrapFromLayout then hands the
    // view itself back to the inflater as androidx does. (Before, the container
    // was returned and carried the tag's android:id too, which hid the
    // fragment's own view from findViewById: Rangskat's NavController.) Setting the marks without attaching would strand every
    // `<fragment>` below STARTED -- strictly worse than the late view we have now.

    /** Tell the manager this fragment came from a `<fragment>` tag, mirroring the
     *  app's own FragmentLayoutInflaterFactory writes at bci 550/594/1010.
     *
     *  mContainer is set to OUR container rather than to the inflating parent
     *  because in our tree that container IS the ViewGroup the fragment's view
     *  lives in, which is what mContainer means. It has to be in place before the
     *  commit, since commitNow is where moveToExpectedState runs; androidx has the
     *  same ordering (mContainer at bci 1010, moveToExpectedState at 1015).
     *
     *  Every write is individually optional: setFieldIfPresent reports a miss and
     *  moves on. A build where only SOME of these survive degrades to today's
     *  late-view behaviour rather than to a broken one, because the floor clause
     *  needs mFromLayout AND mInLayout together and the cap clause needs a
     *  non-null mView that only the floor produces.
     *
     *  Returns whether BOTH marks landed, which the caller needs: the floor and
     *  androidx's own "do not build this fragment's view yourself" early-return
     *  are the SAME condition, so a build where the marks did not land must not
     *  get our performCreateView fallback either (see attachInflatedView). */
    private boolean markFromLayout(Object frag, ViewGroup container) {
        boolean ok = setFieldIfPresent(frag, "mFromLayout", Boolean.TRUE);
        // Non-short-circuit on purpose: attempt (and report) both writes.
        ok &= setFieldIfPresent(frag, "mInLayout", Boolean.TRUE);
        if (container != null) setFieldIfPresent(frag, "mContainer", container);
        if (!ok) {
            NativeLog.w(TAG, "from-layout marks did not take on " + frag.getClass().getName()
                + " -- this androidx build does not expose mFromLayout/mInLayout, so the"
                + " fragment's view will not exist until the activity is driven past"
                + " onCreate (the pre-v1 behaviour), not a new failure");
        }
        return ok;
    }

    /** Undo markFromLayout after a FAILED commit. The constructor defaults are the
     *  correct restore values because instantiateFragment always hands back a
     *  freshly constructed Fragment, so there is nothing else these could have been.
     *
     *  Deliberately NOT called after a successful commit, even when the attach
     *  below fails: by then the view exists and mPerformedCreateView is set, and
     *  clearing mFromLayout would let FragmentStateManager.createView() run and
     *  build a SECOND view (it has no mPerformedCreateView guard, only the
     *  mFromLayout early-return). A loud log about a pinned fragment is a better
     *  outcome than a duplicated one. */
    private void unmarkFromLayout(Object frag) {
        setFieldIfPresent(frag, "mFromLayout", Boolean.FALSE);
        setFieldIfPresent(frag, "mInLayout", Boolean.FALSE);
        setFieldIfPresent(frag, "mContainer", null);
    }

    /** Parent the from-layout fragment's view into our container, the job
     *  androidx's own factory does by RETURNING fragment.mView to the inflater.
     *  Without this the `mView.getParent() == null` clause of computeExpectedState
     *  caps the fragment at VIEW_CREATED permanently.
     *
     *  `marked` says whether markFromLayout's writes actually landed, and it gates
     *  the performCreateView fallback for a reason worth stating: mFromLayout is
     *  also what makes FragmentStateManager.createView() early-return. If the mark
     *  did NOT land, androidx will build this fragment's view itself later and
     *  addView it into mContainer, so creating one here would leave the container
     *  holding two views. Without the mark we do nothing and land exactly on the
     *  old late-view behaviour, which is the intended degradation. */
    private void attachInflatedView(Object frag, ViewGroup container, Context context,
                                    String fname, boolean marked) {
        if (container == null) return;
        Object mv = getFieldIfPresent(frag, "mView");
        if (!(mv instanceof View) && marked) {
            // The commit did not reach ensureInflatedView -- an androidx old enough
            // to have no FragmentStateManager, where the inflater factory itself
            // was what called performCreateView. Build the view the way
            // ensureInflatedView does, with a NULL container, so the activity's
            // onCreate still finds it. Safe against duplication precisely because
            // `marked` is true: androidx will not create a second one.
            mv = forceCreateView(frag, context, fname);
        }
        if (!(mv instanceof View)) {
            NativeLog.w(TAG, "<fragment " + fname + "> has no view after commit"
                + (marked ? " and could not create one" : " (from-layout marks did not land)")
                + " -- falling back to the late-view behaviour (the activity's onCreate"
                + " will see a null fragment view)");
            return;
        }
        View v = (View) mv;
        if (v.getParent() == container) return;   // already ours, nothing to do
        if (v.getParent() != null) {
            NativeLog.w(TAG, "<fragment " + fname + "> view is already parented to "
                + v.getParent().getClass().getName() + " -- not reparenting");
            return;
        }
        try {
            // MATCH_PARENT explicitly rather than relying on the container's
            // generateDefaultLayoutParams: a view inflated with a null root carries
            // NO layout params at all (AOSP LayoutInflater only calls
            // root.generateLayoutParams when root != null), and a fragment filling
            // its container is what both FrameLayout's AOSP default and
            // FragmentContainerView produce.
            container.addView(v, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            NativeLog.i(TAG, "M0: <fragment " + fname + "> view attached during onCreate"
                + " (from-layout floor at VIEW_CREATED)");
        } catch (Throwable t) {
            // The one genuinely bad outcome: the view exists, is unparented, and we
            // could not attach it, so computeExpectedState will hold this fragment
            // at VIEW_CREATED. Say so out loud rather than leaving it to be
            // rediscovered as "the fragment never starts".
            NativeLog.e(TAG, "could not attach <fragment " + fname + "> view to "
                + container.getClass().getName() + ": " + t.getClass().getName() + ": "
                + t.getMessage() + " -- the fragment is now PINNED at VIEW_CREATED"
                + " (androidx caps an unparented from-layout fragment there)");
        }
    }

    /** Build the fragment's view directly, mirroring ensureInflatedView's
     *  `performCreateView(inflater, null, savedState)`. Used only when the commit
     *  left mView null.
     *
     *  performCreateView is PUBLIC in androidx and survives R8 under its own name
     *  (verified above), so an exact-signature lookup is right here; a by-shape
     *  scan would risk calling some other 3-arg method. LayoutInflater.from(context)
     *  stands in for the fragment's performGetLayoutInflater, which does NOT survive
     *  R8 -- the difference is the fragment's own theme wrapper, which our inflater
     *  resolves from the activity context anyway.
     *
     *  Returns null (never throws) if the method is absent or refuses. Note this
     *  path does not run performViewCreated (also gone under R8), so onViewCreated
     *  will not have fired -- the log says as much, because a fragment with a view
     *  but no onViewCreated is a confusing state to debug from scratch. */
    private Object forceCreateView(Object frag, Context context, String fname) {
        Method m = findMethod(frag.getClass(), "performCreateView",
            LayoutInflater.class, ViewGroup.class, android.os.Bundle.class);
        if (m == null) {
            NativeLog.w(TAG, "no performCreateView(LayoutInflater,ViewGroup,Bundle) on "
                + frag.getClass().getName() + " -- cannot build <fragment " + fname
                + "> view early");
            return null;
        }
        try {
            m.invoke(frag, LayoutInflater.from(context), null, null);
        } catch (Throwable t) {
            Throwable c = (t instanceof java.lang.reflect.InvocationTargetException
                           && t.getCause() != null) ? t.getCause() : t;
            NativeLog.e(TAG, "performCreateView on <fragment " + fname + "> threw "
                + c.getClass().getName() + ": " + c.getMessage());
            return null;
        }
        Object mv = getFieldIfPresent(frag, "mView");
        if (mv != null) {
            NativeLog.i(TAG, "<fragment " + fname + "> view built via performCreateView"
                + " (the manager did not reach ensureInflatedView); onViewCreated has"
                + " NOT run on this path");
        }
        return mv;
    }

    /** Find and invoke a 3-arg add(...) on the transaction, by SHAPE so it is
     *  robust against R8 method renaming. Prefers add(ViewGroup, Fragment, String)
     *  (androidx 1.2+, attaches into the container view directly); falls back to
     *  add(int containerViewId, Fragment, String). */
    private boolean addToTransaction(Object tx, ViewGroup container, Object frag, String tag) {
        Class<?> txc = tx.getClass();
        // add(ViewGroup, Fragment, String)
        for (Method m : txc.getMethods()) {
            if (m.getParameterCount() != 3) continue;
            Class<?>[] p = m.getParameterTypes();
            if (ViewGroup.class.isAssignableFrom(p[0]) && p[1].isInstance(frag) && p[2] == String.class) {
                try { m.invoke(tx, container, frag, tag); return true; }
                catch (Throwable t) { NativeLog.w(TAG, "add(ViewGroup,...) threw " + t); }
            }
        }
        // add(int containerViewId, Fragment, String) -- ensure the container has an id.
        int cid = container.getId();
        if (cid == View.NO_ID) { cid = View.generateViewId(); container.setId(cid); }
        for (Method m : txc.getMethods()) {
            if (m.getParameterCount() != 3) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p[0] == int.class && p[1].isInstance(frag) && p[2] == String.class) {
                try { m.invoke(tx, cid, frag, tag); return true; }
                catch (Throwable t) { NativeLog.w(TAG, "add(int,...) threw " + t); }
            }
        }
        // LAST RESORT: rebuild the add the way AOSP's own FragmentTransaction does.
        // R8 can inline the entire add(...) family away -- measured on Aegis 3.4.2,
        // whose transaction class exposes ONLY `addOp(Op)`. AOSP:
        //   void doAddOp(int containerViewId, Fragment fragment, String tag, int opcmd) {
        //       if (tag != null) { ... fragment.mTag = tag; }
        //       if (containerViewId != 0) {
        //           ... fragment.mFragmentId = containerViewId;
        //               fragment.mContainerId = containerViewId; }
        //       addOp(new Op(opcmd, fragment));
        //   }
        // so the container id and tag live on the FRAGMENT, not on the Op -- which
        // is why Op has no such fields. This is not a bypass: it is the identical
        // object graph the app would have built itself.
        return addViaOp(tx, cid, frag, tag);
    }

    /** AOSP FragmentTransaction.OP_ADD. An engine-level constant, stable across
     *  every androidx release (OP_NULL=0, OP_ADD=1, OP_REPLACE=2, ...), not a
     *  per-APK value. */
    private static final int OP_ADD = 1;

    /** Reconstruct `add(containerViewId, fragment, tag)` from `addOp(new Op(...))`
     *  when R8 has inlined the add() family away. Everything is located BY SHAPE:
     *  the Op class is whatever single parameter `addOp` takes, and its ctor is
     *  whatever `(int, <something that isInstance(frag)>)` it declares. */
    private boolean addViaOp(Object tx, int cid, Object frag, String tag) {
        Method addOp = null;
        Class<?> opCls = null;
        Constructor<?> opCtor = null;
        for (Method m : tx.getClass().getMethods()) {
            if (m.getParameterCount() != 1) continue;
            Class<?> q = m.getParameterTypes()[0];
            if (q.isPrimitive() || q == String.class || q.isInstance(frag)) continue;
            for (Constructor<?> c : q.getDeclaredConstructors()) {
                Class<?>[] cp = c.getParameterTypes();
                if (cp.length == 2 && cp[0] == int.class && cp[1].isInstance(frag)) {
                    addOp = m; opCls = q; opCtor = c; break;
                }
            }
            if (addOp != null) break;
        }
        if (addOp == null) return false;

        // Container id + tag go on the FRAGMENT (AOSP doAddOp, above). These field
        // names survive R8 in the measured case; if a future build renames them the
        // add still happens and the miss is logged rather than silently wrong.
        if (tag != null) setFieldIfPresent(frag, "mTag", tag);
        if (cid != 0 && cid != View.NO_ID) {
            setFieldIfPresent(frag, "mFragmentId", Integer.valueOf(cid));
            setFieldIfPresent(frag, "mContainerId", Integer.valueOf(cid));
        }
        try {
            opCtor.setAccessible(true);
            Object op = opCtor.newInstance(Integer.valueOf(OP_ADD), frag);
            addOp.setAccessible(true);
            addOp.invoke(tx, op);
            NativeLog.i(TAG, "add RECONSTRUCTED via " + addOp.getName() + "(new "
                + opCls.getName() + "(OP_ADD, fragment)) -- R8 inlined the add() family");
            return true;
        } catch (Throwable t) {
            NativeLog.e(TAG, "addViaOp failed on " + opCls.getName() + ": "
                + t.getClass().getName() + ": " + t.getMessage());
            return false;
        }
    }

    /** Set a public/declared field by name if it exists, tolerating absence.
     *  Returns whether the write actually landed, so a caller that depends on the
     *  field (markFromLayout) can report a degraded outcome instead of assuming. */
    private static boolean setFieldIfPresent(Object target, String name, Object value) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) { return false; }
        }
        NativeLog.w(TAG, "field " + name + " not found on " + target.getClass().getName()
            + " -- fragment may not bind to its container");
        return false;
    }

    /** Read a declared field by name, tolerating absence. The counterpart to
     *  setFieldIfPresent, and the only way to reach Fragment.mView: getView() does
     *  not survive R8 (measured on Aegis 3.4.2) while the mView FIELD does. */
    private static Object getFieldIfPresent(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) { return null; }
        }
        NativeLog.w(TAG, "field " + name + " not found on " + target.getClass().getName());
        return null;
    }

    /** Commit the transaction synchronously. commitNowAllowingStateLoss mirrors
     *  androidx FragmentContainerView's inflation path (tolerant of the host
     *  state during onCreate). Falls back through commitNow, then an async
     *  commit + executePendingTransactions flush. */
    private boolean commitNow(Object fm, Object tx) {
        StringBuilder why = new StringBuilder();
        lastCommitFail = null;
        if (invokeReporting(tx, "commitNowAllowingStateLoss", why)) return true;
        if (invokeReporting(tx, "commitNow", why)) return true;
        if (invokeReporting(tx, "commitAllowingStateLoss", why)
                || invokeReporting(tx, "commit", why)) {
            tryInvoke0(fm, "executePendingTransactions");
            return true;
        }
        lastCommitFail = why.toString();
        return false;
    }

    /** tryInvoke0, but it RECORDS why the call failed instead of swallowing it.
     *  Only the commit path needs this: a commit that throws is the difference
     *  between "we do not recognise this transaction class" and "androidx
     *  refused the transaction for a reason it stated out loud". Appends
     *  "<method> -> <cause class>: <cause message>" to `why`, unwrapping the
     *  reflective InvocationTargetException so the app-visible exception is what
     *  gets reported rather than the wrapper. */
    private static boolean invokeReporting(Object target, String method, StringBuilder why) {
        if (target == null) return false;
        Method m = findMethod(target.getClass(), method);
        if (m == null) return false;   // not this androidx shape; not a failure
        try { m.invoke(target); return true; }
        catch (Throwable t) {
            Throwable c = (t instanceof java.lang.reflect.InvocationTargetException
                           && t.getCause() != null) ? t.getCause() : t;
            if (why.length() > 0) why.append("; ");
            why.append(method).append(" -> ").append(c.getClass().getName())
               .append(": ").append(c.getMessage());
            return false;
        }
    }

    /** Instantiate the fragment via the app's FragmentFactory when available
     *  (honors a custom FragmentFactory), else directly via the app classloader. */
    private Object instantiateFragment(Object fm, String fname, ClassLoader cl) {
        try {
            Object factory = invoke0(fm, "getFragmentFactory");
            if (factory != null) {
                Method inst = findMethod(factory.getClass(), "instantiate",
                    ClassLoader.class, String.class);
                if (inst != null) {
                    Object f = inst.invoke(factory, cl, fname);
                    if (f != null) return f;
                }
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> c = Class.forName(fname, true, cl);
            Constructor<?> ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Throwable t) {
            NativeLog.w(TAG, "instantiate " + fname + " failed: " + t);
            return null;
        }
    }

    // ---- small reflection helpers ----

    /** Find and call the no-arg method whose RETURN TYPE is a FragmentTransaction
     *  (or androidx's BackStackRecord concrete subclass), for an R8'd app where
     *  `beginTransaction` no longer exists under that name. Prefers an exact
     *  `FragmentTransaction` return over `BackStackRecord` so we get the API
     *  method rather than an internal one, and skips anything taking arguments. */
    private static Object beginTransactionByReturnType(Object fm) {
        if (fm == null) return null;
        java.lang.reflect.Method best = null;
        for (Class<?> c = fm.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            java.lang.reflect.Method[] ms;
            try { ms = c.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (java.lang.reflect.Method m : ms) {
                if (m.getParameterCount() != 0) continue;
                String rn = m.getReturnType().getName();
                boolean exact = rn.endsWith(".FragmentTransaction") || rn.endsWith("$FragmentTransaction");
                boolean sub   = rn.endsWith(".BackStackRecord");
                if (!exact && !sub) continue;
                if (best == null || exact) { best = m; if (exact) break; }
            }
            if (best != null && best.getReturnType().getName().endsWith("FragmentTransaction")) break;
        }
        if (best == null) return null;
        try {
            best.setAccessible(true);
            Object tx = best.invoke(fm);
            if (tx != null) {
                NativeLog.i(TAG, "beginTransaction resolved BY RETURN TYPE: "
                    + best.getName() + "() -> " + tx.getClass().getName()
                    + " (R8 renamed the method)");
            }
            return tx;
        } catch (Throwable t) {
            NativeLog.e(TAG, "beginTransaction-by-return-type " + best.getName()
                + "() threw: " + t.getClass().getName() + ": " + t.getMessage());
            return null;
        }
    }

    /** Construct the transaction the way AOSP's beginTransaction() does, for an
     *  app where R8 inlined that method out of existence entirely.
     *  AOSP FragmentManager: `public FragmentTransaction beginTransaction() {
     *  return new BackStackRecord(this); }` -- so a class taking exactly this
     *  FragmentManager in a 1-arg constructor IS the transaction type. Located by
     *  SHAPE: scan the manager's own method signatures for a parameter type with
     *  such a constructor (androidx's FragmentManagerImpl declares
     *  `execSingleAction(BackStackRecord, boolean)`, which names it for us). */
    private static Object beginTransactionByConstruction(Object fm) {
        if (fm == null) return null;
        for (Class<?> c = fm.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Method[] ms;
            try { ms = c.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (Method m : ms) {
                for (Class<?> q : m.getParameterTypes()) {
                    if (q.isPrimitive() || q.isInterface() || q == String.class) continue;
                    for (Constructor<?> ctor : q.getDeclaredConstructors()) {
                        Class<?>[] cp = ctor.getParameterTypes();
                        if (cp.length != 1 || !cp[0].isInstance(fm)) continue;
                        try {
                            ctor.setAccessible(true);
                            Object tx = ctor.newInstance(fm);
                            NativeLog.i(TAG, "beginTransaction RECONSTRUCTED as new "
                                + q.getName() + "(fm) -- R8 inlined beginTransaction()");
                            return tx;
                        } catch (Throwable t) {
                            NativeLog.w(TAG, "candidate transaction " + q.getName()
                                + "(fm) threw " + t.getClass().getName());
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Object invoke0(Object target, String method) {
        if (target == null) return null;
        Method m = findMethod(target.getClass(), method);
        if (m == null) return null;
        try { return m.invoke(target); }
        catch (Throwable t) { return null; }
    }

    private static boolean tryInvoke0(Object target, String method) {
        if (target == null) return false;
        Method m = findMethod(target.getClass(), method);
        if (m == null) return false;
        try { m.invoke(target); return true; }
        catch (Throwable t) { return false; }
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /** Wraps an AttributeSet but hides android:name / class so the app's
     *  FragmentContainerView(Context, AttributeSet) ctor does not throw
     *  "must be within a FragmentActivity to use android:name". obtainStyledAttributes
     *  already returns null for android:name in our framework, so the only other
     *  name source the ctor reads is attrs.classAttribute -- which we null out. */
    private static final class NameStrippedAttributeSet implements AttributeSet {
        private final AttributeSet d;
        NameStrippedAttributeSet(AttributeSet delegate) { this.d = delegate; }
        private static boolean isName(String n) { return "name".equals(n) || "class".equals(n); }

        @Override public String getClassAttribute() { return null; }
        @Override public String getAttributeValue(String ns, String name) {
            return isName(name) ? null : d.getAttributeValue(ns, name);
        }
        @Override public int getAttributeCount() { return d.getAttributeCount(); }
        @Override public String getAttributeName(int i) { return d.getAttributeName(i); }
        @Override public String getAttributeValue(int i) { return d.getAttributeValue(i); }
        @Override public String getPositionDescription() { return d.getPositionDescription(); }
        @Override public int getAttributeNameResource(int i) { return d.getAttributeNameResource(i); }
        @Override public int getAttributeListValue(String ns, String a, String[] o, int def) { return d.getAttributeListValue(ns, a, o, def); }
        @Override public boolean getAttributeBooleanValue(String ns, String a, boolean def) { return d.getAttributeBooleanValue(ns, a, def); }
        @Override public int getAttributeResourceValue(String ns, String a, int def) { return d.getAttributeResourceValue(ns, a, def); }
        @Override public int getAttributeIntValue(String ns, String a, int def) { return d.getAttributeIntValue(ns, a, def); }
        @Override public int getAttributeUnsignedIntValue(String ns, String a, int def) { return d.getAttributeUnsignedIntValue(ns, a, def); }
        @Override public float getAttributeFloatValue(String ns, String a, float def) { return d.getAttributeFloatValue(ns, a, def); }
        @Override public int getAttributeListValue(int i, String[] o, int def) { return d.getAttributeListValue(i, o, def); }
        @Override public boolean getAttributeBooleanValue(int i, boolean def) { return d.getAttributeBooleanValue(i, def); }
        @Override public int getAttributeResourceValue(int i, int def) { return d.getAttributeResourceValue(i, def); }
        @Override public int getAttributeIntValue(int i, int def) { return d.getAttributeIntValue(i, def); }
        @Override public int getAttributeUnsignedIntValue(int i, int def) { return d.getAttributeUnsignedIntValue(i, def); }
        @Override public float getAttributeFloatValue(int i, float def) { return d.getAttributeFloatValue(i, def); }
        @Override public String getIdAttribute() { return d.getIdAttribute(); }
        @Override public int getIdAttributeResourceValue(int def) { return d.getIdAttributeResourceValue(def); }
        @Override public int getStyleAttribute() { return d.getStyleAttribute(); }
    }
}
