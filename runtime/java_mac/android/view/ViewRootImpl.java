// android.view.ViewRootImpl -- Tier 3 M6: the schedule authority that lets the
// view tree be traversed (measured/laid out/drawn) ONLY WHEN A VIEW ASKS,
// instead of brute-forcing a full measure->layout->draw every single frame.
//
// Real Android: ViewRootImpl owns the window's root view + attach/insets/focus,
// and a scheduleTraversals() that invalidate()/requestLayout() call to request a
// traversal on the next Choreographer CALLBACK_TRAVERSAL; performTraversals()
// (measure -> layout -> draw) runs ONLY when mTraversalScheduled was set, then
// clears it (see frameworks/base ViewRootImpl.requestLayout/invalidate/
// scheduleTraversals/doTraversal). Idle window -> zero traversals.
//
// In Mac.apk the traversal BODY already lives in ViewCanvasDriver (it runs
// measure->layout->draw->present on the ONE main loop each Metal vsync). What
// was missing is the GATE: ViewCanvasDriver traversed unconditionally every
// frame (the ~15 enforce/fixup hacks re-assert geometry every frame on top of
// it). This class is that gate -- "has anyone asked for a traversal since the
// last frame?" -- so the driver can skip the whole pass when the tree is clean.
//
// Single-window model (one NSWindow, one root) -> a process singleton. Every
// call site is gated by MACAPK_REAL_LOOPER, so flag-off keeps the historical
// every-frame traversal byte-for-byte.
//
// Scope note (M6 is the first increment): this owns the traversal SCHEDULE.
// Owning attach/insets/focus and routing through Choreographer.CALLBACK_TRAVERSAL
// for animation timing migrate here in M7; retiring the per-frame hacks that the
// gate makes redundant is M8.
package android.view;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ViewRootImpl {
    private static ViewRootImpl sInstance;

    public static synchronized ViewRootImpl getInstance() {
        if (sInstance == null) sInstance = new ViewRootImpl();
        return sInstance;
    }

    // Start scheduled so the very first frame always traverses (initial layout).
    private final AtomicBoolean mTraversalScheduled = new AtomicBoolean(true);
    // Tier 3 (scroll perf): a traversal that needs measure+layout, not just a
    // redraw. AOSP ViewRootImpl.performTraversals runs performMeasure/perform-
    // Layout ONLY when mLayoutRequested (set by View.requestLayout); a plain
    // invalidate() (set by scroll's offsetChildrenVertical, animations, ripples)
    // schedules a DRAW pass and leaves the stable layout untouched. We mirror
    // that split so a RecyclerView/ScrollView scroll frame is draw-only (O(visible)
    // redraw) instead of a full-tree re-measure+re-layout every frame. Starts true
    // so the first frame lays out. See ViewCanvasDriver.doFrame.
    private final AtomicBoolean mLayoutRequested = new AtomicBoolean(true);
    private View mView;

    private ViewRootImpl() {}

    /** Set/replace the window's root view (a new content view needs a full
     *  measure+layout traversal). */
    public void setView(View root) {
        mView = root;
        mTraversalScheduled.set(true);
        mLayoutRequested.set(true);
    }
    public View getView() { return mView; }

    /** invalidate() (and touch / surface-change redraw) calls this to ask for a
     *  measure-skipping DRAW on the next frame. Coalesced / idempotent. */
    public void scheduleTraversal() { mTraversalScheduled.set(true); }

    /** View.requestLayout (and setVisibility / setPadding / adapter changes) calls
     *  this to ask for a full measure+layout+draw traversal on the next frame.
     *  Implies a traversal too. AOSP: requestLayout sets mLayoutRequested AND
     *  scheduleTraversals. */
    public void requestLayoutTraversal() {
        mLayoutRequested.set(true);
        mTraversalScheduled.set(true);
    }

    public boolean isTraversalScheduled() { return mTraversalScheduled.get(); }
    public boolean isLayoutRequested()   { return mLayoutRequested.get(); }

    /** The driver calls this once per frame: returns true AND clears the flag iff
     *  a traversal is pending. (AOSP doTraversal: clear mTraversalScheduled, then
     *  performTraversals.) */
    public boolean consumeTraversalScheduled() { return mTraversalScheduled.getAndSet(false); }

    /** The driver calls this each traversal: returns true AND clears the flag iff a
     *  measure+layout was requested since the last frame. False => a draw-only frame
     *  is sufficient (AOSP performTraversals gates measure/layout on this). */
    public boolean consumeLayoutRequested() { return mLayoutRequested.getAndSet(false); }

    /** Convenience for the single-window case: schedule a DRAW (invalidate). */
    public static void schedule() { if (INVAL_CENSUS) census(); getInstance().scheduleTraversal(); }
    // MACAPK_TRACE_INVALIDATE=1 (diagnostic): WHO keeps the window dirty. Every
    // schedule() is attributed to its framework entry (View.invalidate,
    // Drawable.invalidateSelf ...) and the first APP frame that called it, and every
    // 5 s the 8 commonest pairs are logged as `[inval-census]` (tag Mac.apk.Inval).
    // An app that redraws every frame on a static screen shows its animators here:
    // Telegram's chat list (2026-10-01) is a SpoilerEffect cell, ActionBar
    // .setTranslationY and MainTabsActivity's blur fade, 36 each per 5 s -- small
    // views, so the whole-window repaint each of them causes is OUR cost (no damage
    // rects yet: docs/damage-aware-partial-redraw-design_2026-08-30.md S1).
    static final boolean INVAL_CENSUS = "1".equals(System.getenv("MACAPK_TRACE_INVALIDATE"));
    private static final java.util.HashMap<String, int[]> sCensus = new java.util.HashMap<>();
    private static long sCensusT0;
    private static void census() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        String entry = "?", app = "?";
        for (int i = 2; i < st.length; i++) {
            String c = st[i].getClassName();
            boolean fw = c.startsWith("android.") || c.startsWith("com.macapk.") || c.startsWith("java.") || c.startsWith("androidx.");
            if (entry.equals("?")) entry = c.substring(c.lastIndexOf('.') + 1) + "." + st[i].getMethodName();
            if (!fw) { app = c + "." + st[i].getMethodName() + ":" + st[i].getLineNumber()
                    + (i + 1 < st.length ? " <- " + st[i + 1].getClassName() + "." + st[i + 1].getMethodName() : ""); break; }
        }
        long now = System.nanoTime();
        synchronized (sCensus) {
            sCensus.computeIfAbsent(entry + " | " + app, k -> new int[1])[0]++;
            if (sCensusT0 == 0) sCensusT0 = now;
            if (now - sCensusT0 > 5_000_000_000L) {
                StringBuilder sb = new StringBuilder("[inval-census] 5s:");
                sCensus.entrySet().stream().sorted((a, b) -> b.getValue()[0] - a.getValue()[0]).limit(8)
                    .forEach(e -> sb.append("\n  ").append(e.getValue()[0]).append("  ").append(e.getKey()));
                com.macapk.loader.NativeLog.i("Mac.apk.Inval", sb.toString());
                sCensus.clear(); sCensusT0 = now;
            }
        }
    }
    /** Convenience for the single-window case: schedule a measure+layout+draw. */
    public static void scheduleLayout() { getInstance().requestLayoutTraversal(); }

    // ------------------------------------------------------------------
    // TOUCH MODE (AOSP ViewRootImpl.ensureTouchMode / enterTouchMode /
    // leaveTouchMode, android14-release :6088-6195).
    //
    // A device is in touch mode after a touch and leaves it on a D-pad,
    // arrow, Tab or typing key; outside touch mode every FOCUSABLE view can
    // take focus (not only focusableInTouchMode ones) and focus moves with the
    // arrows. Since Android 14 the state is per DISPLAY and shared by every
    // window on it (WindowManager.setInTouchMode(inTouchMode, displayId)), and
    // we have one display, so it is one process-wide value. It starts at
    // config_defaultInTouchMode, which is TRUE on a phone or tablet (false on
    // a TV); we present a normal touch device, so it starts true.
    //
    // ⚠ View.isInTouchMode() was a constant true, so the arrow keys could
    // never put focus on a view that is focusable but not focusableInTouchMode
    // -- every androidx.leanback card, row and header, and every plain Button.
    // A TV app was unusable from the keyboard: SmartTube took every arrow and
    // returned false from dispatchKeyEvent because nothing held focus.
    // ------------------------------------------------------------------
    private static volatile boolean sInTouchMode = true;

    /** AOSP View.isInTouchMode reads mAttachInfo.mInTouchMode for an attached
     *  view, which is this. */
    public static boolean macapkInTouchMode() { return sInTouchMode; }

    /** AOSP ensureTouchMode(inTouchMode) for the window whose root is `root`:
     *  returns true when the mode changed AND focus changed as a result (the
     *  caller then consumes the key that caused it). */
    public static boolean macapkEnsureTouchMode(View root, boolean inTouchMode) {
        if (sInTouchMode == inTouchMode) return false;
        sInTouchMode = inTouchMode;
        if (root == null) return false;
        try { root.getViewTreeObserver().dispatchOnTouchModeChanged(inTouchMode); }
        catch (Throwable ignored) { }
        return inTouchMode ? enterTouchMode(root) : leaveTouchMode(root);
    }

    /** AOSP enterTouchMode: a focused view that cannot hold focus in touch
     *  mode hands it to an ancestor that wants it after its descendants and
     *  is focusable in touch mode, or loses it with nothing in its place. */
    private static boolean enterTouchMode(View root) {
        if (!root.hasFocus()) return false;
        View focused = root.findFocus();
        if (focused == null || focused.isFocusableInTouchMode()) return false;
        ViewGroup ancestor = findAncestorToTakeFocusInTouchMode(focused);
        if (ancestor != null) return ancestor.requestFocus();
        focused.clearFocus();
        return true;
    }

    private static ViewGroup findAncestorToTakeFocusInTouchMode(View focused) {
        ViewParent parent = focused.getParent();
        while (parent instanceof ViewGroup vg) {
            if (vg.getDescendantFocusability() == ViewGroup.FOCUS_AFTER_DESCENDANTS
                    && vg.isFocusableInTouchMode()) {
                return vg;
            }
            if (vg.isRootNamespace()) return null;
            parent = vg.getParent();
        }
        return null;
    }

    /** AOSP leaveTouchMode: a focused leaf (or a group that does not prefer
     *  its children) keeps focus; otherwise the window's default focus is
     *  restored, which in non-touch mode reaches every focusable view. */
    private static boolean leaveTouchMode(View root) {
        if (root.hasFocus()) {
            View focused = root.findFocus();
            if (!(focused instanceof ViewGroup fg)) return false;
            if (fg.getDescendantFocusability() != ViewGroup.FOCUS_AFTER_DESCENDANTS) return false;
        }
        return root.restoreDefaultFocus();
    }
}
