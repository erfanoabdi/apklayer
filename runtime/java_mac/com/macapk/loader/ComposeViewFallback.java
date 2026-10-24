// ComposeViewFallback -- paint extracted text from any ComposeView
// whose Compose runtime didn't manage to draw into our Canvas. The
// Compose composition / renderer chain depends on kotlinx-coroutines
// + Compose's frame scheduler, both of which our framework only
// partially drives. When a ComposeView's draw path never paints,
// the affected View slot ends up blank -- F-Droid's "F-Droid is
// under threat" call-to-action banner is the canonical victim.
//
// This fallback walks the AndroidComposeView's LayoutNode tree via
// reflection, harvests text from any TextStringSimpleNode /
// TextAnnotatedStringNode it finds, and paints those strings as a
// fallback banner inside the ComposeView's bounds. No Compose-
// runtime APIs are called -- we only READ state that the composition
// already set up.
//
// Generic across any APK with a Compose UI: keys exclusively on the
// Compose class names (`androidx.compose.ui.platform.AndroidComposeView`,
// `androidx.compose.foundation.text.modifiers.TextStringSimpleNode`,
// etc.). Nothing here mentions any package name. An app whose
// Compose actually renders will already have painted into the
// ComposeView region; the fallback overpaint will then be invisible
// behind the live content because the live content drew opaque
// pixels there.
//
// Two diagnostics:
//   MACAPK_TRACE_COMPOSE_FALLBACK=1 -- log per-frame which
//   ComposeViews were walked and how much text was harvested.
//   MACAPK_COMPOSE_FALLBACK=0 -- disable entirely.
package com.macapk.loader;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ComposeViewFallback {
    // Process-environment snapshots for the per-draw / per-frame / per-layout /
    // per-event sites below (debug/w9textdraw_verify/hoist_getenv.py). A
    // System.getenv call encodes the name and hashes it on every call; the
    // environment it reads is copied once at JVM start (JDK ProcessEnvironment,
    // "subsequent calls to putenv/setenv from C will not be visible from Java
    // code"), so one read at class init is the same value. First in the body, so
    // no other static initializer of this class can observe them unset.
    private static final String ENV_MACAPK_DUMP_SLOTS = System.getenv("MACAPK_DUMP_SLOTS");
    private static final String ENV_MACAPK_FORCE_REMEASURE = System.getenv("MACAPK_FORCE_REMEASURE");
    private static final String ENV_MACAPK_FORCE_REMEASURE_MODE = System.getenv("MACAPK_FORCE_REMEASURE_MODE");
    private static final String ENV_MACAPK_NAVSTACK_HOST_PROBE = System.getenv("MACAPK_NAVSTACK_HOST_PROBE");
    private static final String ENV_MACAPK_PROBE_SHEET = System.getenv("MACAPK_PROBE_SHEET");

    private static final String TAG = "Mac.apk.ComposeFallback";

    private ComposeViewFallback() {}

    private static volatile boolean traced = false;
    private static final boolean DISABLED =
        "0".equals(System.getenv("MACAPK_COMPOSE_FALLBACK"));
    private static final boolean TRACE =
        "1".equals(System.getenv("MACAPK_TRACE_COMPOSE_FALLBACK"));

    // MACAPK_TRACE_COMPOSE_INSERT: trace requestLayout()/invalidate() calls on the
    // AndroidComposeView host. Per AOSP, a Compose content INSERT
    // (LayoutNode.insertAt -> attach -> onRequestMeasure -> requestLayout/invalidate)
    // is physically incapable of being silent -- so if these NEVER fire on the host
    // after the content composes, no insert change-op ever reached the applier (the
    // composer recorded an empty change list). Distinguishes "composer never emitted
    // an insert" from "insert reached the tree but our measure/layout flush dropped
    // it." Env-gated; off by default.
    public static final boolean MACAPK_TRACE_COMPOSE_INSERT =
        System.getenv("MACAPK_TRACE_COMPOSE_INSERT") != null;
    // MACAPK_NAVSTACK_FORCE=1: confirmation hack -- force every navstack-related
    // Boolean MutableState (per-entry visibility otv.d, host.e()=n.f, etc.) to TRUE
    // to test whether Reddit's welcome blank is that latch. Diagnostic only; see
    // the slot-loop call site + docs/reddit-navstack-structure_2026-06-18.md.
    private static final boolean NAVSTACK_FORCE =
        "1".equals(System.getenv("MACAPK_NAVSTACK_FORCE"));
    /** Cheap name-based AndroidComposeView check (handles the R8-renamed Reddit
     *  case androidx.compose.ui.platform.c). For the requestLayout/invalidate trace
     *  only -- a per-call findRootLayoutNode would be too heavy. */
    public static boolean macapkIsComposeHost(View v) {
        if (v == null) return false;
        String n; try { n = v.getClass().getName(); } catch (Throwable t) { return false; }
        return n.endsWith("AndroidComposeView") || n.equals("androidx.compose.ui.platform.c");
    }

    /** Walk the view tree and paint a text fallback inside every
     *  ComposeView whose Compose-runtime draw didn't manage to fill
     *  its bounds with content. Called from ViewCanvasDriver.macapk-
     *  DrawFrame AFTER rootView.draw, so the fallback paints on top
     *  of whatever Compose may have done (which is usually nothing). */
    public static void paint(View root, Canvas canvas) {
        if (DISABLED || root == null || canvas == null) return;
        // M24: real Jetpack Compose now renders (M22 bring-up) -- but ONLY on
        // the MACAPK_REAL_LOOPER path (it needs the real main Looper +
        // Choreographer frame clock + the content-root ViewTree owners, all
        // flag-gated). When real Compose draws, this fallback's OPAQUE
        // colorErrorContainer fill (drawn AFTER rootView.draw, i.e. ON TOP)
        // CLOBBERS the live render -- the canonical symptom was a foundation
        // Compose app's 8 colored Box rows being overpainted by a solid pink
        // (#FFDAD6) banner. The old comment claimed "live content drew opaque
        // pixels there so the overpaint is invisible," which is backwards: the
        // fallback runs last and covers the live content. So skip it entirely
        // when the flag is on (real Compose owns the ComposeView region);
        // flag-OFF the fallback still paints its harvested-text banner because
        // Compose doesn't render there (this whole class is a flag-OFF
        // compatibility crutch, to be deleted when the flag default flips).
        if (android.os.Looper.MACAPK_REAL_LOOPER) return;
        try {
            // v1.0.144 fix: callers translate the canvas to the root's
            // coordinate frame BEFORE invoking us (ViewCanvasDriver does
            // `canvas.translate(0, statusBarH)`). The recursion below
            // accumulates `top`/`left` from each view starting at root,
            // so passing initial offsets of (0, 0) double-counts the
            // root's own (left, top) -- because rootView is itself
            // positioned at (0, statusBarH) in its parent's coords, so
            // root.getTop() == statusBarH and gets added on top of the
            // canvas translate that already applied that statusBarH.
            //
            // Result before this fix: the threat banner (a ComposeView
            // child of rootView's content) painted at canvas y =
            // statusBarH + statusBarH = 2 * statusBarH instead of right
            // below the status bar.
            //
            // Pass NEGATIVE root.left / root.top as the initial
            // accumulator so root's contribution cancels: when the
            // recursion hits root, `vy = (-root.top) + root.top = 0`,
            // and children accumulate from 0 onward in coords matching
            // the already-translated canvas.
            paintRec(root, canvas, new java.util.IdentityHashMap<>(),
                     -root.getLeft(), -root.getTop());
        } catch (Throwable t) {
            if (!traced) {
                traced = true;
                NativeLog.w(TAG, "fallback walker threw: " + t);
            }
        }
    }

    private static void paintRec(View v, Canvas canvas,
                                  java.util.IdentityHashMap<View, Boolean> visited,
                                  int absX, int absY) {
        if (v == null) return;
        if (visited.put(v, Boolean.TRUE) != null) return;
        int vx = absX + v.getLeft();
        int vy = absY + v.getTop();
        String cn;
        try { cn = v.getClass().getName(); } catch (Throwable t) { return; }
        // The Compose UI host is `ComposeView` (extends
        // AbstractComposeView). The actual draw tree lives one level
        // down in `AndroidComposeView`. We hook at the ComposeView
        // level so an app with a custom AbstractComposeView subclass
        // (KomposeView, AndroidView, etc.) still matches.
        if (cn.endsWith(".ComposeView")
            || cn.endsWith(".AbstractComposeView")
            || cn.endsWith("AndroidComposeView")) {
            tryPaintComposeFallback(v, canvas, vx, vy);
            // Don't recurse further -- everything below is Compose
            // internals; LayoutNode walker handles that.
            return;
        }
        if (v instanceof android.view.ViewGroup vg) {
            int n = vg.getChildCount();
            for (int i = 0; i < n; i++) {
                paintRec(vg.getChildAt(i), canvas, visited, vx, vy);
            }
        }
    }

    private static void tryPaintComposeFallback(View v, Canvas canvas,
                                                  int absX, int absY) {
        int w = v.getRight() - v.getLeft();
        int h = v.getBottom() - v.getTop();
        if (w <= 0 || h <= 0) return;
        // Locate AndroidComposeView -- either THIS view (if v is the
        // inner host) or a child of v.
        View hostView = locateAndroidComposeView(v);
        if (hostView == null) return;
        // Pull root LayoutNode via getRoot() reflection.
        Object root = invokeNoArg(hostView, "getRoot");
        if (root == null) return;
        // Walk LayoutNode tree, harvesting text strings.
        java.util.ArrayList<String> texts = new java.util.ArrayList<>();
        try {
            harvestText(root, texts, new java.util.IdentityHashMap<>(), 0);
        } catch (Throwable t) {
            if (TRACE) NativeLog.w(TAG, "harvest threw: " + t);
            return;
        }
        if (texts.isEmpty()) return;
        if (TRACE) {
            NativeLog.i(TAG, "ComposeView " + v.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(v))
                + " bounds=" + w + "x" + h + " harvested " + texts.size()
                + " text(s); first='"
                + (texts.get(0).length() > 60 ? texts.get(0).substring(0, 57) + "..." : texts.get(0))
                + "'");
        }
        // Paint a fallback banner: themed colorErrorContainer fill,
        // colorOnErrorContainer text. F-Droid's threat banner uses an
        // error-tinted Surface; the M3 token colorErrorContainer is
        // the AOSP convention for "tinted-warning surface."
        // Without that token resolving, fall back to a soft peach.
        int bgColor = android.content.res.Resources.macapkThemeColor(
            "colorErrorContainer", 0xFFFFDAD6);
        // Many call-to-action banners use a softer tint than the full
        // error color; check colorTertiaryContainer too.
        if ((bgColor & 0xFFFFFF) == 0xFFFBFE) {
            bgColor = 0xFFFFDAD6; // M3 fallback peach
        }
        int fgColor = android.content.res.Resources.macapkThemeColor(
            "colorOnErrorContainer", 0xFF410002);

        Paint bg = new Paint();
        bg.setAntiAlias(true);
        bg.setStyle(Paint.Style.FILL);
        bg.setColor(bgColor);
        // Fill the ComposeView's full bounds. Position the banner via
        // the absolute view-tree coords (since we're painting AFTER
        // the rootView.draw which left the canvas at its outer
        // translate).
        canvas.drawRect(absX, absY, absX + w, absY + h, bg);

        // Text painter
        float density = android.util.DisplayMetrics.macapkDeviceDensity();
        try {
            if (v.getResources() != null
                && v.getResources().getDisplayMetrics() != null
                && v.getResources().getDisplayMetrics().density > 0f) {
                density = v.getResources().getDisplayMetrics().density;
            }
        } catch (Throwable ignored) {}
        float textSizePx = 13f * density;
        Paint text = new Paint();
        text.setAntiAlias(true);
        text.setColor(fgColor);
        text.setTextSize(textSizePx);
        // Horizontal pad: 16dp left (banner edge to text), close-button
        // slot 48dp on the right reserved for the X.
        int padH = Math.round(16f * density);
        int closeSlot = Math.round(48f * density);
        int avail = w - padH - closeSlot;
        if (avail <= 0) avail = w - padH;
        // Vertical pad: 0dp from the banner's TOP to the first line's
        // cap line. v1.0.144 fixed the banner's container offset (it
        // was painting 24dp lower than intended due to a double-count
        // of rootView.getTop()); with the banner now flush with the
        // status bar, the previous 4dp padTop became visible padding
        // INSIDE the banner -- making the text sit 5-10 px lower than
        // the GT. Tightening to 0dp aligns the text's cap-height with
        // the banner's top edge, matching the M3 alert-banner spec
        // (text touches the banner's top inset with no internal pad).
        int padTop = 0;
        // Combine harvested texts into one block; usually the longest
        // is the primary message + a URL.
        StringBuilder combined = new StringBuilder();
        for (String s : texts) {
            if (combined.length() > 0) combined.append(' ');
            combined.append(s);
        }
        java.util.List<String> lines = wrapText(combined.toString(), text, avail);
        Paint.FontMetrics fm = text.getFontMetrics();
        float lineH = fm.descent - fm.ascent;
        float startY = absY + padTop - fm.ascent;
        for (int i = 0; i < lines.size() && i * lineH < h - padTop; i++) {
            try {
                canvas.drawText(lines.get(i), absX + padH,
                                 startY + i * lineH, text);
            } catch (Throwable ignored) {}
        }
        // Close (X) button on the right.
        try {
            int cx = absX + w - padH - closeSlot / 2;
            int cy = absY + h / 2;
            int sz = Math.round(8f * density);
            Paint x = new Paint();
            x.setAntiAlias(true);
            x.setStyle(Paint.Style.STROKE);
            x.setStrokeWidth(Math.max(1f, 1.5f * density));
            x.setColor(fgColor);
            canvas.drawLine(cx - sz, cy - sz, cx + sz, cy + sz, x);
            canvas.drawLine(cx - sz, cy + sz, cx + sz, cy - sz, x);
        } catch (Throwable ignored) {}
    }

    private static View locateAndroidComposeView(View v) {
        if (v == null) return null;
        String name;
        try { name = v.getClass().getName(); } catch (Throwable t) { return null; }
        if (name.endsWith("AndroidComposeView")) return v;
        if (v instanceof android.view.ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                View inner = locateAndroidComposeView(vg.getChildAt(i));
                if (inner != null) return inner;
            }
        }
        return null;
    }

    /** Walk a Compose LayoutNode tree harvesting text strings from
     *  TextStringSimpleNode / TextAnnotatedStringNode modifier nodes.
     *  Deep + recursive via the LayoutNode._foldedChildren collection
     *  and the NodeChain head/child linked list. Catches every
     *  ReflectiveOperationException as the field shapes are not
     *  guaranteed across Compose versions; on any failure, harvest
     *  bails out for this node and continues with siblings. */
    private static void harvestText(Object node, java.util.List<String> out,
                                     java.util.IdentityHashMap<Object, Boolean> visited,
                                     int depth) throws Exception {
        if (node == null || depth > 32) return;
        if (visited.put(node, Boolean.TRUE) != null) return;
        // Walk the NodeChain on this LayoutNode.
        Object nodes = readField(node, "nodes");
        if (nodes != null) {
            Object head = readField(nodes, "head");
            walkModifierChain(head, out, visited, 0);
        }
        // Recurse into children. Compose 1.5+ uses
        // `_foldedChildren` (MutableVectorWithMutationTracking).
        Object foldedChildren = readField(node, "_foldedChildren");
        if (foldedChildren != null) {
            Object vector = readField(foldedChildren, "vector");
            Object[] content = vector != null ? extractContent(vector) : null;
            if (content != null) {
                for (Object child : content) {
                    if (child == null) continue;
                    if (!child.getClass().getName().endsWith("LayoutNode")) continue;
                    harvestText(child, out, visited, depth + 1);
                }
            }
        }
    }

    // ---- DIAGNOSTIC (MACAPK_TRACE_COMPOSE_FALLBACK=1) ----------------------
    // Dump the AndroidComposeView LayoutNode tree (node count + per-node
    // measured size + class + harvested text) even under MACAPK_REAL_LOOPER,
    // WITHOUT painting. Lets us see whether a Compose screen's composition
    // produced content nodes (with non-zero size) vs an empty tree -- the
    // difference between "composes but draws nothing because the tree is empty"
    // and "tree has content but our draw path drops it".
    private static int sDiagCount = 0;
    private static int sMaxNodes = 0;
    private static boolean sRootDumped = false;
    private static boolean sProbedComposition = false;

    /** DIAGNOSTIC: from an AndroidComposeView host, find its parent
     *  AbstractComposeView's WrappedComposition and report which AndroidComposeView
     *  the composition's applier targets + that view's root tree size, vs this
     *  host. Reveals whether the content composed into an orphaned/different
     *  AndroidComposeView instance than the one in the drawn view tree. */
    private static void probeCompositionRoot(View host) {
        android.view.ViewParent p = host.getParent();
        if (!(p instanceof View)) { NativeLog.i(TAG, "probe: host parent not a View"); return; }
        View parent = (View) p;
        // Scan the AbstractComposeView parent's fields for the WrappedComposition:
        // an object holding BOTH an AndroidComposeView-shaped View and another object
        // (the Composition). Then read its AndroidComposeView field.
        Class<?> pc = parent.getClass();
        while (pc != null && pc != Object.class) {
            for (Field f : pc.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object wc = f.get(parent);
                    if (wc == null || wc instanceof View) continue;
                    // does wc hold an AndroidComposeView-shaped View?
                    for (Field g : wc.getClass().getDeclaredFields()) {
                        try {
                            g.setAccessible(true);
                            Object acv = g.get(wc);
                            if (!(acv instanceof View)) continue;
                            Object acvRoot = findRootLayoutNode((View) acv);
                            if (acvRoot == null) continue;
                            int n = subtreeCount(acvRoot, 0, new java.util.IdentityHashMap<>());
                            boolean sameAsHost = (acv == host);
                            NativeLog.i(TAG, "probe: parent=" + parent.getClass().getName()
                                + " field=" + f.getName() + "." + g.getName()
                                + " -> composition AndroidComposeView id="
                                + System.identityHashCode(acv) + " (hostId="
                                + System.identityHashCode(host) + " same=" + sameAsHost
                                + ") rootNodes=" + n);
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            pc = pc.getSuperclass();
        }
    }
    // ---- Bottom-sheet animation probe (MACAPK_PROBE_SHEET) ---------------
    // Find any com.reddit.ui.compose.ds.BottomSheetState / androidx
    // SwipeableState reachable from the view tree and log its live offset /
    // currentValue / targetValue / anchors each tick, so we can see whether the
    // Reddit auth bottom sheet's enter animation has the WRONG target (resolves
    // to a hidden/peek anchor) or the RIGHT target but a value that never
    // advances (frame-driven tween stalls). Gated; default off.
    private static final java.util.Set<Object> sSheetSeenThisRun =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static int sSheetProbeTick = 0;
    // --- Lightweight cached-`l` probe state (the decisive churn-vs-population trace) ---
    // The heavy per-frame getRoot BFS below IS the perturbation that masks the bug
    // (probe-on -> open wins; probe-off -> open stays Hidden). So once we locate the
    // DS BottomSheetState `l`, cache it and read its fields cheaply EVERY frame with
    // ZERO tree-walk, so we observe the real (failing) open trajectory.
    private static volatile Object sCachedSheetL = null;
    // Counts invalidate()/requestLayout() calls on ANY AndroidComposeView host,
    // bumped from View.java. Decisive: if these climb during the open, androidx IS
    // requesting relayout (our gate/traversal drops it); if flat, androidx never
    // requests relayout (the layout snapshot observer isn't firing for the sheet).
    public static final java.util.concurrent.atomic.AtomicInteger sAcvInval =
        new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger sAcvReqLayout =
        new java.util.concurrent.atomic.AtomicInteger();
    // Gate the per-invalidate counter bump (View.java) so normal runs pay nothing
    // (invalidate() is hot; macapkIsComposeHost does a getClass().getName()+endsWith).
    public static final boolean SHEET_PROBE_ON = "1".equals(System.getenv("MACAPK_PROBE_SHEET"));
    private static int sLiteFrame = 0;
    private static boolean sPrevM = false;
    private static int sMFlips = 0;          // false->true transitions of m = open (re)starts
    private static boolean sOffMoved = false;
    private static float sOff0 = Float.NaN;
    private static float sKMax = -1e30f;
    public static void probeBottomSheetState(View rootView) {
        if (!"1".equals(ENV_MACAPK_PROBE_SHEET)) return;
        // FAST PATH: `l` already found -> cheap field reads, no BFS, every frame.
        Object cached = sCachedSheetL;
        if (cached != null) { try { dumpSheetStateLite(cached); } catch (Throwable ignored) {} return; }
        // SEARCH PATH: throttle the heavy BFS so the pre-cache search perturbs
        // minimally (it only needs to run until the pushed sheet's `l` appears,
        // which dumpSheetState caches on first match).
        sSheetProbeTick++;
        if ((sSheetProbeTick % 3) != 0) return;
        sSheetSeenThisRun.clear();
        sSheetComposeSeen = 0; sSheetDsSeen = 0;
        try { sheetBfs(rootView, 0); } catch (Throwable t) { NativeLog.w(TAG, "probeSheet: " + t); }
        // The DS state `l` lives in the bottom sheet's MODIFIER chain on the
        // Compose LayoutNode tree, not in plain View fields -- walk each compose
        // host's getRoot() LayoutNode tree + modifier chains (like harvestText) and
        // feed every modifier node into the sheet matcher.
        try {
            java.util.ArrayList<View> hosts = new java.util.ArrayList<>();
            collectComposeViews(rootView, hosts);
            for (View h : hosts) {
                View inner = locateAndroidComposeView(h);
                Object root = (inner != null) ? invokeNoArg(inner, "getRoot") : null;
                if (root != null) sheetWalkLayoutNodes(root, 0);
            }
        } catch (Throwable t) { NativeLog.w(TAG, "probeSheet layoutnodes: " + t); }
        NativeLog.i(TAG, "probeSheet#" + sSheetProbeTick + " visited=" + sSheetSeenThisRun.size()
            + " composeObjs=" + sSheetComposeSeen + " dsObjs=" + sSheetDsSeen);
    }

    // Cheap per-frame read of the cached DS BottomSheetState `l`. Logs the
    // decisive churn-vs-population signals with NO tree walk:
    //   k       = Animatable l.k value (the offset the open animates first)
    //   off     = swipeableState.offset (l.e.e) -- the ONLY value the rendered
    //             custom Modifier.layout reads once (m||n) latches false; the
    //             open's step-4 hand-off (l.b) is the ONLY writer of it
    //   m/n     = the e() selector flags; mFlips counts false->true (= open restarts)
    //   fAnchors= l.f() computed-anchors keyset; swAnchors = the Material swipeable
    //             anchors keyset -> if these JITTER frame-to-frame, the
    //             transformLatest(combine(snapshotFlow{anchors})) re-emits and
    //             CANCELS the open (anchor-churn). If they are STABLE and contain
    //             the target but off never moves, step-4 bailed (anchors-population).
    // DIAGNOSTIC/FIX (MACAPK_FORCE_REMEASURE=1): force every attached
    // AndroidComposeView to re-measure its root each frame. Compose's measureAndLayout
    // (AndroidComposeView.dispatchDraw -> measureAndLayout) early-returns when the
    // relayoutNodes set is empty, so an animating LayoutNode whose layout-read state
    // changed but whose snapshot observer did NOT fire requestRemeasure (the Reddit DS
    // bottom-sheet case: the offset animates but the custom Modifier.layout is never
    // re-placed -> sheet frozen off-screen) never re-lays-out. Calling the Owner's
    // onRequestMeasure(root, affectsLookahead=false, forceRequest=true,
    // scheduleMeasureAndLayout=true) marks the root dirty so the next dispatchDraw
    // re-measures+re-places it. Found rename-proof by SHAPE: the AndroidComposeView
    // method taking (LayoutNode, boolean, boolean, boolean) -> void. Gated; this is the
    // confirm-the-diagnosis lever (a real, generic fix would gate this on "host has an
    // active Compose animation this frame", not force it unconditionally every frame).
    private static java.lang.reflect.Method sOnReqMeasure = null;   // (node,Z,Z,Z) onRequestMeasure
    private static java.lang.reflect.Method sOnReqRelayout = null;  // (node,Z,Z)  onRequestRelayout
    private static boolean sOnReqMeasureResolved = false;
    public static void forceRemeasureHosts(View rootView) {
        if (rootView == null || !"1".equals(ENV_MACAPK_FORCE_REMEASURE)) return;
        try {
            boolean[] any = {false};
            forceRemeasureWalk(rootView, any, new java.util.IdentityHashMap<>(), 0);
            if (any[0] && android.os.Looper.MACAPK_REAL_LOOPER)
                try { android.view.ViewRootImpl.schedule(); } catch (Throwable ignored) {}
        } catch (Throwable t) { NativeLog.w(TAG, "forceRemeasureHosts: " + t); }
    }
    /** Rename-proof: an AndroidComposeView is any View with a getRoot() returning an
     *  androidx.compose.ui.node.* LayoutNode (R8 renames the class but keeps the
     *  package). For each, call Owner.onRequestMeasure(root, false, true, true). */
    private static void forceRemeasureWalk(View v, boolean[] any,
                                            java.util.IdentityHashMap<View, Boolean> visited, int depth) {
        if (v == null || depth > 40 || visited.put(v, Boolean.TRUE) != null) return;
        Object root = invokeNoArg(v, "getRoot");
        if (root != null && root.getClass().getName().startsWith("androidx.compose.ui.node.")) {
            java.lang.reflect.Method m = sOnReqMeasure;
            java.lang.reflect.Method mr = sOnReqRelayout;
            if (!sOnReqMeasureResolved) {
                m = findMethodByNodeBoolArity(v, root.getClass(), 4);   // onRequestMeasure(node,Z,Z,Z)
                mr = findMethodByNodeBoolArity(v, root.getClass(), 3);  // onRequestRelayout(node,Z,Z)
                sOnReqMeasure = m; sOnReqRelayout = mr; sOnReqMeasureResolved = true;
                NativeLog.i(TAG, "forceRemeasure: onRequestMeasure="
                    + (m == null ? "NOT FOUND" : m.getName())
                    + " onRequestRelayout=" + (mr == null ? "NOT FOUND" : mr.getName())
                    + " on " + v.getClass().getName() + " rootClass=" + root.getClass().getName());
            }
            // Mark the ROOT for BOTH remeasure (height) AND relayout (placement Y) --
            // the DS sheet's custom Modifier.layout reads e() in measure AND placement,
            // so a measure-only mark re-runs the height lambda but NOT the placement
            // lambda that sets the stale Y=1620.
            if (m != null) { try { m.invoke(v, root, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE); any[0] = true; } catch (Throwable ignored) {} }
            if (mr != null) { try { mr.invoke(v, root, Boolean.FALSE, Boolean.TRUE); any[0] = true; } catch (Throwable ignored) {} }
            // MACAPK_FORCE_REMEASURE=all: dirty EVERY LayoutNode (root-only marks
            // short-circuit on unchanged constraints; deep animating nodes are skipped).
            if ("all".equals(ENV_MACAPK_FORCE_REMEASURE_MODE)) {
                try { forceRemeasureNode(v, m, mr, root, new java.util.IdentityHashMap<>(), 0); }
                catch (Throwable ignored) {}
            }
        }
        if (v instanceof android.view.ViewGroup vg) {
            for (int i = 0, n = vg.getChildCount(); i < n; i++)
                forceRemeasureWalk(vg.getChildAt(i), any, visited, depth + 1);
        }
    }
    /** Walk the LayoutNode tree (root + _foldedChildren) and call
     *  Owner.onRequestMeasure(node, false, true, false) on EACH node, so every node
     *  (incl. deep animating ones) is marked dirty -> measureAndLayout re-measures +
     *  re-places it. host = the AndroidComposeView (the Owner), m = onRequestMeasure. */
    private static void forceRemeasureNode(View host, java.lang.reflect.Method m, java.lang.reflect.Method mr,
                                           Object node, java.util.IdentityHashMap<Object, Boolean> seen, int depth) {
        if (node == null || depth > 60 || seen.put(node, Boolean.TRUE) != null) return;
        if (m != null)  try { m.invoke(host, node, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE); } catch (Throwable ignored) {}
        if (mr != null) try { mr.invoke(host, node, Boolean.FALSE, Boolean.TRUE); } catch (Throwable ignored) {}
        try {
            Object folded = readField(node, "_foldedChildren");
            if (folded != null) {
                Object vector = readField(folded, "vector");
                Object[] content = vector != null ? extractContent(vector) : null;
                if (content != null) for (Object c : content) {
                    if (c != null && c.getClass().getName().startsWith("androidx.compose.ui.node."))
                        forceRemeasureNode(host, m, mr, c, seen, depth + 1);
                }
            }
        } catch (Throwable ignored) {}
    }
    /** Find an Owner method (node, boolean...) -> void with the given total arity:
     *  arity 4 = onRequestMeasure(node,Z,Z,Z); arity 3 = onRequestRelayout(node,Z,Z). */
    private static java.lang.reflect.Method findMethodByNodeBoolArity(Object acv, Class<?> nodeClass, int arity) {
        for (java.lang.reflect.Method m : acv.getClass().getMethods()) {
            if (m.getReturnType() != void.class) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != arity || !p[0].isAssignableFrom(nodeClass)) continue;
            boolean rest = true;
            for (int i = 1; i < arity; i++) if (p[i] != boolean.class) { rest = false; break; }
            if (rest) return m;
        }
        return null;
    }

    private static void dumpSheetStateLite(Object l) {
        sLiteFrame++;
        float k = readStateNum(readDeclField(l, "k"), "d");   // Animatable value via k.d()
        float off = readSwipeOffset(l);
        Boolean m = readBoolState(l, "m");
        Boolean n = readBoolState(l, "n");
        Object cur = invoke0(l, "c");
        Object tgt = invoke0(l, "g");
        if (Float.isNaN(sOff0) && !Float.isNaN(off)) sOff0 = off;
        if (!Float.isNaN(off) && !Float.isNaN(sOff0) && Math.abs(off - sOff0) > 0.5f) sOffMoved = true;
        if (!Float.isNaN(k) && k > sKMax) sKMax = k;
        boolean mb = (m != null && m);
        if (mb && !sPrevM) sMFlips++;
        sPrevM = mb;
        String fAnch = anchorKeys(invoke0(l, "f"));
        String swAnch = swipeAnchorKeys(l);
        // Throttle the log to every 2nd frame so a long run stays readable while
        // still resolving the ~20-frame open window.
        if ((sLiteFrame & 1) == 0) return;
        NativeLog.i(TAG, "SHEETLITE#" + sLiteFrame
            + " k=" + fmt1(k) + " kMax=" + fmt1(sKMax)
            + " off=" + fmt1(off) + " off0=" + fmt1(sOff0) + " moved=" + sOffMoved
            + " m=" + m + " mFlips=" + sMFlips + " n=" + n
            + " cur=" + cur + " tgt=" + tgt
            + " acvInval=" + sAcvInval.get() + " acvReqLayout=" + sAcvReqLayout.get()
            + " fAnchors=" + fAnch + " swAnchors=" + swAnch);
        // Decisive: dump l.e() (the DS effective offset the custom Modifier.layout reads)
        // + EVERY SwipeableState float field, so we can see which value tracks the stale
        // rendered 1620 vs which reached 893 (the probe's `off` reads only one field).
        Object dsE = invoke0(l, "e");                 // l.e() method = effective offset (float)
        NativeLog.i(TAG, "SHEETSWIPE#" + sLiteFrame + " dsE()=" + dsE
            + " SwipeableState{" + swipeFields(l) + "}");
    }
    private static Object readDeclField(Object o, String name) {
        try { java.lang.reflect.Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
        catch (Throwable t) { return null; }
    }
    /** Read a Compose State/Animatable numeric value via `valMethod` (e.g. "d") then getValue/getFloatValue. */
    private static float readStateNum(Object st, String valMethod) {
        if (st == null) return Float.NaN;
        for (String mn : new String[]{valMethod, "getValue", "getFloatValue", "d", "getCurrentValue"}) {
            if (mn == null) continue;
            try { Object v = st.getClass().getMethod(mn).invoke(st);
                if (v instanceof Number) return ((Number) v).floatValue(); } catch (Throwable ignored) {}
        }
        return Float.NaN;
    }
    /** swipeableState.offset = l.e (Material SwipeableState) . field e (offset State) . getValue(). */
    private static float readSwipeOffset(Object l) {
        Object sw = readDeclField(l, "e");
        if (sw == null) return Float.NaN;
        Object offState = readDeclField(sw, "e");
        return readStateNum(offState != null ? offState : sw, "getValue");
    }
    /** Dump every field of l.e (the Material SwipeableState) that holds a numeric
     *  State or a raw float, so we can identify which field the renderer's stale 1620
     *  tracks (e/f/g/n are FloatStates; k/l are raw floats; c is currentValue). */
    private static String swipeFields(Object l) {
        Object sw = readDeclField(l, "e");
        if (sw == null) return "noSW";
        StringBuilder sb = new StringBuilder();
        try {
            for (java.lang.reflect.Field f : sw.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                if (f.getType() == float.class) {
                    try { sb.append(f.getName()).append("(raw)=").append(f.getFloat(sw)).append(' '); } catch (Throwable ig) {}
                    continue;
                }
                Object v; try { v = f.get(sw); } catch (Throwable t) { continue; }
                if (v == null || v instanceof CharSequence || v instanceof Number || v instanceof Boolean) continue;
                String s = readStateAny(v);
                if (s != null) sb.append(f.getName()).append('=').append(s).append(' ');
            }
        } catch (Throwable ignored) {}
        return sb.toString();
    }
    /** If o is a Compose State, return its value (Float via getFloatValue/getValue, or
     *  enum/string for currentValue); else null. */
    private static String readStateAny(Object st) {
        for (String mn : new String[]{"getFloatValue", "getValue"}) {
            try {
                Object v = st.getClass().getMethod(mn).invoke(st);
                if (v instanceof Number) return String.valueOf(((Number) v).floatValue());
                if (v != null && (v instanceof Enum || v.getClass().getName().contains("Visibility")
                    || v.getClass().getName().startsWith("androidx.compose.material")))
                    return v.toString();
            } catch (Throwable ignored) {}
        }
        return null;
    }
    private static Boolean readBoolState(Object o, String field) {
        Object st = readDeclField(o, field);
        if (st == null) return null;
        try { Object v = st.getClass().getMethod("getValue").invoke(st);
            return (v instanceof Boolean) ? (Boolean) v : null; } catch (Throwable t) { return null; }
    }
    private static Object invoke0(Object o, String m) {
        try { java.lang.reflect.Method me = o.getClass().getMethod(m); me.setAccessible(true); return me.invoke(o); }
        catch (Throwable t) { return null; }
    }
    private static String anchorKeys(Object map) {
        if (!(map instanceof java.util.Map)) return String.valueOf(map);
        try { java.util.TreeSet<String> ks = new java.util.TreeSet<>();
            for (Object k : ((java.util.Map<?,?>) map).keySet()) ks.add(String.valueOf(k));
            return ks.toString();
        } catch (Throwable t) { return "?"; }
    }
    /** Find the Material SwipeableState (l.e) anchors map and return its keyset --
     *  scans l.e's fields for a non-empty Map<Float,?> (direct or wrapped in a State). */
    private static String swipeAnchorKeys(Object l) {
        Object sw = readDeclField(l, "e");
        if (sw == null) return "noSW";
        try {
            for (java.lang.reflect.Field f : sw.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Object v; try { f.setAccessible(true); v = f.get(sw); } catch (Throwable t) { continue; }
                if (v == null) continue;
                if (v instanceof java.util.Map<?,?> mp && !mp.isEmpty()
                    && mp.keySet().iterator().next() instanceof Number)
                    return f.getName() + "=" + anchorKeys(v);
                try { Object iv = v.getClass().getMethod("getValue").invoke(v);
                    if (iv instanceof java.util.Map<?,?> mp2 && !mp2.isEmpty()
                        && mp2.keySet().iterator().next() instanceof Number)
                        return f.getName() + ".v=" + anchorKeys(iv);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return "noMap";
    }
    private static String fmt1(float f) {
        return Float.isNaN(f) ? "nan" : String.valueOf(Math.round(f * 10f) / 10f);
    }

    private static int sSheetComposeSeen = 0;
    private static int sSheetDsSeen = 0;
    // Walk the LayoutNode tree (mirrors harvestText): for each node, run the
    // sheet matcher over its modifier-chain nodes (where the swipeable/sheet state
    // is held), then recurse into children.
    private static void sheetWalkLayoutNodes(Object node, int depth) {
        if (node == null || depth > 40) return;
        try {
            Object nodes = readField(node, "nodes");          // NodeChain
            if (nodes != null) {
                Object head = readField(nodes, "head");
                int guard = 0;
                for (Object n = head; n != null && guard < 200; guard++) {
                    sheetBfs(n, 6);                            // shallow BFS from each modifier node
                    Object nx = readField(n, "child");        // NodeChain links via `child`
                    if (nx == n) break;
                    n = nx;
                }
            }
            Object folded = readField(node, "_foldedChildren");
            if (folded != null) {
                Object vector = readField(folded, "vector");
                Object[] content = vector != null ? extractContent(vector) : null;
                if (content != null) for (Object c : content) {
                    if (c != null && c.getClass().getName().endsWith("LayoutNode"))
                        sheetWalkLayoutNodes(c, depth + 1);
                }
            }
        } catch (Throwable ignored) {}
    }
    private static void sheetBfs(Object o, int depth) {
        if (o == null || depth > 26 || !sSheetSeenThisRun.add(o)) return;
        Class<?> oc = o.getClass();
        String cn = oc.getName();
        if (cn.startsWith("androidx.compose.") || cn.contains(".compose.")) sSheetComposeSeen++;
        if (cn.startsWith("com.reddit.ui.compose.ds.")) sSheetDsSeen++;
        boolean isSheet = cn.contains("BottomSheetState") || cn.contains("SwipeableState")
            || cn.contains("AnchoredDraggableState") || cn.contains("SheetState")
            || cn.equals("com.reddit.ui.compose.ds.l")          // the R8-renamed DS BottomSheetState
            || cn.equals("androidx.compose.material.e");          // the R8-renamed Material SwipeableState
        if (isSheet) dumpSheetState(o, cn);
        // Recurse: fields (skip primitives/strings) + List/Map/array elements.
        for (Class<?> k = oc; k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if (ft.isPrimitive() || ft == String.class) continue;
                Object v; try { f.setAccessible(true); v = f.get(o); } catch (Throwable t) { continue; }
                if (v == null || v instanceof CharSequence || v instanceof Number || v instanceof Boolean) continue;
                if (v instanceof java.util.List<?> li) {
                    int n = 0; for (Object e : li) { sheetBfs(e, depth + 1); if (++n >= 200) break; }
                } else if (v instanceof java.util.Map<?,?> mp) {
                    int n = 0; for (Object e : mp.values()) { sheetBfs(e, depth + 1); if (++n >= 200) break; }
                } else if (v.getClass().isArray()) {
                    // Object arrays include Compose slot-table slots[] (thousands of
                    // remembered values incl the BottomSheetState) -- scan widely.
                    if (v instanceof Object[] arr) { int n = 0; for (Object e : arr) { sheetBfs(e, depth + 1); if (++n >= 5000) break; } }
                } else {
                    // Recurse into any non-JDK object (R8 renames androidx into
                    // arbitrary packages, so we cannot allow-list by name). The
                    // visited set + depth cap bound the walk; skip JDK/coroutine
                    // internals to avoid huge unrelated graphs.
                    String vn = v.getClass().getName();
                    if (!vn.startsWith("java.") && !vn.startsWith("javax.")
                        && !vn.startsWith("sun.") && !vn.startsWith("jdk.")
                        && !vn.startsWith("kotlinx.coroutines")
                        && !(v instanceof View)) sheetBfs(v, depth + 1);
                }
            }
        }
    }
    private static void dumpSheetState(Object o, String cn) {
        StringBuilder sb = new StringBuilder("SHEET#" + sSheetProbeTick + " " + cn + " {");
        // DS l: single-letter no-arg getters c()=currentValue g()=targetValue
        // e()=offset(float) d()=bool i()=bool; dump them + the wrapped
        // SwipeableState (field e) offset + Animatable (field k) value + Mutex (p).
        if (cn.equals("com.reddit.ui.compose.ds.l")) {
            // Cache for the lightweight every-frame trajectory probe (stops the BFS).
            if (sCachedSheetL == null) {
                sCachedSheetL = o;
                NativeLog.i(TAG, "SHEETLITE cached l @" + Integer.toHexString(System.identityHashCode(o))
                    + " (probe now reads it every frame with no tree walk)");
            }
            sb.append("id=").append(Integer.toHexString(System.identityHashCode(o))).append(' ');
            for (String g : new String[]{"c","g","e","d","i"}) {
                try { java.lang.reflect.Method m = o.getClass().getMethod(g); m.setAccessible(true);
                    sb.append(g).append("()=").append(m.invoke(o)).append(' '); } catch (Throwable ignored) {}
            }
            // Anchor maps drive the transformLatest open: f() = computed anchors,
            // raw f field = swipeable anchors. Dump their KEY SETS (the anchor
            // offset floats) so we can see if they JITTER frame-to-frame (which
            // would re-emit the anchor snapshotFlow and cancel the open animation).
            try { java.lang.reflect.Method m = o.getClass().getMethod("f"); m.setAccessible(true);
                Object map = m.invoke(o);
                sb.append("anchorsF()=").append(map instanceof java.util.Map ? ((java.util.Map<?,?>) map).keySet() : map).append(' ');
            } catch (Throwable ignored) {}
            try { java.lang.reflect.Field rf = o.getClass().getDeclaredField("f"); rf.setAccessible(true);
                Object st = rf.get(o); Object raw = st.getClass().getMethod("getValue").invoke(st);
                sb.append("rawAnchors=").append(raw instanceof java.util.Map ? ((java.util.Map<?,?>) raw).keySet() : raw).append(' ');
            } catch (Throwable ignored) {}
            for (String fn : new String[]{"e","k","p","i","d","f","h","n"}) {
                try { java.lang.reflect.Field f = o.getClass().getDeclaredField(fn); f.setAccessible(true);
                    Object fv = f.get(o);
                    sb.append("[").append(fn).append("=");
                    if (fv == null) sb.append("null");
                    else { String num = tryReadStateNumber(fv, null);
                        if (num != null) sb.append(num);
                        else sb.append(fv.getClass().getSimpleName()).append(subStateSummary(fv)); }
                    sb.append("] ");
                } catch (Throwable ignored) {}
            }
            NativeLog.i(TAG, sb.append('}').toString());
            return;
        }
        // Try common getters first (DS BottomSheetState keeps readable names).
        for (String g : new String[]{"getCurrentValue","getTargetValue","getOffset","getProgress",
                                      "getDirection","isVisible","isAnimationRunning","getSwipeableState"}) {
            try {
                java.lang.reflect.Method m = o.getClass().getMethod(g);
                m.setAccessible(true);
                Object r = m.invoke(o);
                String rs = (r == null) ? "null"
                    : (r instanceof Number || r instanceof Boolean || r instanceof CharSequence) ? String.valueOf(r)
                    : tryReadStateNumber(r, r.getClass().getSimpleName());
                sb.append(g.substring(3)).append('=').append(rs).append(' ');
            } catch (Throwable ignored) {}
        }
        // Walk float-valued State fields + raw float fields (offset, anchors).
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true); Object v = f.get(o);
                    if (v == null) continue;
                    if (f.getType() == float.class) sb.append(f.getName()).append('=').append(f.getFloat(o)).append(' ');
                    else if (v instanceof java.util.Map) sb.append(f.getName()).append("(anchors?)=").append(((java.util.Map<?,?>) v).values()).append(' ');
                    else {
                        String num = tryReadStateNumber(v, null);
                        if (num != null) sb.append(f.getName()).append('=').append(num).append(' ');
                    }
                } catch (Throwable ignored) {}
            }
        }
        sb.append('}');
        NativeLog.i(TAG, sb.toString());
    }
    /** One-level summary of a sub-state object (SwipeableState e / Animatable k /
     *  Mutex p): dump any float/State-number fields + getValue/isRunning/isLocked. */
    private static String subStateSummary(Object o) {
        StringBuilder sb = new StringBuilder("(");
        for (String g : new String[]{"getValue","isRunning","getCurrentValue","getTargetValue","getOffset","isLocked"}) {
            try { java.lang.reflect.Method m = o.getClass().getMethod(g); m.setAccessible(true);
                Object r = m.invoke(o);
                String rs = (r instanceof Number || r instanceof Boolean || r instanceof CharSequence)
                    ? String.valueOf(r) : (r == null ? "null" : tryReadStateNumber(r, r.getClass().getSimpleName()));
                sb.append(g).append('=').append(rs).append(' ');
            } catch (Throwable ignored) {}
        }
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try { f.setAccessible(true); Object v = f.get(o); if (v == null) continue;
                    if (f.getType() == float.class) sb.append(f.getName()).append('=').append(f.getFloat(o)).append(' ');
                    else { String num = tryReadStateNumber(v, null); if (num != null) sb.append(f.getName()).append('=').append(num).append(' '); }
                } catch (Throwable ignored) {}
            }
        }
        return sb.append(')').toString();
    }
    /** If o is a Compose State<Float>/State<Number>, return its numeric getValue; else null (or `dflt`). */
    private static String tryReadStateNumber(Object o, String dflt) {
        try {
            java.lang.reflect.Method gm = o.getClass().getMethod("getValue");
            gm.setAccessible(true);
            Object cur = gm.invoke(o);
            if (cur instanceof Number) return "State(" + cur + ")";
        } catch (Throwable ignored) {}
        // Compose FloatState exposes getFloatValue()
        try {
            java.lang.reflect.Method gm = o.getClass().getMethod("getFloatValue");
            gm.setAccessible(true);
            Object cur = gm.invoke(o);
            if (cur instanceof Number) return "FloatState(" + cur + ")";
        } catch (Throwable ignored) {}
        return dflt;
    }

    public static void macapkDiagnoseTree(View rootView) {
        if (!TRACE) return;
        try {
            // One-shot: decode EVERY composition in the tree (the welcome content
            // lives in a nested ComposeView the per-host loop below skips when its
            // getRoot is null). Gated on the same MACAPK_DUMP_SLOTS verbose flag.
            if ("1".equals(ENV_MACAPK_DUMP_SLOTS)
                && sDiagCount >= 4 && sDecodeAllRuns < 3) decodeEveryComposition(rootView);
            if ("1".equals(ENV_MACAPK_DUMP_SLOTS) && sDiagCount >= 1 && !sProbedNav) {
                java.util.ArrayList<View> nh = new java.util.ArrayList<>();
                collectComposeViews(rootView, nh);
                NativeLog.i(TAG, "navstack-probe gate: hosts=" + nh.size() + " sDiagCount=" + sDiagCount);
                for (View hh : nh) { try { probeNavstackEntries(hh); } catch (Throwable ignored) {} }
            }
            java.util.ArrayList<View> hosts = new java.util.ArrayList<>();
            collectComposeViews(rootView, hosts);
            // Dump the VIEW hierarchy once we still can't find a ComposeView, OR
            // every ~10th diagnostic pass, so we can see whether the Conductor
            // controller view (with the ComposeView) is actually attached to the
            // window tree vs orphaned.
            if (hosts.isEmpty() || (sDiagCount++ % 10) == 0) {
                StringBuilder vt = new StringBuilder();
                dumpViewTree(rootView, 0, new int[]{0}, vt);
                NativeLog.i(TAG, "view-tree (root=" + (rootView == null ? "null" : rootView.getClass().getName())
                    + "):\n" + vt);
            }
            if (hosts.isEmpty()) { NativeLog.i(TAG, "compose-tree: no AndroidComposeView in tree"); return; }
            int hostCount = hosts.size();
            for (View host : hosts) {
                Object root = findRootLayoutNode(host);
                int hw = host.getRight() - host.getLeft();
                int hh = host.getBottom() - host.getTop();
                if (root == null) { NativeLog.i(TAG, "compose-tree host=" + host.getClass().getName()
                    + " bounds=" + hw + "x" + hh + " getRoot=null"); continue; }
                // {nodes, sized, unreadable}. `unreadable` is separate from
                // `sized` on purpose: a reflective miss must never be reported
                // as a measured-zero (see dumpNode's size read).
                int[] counts = {0, 0, 0};
                java.util.ArrayList<String> texts = new java.util.ArrayList<>();
                StringBuilder sb = new StringBuilder();
                dumpNode(root, 0, counts, texts, new java.util.IdentityHashMap<>(), sb);
                if (counts[0] > sMaxNodes) {
                    sMaxNodes = counts[0];
                    NativeLog.i(TAG, "compose-tree MAX-nodes=" + sMaxNodes
                        + " (hostCount=" + hostCount + ") host=" + host.getClass().getName()
                        + " vis=" + host.getVisibility());
                }
                NativeLog.i(TAG, "compose-tree host=@" + Integer.toHexString(System.identityHashCode(host))
                    + " " + host.getClass().getName()
                    + " vis=" + host.getVisibility() + " bounds=" + hw + "x" + hh + " nodes=" + counts[0]
                    + " sized=" + counts[1]
                    + (counts[2] > 0 ? " unreadable=" + counts[2] : "")
                    + " texts=" + texts.size() + " " + texts);
                NativeLog.i(TAG, "compose-tree:\n" + sb);
                // Suspiciously small tree (content composed but not attached):
                // dump the ROOT's full field inventory so we can SEE whether a
                // children vector with content exists that the walker missed.
                // One-shot probe: navigate the host's parent AbstractComposeView ->
                // its WrappedComposition (field holding {AndroidComposeView, Composition})
                // -> the AndroidComposeView the composition's applier ACTUALLY targets,
                // and compare its root tree to this view-tree host. If the composition's
                // AndroidComposeView is a DIFFERENT instance with a populated tree, the
                // content composed into an ORPHANED (detached) AndroidComposeView.
                if (!sProbedComposition) {
                    sProbedComposition = true;
                    try { probeCompositionRoot(host); } catch (Throwable t) {
                        NativeLog.w(TAG, "probeCompositionRoot: " + t); }
                }
                // v1.0.411: report the Recomposer state for this host. If the
                // content composed (resources load) but the tree is empty, the
                // decisive fork is whether the recomposer's apply is PENDING/stuck
                // (hasPendingWork=true, state=PendingWork -> our frame/dispatch
                // isn't draining the recompose loop) vs Idle (apply ran, composed
                // empty -> upstream). Sampled each diagnose so we see it evolve.
                try { probeRecomposerState(host); } catch (Throwable t) {
                    NativeLog.w(TAG, "probeRecomposerState: " + t); }
                if (sViewStateProbes < 8) {
                    sViewStateProbes++;
                    try { probeMoleculeViewState(host); } catch (Throwable t) {
                        NativeLog.w(TAG, "probeMoleculeViewState: " + t); }
                    // v1.0.434: deep host probe (walks Views too) to read host.e() live.
                    try { probeNavHostDeep(host); } catch (Throwable t) {
                        NativeLog.w(TAG, "probeNavHostDeep: " + t); }
                }
                // v1.0.434 MAXIMAL confirmation force (MACAPK_NAVSTACK_FORCE=1): the
                // welcome ViewState is READY (n9k0 SingleImage) but the entry's
                // visibility latch keeps it out of the AnimatedContent node. Forcing
                // the few stored slot states didn't help (the gate is a derivedStateOf
                // over host.e()/transition). So BFS the WHOLE object graph from the
                // host and force EVERY Boolean MutableState true -- the widest net for
                // the latch + its inputs. Decisive: if the welcome then paints, it IS a
                // visibility-latch gate (narrow down which); if not, it is structural
                // (the content composable is not in the composition). Removal: gate
                // cracked/refuted. See docs/reddit-navstack-structure_2026-06-18.md.
                if (NAVSTACK_FORCE) {
                    try { forceAllBooleanStatesFromHost(host); } catch (Throwable t) {
                        NativeLog.w(TAG, "forceAllBooleanStatesFromHost: " + t); }
                }
                // One-shot: dump the FIRST host's root inventory regardless of node
                // count, so we can compare a WORKING populated root (ComposeTest) vs
                // the broken empty Reddit root (is the root's measurePolicy/coordinator
                // set, or did the AndroidComposeView never finish initializing it?).
                if (!sRootDumped) {
                    sRootDumped = true;
                    NativeLog.i(TAG, "ROOT-INVENTORY (nodes=" + counts[0] + " host="
                        + host.getClass().getName() + "):\n" + fieldInventory(root));
                }
                if (counts[0] <= 4) {
                    // Walk the single-child chain dumping each node's inventory so
                    // we can see whether apply inserted 0 levels (root child is a
                    // wrapper) or 1 level (root child is the content's Surface but
                    // its descendants didn't apply).
                    Object cur = root; int lvl = 0;
                    while (cur != null && lvl < 4) {
                        NativeLog.i(TAG, "node[" + lvl + "]-field-inventory ("
                            + System.identityHashCode(cur) + "):\n" + fieldInventory(cur));
                        java.util.List<Object> ck = layoutNodeChildren(cur);
                        cur = (ck != null && ck.size() == 1) ? ck.get(0) : null;
                        lvl++;
                    }
                }
            }
        } catch (Throwable t) { NativeLog.w(TAG, "diagnoseTree: " + t); }
    }

    // AOSP-confirmed (Compose 1.7.6 sources) SlotTable decoder. The composer
    // writes the group tree into an int[] `groups` (5-int stride per group:
    // [0]=Key [1]=GroupInfo [2]=ParentAnchor [3]=Size [4]=DataAnchor) + an
    // Object[] `slots`; a group is a NODE group iff (GroupInfo & 0x40000000)!=0,
    // and its node lives at slots[groups[g*5+4]]. (Composer.kt createNode L1825 /
    // inserting L1711; SlotTable.kt offsets L3763-3771, NodeBit mask L3775-3804,
    // node read L1199-1202.) Reading this int[] is authoritative -- it shows
    // whether b.d's composables produced GROUPS but no NODE groups (createNode
    // never ran because inserting==false), vs the field-walk BFS which can
    // undercount nodes held only inside the slot Object[]. Field shapes matched
    // by shape (one int[] + one Object[]) so it survives R8 rename drift.
    private static void decodeSlotTable(Object compositionImpl) {
        // Decode EVERY int[]+Object[]-holding field on the CompositionImpl (zva)
        // -- it owns more than one (the SlotTable, plus inner readers/writers),
        // so log each with its field name + group/node counts to be sure we read
        // the real content slot table, not a sibling.
        for (Class<?> c = compositionImpl.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true); Object v = f.get(compositionImpl);
                    if (v == null) continue;
                    boolean hasInt = false, hasObj = false;
                    for (Field g : v.getClass().getDeclaredFields()) {
                        Class<?> t = g.getType();
                        if (t == int[].class) hasInt = true;
                        if (t == Object[].class) hasObj = true;
                    }
                    if (hasInt && hasObj) decodeOneSlotTable(compositionImpl, f.getName(), v);
                } catch (Throwable ignored) {}
            }
        }
    }
    private static void decodeOneSlotTable(Object compositionImpl, String fieldName, Object slotTable) {
        try {
            int[] groups = null; Object[] slots = null; int sizeA = -1, sizeB = -1;
            for (Field f : slotTable.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true); Object v = f.get(slotTable);
                    if (v instanceof int[]) groups = (int[]) v;
                    else if (v instanceof Object[]) slots = (Object[]) v;
                    else if (f.getType() == int.class) { if (sizeA < 0) sizeA = f.getInt(slotTable); else if (sizeB < 0) sizeB = f.getInt(slotTable); }
                } catch (Throwable ignored) {}
            }
            if (groups == null || slots == null) { NativeLog.i(TAG, "slottable-decode: arrays null"); return; }
            int maxGroups = groups.length / 5;
            int groupsSize = maxGroups;
            if (sizeA >= 0 && sizeA <= maxGroups) groupsSize = sizeA;
            else if (sizeB >= 0 && sizeB <= maxGroups) groupsSize = sizeB;
            int nodeGroups = 0; StringBuilder keys = new StringBuilder();
            for (int g = 0; g < groupsSize; g++) {
                int base = g * 5, info = groups[base + 1];
                if ((info & 0x40000000) != 0) {
                    nodeGroups++;
                    int data = groups[base + 4];
                    Object node = (data >= 0 && data < slots.length) ? slots[data] : null;
                    if (nodeGroups <= 20) NativeLog.i(TAG, "slot g#" + g + " key=" + groups[base] + " NODE size=" + groups[base + 3]
                        + " node=" + (node == null ? "null" : node.getClass().getName()));
                }
                if (g < 40) keys.append(groups[base]).append(',');
            }
            NativeLog.i(TAG, "slottable-decode @" + Integer.toHexString(System.identityHashCode(compositionImpl))
                + " field=" + fieldName + ":" + slotTable.getClass().getSimpleName()
                + " TOTALgroups=" + groupsSize + " NODEgroups=" + nodeGroups
                + " slotsLen=" + slots.length + " firstKeys=[" + keys + "]");
            // VERBOSE per-group slot dump (MACAPK_DUMP_SLOTS=1). Decisive for the
            // "what composed?" fork: for each group, the class simpleNames of the
            // objects in its slot-data range [dataAnchor(g), dataAnchor(g+1)) reveal
            // whether b.d composed the SingleImage CONTENT (Painters / BitmapPainter /
            // VectorPainter / Modifier elements / RPL theme objects) or a node-less
            // LOADING branch (CircularProgressIndicator / a sentinel). The group's
            // data slots begin at slots[dataAnchor]; dataAnchor is end-relative when
            // negative (AOSP SlotTable gap convention). Decode is read-only.
            if ("1".equals(ENV_MACAPK_DUMP_SLOTS)) {
                int dumpN = Math.min(groupsSize, 80);
                for (int g = 0; g < dumpN; g++) {
                    int base = g * 5;
                    int da   = resolveAnchor(groups[base + 4], slots.length);
                    int daN  = (g + 1 < groupsSize) ? resolveAnchor(groups[(g + 1) * 5 + 4], slots.length) : slots.length;
                    boolean isNode = (groups[base + 1] & 0x40000000) != 0;
                    StringBuilder sd = new StringBuilder();
                    if (da >= 0 && daN >= da && daN <= slots.length) {
                        for (int s = da; s < daN && s < slots.length; s++) {
                            Object so = slots[s];
                            sd.append(so == null ? "null" : so.getClass().getSimpleName());
                            // include a short value for likely-interesting leaves
                            if (so != null) {
                                String full = so.getClass().getName();
                                if (full.contains("painter") || full.contains("Painter")
                                    || full.contains("Bitmap") || full.contains("Vector")
                                    || full.contains("Color") || so instanceof Integer
                                    || so instanceof String || so instanceof Boolean) {
                                    sd.append('{').append(safeToString(so)).append('}');
                                }
                            }
                            sd.append(' ');
                        }
                    }
                    NativeLog.i(TAG, "slotgrp g#" + g + " key=" + groups[base]
                        + (isNode ? " NODE" : "")
                        + " parent=" + groups[base + 2] + " size=" + groups[base + 3]
                        + " data=[" + da + ".." + daN + ") {" + sd + "}");
                    // If any slot in this group is a List of short-R8-name elements
                    // (the navstack visualState list n.h, the entry/transition lists),
                    // dump each element's LIVE visibility (a()/all-bool-methods) + screen.
                    // This reads the rtv.a() gate value DIRECTLY from the composition
                    // slots (reachable here; the view-field-graph navstack probe missed it).
                    if (da >= 0) {
                        for (int s = da; s < daN && s < slots.length; s++) {
                            Object so = slots[s];
                            if (so == null) continue;
                            // v1.0.428 DECISIVE: the rtv visualState list is a FIELD of the
                            // navstack host object (znb0 @ g#47 slot 101 = com.reddit.navstack.n),
                            // NOT a direct slot -- which is why every prior probe missed it.
                            // Descend one level: a slot object with a List field whose elements
                            // expose a 0-arg Boolean a() (the rtv visibility getter) IS the host;
                            // read each rtv.a() (the m.g `if(rtv.a()==FALSE) continue` gate value)
                            // + the host's bool methods (host.e()) + each entry's ref fields
                            // (the nr0 inputs: backState / enter-Transition / host).
                            if (sNavHostDumps < 4 && !(so instanceof java.util.List)
                                && !(so instanceof CharSequence) && !(so instanceof Number)
                                && looksLikeNavHost(so)) {
                                sNavHostDumps++;
                                probeNavstackHostObject(so, g, s);
                            }
                            // v1.0.434 DECISIVE: read the gate inputs directly off the
                            // NavStackHostImpl by R8-KEPT package (com.reddit.navstack.*),
                            // not the drift-prone shape heuristic. Dumps host.e() (+ all
                            // 0-arg boolean getters) AND every MutableState field's live
                            // value -- so host.e()/the backState/visibility latches are
                            // read directly. See bytecode: n.e()=this.f.getValue().
                            if (!(so instanceof CharSequence) && !(so instanceof Number)
                                && so.getClass().getName().startsWith("com.reddit.navstack.")) {
                                try { probeNavStackByPackage(so); } catch (Throwable ignored) {}
                            }
                            // v1.0.434 CONFIRMATION HACK (gated MACAPK_NAVSTACK_FORCE=1):
                            // test whether the welcome blank is the navstack's per-entry
                            // visibility MutableState (otv.d) staying false. For any
                            // navstack-related slot object (class in com.reddit.navstack.*,
                            // or holding a field typed com.reddit.navstack.*), force every
                            // Boolean MutableState field to TRUE. If the welcome then
                            // paints, otv.d/host.e() IS the gate (find the real signal that
                            // should set it). Removal condition: gate confirmed/refuted.
                            // See docs/reddit-navstack-structure_2026-06-18.md.
                            if (NAVSTACK_FORCE && !(so instanceof CharSequence)
                                && !(so instanceof Number)) {
                                // MutableStates ARE composition slots, so the per-entry
                                // visibility latch is a direct slot: force any Boolean
                                // MutableState slot whose value is FALSE to TRUE.
                                try { forceIfBooleanState(so); } catch (Throwable ignored) {}
                                // And recurse navstack/back-stack objects for nested latches.
                                if (isNavstackRelated(so) || so instanceof java.util.List) {
                                    try { forceBooleanStatesTrue(so); } catch (Throwable ignored) {}
                                }
                            }
                            if (!(so instanceof java.util.List<?> li) || li.isEmpty() || li.size() > 40) continue;
                            Object e0; try { e0 = li.get(0); } catch (Throwable t) { continue; }
                            if (e0 == null) continue;
                            String esn = e0.getClass().getSimpleName();
                            if (esn.length() < 1 || esn.length() > 5 || e0 instanceof CharSequence
                                || e0 instanceof Number || e0.getClass().isArray()) continue;
                            String scr0 = describeEntryScreen(e0);
                            String bm0 = allBoolMethods(e0);
                            if ("?".equals(scr0) && bm0.isEmpty()) continue;
                            NativeLog.i(TAG, "  slotlist g#" + g + " s=" + s + " " + so.getClass().getName()
                                + " size=" + li.size() + " elem=" + e0.getClass().getName());
                            int ei = 0;
                            for (Object e : li) {
                                if (e == null) { NativeLog.i(TAG, "    li#" + (ei++) + " null"); continue; }
                                NativeLog.i(TAG, "    li#" + (ei++) + " " + e.getClass().getName()
                                    + " bools={" + allBoolMethods(e) + "} screen=" + describeEntryScreen(e));
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) { NativeLog.w(TAG, "slottable-decode: " + t); }
    }
    /** AOSP SlotTable data/parent anchors are end-relative when negative
     *  (the gap convention): a negative anchor n maps to slots.length + n + 1.
     *  Non-gapped (post-close) tables store direct non-negative indices. */
    private static int resolveAnchor(int anchor, int len) {
        return anchor < 0 ? len + anchor + 1 : anchor;
    }

    // v1.0.428: read the navstack visibility gate directly from the host object.
    // RENAME-PROOF (this is Reddit 2026.21.0; the rtv.a()/znb0/m.g names from the
    // v1.0.413 study were an OLDER build): detect by STRUCTURE, not R8 names.
    private static int sNavHostDumps = 0;
    /** An APP (non-framework) class: the rtv host + entries are Reddit classes,
     *  which lets us exclude androidx LayoutNode lists that also expose a Boolean. */
    private static boolean isAppClass(Class<?> c) {
        if (c == null) return false;
        String n = c.getName();
        return !(n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("kotlin.")
              || n.startsWith("kotlinx.") || n.startsWith("androidx.") || n.startsWith("android.")
              || n.startsWith("com.google.android.") || n.startsWith("dalvik.") || n.startsWith("sun.")
              || n.startsWith("jdk.") || n.startsWith("com.macapk."));
    }
    /** First 0-arg method returning the BOXED java.lang.Boolean (nullable). rtv's
     *  visibility getter returns Boolean (initial null; the gate compares to
     *  Boolean.FALSE), which distinguishes it whatever R8 named it. */
    private static java.lang.reflect.Method nullableBooleanMethod(Class<?> c) {
        try {
            for (java.lang.reflect.Method m : c.getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (m.getReturnType() == Boolean.class) return m;
            }
        } catch (Throwable ignored) {}
        return null;
    }
    /** True if `o` is an app object holding a List field of app-class elements that
     *  each expose a boxed-Boolean getter -- i.e. the navstack host n holding the
     *  rtv visualState list. Excludes androidx LayoutNode lists. */
    private static boolean looksLikeNavHost(Object o) {
        if (!isAppClass(o.getClass())) return false;
        try {
            for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    Object v; try { f.setAccessible(true); v = f.get(o); } catch (Throwable t) { continue; }
                    if (v instanceof java.util.List<?> li && !li.isEmpty() && li.size() <= 40) {
                        Object e0; try { e0 = li.get(0); } catch (Throwable t) { continue; }
                        if (e0 != null && isAppClass(e0.getClass()) && nullableBooleanMethod(e0.getClass()) != null) return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
    /** Dump the live navstack gate: host.e() (and all host bool methods), then for
     *  each rtv in the visualState List, a() (the m.g skip value) + bool methods +
     *  screen + ref-field summary (backState / enter-Transition / host inputs to nr0). */
    private static void probeNavstackHostObject(Object n, int g, int s) {
        NativeLog.i(TAG, "NAVHOST g#" + g + " s=" + s + " " + n.getClass().getName()
            + " hostBools={" + allBoolMethods(n) + "} hostFields={" + summarizeRefFields(n) + "}");
        try {
            for (Class<?> c = n.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    Object v; try { f.setAccessible(true); v = f.get(n); } catch (Throwable t) { continue; }
                    if (!(v instanceof java.util.List<?> li) || li.isEmpty() || li.size() > 40) continue;
                    Object e0; try { e0 = li.get(0); } catch (Throwable t) { continue; }
                    if (e0 == null || !isAppClass(e0.getClass())) continue;
                    java.lang.reflect.Method am = nullableBooleanMethod(e0.getClass());
                    if (am == null) continue;
                    NativeLog.i(TAG, "  NAVHOST.field " + f.getName() + " = List<" + e0.getClass().getName()
                        + "> size=" + li.size() + " visGetter=" + am.getName());
                    int i = 0;
                    for (Object e : li) {
                        if (e == null) { NativeLog.i(TAG, "    rtv#" + (i++) + " null"); continue; }
                        Object vis; try { am.setAccessible(true); vis = am.invoke(e); } catch (Throwable t) { vis = "<threw>"; }
                        NativeLog.i(TAG, "    rtv#" + (i++) + " a()=" + vis
                            + " bools={" + allBoolMethods(e) + "}"
                            + " screen=" + describeEntryScreen(e)
                            + " fields={" + summarizeRefFields(e) + "}");
                    }
                }
            }
        } catch (Throwable t) { NativeLog.w(TAG, "NAVHOST: " + t); }
    }
    // v1.0.434: read the navstack gate inputs directly off a com.reddit.navstack.*
    // object (the package is R8-KEPT; only the class letter drifts). The host
    // (NavStackHostImpl) has a 0-arg boolean e() (= this.f.getValue(), the gate's
    // host.e() input) + MutableState fields e/f/g + a List of entries. We dump
    // every 0-arg boolean getter's LIVE value + every MutableState field's value,
    // so host.e() and the per-entry visibility latches are read directly -- the
    // decisive "which gate input is false" datum, drift-proof vs the shape probe.
    private static int sNavPkgDumps = 0;
    private static boolean probeNavStackByPackage(Object o) {
        if (sNavPkgDumps >= 8) return false;
        Class<?> c = o.getClass();
        boolean hasBool = false, hasList = false, hasState = false;
        for (java.lang.reflect.Method m : c.getDeclaredMethods())
            if (m.getParameterCount() == 0 && m.getReturnType() == boolean.class) { hasBool = true; break; }
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (java.util.List.class.isAssignableFrom(f.getType())) hasList = true;
            String tn = f.getType().getName();
            if (tn.length() <= 4 || tn.contains("compose")) hasState = true;  // m0y / MutableState
        }
        // The host is the navstack object that has BOTH a boolean getter and a
        // List of entries (other navstack classes lack one or the other).
        if (!hasBool || !hasList) return false;
        sNavPkgDumps++;
        StringBuilder sb = new StringBuilder("NAVHOST-PKG " + c.getName() + " bools={");
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                try { m.setAccessible(true); sb.append(m.getName()).append("()=").append(m.invoke(o)).append(' '); }
                catch (Throwable t) {}
            }
        }
        sb.append("} states={");
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            Object v; try { f.setAccessible(true); v = f.get(o); } catch (Throwable t) { continue; }
            if (v == null) continue;
            Object gv = null;
            try { java.lang.reflect.Method gm = v.getClass().getMethod("getValue");
                  gm.setAccessible(true); gv = gm.invoke(v); } catch (Throwable t) {}
            if (gv != null) sb.append(f.getName()).append(".value=").append(safeToString(gv)).append(' ');
            else if (v instanceof java.util.List<?> li) {
                sb.append(f.getName()).append("=List[").append(li.size()).append(']');
                if (!li.isEmpty()) { try { sb.append('(').append(li.get(0).getClass().getSimpleName()).append(')'); } catch (Throwable t) {} }
                sb.append(' ');
            }
        }
        sb.append('}');
        NativeLog.i(TAG, sb.toString());
        return true;
    }

    // v1.0.434 confirmation hack support. An object is "navstack-related" if its own
    // class is in the R8-kept com.reddit.navstack package, or it holds a field typed
    // in that package (catches the entry `otv` whose field a = com.reddit.navstack.q).
    private static boolean isNavstackRelated(Object o) {
        Class<?> c = o.getClass();
        if (c.getName().startsWith("com.reddit.navstack.")) return true;
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (f.getType().getName().startsWith("com.reddit.navstack.")) return true;
            }
        }
        return false;
    }
    private static final java.util.Set<Object> sForced =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** Force every Boolean-valued Compose MutableState reachable from `o` (through
     *  navstack-related fields + List elements, bounded depth) to TRUE -- the
     *  per-entry visibility otv.d, host.e()=n.f, etc. The entries live inside the
     *  back-stack List (znb0), so this recurses to reach them. Once per object. */
    /** If `o` itself is a Compose MutableState whose value is Boolean.FALSE, set it
     *  TRUE. MutableStates are stored directly as composition slots, so the
     *  per-entry visibility latch is reachable here. */
    private static void forceIfBooleanState(Object o) {
        if (o == null) return;   // idempotent (set TRUE), no sForced guard so forceRec can still recurse o
        try {
            java.lang.reflect.Method gm = o.getClass().getMethod("getValue");
            gm.setAccessible(true);
            Object cur = gm.invoke(o);
            if (cur instanceof Boolean && !((Boolean) cur)) {
                java.lang.reflect.Method sm = o.getClass().getMethod("setValue", Object.class);
                sm.setAccessible(true);
                sm.invoke(o, Boolean.TRUE);
                NativeLog.i(TAG, "NAVSTACK_FORCE slot-state " + o.getClass().getName()
                    + " (was false) -> TRUE");
            }
        } catch (Throwable ignored) {}
    }
    private static void forceBooleanStatesTrue(Object o) { forceRec(o, 0); }
    private static void forceRec(Object o, int depth) {
        if (o == null || depth > 5 || !sForced.add(o)) return;
        Class<?> c = o.getClass();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Object v; try { f.setAccessible(true); v = f.get(o); } catch (Throwable t) { continue; }
                if (v == null) continue;
                // 1. Force a Boolean MutableState field to TRUE.
                try {
                    java.lang.reflect.Method gm = v.getClass().getMethod("getValue");
                    gm.setAccessible(true);
                    Object cur = gm.invoke(v);
                    if (cur instanceof Boolean && !((Boolean) cur)) {
                        java.lang.reflect.Method sm = v.getClass().getMethod("setValue", Object.class);
                        sm.setAccessible(true);
                        sm.invoke(v, Boolean.TRUE);
                        NativeLog.i(TAG, "NAVSTACK_FORCE set " + c.getName() + "." + f.getName()
                            + " (was false) -> TRUE");
                    }
                } catch (Throwable ignored) {}
                // 2. Recurse into navstack-related field objects (host -> entries).
                if (depth < 5 && !(v instanceof CharSequence) && !(v instanceof Number)
                    && !(v instanceof Boolean) && !(v instanceof java.util.List) && isNavstackRelated(v)) {
                    forceRec(v, depth + 1);
                }
                // 3. Recurse into List elements (the back stack znb0 holds the entries).
                if (depth < 5 && v instanceof java.util.List<?> li) {
                    int n = 0;
                    for (Object e : li) {
                        if (e != null && isNavstackRelated(e)) forceRec(e, depth + 1);
                        if (++n >= 40) break;
                    }
                }
            }
        }
    }

    /** One-line summary of an object's instance fields: "name=val_or_ClassSimpleName".
     *  Reveals an rtv's nr0 inputs (its host ref, backState, enter-Transition). */
    private static String summarizeRefFields(Object o) {
        StringBuilder sb = new StringBuilder(); int n = 0;
        try {
            for (Class<?> c = o.getClass(); c != null && c != Object.class && n < 24; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    Object v; try { f.setAccessible(true); v = f.get(o); } catch (Throwable t) { continue; }
                    String vs;
                    if (v == null) vs = "null";
                    else if (v instanceof Boolean || v instanceof Number || v instanceof CharSequence) vs = String.valueOf(v);
                    else vs = v.getClass().getSimpleName();
                    sb.append(f.getName()).append('=').append(vs).append(' ');
                    if (++n >= 24) break;
                }
            }
        } catch (Throwable ignored) {}
        return sb.toString();
    }

    private static int sDecodeAllRuns = 0;
    private static boolean sDumpedAllComps = false;
    /** Decode EVERY composition in the whole view tree, not just the one host
     *  that survived the getRoot!=null gate. Reddit hosts the welcome screen in a
     *  nested ComposeView (Conductor controller view) separate from the
     *  ScreenContainerView's screen-container ComposeView, so the welcome content
     *  (b.d -> gq00.D Surface / m2k0 bg Image / b5f0 overlay) lives in a DIFFERENT
     *  CompositionImpl than the one decodeSlotTable was reaching. For each View in
     *  the tree, find its WrappedComposition (androidx.compose.ui.platform.a0,
     *  field on AbstractComposeView) and decode a0.b (the CompositionImpl). One-shot. */
    private static void decodeEveryComposition(View root) {
        if (sDecodeAllRuns >= 3) return; sDecodeAllRuns++;
        try {
            java.util.ArrayList<View> all = new java.util.ArrayList<>();
            flattenViews(root, all, 0);
            int composeish = 0, decoded = 0;
            NativeLog.i(TAG, "decode-all run#" + sDecodeAllRuns + ": scanning " + all.size() + " views for compositions");
            for (View v : all) {
                try {
                    String cn = v.getClass().getName();
                    boolean composeView = cn.contains("ComposeView") || cn.startsWith("androidx.compose.ui.platform.");
                    // Find the WrappedComposition (a0) by: (1) field named "c" (the
                    // AbstractComposeView.composition slot the working probe uses),
                    // (2) any field whose value's class is androidx.compose.ui.platform.a0.
                    Object wrapped = readField(v, "c");
                    if (wrapped == null || !"androidx.compose.ui.platform.a0".equals(wrapped.getClass().getName()))
                        wrapped = findFieldValueOfClassName(v, "androidx.compose.ui.platform.a0");
                    if (!composeView && wrapped == null) continue;
                    composeish++;
                    Object comp = wrapped != null ? readField(wrapped, "b") : null;  // a0.b = CompositionImpl (zva)
                    Object acv  = wrapped != null ? readField(wrapped, "a") : null;   // a0.a = AndroidComposeView
                    NativeLog.i(TAG, "decode-all HOST " + cn + "@" + Integer.toHexString(System.identityHashCode(v))
                        + " parent=" + (v.getParent() == null ? "null" : v.getParent().getClass().getSimpleName())
                        + " wrapped=" + (wrapped == null ? "NONE" : "@" + Integer.toHexString(System.identityHashCode(wrapped)))
                        + " comp=" + (comp == null ? "null" : comp.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(comp)))
                        + " acv=" + (acv == null ? "null" : "@" + Integer.toHexString(System.identityHashCode(acv))));
                    if (comp != null) { decodeSlotTable(comp); decoded++; }
                } catch (Throwable ignored) {}
            }
            NativeLog.i(TAG, "decode-all run#" + sDecodeAllRuns + " done: composeViews=" + composeish + " decoded=" + decoded);
        } catch (Throwable t) { NativeLog.w(TAG, "decode-all: " + t); }
    }
    private static void flattenViews(View v, java.util.List<View> out, int depth) {
        if (v == null || depth > 60) return;
        out.add(v);
        if (v instanceof android.view.ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) flattenViews(vg.getChildAt(i), out, depth + 1);
        }
    }
    /** Scan all instance fields (full hierarchy) of `o` for the first value whose
     *  runtime class name equals `cn`. Used to locate the WrappedComposition on an
     *  AbstractComposeView regardless of R8 field-name rename. */
    private static Object findFieldValueOfClassName(Object o, String cn) {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try { f.setAccessible(true); Object v = f.get(o);
                    if (v != null && v.getClass().getName().equals(cn)) return v; }
                catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static int sForceRuns = 0;
    /** BFS the object graph from the host (like probeMoleculeViewState) and force
     *  EVERY Boolean Compose MutableState to TRUE. The widest possible net for the
     *  per-entry visibility latch + the host.e()/transition inputs the derived gate
     *  reads. Bounded budget + visited set; logs a count of what flipped. */
    private static void forceAllBooleanStatesFromHost(View host) {
        if (sForceRuns >= 4) return; sForceRuns++;
        java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
        java.util.IdentityHashMap<Object,Boolean> seen = new java.util.IdentityHashMap<>();
        q.add(host);
        int budget = 60000, flipped = 0;
        while (!q.isEmpty() && budget-- > 0) {
            Object o = q.poll();
            if (o == null || seen.put(o, Boolean.TRUE) != null) continue;
            // Flip o if it is a Boolean MutableState.
            try {
                java.lang.reflect.Method gm = o.getClass().getMethod("getValue");
                gm.setAccessible(true);
                Object cur = gm.invoke(o);
                if (cur instanceof Boolean && !((Boolean) cur)) {
                    java.lang.reflect.Method sm = o.getClass().getMethod("setValue", Object.class);
                    sm.setAccessible(true);
                    sm.invoke(o, Boolean.TRUE);
                    flipped++;
                    if (flipped <= 40) NativeLog.i(TAG, "FORCE-ALL flip " + o.getClass().getName());
                }
            } catch (Throwable ignored) {}
            // Enqueue neighbors (skip the View tree + JDK leaves to stay bounded).
            try {
                if (o instanceof java.lang.ref.Reference) {
                    Object r = ((java.lang.ref.Reference<?>) o).get(); if (r != null) q.add(r); continue;
                }
                if (o instanceof java.util.Map) { for (Object e : ((java.util.Map<?,?>) o).values()) if (e != null) q.add(e); continue; }
                if (o instanceof Iterable) { for (Object e : (Iterable<?>) o) if (e != null) q.add(e); continue; }
                if (o instanceof Object[]) { for (Object e : (Object[]) o) if (e != null) q.add(e); continue; }
                if (o instanceof View && o != host) continue;
                String cn = o.getClass().getName();
                if (cn.startsWith("java.") || cn.startsWith("kotlin.") || cn.startsWith("android.")
                    || cn.startsWith("androidx.") && !cn.contains("compose")) continue;
                Class<?> c = o.getClass(); int guard = 0;
                while (c != null && c != Object.class && guard++ < 8) {
                    for (Field f : c.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                        try { f.setAccessible(true); Object v = f.get(o); if (v != null) q.add(v); } catch (Throwable ignored) {}
                    }
                    c = c.getSuperclass();
                }
            } catch (Throwable ignored) {}
        }
        NativeLog.i(TAG, "FORCE-ALL run#" + sForceRuns + " flipped " + flipped
            + " Boolean MutableStates true (budgetLeft=" + budget + ")");
    }

    // v1.0.434 DEEP host probe: the navstack host n (NavStackHostImpl) is owned by
    // the screen/Router BEHIND the Conductor controllers' Views, so the molecule BFS
    // (which skips Views) never reaches it. This walks the WHOLE tree from the root
    // View INCLUDING View fields, finds the host (directly in com.reddit.navstack,
    // with a 0-arg boolean e() AND a Boolean MutableState field), and reads host.e()
    // LIVE -- the decisive fork. With MACAPK_NAVSTACK_FORCE it then forces the host's
    // Boolean states true to test whether host.e() is THE gate.
    private static int sDeepNavRuns = 0;
    private static void probeNavHostDeep(View host) {
        if (sDeepNavRuns >= 3) return; sDeepNavRuns++;
        View root = host; int up = 0;
        while (root.getParent() instanceof View pv && up++ < 80) root = pv;
        java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
        java.util.IdentityHashMap<Object,Boolean> seen = new java.util.IdentityHashMap<>();
        q.add(root);
        int budget = 250000;
        java.util.ArrayList<Object> hosts = new java.util.ArrayList<>();
        final String PKG = "com.reddit.navstack.";
        while (!q.isEmpty() && budget-- > 0) {
            Object o = q.poll();
            if (o == null || seen.put(o, Boolean.TRUE) != null) continue;
            String cn; try { cn = o.getClass().getName(); } catch (Throwable t) { continue; }
            if (hosts.size() < 8 && cn.startsWith(PKG) && cn.indexOf('.', PKG.length()) < 0) {
                boolean hasE = false, hasBoolState = false;
                try {
                    for (java.lang.reflect.Method mm : o.getClass().getDeclaredMethods())
                        if (mm.getParameterCount() == 0 && mm.getReturnType() == boolean.class) { hasE = true; break; }
                    for (Field ff : o.getClass().getDeclaredFields()) {
                        try {
                            ff.setAccessible(true); Object fv = ff.get(o);
                            if (fv != null) {
                                java.lang.reflect.Method gm = fv.getClass().getMethod("getValue");
                                gm.setAccessible(true);
                                if (gm.invoke(fv) instanceof Boolean) { hasBoolState = true; break; }
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
                if (hasE && hasBoolState && !hosts.contains(o)) hosts.add(o);
            }
            try {
                if (o instanceof java.lang.ref.Reference) { Object r = ((java.lang.ref.Reference<?>) o).get(); if (r != null) q.add(r); continue; }
                if (o instanceof java.util.Map) { for (Object e : ((java.util.Map<?,?>) o).values()) if (e != null) q.add(e); continue; }
                if (o instanceof Iterable) { for (Object e : (Iterable<?>) o) if (e != null) q.add(e); continue; }
                if (o instanceof Object[]) { for (Object e : (Object[]) o) if (e != null) q.add(e); continue; }
                if (cn.startsWith("java.") || cn.startsWith("sun.") || cn.startsWith("jdk.")) continue;
                Class<?> c = o.getClass(); int guard = 0;
                while (c != null && c != Object.class && guard++ < 10) {
                    for (Field f : c.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                        try { f.setAccessible(true); Object v = f.get(o); if (v != null) q.add(v); } catch (Throwable ignored) {}
                    }
                    c = c.getSuperclass();
                }
            } catch (Throwable ignored) {}
        }
        if (hosts.isEmpty()) { NativeLog.i(TAG, "navhost-deep: host NOT found (budgetLeft=" + budget + ")"); return; }
        NativeLog.i(TAG, "navhost-deep: found " + hosts.size() + " navstack host(s)");
        int hi = 0;
        for (Object navHost : hosts) {
            StringBuilder sb = new StringBuilder("navhost-deep#" + (hi++) + " " + navHost.getClass().getName() + " bools={");
            for (java.lang.reflect.Method mm : navHost.getClass().getDeclaredMethods())
                if (mm.getParameterCount() == 0 && mm.getReturnType() == boolean.class) {
                    try { mm.setAccessible(true); sb.append(mm.getName()).append("()=").append(mm.invoke(navHost)).append(' '); } catch (Throwable t) {}
                }
            sb.append("} states={");
            for (Field ff : navHost.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(ff.getModifiers())) continue;
                Object v; try { ff.setAccessible(true); v = ff.get(navHost); } catch (Throwable t) { continue; }
                if (v == null) continue;
                Object gv = null;
                try { java.lang.reflect.Method gm = v.getClass().getMethod("getValue"); gm.setAccessible(true); gv = gm.invoke(v); } catch (Throwable t) {}
                if (gv != null) {
                    sb.append(ff.getName()).append('=').append(v.getClass().getSimpleName()).append(".value=").append(safeToString(gv)).append(' ');
                    // If the MutableState holds a List (entries), dump element count.
                    if (gv instanceof java.util.List<?> gl) sb.append("[items=").append(gl.size()).append("] ");
                } else if (v instanceof java.util.List<?> li) {
                    sb.append(ff.getName()).append("=List[").append(li.size()).append("] ");
                }
            }
            sb.append('}');
            NativeLog.i(TAG, sb.toString());
            if (NAVSTACK_FORCE) forceBooleanStatesTrue(navHost);
        }
        if (NAVSTACK_FORCE) NativeLog.i(TAG, "navhost-deep: forced all hosts' Boolean states TRUE (MACAPK_NAVSTACK_FORCE)");
    }

    private static int sViewStateProbes = 0;
    /** BFS from the host for the Reddit CompositionViewModel (e.g.
     *  WelcomeV2ViewModel) reachable via the content lambda's captured screen,
     *  then read its molecule ViewState (t().<derivedState>.getValue()) and log
     *  its concrete type. A loading/sentinel type (jyk0) => the molecule never
     *  produced the populated state (app-environment / data layer); a populated
     *  type (n9k0/m9k0) => the molecule produced but the content still didn't
     *  render (framework). Decisive fork for the blank-screen cause. */
    private static void probeMoleculeViewState(View host) {
        try {
            java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
            java.util.IdentityHashMap<Object,Boolean> seen = new java.util.IdentityHashMap<>();
            q.add(host);
            int budget = 40000;
            Object vm = null, navHost = null;
            while (!q.isEmpty() && budget-- > 0) {
                Object o = q.poll();
                if (o == null || seen.put(o, Boolean.TRUE) != null) continue;
                String cn; try { cn = o.getClass().getName(); } catch (Throwable t) { continue; }
                // v1.0.434: capture the NavStackHostImpl (com.reddit.navstack.* object
                // with a 0-arg boolean e() + a List field) so we can read host.e()
                // LIVE -- the decisive fork (lifecycle gap vs transition gate). Don't
                // break: keep walking for the VM too.
                if (navHost == null && cn.startsWith("com.reddit.navstack.")
                    && cn.indexOf('.', "com.reddit.navstack.".length()) < 0) {  // DIRECTLY in the package (not .features.*)
                    boolean hasE = false, hasList = false, hasBoolState = false;
                    try {
                        for (java.lang.reflect.Method mm : o.getClass().getDeclaredMethods())
                            if (mm.getParameterCount() == 0 && mm.getReturnType() == boolean.class) { hasE = true; break; }
                        for (Field ff : o.getClass().getDeclaredFields()) {
                            if (java.util.List.class.isAssignableFrom(ff.getType())) hasList = true;
                            if (hasBoolState) continue;
                            try {
                                ff.setAccessible(true); Object fv = ff.get(o);
                                if (fv != null) {
                                    java.lang.reflect.Method gm = fv.getClass().getMethod("getValue");
                                    gm.setAccessible(true);
                                    if (gm.invoke(fv) instanceof Boolean) hasBoolState = true;
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                    if (hasE && hasList && hasBoolState) navHost = o;  // NavStackHostImpl: e()+List+Boolean MutableState
                }
                // CompositionViewModel subclass (the molecule host). Match the
                // concrete screen VM or the base class by structure.
                if (vm == null && (cn.startsWith("com.reddit.auth.login.screen.welcomev2.WelcomeV2ViewModel")
                    || cn.equals("com.reddit.screen.presentation.CompositionViewModel")
                    || isSubclassNamed(o.getClass(), "com.reddit.screen.presentation.CompositionViewModel"))) {
                    vm = o; if (navHost != null) break;
                }
                if (o instanceof java.lang.ref.Reference) {
                    try { Object r = ((java.lang.ref.Reference<?>) o).get(); if (r != null && !seen.containsKey(r)) q.add(r); } catch (Throwable ignored) {}
                    continue;
                }
                if (o instanceof java.util.Map) {
                    try { for (Object e : ((java.util.Map<?,?>) o).values()) if (e != null && !seen.containsKey(e)) q.add(e); } catch (Throwable ignored) {}
                    continue;
                }
                if (o instanceof Iterable) {
                    try { for (Object e : (Iterable<?>) o) if (e != null && !seen.containsKey(e)) q.add(e); } catch (Throwable ignored) {}
                    continue;
                }
                if (o instanceof Object[]) { for (Object e : (Object[]) o) if (e != null && !seen.containsKey(e)) q.add(e); continue; }
                if (o instanceof View && o != host) continue;   // don't walk the whole view tree
                // m0y (SnapshotMutableState) holds the content lambda -> getValue().
                String sn = o.getClass().getSimpleName();
                if (sn.equals("m0y") || cn.endsWith("SnapshotMutableStateImpl") || cn.equals("zva")) {
                    try { Object v = invokeNoArg(o, "getValue"); if (v != null && !seen.containsKey(v)) q.add(v); } catch (Throwable ignored) {}
                }
                Class<?> c = o.getClass(); int guard = 0;
                while (c != null && c != Object.class && guard++ < 8) {
                    for (Field f : c.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                        try { f.setAccessible(true); Object v = f.get(o); if (v != null && !seen.containsKey(v)) q.add(v); } catch (Throwable ignored) {}
                    }
                    c = c.getSuperclass();
                }
            }
            // v1.0.434 DECISIVE: dump the navstack host's LIVE gate inputs. host.e()
            // (= the n.f MutableState<Boolean>) is THE fork: true => host active, the
            // blank is the Conductor transition/visible-entry computation; false =>
            // a lifecycle signal we do not deliver to the navstack host is the gate.
            if (navHost != null) {
                StringBuilder sb = new StringBuilder("navhost-live " + navHost.getClass().getName() + " bools={");
                for (java.lang.reflect.Method mm : navHost.getClass().getDeclaredMethods()) {
                    if (mm.getParameterCount() == 0 && mm.getReturnType() == boolean.class) {
                        try { mm.setAccessible(true); sb.append(mm.getName()).append("()=").append(mm.invoke(navHost)).append(' '); }
                        catch (Throwable t) {}
                    }
                }
                sb.append("} states={");
                for (Field ff : navHost.getClass().getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(ff.getModifiers())) continue;
                    Object v; try { ff.setAccessible(true); v = ff.get(navHost); } catch (Throwable t) { continue; }
                    if (v == null) continue;
                    Object gv = null;
                    try { java.lang.reflect.Method gm = v.getClass().getMethod("getValue"); gm.setAccessible(true); gv = gm.invoke(v); }
                    catch (Throwable t) {}
                    if (gv != null) sb.append(ff.getName()).append(".value=").append(safeToString(gv)).append(' ');
                    else if (v instanceof java.util.List<?> li) sb.append(ff.getName()).append("=List[").append(li.size()).append("] ");
                }
                sb.append('}');
                NativeLog.i(TAG, sb.toString());
            } else {
                NativeLog.i(TAG, "navhost-live: no com.reddit.navstack host reachable from host");
            }
            if (vm == null) { NativeLog.i(TAG, "viewstate-probe: no CompositionViewModel reachable from "
                + host.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(host))
                + " vis=" + host.getVisibility()); return; }
            NativeLog.i(TAG, "viewstate-probe (from host @" + Integer.toHexString(System.identityHashCode(host)) + "):");
            Object holder = invokeNoArg(vm, "t");   // CompositionViewModel.t() -> holder b
            Object vs = null;
            if (holder != null) {
                // holder.c is the derived state (zqj0); getValue() -> ViewState.
                Object derived = readField(holder, "c");
                if (derived != null) vs = invokeNoArg(derived, "getValue");
                if (vs == null) { Object hb = readField(holder, "b"); if (hb != null) vs = invokeNoArg(hb, "getValue"); }
            }
            NativeLog.i(TAG, "viewstate-probe vm=" + vm.getClass().getName()
                + " holder=" + (holder == null ? "null" : holder.getClass().getName())
                + " ViewState=" + (vs == null ? "null" : vs.getClass().getName())
                + " value=" + (vs == null ? "null" : safeToString(vs)));
        } catch (Throwable t) { NativeLog.w(TAG, "viewstate-probe: " + t); }
    }
    // v1.0.488: DECISIVE live navstack-HOST probe (MACAPK_NAVSTACK_HOST_PROBE=1, one-shot
    // once the host appears). Finds the bespoke navstack host BY PACKAGE
    // (com.reddit.navstack.n -- R8 KEEPS the package, drifts the class letter) and dumps
    // its full live state so we can see WHICH input to the visible-entry gate is empty:
    //   - every no-arg boolean method (host.e() = readiness = n.f MutableState),
    //   - every field that is a Compose State (MutableState value, incl. Boolean -- the
    //     readStateAny used elsewhere only returns Float/enum), and
    //   - every List field (the entries/back-stack) with per-element class + bool methods
    //     + a per-entry State value (the entry visibility otv.d) + the wrapped Screen.
    // The decompile of the gate logic (what SHOULD make it visible) runs in parallel; this
    // probe gives the LIVE values (which input is actually false/empty on Mac.apk). Read-only.
    private static boolean sProbedNavHost = false;
    public static void probeNavstackHost(View rootView) {
        if (rootView == null || !"1".equals(ENV_MACAPK_NAVSTACK_HOST_PROBE)) return;
        if (sProbedNavHost) return;
        try {
            java.util.ArrayList<View> hosts = new java.util.ArrayList<>();
            collectComposeViews(rootView, hosts);
            java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
            java.util.IdentityHashMap<Object,Boolean> seen = new java.util.IdentityHashMap<>();
            for (View h : hosts) { q.add(h); Object p = h.getParent(); if (p != null) q.add(p); }
            int budget = 500000, found = 0;
            while (!q.isEmpty() && budget-- > 0 && found < 4) {
                Object o = q.poll();
                if (o == null || seen.put(o, Boolean.TRUE) != null) continue;
                String cn; try { cn = o.getClass().getName(); } catch (Throwable t) { continue; }
                if (cn.startsWith("com.reddit.navstack.n") && cn.length() <= 24) { // the host (n / nN)
                    found++; dumpNavHost(o);
                }
                if (o instanceof java.lang.ref.Reference) { try { Object r = ((java.lang.ref.Reference<?>) o).get(); if (r != null && !seen.containsKey(r)) q.add(r); } catch (Throwable ig) {} continue; }
                if (o instanceof java.util.Map) { try { for (Object e : ((java.util.Map<?,?>) o).values()) if (e != null && !seen.containsKey(e)) q.add(e); } catch (Throwable ig) {} continue; }
                if (o instanceof Iterable) { try { for (Object e : (Iterable<?>) o) if (e != null && !seen.containsKey(e)) q.add(e); } catch (Throwable ig) {} continue; }
                if (o instanceof Object[]) { for (Object e : (Object[]) o) if (e != null && !seen.containsKey(e)) q.add(e); continue; }
                // Bound the walk: only descend reddit/androidx/kotlin/java.util objects.
                if (!(cn.startsWith("com.reddit") || cn.startsWith("androidx") || cn.startsWith("kotlin") || cn.startsWith("java.util"))) continue;
                Class<?> c = o.getClass(); int guard = 0;
                while (c != null && c != Object.class && guard++ < 8) {
                    for (Field f : c.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                        try { f.setAccessible(true); Object v = f.get(o); if (v != null && !seen.containsKey(v)) q.add(v); } catch (Throwable ig) {}
                    }
                    c = c.getSuperclass();
                }
            }
            NativeLog.i(TAG, "navstack-host-probe: found " + found + " com.reddit.navstack host(s), visited=" + seen.size());
            if (found > 0) sProbedNavHost = true;
        } catch (Throwable t) { NativeLog.w(TAG, "navstack-host-probe: " + t); }
    }
    private static void dumpNavHost(Object n) {
        NativeLog.i(TAG, "NAVHOST n=@" + Integer.toHexString(System.identityHashCode(n)) + " " + n.getClass().getName());
        try {
            for (java.lang.reflect.Method m : n.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() != 0) continue;
                Class<?> rt = m.getReturnType();
                if (rt == boolean.class || rt == Boolean.class) {
                    try { m.setAccessible(true); Object v = m.invoke(n); NativeLog.i(TAG, "  host." + m.getName() + "()=" + v); } catch (Throwable ig) {}
                }
            }
        } catch (Throwable ig) {}
        Class<?> c = n.getClass(); int guard = 0;
        while (c != null && c != Object.class && guard++ < 4) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true); Object v = f.get(n);
                    if (v == null) { NativeLog.i(TAG, "  field " + f.getName() + " = null"); continue; }
                    String st = readAnyStateValue(v);
                    if (st != null) { NativeLog.i(TAG, "  field " + f.getName() + " (State " + v.getClass().getSimpleName() + ") = " + st); continue; }
                    if (v instanceof java.util.List<?> list) {
                        NativeLog.i(TAG, "  field " + f.getName() + " (List " + v.getClass().getSimpleName() + ") size=" + list.size());
                        int idx = 0;
                        for (Object e : list) {
                            if (idx >= 8) break;
                            NativeLog.i(TAG, "    entry#" + (idx++) + " " + (e == null ? "null" : e.getClass().getName())
                                + " bools=" + (e == null ? "" : allBoolMethods(e))
                                + " vis=" + (e == null ? "" : entryStateFields(e))
                                + " screen=" + (e == null ? "" : describeEntryScreen(e)));
                        }
                    } else if (v.getClass().getName().startsWith("com.reddit.navstack")) {
                        NativeLog.i(TAG, "  field " + f.getName() + " = " + v.getClass().getName()
                            + " {" + entryStateFields(v) + "}");
                    } else {
                        NativeLog.i(TAG, "  field " + f.getName() + " = " + v.getClass().getSimpleName());
                    }
                } catch (Throwable ig) {}
            }
            c = c.getSuperclass();
        }
    }
    /** All Compose-State fields of o (incl. Boolean MutableState m0y) with live values. */
    private static String entryStateFields(Object o) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Field f : o.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v; try { v = f.get(o); } catch (Throwable t) { continue; }
                if (v == null) continue;
                String st = readAnyStateValue(v);
                if (st != null) sb.append(f.getName()).append('=').append(st).append(' ');
            }
        } catch (Throwable ig) {}
        return sb.toString();
    }
    /** Returns the value of a Compose State (getValue/getFloatValue/getIntValue), incl.
     *  Boolean -- ONLY for State-shaped objects (R8 MutableState m0y is a short name, or
     *  androidx.compose.runtime.*); avoids calling getValue on arbitrary objects. */
    private static String readAnyStateValue(Object st) {
        if (st == null) return null;
        String cn = st.getClass().getName();
        boolean stateShaped = cn.startsWith("androidx.compose.runtime")
            || st.getClass().getSimpleName().length() <= 5; // R8 m0y / sm0 etc.
        if (!stateShaped) return null;
        for (String mn : new String[]{"getValue", "getFloatValue", "getIntValue"}) {
            try {
                java.lang.reflect.Method m = st.getClass().getMethod(mn);
                if (m.getParameterCount() != 0) continue;
                Object v = m.invoke(st);
                if (v == st) return null; // not a real getValue
                return String.valueOf(v);
            } catch (Throwable ig) {}
        }
        return null;
    }
    private static boolean sProbedNav = false;
    /** DECISIVE navstack-entry probe (MACAPK_DUMP_SLOTS=1, one-shot). The welcome
     *  content (WelcomeV2Screen.i3 -> b.d) composes only if the navstack host's
     *  per-entry visualState 'rtv' resolves NOT-FALSE; m.g does
     *  `if (rtv.a()==Boolean.FALSE) continue;` to skip an entry. This reads, for
     *  each entry in the navstack host's visualState list (com.reddit.navstack.n
     *  field h, a SnapshotStateList of rtv), the LIVE rtv.a() (TRUE/FALSE/null) and
     *  the screen it wraps, plus host.e() (n.f host-ready) -- settling whether the
     *  entry is SKIPPED (rtv.a()==FALSE) vs rendered-but-empty (a Compose emit
     *  failure). Robust to R8 rename: finds navstack objects by package and the
     *  visualState list by duck-typing (a List whose elements have a 0-arg method
     *  returning Boolean). Read-only. */
    private static void probeNavstackEntries(View host) {
        if (sProbedNav) return; sProbedNav = true;
        NativeLog.i(TAG, "navstack-probe BEGIN host=" + host.getClass().getName());
        try {
            java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
            java.util.IdentityHashMap<Object,Boolean> seen = new java.util.IdentityHashMap<>();
            q.add(host); Object par = host.getParent(); if (par != null) q.add(par);
            int budget = 300000, listsDumped = 0, candProbed = 0;
            java.util.IdentityHashMap<Object,Boolean> dumpedLists = new java.util.IdentityHashMap<>();
            while (!q.isEmpty() && budget-- > 0) {
                Object o = q.poll();
                if (o == null || seen.put(o, Boolean.TRUE) != null) continue;
                String cn; try { cn = o.getClass().getName(); } catch (Throwable t) { continue; }
                // LIBERAL + DIAGNOSTIC: dump EVERY small List of short-R8-name elements
                // (the navstack visualState list n.h is a SnapshotStateList of `rtv`,
                // a <=4-char R8 name). For each, log elem class + the resolved a()/Boolean
                // method value + the referenced Screen, so we can SEE the entry visibility
                // even if exact-name detection drifts.
                if (candProbed < 40 && o instanceof java.util.List<?> list && !list.isEmpty() && list.size() <= 40
                    && dumpedLists.put(o, Boolean.TRUE) == null) {
                    Object e0 = null; try { e0 = list.get(0); } catch (Throwable ignored) {}
                    String esn = e0 == null ? "" : e0.getClass().getSimpleName();
                    if (e0 != null && esn.length() >= 1 && esn.length() <= 4
                        && !(e0 instanceof CharSequence) && !(e0 instanceof Number)
                        && !(e0 instanceof java.util.Map.Entry) && !e0.getClass().isArray()) {
                        candProbed++;
                        java.lang.reflect.Method am = zeroArgBooleanMethod(e0.getClass());
                        String scr = describeEntryScreen(e0);
                        // Only fully dump lists that look navstack-ish (a Boolean method OR a Screen ref)
                        if (am != null || !"?".equals(scr)) {
                            listsDumped++;
                            NativeLog.i(TAG, "navstack-entries list=" + cn + "@" + Integer.toHexString(System.identityHashCode(o))
                                + " size=" + list.size() + " elem=" + e0.getClass().getName()
                                + " boolMethod=" + (am == null ? "none" : am.getName()) + " elem0Screen=" + scr);
                            int idx = 0;
                            for (Object e : list) {
                                if (e == null) { NativeLog.i(TAG, "  entry#" + (idx++) + " null"); continue; }
                                Object vis = null;
                                if (am != null) { try { am.setAccessible(true); vis = am.invoke(e); } catch (Throwable t) { vis = "<threw>"; } }
                                NativeLog.i(TAG, "  entry#" + (idx++) + " " + e.getClass().getName()
                                    + " bool=" + vis + " screen=" + describeEntryScreen(e) + " allBoolMethods=" + allBoolMethods(e));
                            }
                        }
                    }
                }
                if (o instanceof java.lang.ref.Reference) { try { Object r = ((java.lang.ref.Reference<?>) o).get(); if (r != null && !seen.containsKey(r)) q.add(r); } catch (Throwable ignored) {} continue; }
                if (o instanceof java.util.Map) { try { for (Object e : ((java.util.Map<?,?>) o).values()) if (e != null && !seen.containsKey(e)) q.add(e); } catch (Throwable ignored) {} continue; }
                if (o instanceof Iterable) { try { for (Object e : (Iterable<?>) o) if (e != null && !seen.containsKey(e)) q.add(e); } catch (Throwable ignored) {} continue; }
                if (o instanceof Object[]) { for (Object e : (Object[]) o) if (e != null && !seen.containsKey(e)) q.add(e); continue; }
                if (o instanceof View && o != host && o != par) continue;
                Class<?> c = o.getClass(); int guard = 0;
                while (c != null && c != Object.class && guard++ < 8) {
                    for (Field f : c.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                        try { f.setAccessible(true); Object v = f.get(o); if (v != null && !seen.containsKey(v)) q.add(v); } catch (Throwable ignored) {}
                    }
                    c = c.getSuperclass();
                }
            }
            NativeLog.i(TAG, "navstack-entries: scanned visited=" + seen.size() + " entryListsDumped=" + listsDumped);
        } catch (Throwable t) { NativeLog.w(TAG, "navstack-entries: " + t); }
    }
    /** All 0-arg Boolean/boolean-returning public methods of o, with their live
     *  values: "name=val name2=val2". Reveals the rtv visibility getter whatever
     *  its R8 name. Bounded; never throws. */
    private static String allBoolMethods(Object o) {
        StringBuilder sb = new StringBuilder();
        try {
            int n = 0;
            for (java.lang.reflect.Method m : o.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                Class<?> rt = m.getReturnType();
                if (rt != Boolean.class && rt != boolean.class) continue;
                if (m.getName().equals("isEmpty")) continue;
                Object v; try { m.setAccessible(true); v = m.invoke(o); } catch (Throwable t) { continue; }
                sb.append(m.getName()).append('=').append(v).append(' ');
                if (++n >= 12) break;
            }
        } catch (Throwable ignored) {}
        return sb.toString();
    }
    private static java.util.List<?> asList(Object v) {
        if (v instanceof java.util.List) return (java.util.List<?>) v;
        // SnapshotStateList implements List; some wrappers expose a backing list.
        return null;
    }
    private static java.lang.reflect.Method zeroArgBooleanMethod(Class<?> c) {
        // rtv.a() returns Boolean. Prefer a public 0-arg method named 'a' returning Boolean/boolean.
        for (java.lang.reflect.Method m : c.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            Class<?> rt = m.getReturnType();
            if ((rt == Boolean.class || rt == boolean.class) && m.getName().equals("a")) return m;
        }
        for (java.lang.reflect.Method m : c.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            Class<?> rt = m.getReturnType();
            if (rt == Boolean.class && m.getName().length() <= 2) return m;
        }
        return null;
    }
    /** Walk an entry's fields to find a referenced Screen (ComposeScreen subclass /
     *  a class whose name contains 'Screen' or the welcome package). */
    private static String describeEntryScreen(Object entry) {
        java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
        java.util.IdentityHashMap<Object,Boolean> seen = new java.util.IdentityHashMap<>();
        q.add(entry); int budget = 80;
        while (!q.isEmpty() && budget-- > 0) {
            Object o = q.poll();
            if (o == null || seen.put(o, Boolean.TRUE) != null) continue;
            String cn; try { cn = o.getClass().getName(); } catch (Throwable t) { continue; }
            if (cn.contains("welcomev2") || cn.contains(".screen.welcome") || cn.endsWith("Screen")
                || cn.contains("WelcomeV2")) return cn + "@" + Integer.toHexString(System.identityHashCode(o));
            for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    try { f.setAccessible(true); Object v = f.get(o); if (v != null && !seen.containsKey(v)) q.add(v); } catch (Throwable ignored) {}
                }
            }
        }
        return "?";
    }
    private static boolean isSubclassNamed(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
            if (k.getName().equals(name)) return true;
        return false;
    }
    private static String safeToString(Object o) {
        try { String s = String.valueOf(o); return s.length() > 200 ? s.substring(0, 200) : s; }
        catch (Throwable t) { return "<toString threw>"; }
    }

    /** Find the androidx.compose.runtime.Recomposer driving this host's
     *  composition (BFS over fields from the host + its AbstractComposeView
     *  parent; Recomposer is NOT renamed) and log its state. PendingWork +
     *  hasPendingWork=true => compose/apply work is queued but not draining
     *  (frame/dispatch gap); Idle => no work pending (composed empty / upstream). */
    private static void probeRecomposerState(View host) {
        // Decode EVERY RedditComposeView's composition in the WHOLE tree (both the
        // navstack/Conductor host AND the WelcomeV2Screen controller view under a5f0).
        // Called from this proven path; walk up to the true root first. Settles
        // whether the welcome controller's ComposeView composed b.d or is empty.
        if ("1".equals(ENV_MACAPK_DUMP_SLOTS)) {
            try {
                View r = host; int g = 0;
                while (r.getParent() instanceof View pv && g++ < 60) r = pv;
                decodeEveryComposition(r);
            } catch (Throwable t) { NativeLog.w(TAG, "decode-all(rec): " + t); }
        }
        // Direct check on the AbstractComposeView (host or its parent): does a
        // composition even exist (getHasComposition), and what are its
        // WrappedComposition (field c) + parent CompositionContext (field d)?
        for (Object cand : new Object[]{ host, host.getParent() }) {
            try {
                if (cand == null) continue;
                String ccn = cand.getClass().getName();
                if (!(ccn.startsWith("com.reddit.screen.RedditComposeView")
                      || ccn.endsWith("ComposeView") || ccn.endsWith("AbstractComposeView"))) continue;
                Object hasComp = invokeNoArg(cand, "getHasComposition");
                Object wrapped = readField(cand, "c");   // AbstractComposeView.composition (a0)
                Object parentCtx = readField(cand, "d");  // AbstractComposeView.parentContext (tva)
                Object innerComp = wrapped != null ? readField(wrapped, "b") : null; // a0.composition (zva)
                NativeLog.i(TAG, "composition-probe " + ccn + "@" + Integer.toHexString(System.identityHashCode(cand))
                    + " hasComposition=" + hasComp
                    + " wrapped(c)=" + (wrapped == null ? "null" : wrapped.getClass().getName())
                    + " innerComp(c.b)=" + (innerComp == null ? "null" : innerComp.getClass().getName())
                    + " parentCtx(d)=" + (parentCtx == null ? "null" : parentCtx.getClass().getName()));
                // CONFIRMING DIAGNOSTIC: decode the CompositionImpl's slot table directly
                // (the int[] the composer wrote), counting node-groups by the AOSP NodeBit.
                // Distinguishes "createNode never fired (inserting==false)" from "BFS undercount".
                if (innerComp != null) decodeSlotTable(innerComp);
            } catch (Throwable t) { NativeLog.w(TAG, "composition-probe: " + t); }
        }
        // DECISIVE navstack-entry visibility probe (rtv.a() per entry). Called from
        // here because probeRecomposerState provably runs for a rooted host; the
        // macapkDiagnoseTree-top call site did not fire reliably. One-shot inside.
        if ("1".equals(ENV_MACAPK_DUMP_SLOTS)) {
            try { probeNavstackEntries(host); } catch (Throwable t) { NativeLog.w(TAG, "navstack-probe(rec): " + t); }
        }
        // Collect ALL Recomposer (R8-renamed androidx.compose.runtime.f) instances
        // reachable from the host -- there are several (the molecule's own
        // recomposer + the window recomposer). Log each one's Recomposer$State
        // (NOT renamed): Idle => parked at awaitWorkAvailable (never invalidated);
        // PendingWork => invalidated, waiting for a frame; Inactive => not started.
        java.util.ArrayList<Object> recomposers = new java.util.ArrayList<>();
        Object recomposer = null;
        // DECISIVE emit-vs-apply test: count LayoutNode (renamed androidx.compose.ui.node.h)
        // instances reachable from the host (the composition's slot table holds every
        // EMITTED node, whether or not it was linked into c.getRoot()). If this count
        // >> the live tree count (root + ~1), the SingleImage nodes WERE emitted by i3
        // but applyChanges never inserted them (apply-failure). If it's ~equal, the emit
        // itself produced nothing (a CompositionLocal/feature gate).
        int[] layoutNodeCount = {0};
        int[] reachStats = {0, 0};   // [0]=visited, [1]=sawComposition(zva)?1:0
        // Collect EVERY distinct CompositionImpl reachable from the host -- this
        // catches SUBcompositions (e.g. the navstack per-entry SubcomposeLayout
        // child compositions that hold WelcomeV2Screen.Content) that the view-tree
        // walk can't reach. Decoded below to find which composition holds b.d.
        java.util.ArrayList<Object> allCompositions = new java.util.ArrayList<>();
        try {
            java.util.ArrayDeque<Object> q = new java.util.ArrayDeque<>();
            java.util.IdentityHashMap<Object,Boolean> seen = new java.util.IdentityHashMap<>();
            q.add(host);
            Object par = host.getParent();
            if (par != null) q.add(par);
            int budget = 200000;
            while (!q.isEmpty() && budget-- > 0) {
                Object o = q.poll();
                if (o == null || seen.put(o, Boolean.TRUE) != null) continue;
                reachStats[0]++;
                String cn; try { cn = o.getClass().getName(); } catch (Throwable t) { continue; }
                if (cn.equals("androidx.compose.ui.node.h") || cn.endsWith("LayoutNode")) layoutNodeCount[0]++;
                if (cn.equals("zva") || cn.endsWith("CompositionImpl")) { reachStats[1] = 1; if (!allCompositions.contains(o)) allCompositions.add(o); }
                else if (cn.endsWith("SlotTable")) reachStats[1] = 1;
                if (cn.equals("androidx.compose.runtime.Recomposer")
                        || cn.equals("androidx.compose.runtime.f")) {
                    if (recomposer == null) recomposer = o;
                    if (!recomposers.contains(o)) recomposers.add(o);
                    // keep scanning to find the others
                }
                // Deref WeakReference/SoftReference (AbstractComposeView holds its
                // parent CompositionContext -- which leads to the Recomposer -- via
                // a WeakReference field that a plain field walk can't follow).
                if (o instanceof java.lang.ref.Reference) {
                    try { Object ref = ((java.lang.ref.Reference<?>) o).get();
                        if (ref != null && !seen.containsKey(ref)) q.add(ref); }
                    catch (Throwable ignored) {}
                    continue;
                }
                // Traverse collection / map / array ELEMENTS (Compose keeps
                // compositions + their parent contexts in lists/maps, not just
                // plain fields).
                if (o instanceof java.util.Map) {
                    try { for (Object e : ((java.util.Map<?,?>) o).values()) if (e != null && !seen.containsKey(e)) q.add(e); }
                    catch (Throwable ignored) {}
                    try { for (Object e : ((java.util.Map<?,?>) o).keySet()) if (e != null && !seen.containsKey(e)) q.add(e); }
                    catch (Throwable ignored) {}
                    continue;
                }
                if (o instanceof Iterable) {
                    try { for (Object e : (Iterable<?>) o) if (e != null && !seen.containsKey(e)) q.add(e); }
                    catch (Throwable ignored) {}
                    continue;
                }
                if (o instanceof Object[]) {
                    for (Object e : (Object[]) o) if (e != null && !seen.containsKey(e)) q.add(e);
                    continue;
                }
                // Don't descend into the View tree (huge) beyond the two seeds.
                if (o instanceof View && o != host && o != par) continue;
                Class<?> c = o.getClass(); int guard = 0;
                while (c != null && c != Object.class && guard++ < 8) {
                    for (Field f : c.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        if (f.getType().isPrimitive()) continue;
                        try { f.setAccessible(true); Object v = f.get(o);
                            if (v != null && !seen.containsKey(v)) q.add(v); }
                        catch (Throwable ignored) {}
                    }
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable t) { NativeLog.w(TAG, "recomposer-probe walk: " + t); }
        // EMIT-vs-APPLY verdict: total LayoutNodes reachable from the host (slot
        // table + tree) vs the live tree count. count >> tree => emitted but not
        // applied (apply-failure); count ~= tree => emit produced nothing.
        NativeLog.i(TAG, "layoutnode-reach-probe host=@" + Integer.toHexString(System.identityHashCode(host))
            + " " + host.getClass().getName()
            + " totalLayoutNodesReachable=" + layoutNodeCount[0]
            + " objectsVisited=" + reachStats[0]
            + " reachedComposition=" + (reachStats[1] == 1)
            + " distinctCompositions=" + allCompositions.size());
        // Decode EVERY reachable CompositionImpl (one-shot via MACAPK_DUMP_SLOTS),
        // including subcompositions. The one whose slot table contains b.d's emit
        // (m2k0 / gq00 / i1f Image / a Painter) is the welcome content composition;
        // if NONE contain it, WelcomeV2Screen.Content never composed anywhere.
        if ("1".equals(ENV_MACAPK_DUMP_SLOTS) && !sDumpedAllComps) {
            sDumpedAllComps = true;
            int ci = 0;
            for (Object comp : allCompositions) {
                NativeLog.i(TAG, "allcomp #" + (ci++) + " " + comp.getClass().getName()
                    + "@" + Integer.toHexString(System.identityHashCode(comp)));
                try { decodeSlotTable(comp); } catch (Throwable ignored) {}
            }
        }
        if (recomposers.isEmpty()) {
            NativeLog.i(TAG, "recomposer-probe: none reachable from " + host.getClass().getName());
            return;
        }
        for (Object rc : recomposers) {
            // Read each StateFlow-typed field; its value, if a Recomposer$State
            // (NOT renamed), is the recomposer's current state.
            String state = "?";
            try {
                Class<?> c = rc.getClass();
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(rc);
                        if (v == null) continue;
                        Object val = invokeNoArg(v, "getValue");   // StateFlow.getValue()
                        if (val != null && val.getClass().getName().equals("androidx.compose.runtime.Recomposer$State")) {
                            state = String.valueOf(val) + " (field " + f.getName() + ")";
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            NativeLog.i(TAG, "recomposer-probe @" + Integer.toHexString(System.identityHashCode(rc))
                + " " + rc.getClass().getName() + " state=" + state);
        }
    }

    private static void collectComposeViews(View v, java.util.List<View> out) {
        if (v == null) return;
        String nm; try { nm = v.getClass().getName(); } catch (Throwable t) { return; }
        // Structural match: the AndroidComposeView (even if R8-renamed to e.g.
        // androidx.compose.ui.platform.c) is the View that holds a root
        // LayoutNode. getRoot() is renamed in obfuscated builds, so detect by
        // finding a LayoutNode-shaped field rather than calling getRoot().
        boolean isHost = nm.endsWith("AndroidComposeView");
        if (!isHost && findRootLayoutNode(v) != null) isHost = true;
        if (isHost) out.add(v);   // don't return: a ComposeView may nest another
        if (v instanceof android.view.ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) collectComposeViews(vg.getChildAt(i), out);
        }
    }

    // ---- Rename-proof LayoutNode access ------------------------------------
    // In fully-obfuscated builds (Reddit) the AndroidComposeView is renamed
    // (androidx.compose.ui.platform.c), its getRoot() accessor is renamed, the
    // LayoutNode class is renamed (androidx.compose.ui.node.h), and the
    // children-vector field name is renamed. We identify a LayoutNode purely by
    // SHAPE: an object that holds a MutableVector-like (some field that is an
    // Object[] container) whose elements are instances of the SAME class. That
    // self-referential children vector is unique to LayoutNode in the Compose
    // tree, so it survives any renaming.

    /** Find the root LayoutNode held by a (possibly renamed) AndroidComposeView.
     *  An AndroidComposeView can hold SEVERAL LayoutNode-shaped fields (the real
     *  root plus a lookahead/throwaway root); pick the one whose subtree is
     *  largest so we dump the real content tree, not an empty placeholder. */
    private static Object findRootLayoutNode(View host) {
        if (host == null) return null;
        Object r = invokeNoArg(host, "getRoot");
        Object best = null; int bestCount = -1;
        if (isLayoutNodeLike(r)) { best = r; bestCount = subtreeCount(r, 0,
            new java.util.IdentityHashMap<>()); }
        Class<?> c = host.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try { f.setAccessible(true); Object v = f.get(host);
                    if (v == r || !isLayoutNodeLike(v)) continue;
                    int n = subtreeCount(v, 0, new java.util.IdentityHashMap<>());
                    if (n > bestCount) { best = v; bestCount = n; }
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return best;
    }

    private static int subtreeCount(Object node, int depth,
                                    java.util.IdentityHashMap<Object,Boolean> seen) {
        if (node == null || depth > 40 || seen.put(node, Boolean.TRUE) != null) return 0;
        int n = 1;
        java.util.List<Object> kids = layoutNodeChildren(node);
        if (kids != null) for (Object k : kids) n += subtreeCount(k, depth + 1, seen);
        return n;
    }

    private static boolean isLayoutNodeLike(Object o) {
        if (o == null) return false;
        String n; try { n = o.getClass().getName(); } catch (Throwable t) { return false; }
        // Name fast-path: real Compose ("...LayoutNode") and the Reddit-build
        // obfuscated name (androidx.compose.ui.node.h). The duck check below is
        // the general (any-obfuscation) detector.
        if (n.endsWith("LayoutNode") || n.equals("androidx.compose.ui.node.h")) return true;
        // Duck: a children vector with at least one SAME-TYPED element (the
        // self-referential children vector is unique to LayoutNode). An empty
        // vector isn't enough evidence (many classes hold MutableVectors).
        java.util.List<Object> kids = layoutNodeChildren(o);
        return kids != null && !kids.isEmpty();
    }

    /** Return this LayoutNode's child LayoutNodes (rename-proof), or null if the
     *  object isn't LayoutNode-shaped. */
    private static java.util.List<Object> layoutNodeChildren(Object node) {
        if (node == null) return null;
        Class<?> nc = node.getClass();
        java.util.List<Object> bestKids = null;
        // Union of EVERY same-typed vector found on this node (see the comment at
        // the return below for why winner-takes-all under-reports).
        java.util.LinkedHashSet<Object> union = new java.util.LinkedHashSet<>();
        java.util.List<Object> allKids = null;
        boolean foundAnyVector = false;
        Class<?> c = nc;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(node);
                    if (v == null || v instanceof View) continue;
                    // (1) DIRECT vector field (renamed: W = z-sorted children,
                    // x = unfolded children -- both xjv {Object[] a; int c}).
                    Object[] arr = duckVectorContent(v);
                    if (arr != null) {
                        foundAnyVector = true;
                        java.util.List<Object> kids = sameTyped(arr, nc);
                        // Pick the vector with the MOST same-typed elements (the
                        // true children vector); a node holds several MutableVectors
                        // (z-sorted, unfolded, subcompose slots), some empty/partial.
                        if (bestKids == null || kids.size() > bestKids.size()) bestKids = kids;
                        union.addAll(kids);
                        continue;
                    }
                    // (2) NESTED vector, one deref deeper. AOSP LayoutNode keeps the
                    // structural (folded) children in a MutableVector that is itself
                    // WRAPPED: the renamed `_foldedChildren` is an `srn` whose field
                    // `b` holds the xjv. h.A() returns `w.b` (folded) or `x`
                    // (unfolded), and on a freshly-composed-but-not-yet-z-sorted tree
                    // the direct W (z-sorted) is EMPTY -- so the folded vector inside
                    // the wrapper is the ONLY populated source. A single-level field
                    // duck (the old behavior) was structurally blind to it and read
                    // every Compose tree as nodes=2 (root + 1 empty leaf). Descend one
                    // level into reference fields and accept a nested vector ONLY when
                    // it actually holds same-typed (LayoutNode) elements, so unrelated
                    // wrappers don't get mistaken for the children list. Pure field
                    // reads -- no method invocation, so no side effect on the live
                    // Compose tree (we never trigger recreateUnfoldedChildrenIfDirty).
                    for (Field g : v.getClass().getDeclaredFields()) {
                        try {
                            g.setAccessible(true);
                            Object gv = g.get(v);
                            if (gv == null || gv == v || gv instanceof View) continue;
                            Object[] narr = duckVectorContent(gv);
                            if (narr == null) continue;
                            java.util.List<Object> nk = sameTyped(narr, nc);
                            if (!nk.isEmpty()) {
                                foundAnyVector = true;
                                if (bestKids == null || nk.size() > bestKids.size()) bestKids = nk;
                                union.addAll(nk);
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        // Distinguish "not a LayoutNode" (no vector at all) from "LayoutNode with
        // no children" (empty children vector).
        if (!foundAnyVector) return null;
        if (!union.isEmpty()) allKids = new java.util.ArrayList<>(union);
        // UNION, not winner-takes-all (2026-07-29). Picking only the single
        // largest vector under-reports: a LayoutNode owns several same-typed
        // vectors (folded / unfolded / z-sorted) that are populated at different
        // moments, and any of them can hold a child the "biggest" one does not.
        // The measured symptom on nowinandroid was a dump printing `kids=3` while
        // descending exactly ONE new child at four consecutive depths, so the
        // walker reported nodes=9 for a tree the object-graph probe measured at
        // 143. Unioning is safe: dumpNode's IdentityHashMap already dedups, so a
        // child listed in two vectors is still visited once, and a non-child that
        // sneaks in (an ancestor in a z-sort vector) is dropped as already-seen.
        if (allKids != null && bestKids != null && allKids.size() > bestKids.size()) {
            return allKids;
        }
        return bestKids == null
            ? (allKids == null ? java.util.Collections.emptyList() : allKids)
            : bestKids;
    }

    /** Elements of `arr` whose runtime class is exactly `nc` (the self-referential
     *  LayoutNode children invariant). May return empty (a real but childless
     *  children vector). */
    private static java.util.List<Object> sameTyped(Object[] arr, Class<?> nc) {
        java.util.ArrayList<Object> kids = new java.util.ArrayList<>();
        if (arr != null) for (Object e : arr) if (e != null && e.getClass() == nc) kids.add(e);
        return kids;
    }

    /** One-shot deep field inventory of a LayoutNode: every field's type + a
     *  hint of its value, and for any field that contains an Object[] (a
     *  vector), the element class names + count. Reveals a children vector the
     *  walker's same-typed heuristic might have skipped. */
    private static String fieldInventory(Object node) {
        StringBuilder sb = new StringBuilder();
        try {
            Class<?> nc = node.getClass();
            Class<?> c = nc;
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(node);
                        sb.append("  ").append(f.getName()).append(": ")
                          .append(f.getType().getSimpleName());
                        if (v == null) { sb.append(" = null\n"); continue; }
                        Object[] arr = duckVectorContent(v);
                        if (arr != null) {
                            java.util.LinkedHashMap<String,Integer> types = new java.util.LinkedHashMap<>();
                            for (Object e : arr) {
                                String en = e == null ? "null" : e.getClass().getName();
                                types.merge(en, 1, Integer::sum);
                            }
                            sb.append(" = VECTOR[").append(arr.length).append("] ")
                              .append(types);
                            sb.append(arr.length > 0 && types.containsKey(nc.getName())
                                ? " <-- SAME-TYPED children\n" : "\n");
                        } else {
                            sb.append(" = ").append(v.getClass().getSimpleName()).append('\n');
                        }
                    } catch (Throwable ignored) {}
                }
                c = c.getSuperclass();
            }
        } catch (Throwable t) { sb.append("  inventory threw: ").append(t).append('\n'); }
        return sb.toString();
    }

    /** Pull the live slice from a MutableVector-like object by SHAPE: the first
     *  Object[] field, sliced to the first plausible int size field. Matches
     *  androidx MutableVector (content/size) and its renamed forms (xjv: a/c). */
    private static Object[] duckVectorContent(Object vec) {
        if (vec == null || vec instanceof Object[]) {
            return (vec instanceof Object[]) ? (Object[]) vec : null;
        }
        Object[] content = null; int size = -1;
        for (Field f : vec.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                if (content == null && f.getType() == Object[].class) {
                    content = (Object[]) f.get(vec);
                } else if (size < 0 && f.getType() == int.class) {
                    size = f.getInt(vec);
                }
            } catch (Throwable ignored) {}
        }
        if (content == null) return null;
        if (size < 0 || size > content.length) size = content.length;
        if (size == content.length) return content;
        Object[] out = new Object[size];
        System.arraycopy(content, 0, out, 0, size);
        return out;
    }

    private static void dumpViewTree(View v, int depth, int[] count, StringBuilder sb) {
        if (v == null || depth > 16 || count[0] > 120) return;
        count[0]++;
        for (int i = 0; i < depth && i < 14; i++) sb.append("  ");
        int w = 0, h = 0, vis = -1;
        try { w = v.getRight() - v.getLeft(); h = v.getBottom() - v.getTop(); vis = v.getVisibility(); } catch (Throwable t) {}
        String fullName; try { fullName = v.getClass().getName(); } catch (Throwable t) { fullName = "?"; }
        sb.append(fullName).append(' ').append(w).append('x').append(h)
          .append(" vis=").append(vis);
        // Flag AndroidComposeView-shaped hosts (getRoot()->LayoutNode) even if renamed.
        try {
            Object root = invokeNoArg(v, "getRoot");
            if (root != null && root.getClass().getName().endsWith("LayoutNode")) sb.append(" [COMPOSE-HOST]");
        } catch (Throwable ignored) {}
        try { if (v.getParent() == null) sb.append(" [NO-PARENT]"); } catch (Throwable t) {}
        try { if (v instanceof android.view.ViewGroup vg2) sb.append(" children=").append(vg2.getChildCount()); } catch (Throwable t) {}
        sb.append('\n');
        if (v instanceof android.view.ViewGroup vg) {
            int nc = 0; try { nc = vg.getChildCount(); } catch (Throwable t) {}
            for (int i = 0; i < nc; i++) {
                try { dumpViewTree(vg.getChildAt(i), depth + 1, count, sb); } catch (Throwable t) {}
            }
        }
    }

    private static void dumpNode(Object node, int depth, int[] counts,
                                 java.util.List<String> texts,
                                 java.util.IdentityHashMap<Object,Boolean> visited,
                                 StringBuilder sb) throws Exception {
        if (node == null || depth > 24) return;
        if (visited.put(node, Boolean.TRUE) != null) return;
        counts[0]++;
        // v1.0.1047: READ THE SIZE OFF THE LAYOUT DELEGATE, AND DISTINGUISH
        // "COULD NOT READ" FROM "IS ZERO".
        //
        // This used to be `invokeNoArg(node, "getWidth")` with a `: -1` fallback,
        // which maps a FAILED reflective read onto the same value as a real
        // answer. getWidth/getHeight come from the `LayoutInfo` interface, and
        // R8 SHRINKS THAT INTERFACE AWAY in a minified app: Thunderbird's own
        // jar has LayoutNode implementing only ComposeNodeLifecycleCallback,
        // OwnerScope and ComposeUiNode, with zero getWidth/getHeight. So every
        // node read -1x-1 and `sized` counted 0 -- for EVERY minified Compose
        // app, regardless of whether it had measured. A debug build with minify
        // off (nowinandroid) reported real sizes, which is exactly what made the
        // metric look trustworthy.
        //
        // That cost a wrong conclusion: a whole-tree dump of -1x-1 reads as
        // "the measure pass never ran", which is a completely different bug from
        // the truth. Same failure shape the instrument-hygiene rules in
        // debug/README.md exist for -- a verdict over a subset, reported as if
        // it covered everything.
        //
        // measurePassDelegate's width/height are plain public fields on
        // LayoutNodeLayoutDelegate and survive R8. `?` in the dump now means
        // "could not read"; a printed 0 means a genuine zero.
        int w = -1, h = -1;
        Object wObj = invokeNoArg(node, "getWidth");
        Object hObj = invokeNoArg(node, "getHeight");
        if (wObj instanceof Integer && hObj instanceof Integer) {
            w = (Integer) wObj; h = (Integer) hObj;
        } else {
            Object ld = readField(node, "layoutDelegate");
            Object mpd = (ld == null) ? null : readField(ld, "measurePassDelegate");
            Object w2 = (mpd == null) ? null : readField(mpd, "width");
            Object h2 = (mpd == null) ? null : readField(mpd, "height");
            if (w2 instanceof Integer) w = (Integer) w2;
            if (h2 instanceof Integer) h = (Integer) h2;
        }
        if (w > 0 && h > 0) counts[1]++;
        if (w < 0 || h < 0) counts[2]++;   // unreadable, NOT unmeasured
        // harvest text on this node's modifier chain
        int before = texts.size();
        Object nodes = readField(node, "nodes");
        if (nodes != null) walkModifierChain(readField(nodes, "head"), texts,
            new java.util.IdentityHashMap<>(), 0);
        java.util.List<Object> kidsForCount = layoutNodeChildren(node);
        int kidN = kidsForCount == null ? 0 : kidsForCount.size();
        if (counts[0] <= 80) {
            for (int i = 0; i < depth && i < 12; i++) sb.append("  ");
            // `?` = could not read the size at all; a printed 0 is a real zero.
            sb.append(simpleName(node)).append(' ')
              .append(w < 0 ? "?" : String.valueOf(w)).append('x')
              .append(h < 0 ? "?" : String.valueOf(h))
              .append(" kids=").append(kidN);
            // NAME THE NODE (2026-07-29). `draw=[Clip,Text,Draw]` is a
            // class-name-fragment HINT and is far too coarse to identify which
            // node in a placed chain measured 0x0 -- it printed the same three
            // tokens for all nine nodes of nowinandroid's chain. The measure
            // POLICY is the identity that matters (a Scaffold slot, a
            // SubcomposeLayout, an AnimatedContent, a NavDisplay each have a
            // distinct one), and the modifier-chain node classes say what was
            // applied. nowinandroid is a debug build with minify OFF, so both
            // read out with real names.
            // Printed UNCONDITIONALLY, including the null/empty cases. A missing
            // column and a null value are different diagnoses -- no column at all
            // means this build of the dumper is not the one running (a staging
            // problem), `policy=null` means the reflection missed. Guessing
            // between those two cost a whole measurement cycle on 2026-07-29.
            sb.append(" policy=").append(String.valueOf(measurePolicyName(node)));
            sb.append(" mods=[").append(modifierChainNames(node)).append(']');
            String draws = drawModifierSummary(node);
            if (!draws.isEmpty()) sb.append(" draw=[").append(draws).append(']');
            if (texts.size() > before) sb.append(" text=").append(texts.subList(before, texts.size()));
            sb.append('\n');
        }
        // Rename-proof children walk (Reddit's LayoutNode + children-vector
        // field names are obfuscated; _foldedChildren/vector lookups fail).
        java.util.List<Object> kids = layoutNodeChildren(node);
        if (kids != null) for (Object child : kids) {
            dumpNode(child, depth + 1, counts, texts, visited, sb);
        }
    }

    /** The LayoutNode's MeasurePolicy, by class name -- the thing that actually
     *  decides the node's measured size, and therefore the identity you need when
     *  a node in a placed chain measures 0x0. Reads the AOSP field name first
     *  (`measurePolicy`, present verbatim in any build with minify OFF) and falls
     *  back to a duck check: a field whose value's class declares a method taking
     *  a MeasureScope-ish receiver plus a list -- or, more robustly across
     *  obfuscation, whose class name mentions MeasurePolicy / Measure$. Returns
     *  null when nothing matches, so the caller just omits the column. */
    private static String measurePolicyName(Object node) {
        try {
            Object mp = readField(node, "measurePolicy");
            if (mp != null) return mp.getClass().getName();
        } catch (Throwable ignored) {}
        try {
            for (Class<?> c = node.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(node);
                        if (v == null || v instanceof View) continue;
                        String cn = v.getClass().getName();
                        if (cn.contains("MeasurePolicy") || cn.contains("$measure")
                                || cn.contains("MeasureBlocks")) {
                            return cn;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** The LayoutNode's modifier chain as an ordered list of Modifier.Node class
     *  SIMPLE names. Same traversal walkModifierChain uses (NodeChain -> head,
     *  then follow `child`), but it reports every link instead of only harvesting
     *  text -- that is what turns "some node measured 0x0" into "THIS node, with
     *  these modifiers, measured 0x0". Capped so one pathological chain cannot
     *  blow up the dump. */
    private static String modifierChainNames(Object node) {
        StringBuilder out = new StringBuilder();
        try {
            Object nodes = readField(node, "nodes");
            if (nodes == null) return "";
            Object n = readField(nodes, "head");
            java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
            int count = 0;
            while (n != null && count < 24) {
                if (seen.put(n, Boolean.TRUE) != null) break;
                String cn = n.getClass().getName();
                int dot = cn.lastIndexOf('.');
                if (dot >= 0) cn = cn.substring(dot + 1);
                if (out.length() > 0) out.append(',');
                out.append(cn);
                count++;
                Object next = readField(n, "child");
                if (next == n) break;
                n = next;
            }
        } catch (Throwable ignored) {}
        return out.toString();
    }

    /** Best-effort summary of draw-emitting modifier nodes on this LayoutNode's
     *  modifier chain (Background/Painter/Image/Text/Shadow/Clip), by scanning
     *  every Modifier.Node-shaped object reachable from the node's fields one
     *  level deep and keying on class-name fragments that survive obfuscation
     *  only partially -- so this is a hint, not exhaustive. */
    private static String drawModifierSummary(Object node) {
        java.util.LinkedHashSet<String> hits = new java.util.LinkedHashSet<>();
        try {
            Class<?> c = node.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(node);
                        if (v == null) continue;
                        String n = v.getClass().getName();
                        addDrawHit(hits, n);
                        // one level deeper for the NodeChain head/links
                        for (Field g : v.getClass().getDeclaredFields()) {
                            try { g.setAccessible(true); Object w2 = g.get(v);
                                if (w2 != null) addDrawHit(hits, w2.getClass().getName()); }
                            catch (Throwable ignore) {}
                        }
                    } catch (Throwable ignore) {}
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignore) {}
        return String.join(",", hits);
    }

    private static void addDrawHit(java.util.Set<String> hits, String n) {
        if (n == null) return;
        String[] keys = {"Background", "Painter", "Image", "Text", "Shadow",
                         "Clip", "Draw", "Border", "GraphicsLayer"};
        for (String k : keys) if (n.contains(k)) { hits.add(k); return; }
    }

    private static String simpleName(Object o) {
        try { String n = o.getClass().getName(); int i = n.lastIndexOf('.'); return i >= 0 ? n.substring(i + 1) : n; }
        catch (Throwable t) { return "?"; }
    }

    private static void walkModifierChain(Object head, java.util.List<String> out,
                                            java.util.IdentityHashMap<Object, Boolean> visited,
                                            int depth) throws Exception {
        Object n = head;
        int safety = 0;
        while (n != null && safety++ < 256) {
            if (visited.put(n, Boolean.TRUE) != null) return;
            String cn;
            try { cn = n.getClass().getName(); } catch (Throwable t) { break; }
            if (cn.endsWith("TextStringSimpleNode")) {
                Object t = readField(n, "text");
                if (t instanceof String s && !s.isEmpty()) out.add(s);
            } else if (cn.endsWith("TextAnnotatedStringNode")) {
                Object ann = readField(n, "text");
                if (ann != null) {
                    Object str = invokeNoArg(ann, "getText");
                    if (str == null) str = readField(ann, "text");
                    if (str instanceof String s && !s.isEmpty()) out.add(s);
                }
            }
            // Advance to next node in chain (Modifier.Node has
            // `child` field linking forward).
            Object next = readField(n, "child");
            if (next == n) break;
            n = next;
        }
    }

    /** MutableVector exposes `content` (Object[]) and `size` (int)
     *  fields. Pull the live slice. */
    private static Object[] extractContent(Object mutableVector) {
        try {
            Object content = readField(mutableVector, "content");
            Object sizeObj = readField(mutableVector, "size");
            if (content instanceof Object[] arr) {
                int size = (sizeObj instanceof Integer i) ? i : arr.length;
                if (size < 0 || size > arr.length) size = arr.length;
                if (size == arr.length) return arr;
                Object[] out = new Object[size];
                System.arraycopy(arr, 0, out, 0, size);
                return out;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Greedy word-wrap to fit `avail` host-pixels. */
    private static java.util.List<String> wrapText(String s, Paint p, int avail) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (s == null || s.isEmpty() || avail <= 0) return out;
        String[] words = s.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            String trial = line.length() == 0 ? w : line.toString() + " " + w;
            float width;
            try { width = p.measureText(trial); }
            catch (Throwable t) { width = trial.length() * p.getTextSize() * 0.55f; }
            if (width <= avail || line.length() == 0) {
                line.setLength(0);
                line.append(trial);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(w);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static Object readField(Object owner, String name) {
        if (owner == null) return null;
        try {
            Class<?> c = owner.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(owner);
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object invokeNoArg(Object owner, String methodName) {
        if (owner == null) return null;
        try {
            Class<?> c = owner.getClass();
            while (c != null) {
                try {
                    Method m = c.getDeclaredMethod(methodName);
                    m.setAccessible(true);
                    return m.invoke(owner);
                } catch (NoSuchMethodException ignored) {
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
