// FragmentLifecycle -- Tier 2 M4. Drives the app's OWN androidx/support
// FragmentManager(s) so hosted fragments reach onViewCreated and advance to
// RESUMED, the way real Android's FragmentActivity does it as part of the
// activity lifecycle. ActivityHost.performLaunch already drove the Activity
// (and its LifecycleRegistry, via the v1.0.363 ActivityLifecycleCallbacks
// dispatch) to RESUMED; this completes the picture for fragments.
//
// Why a separate, explicit drive (vs relying purely on the host dispatch):
// a FragmentManager commits transactions by POSTING a runnable to the main
// looper (FragmentManager's mExecCommit). Before Tier 2 there was no real
// main loop, so those posts only drained as a side effect of rendering and
// often never ran during bootstrap -- so a `<fragment>` / FragmentContainerView
// declared in the activity layout never executed its add, and onCreateView /
// onViewCreated never fired. With the real loop (M1-M3) those posts now run;
// executePendingTransactions() here FLUSHES any still-pending transaction
// synchronously and the fragments move to the host's current state (RESUMED).
//
// R8 discipline: this must work even when the app's androidx/support classes
// are renamed (the Reddit case: androidx.lifecycle is renamed, getLifecycle()
// is NoSuchMethod). So we DO NOT hardcode androidx/support class names. We
// locate FragmentManagers by the PUBLIC method shape the app calls anyway
// (getSupportFragmentManager()/getFragmentManager()), and fall back to a
// duck-typed field scan for an object exposing executePendingTransactions().
// Everything is wrapped in try/catch and is a no-op for apps with no
// fragments (GL games), so it can never regress a working app.
package com.macapk.loader;

import android.app.Activity;
import android.content.Context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class FragmentLifecycle {
    private static final String TAG = "Mac.apk.FragmentLifecycle";
    private FragmentLifecycle() {}

    /** Flush + advance every FragmentManager reachable from the activity so
     *  its fragments reach the activity's (already RESUMED) state. Safe + a
     *  no-op for fragment-less apps. */
    public static void driveToResumed(Activity activity) {
        if (activity == null) return;
        List<Object> roots = new ArrayList<>();
        // THE APP'S OWN MANAGER FIRST, via the same app-loader-preferring lookup
        // FragmentInflaterFactory hosts on. Without this we drive a manager the
        // hosted fragment was never added to. MEASURED on Aegis 3.4.2
        // (2026-07-30): the fragment was instantiated, added and COMMITTED
        // successfully, yet this reported `0 fragment(s)` twice -- because
        // getSupportFragmentManager()/getFragmentManager() below returned our
        // framework android.app stub, whose getFragments() answers an EMPTY LIST.
        // An empty list is still `instanceof List`, so drive() treated it as a
        // real (fragment-less) manager and never fell through to the store
        // lookup. Two silent near-misses stacked: the wrong manager, and an
        // empty-but-valid answer from it.
        addIfFragmentManager(roots, getHostFragmentManager(activity));
        addIfFragmentManager(roots, callNoArg(activity, "getSupportFragmentManager"));
        addIfFragmentManager(roots, callNoArg(activity, "getFragmentManager"));
        if (roots.isEmpty()) {
            // Renamed-androidx fallback: scan the activity's fields for a
            // FragmentManager-shaped object (one with executePendingTransactions).
            Object fm = scanForFragmentManager(activity);
            addIfFragmentManager(roots, fm);
        }
        if (roots.isEmpty()) return;   // fragment-less app
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        int[] counts = new int[2];   // [0]=managers driven, [1]=fragments seen
        for (Object fm : roots) drive(fm, seen, counts, 0);
        if (counts[0] > 0) {
            NativeLog.i(TAG, "M4: drove " + counts[0] + " FragmentManager(s), "
                + counts[1] + " fragment(s) to the host state");
        }
    }

    /** Locate the activity's PRIMARY (support/androidx) FragmentManager the
     *  same R8-proof way driveToResumed does -- by the public method shape
     *  (getSupportFragmentManager()/getFragmentManager()), with a duck-typed
     *  field-scan fallback for renamed-androidx apps. Returns null for a
     *  fragment-less app. Used by FragmentInflaterFactory (Tier 3 M0) to host
     *  a Fragment instantiated from a <fragment>/FragmentContainerView tag. */
    public static Object getHostFragmentManager(Activity activity) {
        if (activity == null) return null;
        ClassLoader appCl = activity.getClass().getClassLoader();
        // Prefer the APP's own FragmentManager (support/androidx). Our framework
        // android.app.FragmentManagerImpl stub lives on the system loader, NOT the
        // AppClassLoader -- driving/hosting on it does nothing useful for an app
        // that uses support fragments, so try the app-loaded FM first.
        Object fm = callNoArg(activity, "getSupportFragmentManager");
        if (isAppFragmentManager(fm, appCl)) return fm;
        Object scanned = scanForFragmentManager(activity);
        if (isAppFragmentManager(scanned, appCl)) return scanned;
        // STRUCTURAL fallback (see the block comment above findFragmentManagerClass
        // below) -- tried BEFORE the framework stub, because that stub is a
        // CONFIDENTLY WRONG answer (non-null, right shape, wrong object) rather
        // than an honest "not found". MEASURED 2026-07-30 on AnkiDroid: neither
        // test above fires on that build (getSupportFragmentManager renamed to
        // `s()`; the FragmentManager lives one hop through a FragmentController
        // field the shallow scan above does not follow), so without this tier
        // getHostFragmentManager silently returned a fabricated
        // android.app.FragmentManagerImpl and FragmentInflaterFactory correctly
        // (but uselessly) refused to host on it.
        Object byType = viaFragmentManagerClass(activity, appCl);
        if (byType != null) return byType;
        // Fall back to getFragmentManager() (may be our framework stub for an
        // app that uses the deprecated android.app fragments) -- preserves the
        // pre-Tier-3 behavior for those apps.
        fm = callNoArg(activity, "getFragmentManager");
        if (fm != null && looksLikeFragmentManager(fm.getClass())) return fm;
        return scanned;   // last resort (may be null)
    }

    // ---- FragmentManager discovery via FragmentContainerView's own ctor shape ---
    // WHY (measured 2026-07-30 on AnkiDroid 2.24.0, com.ichi2.anki_22400300.apk).
    // The two tests above both depend on SOME original name surviving R8:
    // looksLikeFragmentManager needs "getFragments"/"executePendingTransactions"
    // to keep their names; looksLikeFragmentManagerByType (used by the field
    // scan) needs a class literally named "...FragmentTransaction" or
    // "...BackStackRecord" to survive, so it can recognise a manager by the
    // types its OWN methods traffic in. AnkiDroid's build renames EVERYTHING
    // under androidx.fragment.app to single/double-letter names -- BackStackRecord
    // and FragmentTransaction included -- so neither test can ever match (this is
    // a STRONGER obfuscation than Aegis 3.4.2, whose BackStackRecord/
    // FragmentManagerImpl names happened to survive). getSupportFragmentManager
    // is renamed too (`androidx.fragment.app.r0.s()` on the measured build), and
    // the shallow field scan above can't reach the manager either way:
    // FragmentActivity does not hold it as a direct field, only a
    // FragmentController (also renamed) that returns it from a method call.
    //
    // The one class R8 is STRUCTURALLY FORCED to leave intact is
    // androidx.fragment.app.FragmentContainerView: apps instantiate it by
    // fully-qualified-name reflection straight from XML
    // (<androidx.fragment.app.FragmentContainerView .../>), so AGP/R8's default
    // consumer keep rules for the fragment library preserve its name and public
    // API on every build -- this is not an AnkiDroid-specific fact, it is how R8
    // must treat any class referenced by name from a layout resource, the same
    // reason FragmentContainerView.class (unlike everything around it) appears
    // unrenamed in AnkiDroid's own converted jar. Its own AOSP source declares
    // exactly two public constructors:
    //   FragmentContainerView(Context, AttributeSet)
    //   FragmentContainerView(Context, AttributeSet, FragmentManager)
    // -- so the 3rd parameter's type IS the (possibly renamed) FragmentManager
    // class, discoverable with ZERO string matching. Once we have that Class
    // object, any no-arg method on the Activity's hierarchy whose return type is
    // assignable to it is the app's getSupportFragmentManager(), however R8
    // renamed it -- verified against AnkiDroid's own disassembled
    // androidx.fragment.app.r0 (FragmentActivity): its only such method is `s()`.

    private static final Map<ClassLoader, Class<?>> sFragmentManagerClassCache = new WeakHashMap<>();

    /** The Activity's live FragmentManager, found by first locating the (possibly
     *  renamed) FragmentManager CLASS via FragmentContainerView's own ctor shape,
     *  then scanning for a no-arg method on the activity whose return type is
     *  assignable to it. Null if the app has no FragmentContainerView on this
     *  loader (no fragments, or an app that never uses the class) or no such
     *  accessor is found. */
    private static Object viaFragmentManagerClass(Activity activity, ClassLoader appCl) {
        Class<?> fmClass = findFragmentManagerClass(appCl);
        if (fmClass == null) return null;
        Object v = callMethodReturning(activity, fmClass);
        if (v == null || v.getClass().getClassLoader() != appCl) return null;
        NativeLog.i(TAG, "getSupportFragmentManager found BY RETURN TYPE ("
            + fmClass.getName() + ") -- R8 renamed the accessor and no field held it directly");
        return v;
    }

    /** Resolve androidx.fragment.app.FragmentManager (however R8 renamed it) from
     *  FragmentContainerView's own 3-arg constructor -- see the block comment
     *  above. Cached per classloader (including a cached miss) since this runs on
     *  every inflate that might need it. */
    private static Class<?> findFragmentManagerClass(ClassLoader cl) {
        if (cl == null) return null;
        synchronized (sFragmentManagerClassCache) {
            if (sFragmentManagerClassCache.containsKey(cl)) return sFragmentManagerClassCache.get(cl);
        }
        Class<?> found = null;
        try {
            Class<?> fcv = Class.forName("androidx.fragment.app.FragmentContainerView", false, cl);
            for (Constructor<?> c : fcv.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length != 3) continue;
                if (!Context.class.isAssignableFrom(p[0])) continue;
                if (!android.util.AttributeSet.class.isAssignableFrom(p[1])) continue;
                if (p[2].isPrimitive() || p[2] == String.class) continue;
                found = p[2];
                break;
            }
        } catch (Throwable ignored) {}
        synchronized (sFragmentManagerClassCache) {
            sFragmentManagerClassCache.put(cl, found);
        }
        return found;
    }

    /** Find and call a no-arg, non-static method anywhere in target's class
     *  hierarchy whose return type is assignable to `want`, returning the first
     *  non-null result. The rename-proof getSupportFragmentManager() lookup: it
     *  does not care what the method is CALLED, only what it RETURNS. */
    private static Object callMethodReturning(Object target, Class<?> want) {
        if (target == null || want == null) return null;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Method[] ms;
            try { ms = c.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (Method m : ms) {
                if (m.getParameterCount() != 0) continue;
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                Class<?> rt = m.getReturnType();
                if (rt.isPrimitive() || rt == void.class) continue;
                if (!want.isAssignableFrom(rt)) continue;
                try {
                    m.setAccessible(true);
                    Object v = m.invoke(target);
                    if (v != null) return v;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    /** True if `o` looks like a FragmentManager AND is loaded by the app's
     *  classloader (i.e. the app's own renamed androidx/support FM, not our
     *  framework android.app stub on the system loader). */
    private static boolean isAppFragmentManager(Object o, ClassLoader appCl) {
        // Accept EITHER test. Name-only here would silently undo the two-pass
        // scan: androidx.fragment.app.FragmentManagerImpl -- the real manager,
        // whose class name survives R8 -- declares NEITHER getFragments() NOR
        // executePendingTransactions(), so a name-only gate rejects precisely the
        // object the type pass exists to find, and the scan falls back to the
        // merged store it was written to avoid.
        return o != null
            && (looksLikeFragmentManagerByType(o.getClass())
                || looksLikeFragmentManager(o.getClass()))
            && o.getClass().getClassLoader() == appCl;
    }

    /** Recursively executePendingTransactions() on a FragmentManager and each
     *  fragment's child FragmentManager, logging fragment view/resumed state. */
    private static void drive(Object fm, IdentityHashMap<Object, Boolean> seen,
                              int[] counts, int depth) {
        if (fm == null || depth > 8 || seen.put(fm, Boolean.TRUE) != null) return;
        counts[0]++;
        // Flush pending adds -> onAttach/onCreate/onCreateView/onViewCreated,
        // then the host's current state (STARTED/RESUMED) since the activity
        // dispatch already ran. Idempotent: no pending work -> returns false.
        try { callNoArg(fm, "executePendingTransactions"); }
        catch (Throwable ignored) {}
        // Walk the fragments (public getFragments() since support 26 / androidx).
        Object fragsObj = callNoArg(fm, "getFragments");
        // R8 can leave the MANAGER without getFragments() while the STORE keeps
        // it. Measured on Aegis 3.4.2: androidx.fragment.app.FragmentManagerImpl
        // declares no getFragments() at all, so this returned early and drove 0
        // fragments even though the add HAD committed -- the fragment existed and
        // was simply invisible to us, so it never advanced to onCreateView and
        // the app NPE'd on the null view it expected.
        //
        // The store is reachable FROM the manager, and we already know exactly
        // what it looks like: it is the same object the old name-based duck-type
        // used to mistake FOR the manager (Aegis's R8-merged
        // androidx.emoji2.text.MetadataRepo, carrying addFragment /
        // findActiveFragment / getFragments). So having fixed discovery to reject
        // it as a manager, we now use the manager to FIND it as a store.
        if (!(fragsObj instanceof List)) fragsObj = fragmentsViaStore(fm);
        if (!(fragsObj instanceof List)) return;
        for (Object frag : (List<?>) fragsObj) {
            if (frag == null) continue;
            counts[1]++;
            if (counts[1] <= 12) {
                Object view = callNoArg(frag, "getView");
                Object resumed = callNoArg(frag, "isResumed");
                NativeLog.i(TAG, "M4: fragment " + frag.getClass().getName()
                    + " view=" + (view != null) + " resumed=" + resumed);
            }
            // Nested fragments: drive the child FragmentManager too.
            Object childFm = callNoArg(frag, "getChildFragmentManager");
            drive(childFm, seen, counts, depth + 1);
        }
    }

    /** Find the FragmentStore hanging off a FragmentManager and ask IT for the
     *  fragment list, for an R8'd app whose manager lost getFragments().
     *  Shape-based: any non-static field of the manager whose value answers a
     *  no-arg getFragments() with a List. Deliberately NOT recursive and
     *  deliberately not accepting the manager itself. */
    private static Object fragmentsViaStore(Object fm) {
        for (Class<?> c = fm.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            java.lang.reflect.Field[] fs;
            try { fs = c.getDeclaredFields(); } catch (Throwable t) { continue; }
            for (java.lang.reflect.Field f : fs) {
                if (f.getType().isPrimitive()) continue;
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Object v;
                try { f.setAccessible(true); v = f.get(fm); } catch (Throwable t) { continue; }
                if (v == null || v == fm) continue;
                Object got = callNoArg(v, "getFragments");
                if (got instanceof List) {
                    NativeLog.i("Mac.apk.FragmentLifecycle",
                        "fragments found via STORE " + v.getClass().getName()
                        + " (manager " + fm.getClass().getName()
                        + " has no getFragments() -- R8)");
                    return got;
                }
            }
        }
        return null;
    }

    private static void addIfFragmentManager(List<Object> out, Object o) {
        if (o != null && !out.contains(o) && looksLikeFragmentManager(o.getClass())) {
            out.add(o);
        }
    }

    /** Duck-type by METHOD NAME. Kept, but demoted to a fallback -- see
     *  looksLikeFragmentManagerByType below for why a name test alone is not
     *  safe on an R8'd app. */
    private static boolean looksLikeFragmentManager(Class<?> cls) {
        return findNoArg(cls, "executePendingTransactions") != null
            || findNoArg(cls, "getFragments") != null;
    }

    // ---- FragmentManager identification: TYPE first, NAME only as fallback ---
    // WHY (measured 2026-07-30 on Aegis). R8 does HORIZONTAL CLASS MERGING, and
    // it produced this in Aegis's own converted jar:
    //
    //   public final class androidx.emoji2.text.MetadataRepo
    //           implements androidx.appcompat.view.ActionMode$Callback {
    //     public MetadataRepo(Typeface, MetadataList);
    //     public MetadataRepo(androidx.lifecycle.ViewModelStore, CreationExtras);
    //     public MetadataRepo(javax.crypto.Cipher);
    //     public void addFragment(androidx.fragment.app.Fragment);
    //     public java.util.List getFragments();          <-- matched the name test
    //     public boolean cancelSnackbarLocked(SnackbarRecord, int);
    //   }
    //
    // FragmentStore + MetadataRepo + ViewModelProvider + SnackbarManager + crypto
    // helpers + an ActionMode callback, all merged into ONE class. The
    // addFragment/findActiveFragment/getFragments group is androidx.fragment.app.
    // FragmentStore -- a fragment STORE, not a MANAGER. It has no
    // beginTransaction, so hosting failed and MainActivity NPE'd.
    //
    // BOTH HALVES of the old predicate are wrong on this app, which is why the
    // obvious fix does not work and was checked before being discarded:
    //   * "also require beginTransaction" fails -- that string appears NOWHERE
    //     in Aegis's jar (R8 renamed the method).
    //   * the class that IS the real manager, androidx.fragment.app.
    //     FragmentManagerImpl, declares NEITHER getFragments() NOR
    //     executePendingTransactions().
    // So the name test simultaneously REJECTS the real manager and ACCEPTS the
    // merged store.
    //
    // The asymmetry that makes a fix possible: R8 renamed the METHODS but the
    // androidx.fragment.app CLASS NAMES SURVIVED (56 .class files including
    // BackStackRecord, FragmentTransaction$Op and FragmentManagerImpl). So
    // identify a FragmentManager STRUCTURALLY -- by the types it traffics in --
    // and keep the name test only for un-obfuscated apps and our own
    // android.app stub.
    private static boolean isFragmentTransactionType(Class<?> t) {
        if (t == null || t.isPrimitive()) return false;
        String n = t.getName();
        return n.endsWith(".FragmentTransaction") || n.endsWith("$FragmentTransaction")
            || n.endsWith(".BackStackRecord");
    }

    /** STRONG test: does this class traffic in FragmentTransaction/BackStackRecord?
     *  A store does not; a manager must, because every mutation goes through one. */
    private static boolean looksLikeFragmentManagerByType(Class<?> cls) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            java.lang.reflect.Method[] ms;
            try { ms = c.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (java.lang.reflect.Method m : ms) {
                if (isFragmentTransactionType(m.getReturnType())) return true;
                for (Class<?> p : m.getParameterTypes()) {
                    if (isFragmentTransactionType(p)) return true;
                }
            }
        }
        return false;
    }

    /** Scan the activity's field graph (superclass chain) for a
     *  FragmentManager-shaped object, for apps whose getSupportFragmentManager
     *  was renamed by R8. */
    private static Object scanForFragmentManager(Object activity) {
        // TWO PASSES, and the order is the whole point. A single pass returns the
        // FIRST field that matches, so a weak name-only match found early beats a
        // strong type match found later -- which is exactly how Aegis ended up
        // with androidx.emoji2.text.MetadataRepo (an R8-merged FragmentStore)
        // instead of its real FragmentManagerImpl.
        Object strong = scanFields(activity, true);
        if (strong != null) return strong;
        return scanFields(activity, false);
    }

    /** @param byType true = the structural FragmentTransaction/BackStackRecord
     *                test; false = the legacy method-name duck-type. */
    private static Object scanFields(Object activity, boolean byType) {
        Class<?> c = activity.getClass();
        while (c != null && c != Object.class) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.getType().isPrimitive()) continue;
                // Skip STATICS. A static field can hold a FragmentManager
                // belonging to an activity that has already been destroyed, and
                // driving a transaction into a dead host's manager throws
                // "FragmentManager has been destroyed" -- which then reads as an
                // app bug rather than as us handing it a corpse.
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(activity);
                    if (v == null) continue;
                    boolean hit = byType ? looksLikeFragmentManagerByType(v.getClass())
                                         : looksLikeFragmentManager(v.getClass());
                    if (hit) return v;
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Object callNoArg(Object target, String name) {
        if (target == null) return null;
        Method m = findNoArg(target.getClass(), name);
        if (m == null) return null;
        try { return m.invoke(target); }
        catch (Throwable t) { return null; }
    }

    private static Method findNoArg(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                Method m = c.getDeclaredMethod(name);
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
}
