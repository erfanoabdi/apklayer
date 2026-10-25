// android.window.WindowOnBackInvokedDispatcher -- the per-WINDOW predictive-back
// dispatcher (@hide in AOSP). Ported from
//   frameworks/base core/java/android/window/WindowOnBackInvokedDispatcher.java
//   (android14-release, whole file; android16-release for the API 36 deltas),
// with the key-driven dispatch from
//   core/java/android/view/ViewRootImpl.java NativePreImeInputStage.doOnBackKeyEvent
//   (android14-release :6521-6590; android16-release :7662-7755).
// Full contract, corpus blast radius and the reported patches:
//   docs/predictive-back-onbackinvokeddispatcher_2026-09-26.md
//
// THE MODEL. In AOSP every window's ViewRootImpl owns one of these;
// PhoneWindow.getOnBackInvokedDispatcher() returns a ProxyOnBackInvokedDispatcher
// that buffers registrations until the ViewRootImpl exists and then forwards
// them verbatim, in order (ProxyOnBackInvokedDispatcher.transferCallbacksToDispatcher).
// Mac.apk has no ViewRootImpl per window, so android.view.Window owns this object
// directly -- the proxy's only observable effect (same callbacks, same order,
// same Checker) is preserved by construction.
//
// When BACK arrives for the FOCUSED window (com.macapk.loader.TouchDispatcher
// .dispatchKeyToFocusedWindow, the stand-in for ViewRootImpl's input stages),
// macapkOnBackKey() below runs AOSP's doOnBackKeyEvent for an app that opted in:
//   * the TOP callback (highest priority; within a priority the most recently
//     registered) receives onBackInvoked() on the un-canceled UP;
//   * an OnBackAnimationCallback also gets onBackStarted() on the first DOWN and
//     onBackCancelled() on a canceled UP (key BACK has no progress events --
//     AOSP sends onBackProgressed only for a gesture);
//   * API 34/35: the key is consumed there and never reaches the view tree;
//   * API 36: the key is FORWARDED, with the UP canceled after the callback ran,
//     "so that following stages cancel their back logic" (android16 VRI :7750-7753);
//   * API 33: android13's older stage -- the UP invokes the top callback even when
//     canceled, every other action is dropped, and there is no animation callback
//     (android13-release VRI :6109-6124).
// An app that did NOT opt in keeps the legacy KEYCODE_BACK path untouched, and its
// registrations are dropped with AOSP's warning (Checker below).
//
// BISECT GATE: MACAPK_BACK_INVOKED (default ON). =0 restores the exact pre-change
// path: Window hands out the old OnBackInvokedDispatcher.MacApkImpl (accepts every
// registration, never dispatches) and TouchDispatcher never calls into here.
package android.window;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** @hide */
public class WindowOnBackInvokedDispatcher implements OnBackInvokedDispatcher {
    private static final String TAG = "WindowOnBackDispatcher";

    /** Mac.apk bisect gate, read once per process (see the file header). */
    public static final boolean MACAPK_BACK_INVOKED =
            !"0".equals(System.getenv("MACAPK_BACK_INVOKED"));

    /** AOSP persist.wm.debug.predictive_back_always_enforce, as an env var: every
     *  window behaves as opted in. Diagnostic only; default off, like AOSP. */
    private static final boolean ALWAYS_ENFORCE_PREDICTIVE_BACK =
            "1".equals(System.getenv("MACAPK_PREDICTIVE_BACK_ALWAYS_ENFORCE"));

    // android.os.Build.VERSION_CODES values, spelled out so this file does not
    // depend on which named constants our Build.java happens to carry.
    private static final int TIRAMISU = 33, UPSIDE_DOWN_CAKE = 34,
            VANILLA_ICE_CREAM = 35, BAKLAVA = 36;

    /** main-branch WindowOnBackInvokedDispatcher guards both maps with mLock; the
     *  android14 file relied on the UI thread. Mac.apk needs the lock: a BACK on
     *  a SurfaceView/GL tree is routed on the AppKit thread while the app
     *  registers on the Android main thread. Callbacks are always invoked
     *  OUTSIDE it. */
    private final Object mLock = new Object();

    /** Convenience hashmap to quickly decide if a callback has been added. */
    private final HashMap<OnBackInvokedCallback, Integer> mAllCallbacks = new HashMap<>();
    /** Holds all callbacks by priorities. (public in AOSP, @VisibleForTesting) */
    public final TreeMap<Integer, ArrayList<OnBackInvokedCallback>>
            mOnBackInvokedCallbacks = new TreeMap<>();
    /** API 36: the single PRIORITY_SYSTEM_NAVIGATION_OBSERVER callback, kept out
     *  of the priority map (android16 registerSystemNavigationObserverCallback). */
    private OnBackInvokedCallback mSystemNavigationObserverCallback;
    private Checker mChecker;

    public WindowOnBackInvokedDispatcher(Context context) {
        mChecker = new Checker(context);
    }

    /** Mac.apk: a Window's context is assigned by its subclass constructor
     *  (ActivityWindow / DialogWindow set mContext themselves), so the window
     *  hands in a supplier that reads it at check time. AOSP gets the same
     *  effect from updateContext(), which PhoneWindow calls when it binds. */
    public WindowOnBackInvokedDispatcher(Supplier<Context> context) {
        mChecker = new Checker(context);
    }

    // TODO (AOSP's own): take an Executor for the callback to run on.
    @Override
    public void registerOnBackInvokedCallback(int priority, OnBackInvokedCallback callback) {
        if (mChecker.checkApplicationCallbackRegistration(priority, callback)) {
            registerOnBackInvokedCallbackUnchecked(callback, priority);
        }
    }

    /** Register a callback bypassing platform checks. This is used to register
     *  compatibility callbacks. */
    public void registerOnBackInvokedCallbackUnchecked(OnBackInvokedCallback callback, int priority) {
        synchronized (mLock) {
            if (priority == PRIORITY_SYSTEM_NAVIGATION_OBSERVER && sdkInt() >= BAKLAVA) {
                // android16: at most one observer; it is not a dispatch target.
                if (mAllCallbacks.containsKey(callback)) removeCallbackInternal(callback);
                mSystemNavigationObserverCallback = callback;
                return;
            }
            if (!mOnBackInvokedCallbacks.containsKey(priority)) {
                mOnBackInvokedCallbacks.put(priority, new ArrayList<>());
            }
            ArrayList<OnBackInvokedCallback> callbacks = mOnBackInvokedCallbacks.get(priority);
            // If callback has already been added, remove it and re-add it.
            if (mAllCallbacks.containsKey(callback)) {
                Integer prevPriority = mAllCallbacks.get(callback);
                mOnBackInvokedCallbacks.get(prevPriority).remove(callback);
                if (mOnBackInvokedCallbacks.get(prevPriority).isEmpty() && prevPriority != priority) {
                    mOnBackInvokedCallbacks.remove(prevPriority);
                }
            }
            if (mSystemNavigationObserverCallback == callback) {
                mSystemNavigationObserverCallback = null;
            }
            callbacks.add(callback);
            mAllCallbacks.put(callback, priority);
            // AOSP then pushes the new top to WindowManager
            // (setTopOnBackInvokedCallback). There is no window server here:
            // getTopCallback() is computed when BACK arrives, which is the only
            // thing the server-side copy is ever used for.
        }
    }

    @Override
    public void unregisterOnBackInvokedCallback(OnBackInvokedCallback callback) {
        synchronized (mLock) {
            if (mSystemNavigationObserverCallback == callback) {
                mSystemNavigationObserverCallback = null;
                return;
            }
            if (!mAllCallbacks.containsKey(callback)) return;
            removeCallbackInternal(callback);
            // AOSP: "We should call onBackCancelled() when an active callback is
            // removed from dispatcher" -- sendCancelledIfInProgress, which fires
            // only while a GESTURE's progress animator runs. A key-driven BACK
            // never starts that animator (VRI calls onBackStarted directly), and
            // Mac.apk has no back gesture, so there is never one to cancel.
        }
    }

    private void removeCallbackInternal(OnBackInvokedCallback callback) {
        Integer priority = mAllCallbacks.get(callback);
        ArrayList<OnBackInvokedCallback> callbacks = mOnBackInvokedCallbacks.get(priority);
        if (callbacks != null) {
            callbacks.remove(callback);
            if (callbacks.isEmpty()) mOnBackInvokedCallbacks.remove(priority);
        }
        mAllCallbacks.remove(callback);
    }

    @Override
    public void registerSystemOnBackInvokedCallback(OnBackInvokedCallback callback) {
        registerOnBackInvokedCallbackUnchecked(callback, OnBackInvokedDispatcher.PRIORITY_SYSTEM);
    }

    /** Clears all registered callbacks on the instance (AOSP: the window was
     *  detached -- ViewRootImpl.dispatchDetachedFromWindow -> detachFromWindow). */
    public void clear() {
        synchronized (mLock) {
            mAllCallbacks.clear();
            mOnBackInvokedCallbacks.clear();
            mSystemNavigationObserverCallback = null;
        }
    }

    /** Detaches the dispatcher instance from its window (AOSP: clear()). */
    public void detachFromWindow() { clear(); }

    public OnBackInvokedCallback getTopCallback() {
        synchronized (mLock) {
            if (mAllCallbacks.isEmpty()) return null;
            for (Integer priority : mOnBackInvokedCallbacks.descendingKeySet()) {
                ArrayList<OnBackInvokedCallback> callbacks = mOnBackInvokedCallbacks.get(priority);
                if (!callbacks.isEmpty()) return callbacks.get(callbacks.size() - 1);
            }
            return null;
        }
    }

    /** Mac.apk (debug / gate): the priority a callback is registered at, or null. */
    public Integer macapkPriorityOf(OnBackInvokedCallback callback) {
        synchronized (mLock) { return mAllCallbacks.get(callback); }
    }

    /** android16 tryInvokeSystemNavigationObserverCallback: the observer hears
     *  about a BACK only when the SYSTEM default is what handles it. */
    public void tryInvokeSystemNavigationObserverCallback() {
        OnBackInvokedCallback observer;
        synchronized (mLock) {
            OnBackInvokedCallback top = getTopCallback();
            Integer p = top != null ? mAllCallbacks.get(top) : null;
            if (p == null || p != PRIORITY_SYSTEM) return;
            observer = mSystemNavigationObserverCallback;
        }
        if (observer != null) observer.onBackInvoked();
    }

    /** The context the Checker decides against could differ from the one this
     *  dispatcher was built with; AOSP re-binds it from PhoneWindow. */
    public void updateContext(Context context) { mChecker = new Checker(context); }

    /** Returns false if the legacy back behavior should be used. */
    public boolean isOnBackInvokedCallbackEnabled() {
        return Checker.isOnBackInvokedCallbackEnabled(mChecker.getContext());
    }

    /** Returns false if the legacy back behavior should be used.
     *  <p>Legacy back behavior dispatches KEYCODE_BACK instead of invoking the
     *  application registered {@link OnBackInvokedCallback}. */
    public static boolean isOnBackInvokedCallbackEnabled(Context context) {
        return Checker.isOnBackInvokedCallbackEnabled(context);
    }

    // ---- key-driven dispatch (ViewRootImpl.NativePreImeInputStage) -------------

    /** Not opted in (or the gate is off): deliver the key the legacy way. */
    public static final int MACAPK_KEY_LEGACY = -1;
    /** Opted in but this window holds NO callback at all. See the note in
     *  macapkOnBackKey: in AOSP that cannot happen for an Activity, Dialog or
     *  PopupWindow window; the caller delivers the key the legacy way. */
    public static final int MACAPK_KEY_NO_CALLBACK = -2;
    /** AOSP InputStage results (ViewRootImpl.InputStage FORWARD /
     *  FINISH_HANDLED / FINISH_NOT_HANDLED): forward the (possibly canceled)
     *  event to the view tree, or finish it here. */
    public static final int FORWARD = 0;
    public static final int FINISH_HANDLED = 1;
    public static final int FINISH_NOT_HANDLED = 2;

    /** ViewRootImpl.doOnBackKeyEvent for this window. The callbacks run through
     *  `invoker` (inline, or posted to the main looper when BACK arrived on the
     *  AppKit thread -- AOSP runs them on the window's UI thread). The caller
     *  owns delivery: on FORWARD it hands the view tree the event, CANCELED if
     *  it is an UP (the android16 `keyEvent.cancel()`, done by the caller with
     *  KeyEvent.changeFlags because the event object is shared). */
    public int macapkOnBackKey(KeyEvent keyEvent, Executor invoker) {
        if (!MACAPK_BACK_INVOKED || keyEvent == null
                || keyEvent.getKeyCode() != KeyEvent.KEYCODE_BACK
                || !isOnBackInvokedCallbackEnabled()) {
            return MACAPK_KEY_LEGACY;
        }
        final int sdk = sdkInt();
        final boolean api36 = sdk >= BAKLAVA;
        final OnBackInvokedCallback topCallback = getTopCallback();
        final int action = keyEvent.getAction();
        final boolean canceled = keyEvent.isCanceled();
        if (sdk < UPSIDE_DOWN_CAKE) {
            // Android 13 has its own, simpler stage (android13-release
            // ViewRootImpl.NativePreImeInputStage.onProcess :6109-6124, fetched
            // 2026-09-26): on the UP the top callback runs WITHOUT an isCanceled
            // check and the key is FINISH_HANDLED; every other action is dropped
            // ("Drop other actions such as ACTION_DOWN" -> FINISH_NOT_HANDLED).
            // OnBackAnimationCallback does not exist before API 34, so there is no
            // onBackStarted/onBackCancelled here even if the object implements it.
            // topCallback == null is the same bridge as below (AOSP would drop the
            // DOWN and forward the UP, unreachable there because android13
            // Activity.onCreate :1660-1666 registers its default).
            if (topCallback == null) return MACAPK_KEY_NO_CALLBACK;
            if (action == KeyEvent.ACTION_UP) {
                invoker.execute(topCallback::onBackInvoked);
                return FINISH_HANDLED;
            }
            return FINISH_NOT_HANDLED;
        }
        if (topCallback == null) {
            // In AOSP this branch is unreachable for the windows apps actually
            // focus: Activity.onCreate registers its default (android14
            // Activity.java :1727-1733), Dialog.onStart registers onBackPressed
            // (Dialog.java :456-462), PopupDecorView registers dismiss (PopupWindow
            // .java :2744-2757). Until those patches land in Mac.apk's Activity /
            // Dialog / PopupWindow, the legacy key IS their default back
            // behaviour, so hand it back rather than swallowing BACK. The observer
            // still hears it: the default would have been PRIORITY_SYSTEM.
            if (api36 && action == KeyEvent.ACTION_UP && !canceled) {
                final OnBackInvokedCallback observer;
                synchronized (mLock) { observer = mSystemNavigationObserverCallback; }
                if (observer != null) invoker.execute(observer::onBackInvoked);
            }
            return MACAPK_KEY_NO_CALLBACK;
        }
        if (topCallback instanceof OnBackAnimationCallback) {
            final OnBackAnimationCallback animationCallback = (OnBackAnimationCallback) topCallback;
            switch (action) {
                case KeyEvent.ACTION_DOWN:
                    // AOSP (android14 VRI :6555-6563): "ACTION_DOWN is emitted
                    // twice ... 0 means the button was pressed. 1 means the button
                    // continues to be pressed (long press)." Mac.apk's host key
                    // bridge sends autorepeat DOWNs with repeat 0, so
                    // TouchDispatcher.macapkBackRepeat numbers them first.
                    if (keyEvent.getRepeatCount() == 0) {
                        final BackEvent backEvent = new BackEvent(0, 0, 0f,
                                api36 ? BackEvent.EDGE_NONE : BackEvent.EDGE_LEFT);
                        invoker.execute(() -> animationCallback.onBackStarted(backEvent));
                    }
                    break;
                case KeyEvent.ACTION_UP:
                    if (canceled) {
                        invoker.execute(animationCallback::onBackCancelled);
                    } else {
                        invoker.execute(() -> invokeTop(topCallback, api36));
                        if (!api36) return FINISH_HANDLED;
                    }
                    break;
                default:
                    break;
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (!canceled) {
                invoker.execute(() -> invokeTop(topCallback, api36));
                if (!api36) return FINISH_HANDLED;
            }
            // else AOSP: "Skip onBackInvoked(), reason: keyEvent.isCanceled=true"
        }
        return api36 ? FORWARD : FINISH_NOT_HANDLED;
    }

    private void invokeTop(OnBackInvokedCallback top, boolean api36) {
        if (api36) tryInvokeSystemNavigationObserverCallback();
        top.onBackInvoked();
    }

    static int sdkInt() {
        try { return android.os.Build.VERSION.SDK_INT; } catch (Throwable t) { return 0; }
    }

    // ---- per-root dispatchers for windows that are not an Activity or Dialog --

    /** AOSP gives EVERY window (a PopupWindow, a raw WindowManager.addView root)
     *  its own ViewRootImpl and therefore its own dispatcher. Mac.apk's overlay
     *  windows have no Window object, so their dispatcher lives here, keyed by
     *  the root view. Weak keys; com.macapk.loader.TouchDispatcher prunes a root
     *  whose window is gone (AOSP: detachFromWindow -> clear) before a BACK. */
    private static final java.util.WeakHashMap<android.view.View, WindowOnBackInvokedDispatcher>
            sRootDispatchers = new java.util.WeakHashMap<>();

    /** The dispatcher of the window whose root view is `root` (created on first
     *  use with the root's context, as ViewRootImpl does with its own). */
    public static WindowOnBackInvokedDispatcher macapkForRoot(android.view.View root) {
        if (root == null) return null;
        synchronized (sRootDispatchers) {
            WindowOnBackInvokedDispatcher d = sRootDispatchers.get(root);
            if (d == null) {
                final java.lang.ref.WeakReference<android.view.View> ref =
                        new java.lang.ref.WeakReference<>(root);
                d = new WindowOnBackInvokedDispatcher(() -> {
                    android.view.View v = ref.get();
                    return v != null ? v.getContext() : null;
                });
                sRootDispatchers.put(root, d);
            }
            return d;
        }
    }

    /** The dispatcher a root already has, or null (never creates one). Lets a
     *  view that is being DETACHED still find the dispatcher it registered on
     *  (PopupDecorView.onDetachedFromWindow unregisters there), after its layer
     *  has already left the window stack. */
    public static WindowOnBackInvokedDispatcher macapkPeekRoot(android.view.View root) {
        if (root == null) return null;
        synchronized (sRootDispatchers) { return sRootDispatchers.get(root); }
    }

    /** Forget a root whose window was removed (AOSP detachFromWindow). */
    public static void macapkDetachRoot(android.view.View root) {
        WindowOnBackInvokedDispatcher d;
        synchronized (sRootDispatchers) { d = sRootDispatchers.remove(root); }
        if (d != null) d.detachFromWindow();
    }

    /** Snapshot of the roots that currently hold a dispatcher (for pruning). */
    public static java.util.List<android.view.View> macapkRoots() {
        synchronized (sRootDispatchers) { return new ArrayList<>(sRootDispatchers.keySet()); }
    }

    /**
     * Class used to check whether a callback can be registered or not. This is
     * meant to be shared with {@link ProxyOnBackInvokedDispatcher} which needs to
     * do the same checks. (AOSP; Mac.apk has no proxy -- see the file header.)
     */
    public static class Checker {
        private final Supplier<Context> mContext;

        public Checker(Context context) {
            final java.lang.ref.WeakReference<Context> ref = new java.lang.ref.WeakReference<>(context);
            mContext = ref::get;
        }

        Checker(Supplier<Context> context) { mContext = context; }

        /**
         * Checks whether the given callback can be registered with the given priority.
         * @return true if the callback can be added.
         * @throws IllegalArgumentException if the priority is negative.
         */
        public boolean checkApplicationCallbackRegistration(int priority,
                OnBackInvokedCallback callback) {
            if (!isOnBackInvokedCallbackEnabled(getContext())
                    && !(callback instanceof CompatOnBackInvokedCallback)) {
                warn("OnBackInvokedCallback is not enabled for the application."
                        + "\nSet 'android:enableOnBackInvokedCallback=\"true\"' in the"
                        + " application manifest.");
                return false;
            }
            // android16 predictiveBackPrioritySystemNavigationObserver(): the one
            // negative priority an app may use. Below API 36 it does not exist.
            if (priority < 0 && !(priority == PRIORITY_SYSTEM_NAVIGATION_OBSERVER
                    && sdkInt() >= BAKLAVA)) {
                throw new IllegalArgumentException("Application registered OnBackInvokedCallback "
                        + "cannot have negative priority. Priority: " + priority);
            }
            Objects.requireNonNull(callback);
            return true;
        }

        private Context getContext() {
            try { return mContext.get(); } catch (Throwable t) { return null; }
        }

        /** AOSP isOnBackInvokedCallbackEnabled(Context) (android14 Checker; the
         *  android16 static split into (ActivityInfo, ApplicationInfo) is the same
         *  decision). The package-parser half -- what the flags ARE -- comes from
         *  ManifestBackFlags until our parser records them; see that file. */
        static boolean isOnBackInvokedCallbackEnabled(Context context) {
            // AOSP ENABLE_PREDICTIVE_BACK (persist.wm.debug.predictive_back,
            // default 1) -- our gate plays that role.
            if (!MACAPK_BACK_INVOKED) return false;
            // The API does not exist below Android 13; an app presented a lower
            // level cannot have opted in to it.
            final int sdk = sdkInt();
            if (sdk < TIRAMISU) return false;
            if (ALWAYS_ENFORCE_PREDICTIVE_BACK) return true;
            // If the context is null, return false to use legacy back.
            if (context == null) return false;
            // Check if the context is from an activity.
            while ((context instanceof ContextWrapper) && !(context instanceof Activity)) {
                Context base = ((ContextWrapper) context).getBaseContext();
                if (base == null || base == context) break;
                context = base;
            }
            android.content.pm.ApplicationInfo ai;
            try { ai = context.getApplicationInfo(); } catch (Throwable t) { ai = null; }
            if (ai == null) return false;
            final ManifestBackFlags flags = ManifestBackFlags.of(ai);
            // Activity: <activity android:enableOnBackInvokedCallback> wins when
            // it is SET (ActivityInfo.hasOnBackInvokedCallbackEnabled, API 34).
            if (context instanceof Activity && sdk >= UPSIDE_DOWN_CAKE) {
                String cls;
                try { cls = ((Activity) context).getComponentName().getClassName(); }
                catch (Throwable t) { cls = context.getClass().getName(); }
                Boolean act = flags.activities.get(cls);
                if (act != null) return act;
            }
            // Application: the attribute when present, else the parser default --
            // false through Android 15, `targetSdk > VANILLA_ICE_CREAM` on 16.
            if (flags.application != null) return flags.application;
            if (sdk >= BAKLAVA) {
                int target = ai.targetSdkVersion > 0 ? ai.targetSdkVersion : flags.targetSdk;
                return target > VANILLA_ICE_CREAM;
            }
            return false;
        }

        private static void warn(String msg) {
            try { com.macapk.loader.NativeLog.w(TAG, msg); } catch (Throwable ignored) { }
        }
    }
}
