// GfxTrace -- env-gated diagnostic harvester for the android.graphics draw
// surface. Mirrors the MACAPK_UNRESOLVED_CONTINUE native-symbol harvester (see
// memory "harvest-unresolved-symbols"): with MACAPK_GFX_HARVEST set, every
// instrumented Canvas/Path/Matrix/Paint method records its name on invocation,
// classified
//   REAL   -- we render it faithfully,
//   STUB   -- present but a no-op / approximation that draws nothing or wrong,
//   CAUGHT -- a node's draw threw and the resilient draw pass swallowed it.
//
// WHY: a Compose/Canvas app renders blank when ONE Canvas/Path/Matrix/Paint
// method it calls is missing (NoSuchMethodError, found by the run_apk.sh
// throw-grep) OR is a SILENT no-op (present but draws nothing -- invisible to
// the throw-grep). This harvester surfaces that silent class. One run over an
// app yields the COMPLETE draw-API usage list + which of those are gaps, in a
// SINGLE pass instead of one cold-run per gap (the slow reactive loop the
// v1.0.429 NiA onion-peel used). dump() prints the sorted tally.
//
// The CAUGHT sink is always live (even with the harvest flag off): it is the
// resilient draw pass's diagnostic, replacing the old single top-level
// "rootView.draw threw" log with a per-node one that keeps the rest of the
// tree painting.
package com.macapk.loader;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class GfxTrace {
    private GfxTrace() {}

    public static final boolean ON = System.getenv("MACAPK_GFX_HARVEST") != null;

    private static final ConcurrentHashMap<String, AtomicLong> REAL   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> STUB   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> CAUGHT = new ConcurrentHashMap<>();
    // warn-once so a per-node draw throw logs its full stack exactly once (the
    // old ViewCanvasDriver top-level diagnostic) without 60fps spam.
    private static final java.util.Set<String> warned =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** A draw/clip/transform op we render faithfully. */
    public static void real(String sig) { if (ON) bump(REAL, sig); }
    /** A method present in our impl but a no-op / approximation (silent gap). */
    public static void stub(String sig) { if (ON) bump(STUB, sig); }

    /** A graphics feature exercised by a draw op + whether we honor it, e.g.
     *  feature("shader", "LinearGradient", true) or
     *  feature("colorFilter", "ColorMatrixColorFilter", false). Routes to the
     *  REAL or STUB tally so the inventory shows both the demand and the gap. */
    public static void feature(String kind, String detail, boolean supported) {
        if (!ON) return;
        bump(supported ? REAL : STUB, kind + ":" + detail);
    }

    /** The resilient-draw-pass sink: a node's draw threw and the caller
     *  restored the canvas + continued so siblings still paint. Always records
     *  the count and logs the full stack ONCE per unique site -- a swallowed
     *  draw crash is a real gap the next session needs to see, harvest flag or
     *  not. */
    public static void caught(String site, Throwable t) {
        String msg = (t.getMessage() != null) ? " " + t.getMessage() : "";
        bump(CAUGHT, site + " :: " + t.getClass().getName() + msg);
        String key = site + "|" + t.getClass().getName() + "|" + t.getMessage();
        if (warned.add(key)) {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            NativeLog.i("Mac.apk.GfxTrace", "draw-node CAUGHT " + site + ": " + t + "\n" + sw);
        }
    }

    private static void bump(ConcurrentHashMap<String, AtomicLong> m, String sig) {
        m.computeIfAbsent(sig, k -> new AtomicLong()).incrementAndGet();
    }

    // v1.0.436: loud-catch. The dominant Mac.apk bug class is a missing framework
    // method/field/class that throws inside a callback our framework wraps in
    // `catch (Throwable ignored) {}` (pre-draw, global-layout, attach, inflate,
    // lifecycle dispatch, posted work) -- SILENT, so no log line and the
    // throw-grep can't see it. View.ALPHA (a swallowed NoSuchFieldError in the
    // pre-draw listener that aborted a Conductor animated change) was exactly
    // this. With MACAPK_LOUD_CATCH set, the instrumented swallow sites log the
    // Throwable (warn-once per site+class+msg) so one run surfaces every silent
    // framework gap. This is the fast bug-finder for the whole class.
    public static final boolean LOUD_CATCH = System.getenv("MACAPK_LOUD_CATCH") != null;
    public static void swallow(String site, Throwable t) {
        if (!LOUD_CATCH || t == null) return;
        String key = "swallow|" + site + "|" + t.getClass().getName() + "|" + t.getMessage();
        if (warned.add(key)) {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            NativeLog.i("Mac.apk.LoudCatch", "SWALLOWED @ " + site + ": " + t + "\n" + sw);
        }
    }

    /** Print the current tally. CAUGHT is meaningful even without the harvest
     *  flag; REAL/STUB only populate when MACAPK_GFX_HARVEST is set. */
    public static void dump(String when) {
        if (!ON && CAUGHT.isEmpty()) return;
        StringBuilder sb = new StringBuilder("=== GfxTrace ").append(when).append(" ===\n");
        if (!STUB.isEmpty()) {
            sb.append("-- STUB / silent-no-op draw API invoked (").append(STUB.size()).append(") --\n");
            dumpMap(sb, STUB);
        }
        if (!CAUGHT.isEmpty()) {
            sb.append("-- CAUGHT (resilient pass swallowed a node-draw throw) (").append(CAUGHT.size()).append(") --\n");
            dumpMap(sb, CAUGHT);
        }
        if (ON && !REAL.isEmpty()) {
            sb.append("-- REAL draw API invoked (").append(REAL.size()).append(") --\n");
            dumpMap(sb, REAL);
        }
        NativeLog.i("Mac.apk.GfxTrace", sb.toString());
    }

    private static void dumpMap(StringBuilder sb, ConcurrentHashMap<String, AtomicLong> m) {
        java.util.List<String> keys = new java.util.ArrayList<>(m.keySet());
        java.util.Collections.sort(keys);
        for (String k : keys) sb.append("    ").append(k).append("  x").append(m.get(k).get()).append('\n');
    }
}
