// android.window.OnBackInvokedDispatcher -- registers/unregisters
// OnBackInvokedCallback instances for predictive back (API 33+). AOSP
// frameworks/base core/java/android/window/OnBackInvokedDispatcher.java
// declares this as an INTERFACE with two abstract methods
// (registerOnBackInvokedCallback / unregisterOnBackInvokedCallback), four
// PRIORITY_* int constants, and two no-op default methods
// (registerSystemOnBackInvokedCallback, setImeOnBackInvokedDispatcher).
//
// ⚠ RETIRED THE CLASS-VS-INTERFACE DIVERGENCE, 2026-08-21. This file used to
// say: "KEPT AS A CONCRETE CLASS HERE, not converted to an interface, because
// Window.java already does `new OnBackInvokedDispatcher()` ... Converting this
// type to an interface would stop Window.java from compiling, which is outside
// this file's ownership to repair. Retire condition: if a future session changes
// Window.java (and Activity.java / Dialog.java) to obtain their dispatcher
// through a static factory instead of `new OnBackInvokedDispatcher()`, this can
// become the pure AOSP interface with a package-private impl class."
//
// That retire condition is met below, and it turned out to be urgent rather than
// cosmetic. AOSP declares this type an INTERFACE
// (android14-release core/java/android/window/OnBackInvokedDispatcher.java), and
// androidx.activity's Api33Impl touches it as one. A CLASS where the bytecode
// expects an interface is not a near-miss: the JVM throws
//     java.lang.IncompatibleClassChangeError:
//     Found class android.window.OnBackInvokedDispatcher, but interface was expected
// MEASURED: that single error killed GLSurfaceView lifecycle delivery and took
// unciv from RICH/26,667 colours at presented 31 to BLANK/distinct=1 at 33, 34
// AND 35. It is also why subway and mastodon broke under the per-app clamp. One
// declaration kind gated the entire API-level campaign above 32.
//
// The kind of a type is part of its ABI. When mirroring an AOSP type, the
// interface/class decision is never a matter of local convenience.
//
// setImeOnBackInvokedDispatcher is omitted: its parameter type
// (ImeOnBackInvokedDispatcher) does not exist anywhere in this tree, is
// IME-author-only surface, and no corpus app reaches it. Add both the type
// and the method together if a future session needs it.
//
// Dispatch order (AOSP OnBackInvokedCallback's class javadoc): "callbacks...
// are invoked in reverse order in which they are added within the same
// priority. Between different priorities, callbacks with higher priority are
// invoked first." dispatchOnBackInvoked() below implements exactly that.
// Real AOSP triggers dispatch through a WindowManagerService Binder callback
// that app code never calls directly. ⚠ UPDATED 2026-09-26: the REAL
// implementation is now android.window.WindowOnBackInvokedDispatcher (one per
// window, owned by android.view.Window; BACK reaches it through
// com.macapk.loader.TouchDispatcher.dispatchKeyToFocusedWindow). MacApkImpl
// below is kept ONLY as the MACAPK_BACK_INVOKED=0 bisect path -- it is exactly
// what every window handed out before, registrations accepted and never
// dispatched -- and as the pre-window fallback Activity / Dialog use. Its
// dispatchOnBackInvoked() hook never had a caller. See
// docs/predictive-back-onbackinvokeddispatcher_2026-09-26.md.
package android.window;

public interface OnBackInvokedDispatcher {
    // AOSP priority constants (frameworks/base same file). PRIORITY_SYSTEM
    // and PRIORITY_SYSTEM_NAVIGATION_OBSERVER are @hide platform-internal in
    // AOSP (no public app references them); kept for completeness since this
    // tree draws no SDK-stub/hidden-API line.
    int PRIORITY_SYSTEM_NAVIGATION_OBSERVER = -2;
    int PRIORITY_SYSTEM = -1;
    int PRIORITY_DEFAULT = 0;
    int PRIORITY_OVERLAY = 1000000;

    void registerOnBackInvokedCallback(int priority, OnBackInvokedCallback callback);
    void unregisterOnBackInvokedCallback(OnBackInvokedCallback callback);

    /** AOSP default: registers a callback at system priority. No system
     *  back-animation owner exists in Mac.apk, so this stays a no-op,
     *  matching AOSP's own empty default body. */
    default void registerSystemOnBackInvokedCallback(OnBackInvokedCallback callback) {}

    /** Mac.apk-internal, NOT part of the AOSP interface: invokes the
     *  highest-priority, most-recently-registered callback and returns true, or
     *  returns false if none is registered so the caller can fall back to the
     *  legacy onBackPressed()/finish() path. Defaulted so the type stays
     *  implementable by anything AOSP-shaped. */
    default boolean dispatchOnBackInvoked() { return false; }

    /** The concrete dispatcher every Mac.apk caller should obtain through
     *  {@link #macapkNew()} rather than by construction -- that indirection is
     *  precisely what lets this type keep AOSP's interface kind. */
    final class MacApkImpl implements OnBackInvokedDispatcher {
        private static final class Entry {
            final int priority;
            final OnBackInvokedCallback callback;
            Entry(int p, OnBackInvokedCallback c) { priority = p; callback = c; }
        }
        // Append-only registration order, so "most recently added wins within a
        // priority" (see dispatchOnBackInvoked) is a plain forward scan.
        private final java.util.ArrayList<Entry> mCallbacks = new java.util.ArrayList<>();

        @Override
        public void registerOnBackInvokedCallback(int priority, OnBackInvokedCallback callback) {
            if (callback == null) return;
            synchronized (mCallbacks) { mCallbacks.add(new Entry(priority, callback)); }
        }

        @Override
        public void unregisterOnBackInvokedCallback(OnBackInvokedCallback callback) {
            synchronized (mCallbacks) {
                for (int i = mCallbacks.size() - 1; i >= 0; i--) {
                    if (mCallbacks.get(i).callback == callback) { mCallbacks.remove(i); return; }
                }
            }
        }

        /** Dispatch order (AOSP OnBackInvokedCallback's class javadoc):
         *  "callbacks... are invoked in reverse order in which they are added
         *  within the same priority. Between different priorities, callbacks
         *  with higher priority are invoked first." The forward scan with `>=`
         *  implements exactly that. */
        @Override
        public boolean dispatchOnBackInvoked() {
            Entry top = null;
            synchronized (mCallbacks) {
                for (Entry e : mCallbacks) {
                    if (top == null || e.priority >= top.priority) top = e;
                }
            }
            if (top == null) return false;
            top.callback.onBackInvoked();
            return true;
        }
    }

    /** The factory the retire condition called for. Callers must use this
     *  instead of `new OnBackInvokedDispatcher()`, which an interface cannot
     *  offer -- and that constraint is the whole point. */
    static OnBackInvokedDispatcher macapkNew() { return new MacApkImpl(); }
}
