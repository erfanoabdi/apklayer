// AutoRefresh -- Mac.apk-platform UX policy: when an Activity becomes
// visible, walk its view tree for SwipeRefreshLayout instances with a
// registered OnRefreshListener and invoke the listener once, after a
// short stabilization delay.
//
// Rationale: on a real Android phone the user has a battery profile,
// so AOSP defers periodic refresh work (e.g. F-Droid's RepoUpdateWorker
// with intervalDuration=24h and flexDuration=15min) to roughly 23h45m
// after first enqueue, then runs the worker within the flex window.
// The user-experience is "freshly installed apps show no content until
// you swipe-pull-down to refresh." On a desktop runtime that's not the
// expected UX -- the user clicks Launch and expects to see content
// promptly. Mac.apk's policy: treat the act of becoming-visible as
// an implicit pull-to-refresh gesture, invoking whatever
// OnRefreshListener the app already wired up for its real swipe
// gesture. Generic across any Android app that uses the
// androidx.swiperefreshlayout.widget.SwipeRefreshLayout pattern -- the
// default for content-refresh UX on Android since ~2014.
//
// AOSP-faithful trade-off:
// - The listener the app registered is the AOSP-canonical refresh entry
//   point. We invoke it the same way a real touch gesture would.
// - The 2-second delay lets the activity's onCreate -> onStart ->
//   onResume chain settle (the listener is typically registered in a
//   Fragment's onCreateView, which runs after onResume on first show).
// - The "already refreshed" guard fires per Activity instance once per
//   JVM session; subsequent setContentView changes (e.g. tab switches
//   inside the same Activity that don't create a new Activity) do NOT
//   re-trigger. If the user wants to refresh again they can wait for
//   the next periodic, or in a future Mac.apk version, swipe.
//
// Deviation from real Android: real Android does NOT auto-trigger
// onRefresh on every launch. This is a deliberate platform choice.
// If a future APK does NOT want auto-refresh, it could be disabled
// via a future opt-out preference; until that's needed, the default
// is "refresh on launch" because the typical app expects fresh data
// on launch and the user has no other gesture path yet.
package com.macapk.loader;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoRefresh {
    private static final String TAG = "Mac.apk.AutoRefresh";

    /** How long to wait after onResume before walking the tree. Long
     *  enough for the Fragment's onCreateView (which typically
     *  registers the OnRefreshListener) to have run. */
    private static final long DELAY_MS = 2000;

    /** Per-Activity-instance guard. Identity-hashCode keyed so the
     *  same Activity instance doesn't re-trigger if start() is
     *  somehow re-invoked, while a NEW Activity (e.g. after the user
     *  finishes and re-launches the app from the launcher) gets a
     *  fresh refresh trigger. */
    private static final Set<Integer> alreadyRefreshed = ConcurrentHashMap.newKeySet();

    private AutoRefresh() {}

    /** Hook called from ActivityHost.start right after onResume. */
    public static void scheduleForActivity(Activity activity) {
        if (activity == null) return;
        // RETIRED (default-OFF; set MACAPK_AUTOREFRESH=1 to re-enable). This was
        // a workaround for apps (notably F-Droid) showing no content on launch
        // because their real auto-update path produced nothing. Faking a
        // pull-to-refresh on every launch is a deliberate non-AOSP deviation
        // (real Android never auto-triggers onRefresh). The real cause was a
        // framework bug -- SQLiteCursorImpl.moveToPosition(-1) didn't reset the
        // statement, so Room @Relation queries (F-Droid's getRepositories())
        // returned empty and the first-run RepoUpdateWorker found no repos to
        // fetch. With that fixed, F-Droid auto-loads on first launch via its
        // genuine RepoUpdateManager path, so this band-aid is off by default.
        if (!"1".equals(System.getenv("MACAPK_AUTOREFRESH"))) return;
        int key = System.identityHashCode(activity);
        if (!alreadyRefreshed.add(key)) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { walkAndTrigger(activity); }
            catch (Throwable t) {
                NativeLog.w(TAG, "auto-refresh trigger threw: " + t);
            }
        }, DELAY_MS);
    }

    private static void walkAndTrigger(Activity activity) {
        Window w = activity.getWindow();
        if (w == null) return;
        View decor = w.getDecorView();
        if (decor == null) return;
        List<View> swipeRefreshLayouts = new ArrayList<>();
        collectSwipeRefreshLayouts(decor, swipeRefreshLayouts,
                                    new java.util.IdentityHashMap<>());
        if (swipeRefreshLayouts.isEmpty()) {
            NativeLog.i(TAG, "no SwipeRefreshLayout found in tree; nothing to refresh");
            return;
        }
        NativeLog.i(TAG, "found " + swipeRefreshLayouts.size()
            + " SwipeRefreshLayout instance(s)");
        for (View srl : swipeRefreshLayouts) {
            triggerOnRefresh(srl);
        }
    }

    /** Walk children recursively. SwipeRefreshLayout's class name ends
     *  in "SwipeRefreshLayout" (the androidx widget). AOSP and
     *  androidx names match the same pattern. Cycle-safe via the
     *  IdentityHashMap (some custom ViewGroups have parent/child
     *  reentry). */
    private static void collectSwipeRefreshLayouts(View v,
                                                   List<View> out,
                                                   java.util.IdentityHashMap<View, Boolean> seen) {
        if (v == null || seen.put(v, Boolean.TRUE) != null) return;
        String name = v.getClass().getName();
        if (name.endsWith(".SwipeRefreshLayout") || name.endsWith("$SwipeRefreshLayout")) {
            out.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int n = vg.getChildCount();
            for (int i = 0; i < n; i++) {
                collectSwipeRefreshLayouts(vg.getChildAt(i), out, seen);
            }
        }
    }

    /** AOSP SwipeRefreshLayout exposes setRefreshing(boolean) for the
     *  spinner state and stores the registered listener in a private
     *  field. setOnRefreshListener / mListener has been stable since
     *  the widget's introduction; we look it up via reflection so we
     *  work against either the AOSP-shipped class or the androidx
     *  copy from the APK. We invoke setRefreshing(true) so the
     *  spinner is shown (matches real swipe gesture behavior), then
     *  reflectively call mListener.onRefresh(). The listener does
     *  the actual refresh work (in F-Droid, that's
     *  RepoUpdateManager.updateRepos() -> HttpDownloader -> HTTPS
     *  fetch of index-v2.json -> AppMetadata table populate). */
    private static void triggerOnRefresh(View srl) {
        String cls = srl.getClass().getName();
        try {
            try {
                Method setRefreshing = srl.getClass().getMethod("setRefreshing", boolean.class);
                setRefreshing.invoke(srl, true);
            } catch (Throwable t) {
                NativeLog.w(TAG, "setRefreshing(true) on " + cls + " threw: " + t);
            }
            Field listenerField = findField(srl.getClass(), "mListener");
            if (listenerField == null) {
                NativeLog.w(TAG, "no mListener field on " + cls + "; skipping onRefresh");
                return;
            }
            listenerField.setAccessible(true);
            Object listener = listenerField.get(srl);
            if (listener == null) {
                NativeLog.i(TAG, cls + " has no OnRefreshListener registered; skipping");
                return;
            }
            Method onRefresh = listener.getClass().getMethod("onRefresh");
            onRefresh.invoke(listener);
            NativeLog.i(TAG, "triggered onRefresh on " + cls
                + " (listener=" + listener.getClass().getName() + ")");
        } catch (Throwable t) {
            NativeLog.w(TAG, "triggerOnRefresh on " + cls + " threw: " + t);
        }
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
}
