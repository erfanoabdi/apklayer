package android.view;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/** android.view.ViewTreeObserver -- the per-view-hierarchy event hub.
 *  Apps register listeners for layout / draw / focus / scroll events and
 *  the framework dispatches them at the canonical points of a traversal.
 *
 *  AOSP fidelity (v1.0.418): real listener storage + real dispatch. There
 *  is ONE observer per view tree (owned by the root, returned by every
 *  View.getViewTreeObserver() in that tree), and ViewRootImpl's
 *  performTraversals fires dispatchOnGlobalLayout() after layout and
 *  dispatchOnPreDraw() right before draw. Mac.apk's ViewCanvasDriver
 *  drives those same dispatches each frame.
 *
 *  Why this matters: AndroidComposeView implements OnGlobalLayoutListener /
 *  OnScrollChangedListener / OnTouchModeChangeListener and flushes its
 *  AndroidView interop-layout callbacks through the observer; and a vast
 *  amount of AndroidX (RecyclerView prefetch, fragment transitions,
 *  Snackbar, popups, every "measure then act once" idiom) adds an
 *  OnPreDrawListener that must fire exactly once after layout / before the
 *  next draw. With the prior inert no-op observer all of that silently
 *  dropped. (Found via the AOSP-conformance audit; see
 *  docs/aosp_conformance_backlog.md.)
 *
 *  Listener lists are CopyOnWriteArrayList (AOSP uses CopyOnWriteArray) so a
 *  listener that add/removes itself during dispatch -- the OnPreDrawListener
 *  one-shot pattern, which removeOnPreDrawListener()s itself from inside
 *  onPreDraw() -- does not ConcurrentModification the in-progress iteration. */
public class ViewTreeObserver {
    // Process-environment snapshots for the per-draw / per-frame / per-layout /
    // per-event sites below (debug/w9textdraw_verify/hoist_getenv.py). A
    // System.getenv call encodes the name and hashes it on every call; the
    // environment it reads is copied once at JVM start (JDK ProcessEnvironment,
    // "subsequent calls to putenv/setenv from C will not be visible from Java
    // code"), so one read at class init is the same value. First in the body, so
    // no other static initializer of this class can observe them unset.
    private static final String ENV_MACAPK_AGDK_TRACE = System.getenv("MACAPK_AGDK_TRACE");

    public ViewTreeObserver() {}

    /** AOSP-public layout-change callback. */
    public interface OnGlobalLayoutListener {
        void onGlobalLayout();
    }
    /** AOSP-public pre-draw hook -- return false to cancel current draw. */
    public interface OnPreDrawListener {
        boolean onPreDraw();
    }
    /** AOSP-public draw-completion hook. androidx.activity uses this
     *  for the "fully drawn" reporting that gates the
     *  reportFullyDrawn() API. */
    public interface OnDrawListener {
        void onDraw();
    }
    /** AOSP-public scroll-position listener. */
    public interface OnScrollChangedListener {
        void onScrollChanged();
    }
    /** AOSP-public focus listener (API 18+). */
    public interface OnGlobalFocusChangeListener {
        void onGlobalFocusChanged(View oldFocus, View newFocus);
    }
    /** AOSP-public touch-mode listener. */
    public interface OnTouchModeChangeListener {
        void onTouchModeChanged(boolean isInTouchMode);
    }
    /** AOSP-public window-attach listener (API 18+). */
    public interface OnWindowAttachListener {
        void onWindowAttached();
        void onWindowDetached();
    }
    /** AOSP-public window-focus listener. */
    public interface OnWindowFocusChangeListener {
        void onWindowFocusChanged(boolean hasFocus);
    }
    /** AOSP-public window-visibility listener. Fires when the view tree's
     *  host window's visibility changes (e.g. View.VISIBLE/INVISIBLE/GONE). */
    public interface OnWindowVisibilityChangeListener {
        void onWindowVisibilityChanged(int visibility);
    }
    /** AOSP-public system-visibility listener (API 16+). */
    public interface OnSystemUiVisibilityChangeListener {
        void onSystemUiVisibilityChange(int visibility);
    }
    /** AOSP-public computeInternalInsetsListener. */
    public interface OnComputeInternalInsetsListener {
        void onComputeInternalInsets(Object inoutInfo);
    }

    // Listener storage. CopyOnWriteArrayList => dispatch-time add/remove safe.
    private final CopyOnWriteArrayList<OnGlobalLayoutListener> mOnGlobalLayoutListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnPreDrawListener> mOnPreDrawListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnDrawListener> mOnDrawListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnScrollChangedListener> mOnScrollChangedListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnGlobalFocusChangeListener> mOnGlobalFocusListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnTouchModeChangeListener> mOnTouchModeChangeListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnWindowAttachListener> mOnWindowAttachListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnWindowFocusChangeListener> mOnWindowFocusListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnWindowVisibilityChangeListener> mOnWindowVisibilityListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OnSystemUiVisibilityChangeListener> mOnSystemUiVisibilityListeners = new CopyOnWriteArrayList<>();
    // AOSP registerFrameCommitCallback storage: a one-shot list drained by
    // captureFrameCommitCallbacks() (mirrors ThreadedRenderer's pull, not a
    // push dispatch) rather than a standing CopyOnWriteArrayList like the
    // listener families above.
    private ArrayList<Runnable> mOnFrameCommitListeners;

    private boolean mAlive = true;

    public void addOnGlobalLayoutListener(OnGlobalLayoutListener l) {
        if (l != null) mOnGlobalLayoutListeners.add(l);
        // v1.0.584 AGDK diagnostic (MACAPK_AGDK_TRACE): GameActivity registers its
        // OnGlobalLayoutListener on mSurfaceView.getViewTreeObserver() to drive
        // onContentRectChangedNative -> APP_CMD_CONTENT_RECT_CHANGED (the command
        // MC's android_main waits on after INIT_WINDOW to start rendering). Log
        // WHICH observer instance receives it so we can tell whether it lands in
        // the driven root observer or a floating pre-attach one.
        if (l != null && "1".equals(ENV_MACAPK_AGDK_TRACE)) {
            try { com.macapk.loader.NativeLog.i("AGDKtrace",
                "addOnGlobalLayoutListener " + l.getClass().getName()
                + " -> observer@" + Integer.toHexString(System.identityHashCode(this))
                + " (now " + mOnGlobalLayoutListeners.size() + " listeners)"); }
            catch (Throwable ignored) {}
        }
    }
    public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l) { mOnGlobalLayoutListeners.remove(l); }
    /** Deprecated AOSP alias for removeOnGlobalLayoutListener. */
    public void removeGlobalOnLayoutListener(OnGlobalLayoutListener l) { mOnGlobalLayoutListeners.remove(l); }
    public void addOnPreDrawListener(OnPreDrawListener l) { if (l != null) mOnPreDrawListeners.add(l); }
    public void removeOnPreDrawListener(OnPreDrawListener l) { mOnPreDrawListeners.remove(l); }
    public void addOnDrawListener(OnDrawListener l) { if (l != null) mOnDrawListeners.add(l); }
    public void removeOnDrawListener(OnDrawListener l) { mOnDrawListeners.remove(l); }
    public void addOnScrollChangedListener(OnScrollChangedListener l) { if (l != null) mOnScrollChangedListeners.add(l); }
    public void removeOnScrollChangedListener(OnScrollChangedListener l) { mOnScrollChangedListeners.remove(l); }
    public void addOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l) { if (l != null) mOnGlobalFocusListeners.add(l); }
    public void removeOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l) { mOnGlobalFocusListeners.remove(l); }
    public void addOnTouchModeChangeListener(OnTouchModeChangeListener l) { if (l != null) mOnTouchModeChangeListeners.add(l); }
    public void removeOnTouchModeChangeListener(OnTouchModeChangeListener l) { mOnTouchModeChangeListeners.remove(l); }
    public void addOnWindowAttachListener(OnWindowAttachListener l) { if (l != null) mOnWindowAttachListeners.add(l); }
    public void removeOnWindowAttachListener(OnWindowAttachListener l) { mOnWindowAttachListeners.remove(l); }
    public void addOnWindowFocusChangeListener(OnWindowFocusChangeListener l) { if (l != null) mOnWindowFocusListeners.add(l); }
    public void removeOnWindowFocusChangeListener(OnWindowFocusChangeListener l) { mOnWindowFocusListeners.remove(l); }
    public void addOnWindowVisibilityChangeListener(OnWindowVisibilityChangeListener l) { if (l != null) mOnWindowVisibilityListeners.add(l); }
    public void removeOnWindowVisibilityChangeListener(OnWindowVisibilityChangeListener l) { mOnWindowVisibilityListeners.remove(l); }
    public void addOnSystemUiVisibilityChangeListener(OnSystemUiVisibilityChangeListener l) { if (l != null) mOnSystemUiVisibilityListeners.add(l); }
    public void removeOnSystemUiVisibilityChangeListener(OnSystemUiVisibilityChangeListener l) { mOnSystemUiVisibilityListeners.remove(l); }

    /** AOSP: registers a one-shot callback invoked once the current rendering
     *  content has been submitted to the swap chain (hardware-rendering only;
     *  a no-op path just never drains/fires it). Mirrors ViewTreeObserver's
     *  own storage; the actual post-frame-commit invocation is the render
     *  driver's job (it calls captureFrameCommitCallbacks() then runs each). */
    public void registerFrameCommitCallback(Runnable callback) {
        if (callback == null) return;
        if (mOnFrameCommitListeners == null) mOnFrameCommitListeners = new ArrayList<>();
        mOnFrameCommitListeners.add(callback);
    }
    /** AOSP: drains and returns the pending frame-commit callbacks (null if
     *  none registered), same take-ownership contract as the real
     *  ViewTreeObserver so a caller cannot double-fire a callback. */
    public ArrayList<Runnable> captureFrameCommitCallbacks() {
        ArrayList<Runnable> ret = mOnFrameCommitListeners;
        mOnFrameCommitListeners = null;
        return ret;
    }
    /** AOSP: returns false (not removed) once the callback has already been
     *  captured for firing, matching the real "may already be pending
     *  execution" contract. */
    public boolean unregisterFrameCommitCallback(Runnable callback) {
        if (mOnFrameCommitListeners == null) return false;
        return mOnFrameCommitListeners.remove(callback);
    }

    public boolean isAlive() { return mAlive; }

    /** Called after performLayout. AOSP iterates the listeners and invokes
     *  onGlobalLayout() on each. */
    private long macapkGlDispatchCount = 0;
    public void dispatchOnGlobalLayout() {
        // v1.0.584 AGDK diagnostic: confirm this (the driven root) observer holds
        // GameActivity's listener and actually fires it each traversal.
        if ("1".equals(ENV_MACAPK_AGDK_TRACE)) {
            long c = macapkGlDispatchCount++;
            if (c == 0 || c == 5 || (c % 300) == 0) {
                StringBuilder sb = new StringBuilder();
                for (OnGlobalLayoutListener l : mOnGlobalLayoutListeners) {
                    sb.append(l.getClass().getName()).append(' ');
                }
                try { com.macapk.loader.NativeLog.i("AGDKtrace",
                    "dispatchOnGlobalLayout #" + c + " observer@"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " listeners=[" + sb.toString().trim() + "]"); }
                catch (Throwable ignored) {}
            }
        }
        for (OnGlobalLayoutListener l : mOnGlobalLayoutListeners) {
            try { l.onGlobalLayout(); } catch (Throwable ignored) {}
        }
    }

    /** Called right before performDraw. AOSP ORs the listener return values:
     *  any listener returning false cancels (and reschedules) the current
     *  draw. Returns true if drawing should proceed. The one-shot idiom adds a
     *  listener that removes itself + returns true on its single fire. */
    public boolean dispatchOnPreDraw() {
        boolean cancelDraw = false;
        for (OnPreDrawListener l : mOnPreDrawListeners) {
            try { cancelDraw |= !l.onPreDraw(); } catch (Throwable ignored) {}
        }
        return !cancelDraw;
    }

    /** Called as part of performDraw. AOSP invokes onDraw() on each listener. */
    public void dispatchOnDraw() {
        for (OnDrawListener l : mOnDrawListeners) {
            try { l.onDraw(); } catch (Throwable ignored) {}
        }
    }

    public void dispatchOnScrollChanged() {
        for (OnScrollChangedListener l : mOnScrollChangedListeners) {
            try { l.onScrollChanged(); } catch (Throwable ignored) {}
        }
    }

    public void dispatchOnGlobalFocusChange(View oldFocus, View newFocus) {
        for (OnGlobalFocusChangeListener l : mOnGlobalFocusListeners) {
            try { l.onGlobalFocusChanged(oldFocus, newFocus); } catch (Throwable ignored) {}
        }
    }

    public void dispatchOnTouchModeChanged(boolean inTouchMode) {
        for (OnTouchModeChangeListener l : mOnTouchModeChangeListeners) {
            try { l.onTouchModeChanged(inTouchMode); } catch (Throwable ignored) {}
        }
    }

    public void dispatchOnWindowFocusChange(boolean hasFocus) {
        for (OnWindowFocusChangeListener l : mOnWindowFocusListeners) {
            try { l.onWindowFocusChanged(hasFocus); } catch (Throwable ignored) {}
        }
    }

    /** AOSP name is dispatchOnWindowVisibilityChange (no trailing "d"), kept
     *  to match the real ViewRootImpl call site's method name exactly. */
    public void dispatchOnWindowVisibilityChange(int visibility) {
        for (OnWindowVisibilityChangeListener l : mOnWindowVisibilityListeners) {
            try { l.onWindowVisibilityChanged(visibility); } catch (Throwable ignored) {}
        }
    }

    /** True when any listener kind needs a per-frame traversal/draw so the
     *  ViewCanvasDriver's M6 dirty-gate doesn't skip the frame that would fire
     *  a freshly-registered OnPreDraw/OnGlobalLayout one-shot. */
    public boolean macapkHasLayoutOrDrawListeners() {
        return !mOnGlobalLayoutListeners.isEmpty()
            || !mOnPreDrawListeners.isEmpty()
            || !mOnDrawListeners.isEmpty();
    }

    /** Merge another (typically a pre-attach floating) observer's listeners
     *  into this one, AOSP ViewTreeObserver.merge() semantics, used when a
     *  detached subtree that accumulated listeners attaches into a tree that
     *  already has an observer. */
    public void merge(ViewTreeObserver o) {
        if (o == null || o == this) return;
        mOnGlobalLayoutListeners.addAllAbsent(o.mOnGlobalLayoutListeners);
        mOnPreDrawListeners.addAllAbsent(o.mOnPreDrawListeners);
        mOnDrawListeners.addAllAbsent(o.mOnDrawListeners);
        mOnScrollChangedListeners.addAllAbsent(o.mOnScrollChangedListeners);
        mOnGlobalFocusListeners.addAllAbsent(o.mOnGlobalFocusListeners);
        mOnTouchModeChangeListeners.addAllAbsent(o.mOnTouchModeChangeListeners);
        mOnWindowAttachListeners.addAllAbsent(o.mOnWindowAttachListeners);
        mOnWindowFocusListeners.addAllAbsent(o.mOnWindowFocusListeners);
        mOnWindowVisibilityListeners.addAllAbsent(o.mOnWindowVisibilityListeners);
        mOnSystemUiVisibilityListeners.addAllAbsent(o.mOnSystemUiVisibilityListeners);
        if (o.mOnFrameCommitListeners != null) {
            if (mOnFrameCommitListeners != null) {
                mOnFrameCommitListeners.addAll(o.captureFrameCommitCallbacks());
            } else {
                mOnFrameCommitListeners = o.captureFrameCommitCallbacks();
            }
        }
        o.kill();
    }

    void kill() { mAlive = false; }

    // ---- system-gesture-exclusion listeners (API 29) -------------------------
    // AOSP ViewTreeObserver.add/removeOnSystemGestureExclusionRectsChangedListener:
    // told whenever the union of View.setSystemGestureExclusionRects across the
    // hierarchy changes. Mac.apk stores those rects on the View (see
    // View.setSystemGestureExclusionRects) but has no gesture-nav region for
    // them to affect, so the union never changes and no listener ever fires --
    // the same as a device with no gesture navigation enabled. The listeners are
    // still held (and released) so an app's add/remove pairing behaves.
    private final java.util.ArrayList<java.util.function.Consumer<
            java.util.List<android.graphics.Rect>>> mGestureExclusionListeners =
            new java.util.ArrayList<>();
    public void addOnSystemGestureExclusionRectsChangedListener(
            java.util.function.Consumer<java.util.List<android.graphics.Rect>> listener) {
        if (listener != null) mGestureExclusionListeners.add(listener);
    }
    public void removeOnSystemGestureExclusionRectsChangedListener(
            java.util.function.Consumer<java.util.List<android.graphics.Rect>> listener) {
        mGestureExclusionListeners.remove(listener);
    }
}
