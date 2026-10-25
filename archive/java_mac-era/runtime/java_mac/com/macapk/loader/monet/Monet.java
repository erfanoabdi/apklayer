// Material You from the Mac wallpaper: the one place that decides WHICH system palette
// this process serves. The ramps and role colours themselves are AOSP's own maths
// (MonetOverlay); this class reproduces the part of SystemUI's ThemeOverlayController that
// chooses the seed, and caches the answer.
//
// AOSP android14-release, ThemeOverlayController.reevaluateSystemTheme():
//     mainColor = currentColors == null ? Color.TRANSPARENT : getNeutralColor(currentColors)
//     ... createOverlays(mainColor)
// with getNeutralColor == ColorScheme.getSeedColor(wallpaperColors), which falls back to
// GOOGLE_BLUE itself when the wallpaper has no usable colour (chroma < 5 everywhere, or
// every candidate under 1% of the hue population). So on a device the palette is ALWAYS
// wallpaper-derived; GOOGLE_BLUE is just what an achromatic wallpaper derives to.
//
// WHAT WE DO WITH EACH CASE (and the one place we knowingly differ):
//   - gate off (MACAPK_MONET_WALLPAPER_SEED=0): no overlay; ResourceIndex keeps its fixed
//     table, byte for byte the pre-change palette. The bisect switch.
//   - MACAPK_MONET_SEED=<rrggbb>: a pinned seed, for pairing against an emulator whose
//     wallpaper seed is known. Diagnostic; bypasses the wallpaper.
//   - the host shows a readable picture: its colours, exactly as AOSP extracts them, INCLUDING
//     the GOOGLE_BLUE fallback for an achromatic picture (which is NOT the fixed table: the
//     table's roles are ramp approximations, see the note in ResourceIndex).
//   - MACAPK_FOLLOW_SYSTEM_APPEARANCE=0 (the harness "do not track the macOS appearance at
//     all" switch, see followsHostAppearance): no overlay, the fixed table -- unless a seed is
//     pinned, which is host-independent by construction.
//   - the host shows no readable picture (headless, a folder rotation, a live provider): no
//     overlay, the fixed table; the natives UNBOUND is the same outcome but a different log
//     line, so a forgotten registration cannot pass for a headless host. A device never
//     reaches this state -- it
//     always has SOME wallpaper -- and AOSP's literal code for it (seed TRANSPARENT: ramps
//     from GOOGLE_BLUE but roles from Hct.fromInt(0), hue 0) is a boot transient we have no
//     reason to reproduce.
//
// Cached on disk (~/Library/Caches/Mac.apk/monet/) keyed by the picture's identity, so the
// render+quantize+scheme work (measured ~0.3 s cold) runs once per wallpaper change, not once
// per launch. And it runs OFF the boot path: ResourceIndex.<clinit> only calls prefetch()
// (its own daemon thread) and lays the overlay over its table on the first system_* READ, so
// an app that never reads the palette (every game) never waits for it. See
// docs/monet-wallpaper-seed_2026-09-26.md.
package com.macapk.loader.monet;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Monet {
    private Monet() {}

    private static final String TAG = "Monet";

    /** Bumped whenever anything that shapes the cached answer changes (the ported AOSP maths,
     *  the render, the role table). A mismatch is a cache miss, never a stale hit. */
    static final String ALGO = "android14-release/tonal_spot/1";

    /** UiModeManager.getContrast() -- Settings.Secure.CONTRAST_LEVEL, default 0 (standard).
     *  Our UiModeManager answers 0.0f too; the macOS "Increase contrast" setting is a
     *  candidate host source for it, not wired (see the doc's OPEN list). */
    static final double CONTRAST = 0.0;

    /** MACAPK_MONET_WALLPAPER_SEED, default ON. =0 restores the pre-change fixed palette
     *  exactly: ResourceIndex never asks, WallpaperManager.getWallpaperColors answers null. */
    public static boolean enabled() {
        String e = System.getenv("MACAPK_MONET_WALLPAPER_SEED");
        return e == null || e.isEmpty() || e.charAt(0) != '0';
    }

    /** Boot.macapkFollowSystemAppearance()'s exact test, restated because that method is
     *  package-private to com.macapk.loader: KEEP THE TWO IN STEP. Boot documents
     *  MACAPK_FOLLOW_SYSTEM_APPEARANCE=0 as "does this process track the macOS appearance at
     *  all" -> no. The desktop picture is part of how the host looks, exactly as the dark-mode
     *  setting is, and every capture harness that compares us against a fixed reference sets
     *  this variable (debug/recap_mac.sh -> recap_fleet.sh / recap_ab.sh, canvas_ab.sh,
     *  capture_all.sh, fleet_ab.sh, fdroid_drive.sh, ime_gate.sh ...) precisely so its
     *  pictures do not depend on the machine's look. Honouring it here keeps every one of those
     *  measurements what it was before this landed (the pre-change fixed table), whatever is on
     *  the owner's desktop. A pinned MACAPK_MONET_SEED still wins: it is host-independent. */
    static boolean followsHostAppearance() {
        return !"0".equals(System.getenv("MACAPK_FOLLOW_SYSTEM_APPEARANCE"));
    }

    private static final AtomicBoolean sPrefetchStarted = new AtomicBoolean();

    /** Start resolving on a daemon thread and return at once. Called from
     *  ResourceIndex.<clinit>, which the main thread waits on during boot: the resolve (a
     *  desktop query, then a cache read, or ~0.15-0.45 s of render+quantize the first time a
     *  picture is seen) overlaps the rest of boot instead of sitting inside that class init,
     *  and the first system_* read joins it through state()'s monitor. Gate off: no thread.
     *  Must NOT synchronize on Monet.class (state() holds that for the whole resolve). */
    public static void prefetch() {
        if (!enabled() || !sPrefetchStarted.compareAndSet(false, true)) return;
        try {
            Thread t = new Thread(Monet::state, "macapk-monet");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {
            // No thread: the first reader resolves inline, as before.
        }
    }

    /** One resolved system palette. */
    public static final class State {
        /** The seed ThemeOverlayController would pass createOverlays. */
        public final int seed;
        /** WallpaperColors.getMainColors(), best first (primary, secondary, tertiary...). */
        public final int[] mainColors;
        /** WallpaperColors.getColorHints(). */
        public final int hints;
        /** name (no "android:color/") -> ARGB, the three fabricated overlays combined. */
        public final Map<String, Integer> overlay;
        /** Human-readable provenance for the log line. */
        public final String source;

        State(int seed, int[] mainColors, int hints, Map<String, Integer> overlay, String source) {
            this.seed = seed;
            this.mainColors = mainColors;
            this.hints = hints;
            this.overlay = Collections.unmodifiableMap(overlay);
            this.source = source;
        }
    }

    private static boolean sResolved;
    private static State sState;
    /** The resolve's outcome, in the words it logged: the one-line answer to "why is the palette
     *  what it is". Kept because NativeLog is level-gated (an offline harness runs at
     *  MACAPK_LOG_LEVEL=7 and sees no log at all), so debug/monet_gate.sh reads it here. */
    private static volatile String sOutcome;

    /** What the resolve decided and why, or null before it ran. */
    public static String outcome() {
        return sOutcome;
    }

    /** The resolved palette, or null when the fixed table stands (gate off / no picture).
     *  Resolved once per process; later wallpaper changes are not followed (see OPEN list). */
    public static synchronized State state() {
        if (!sResolved) {
            sResolved = true;
            try {
                sState = resolve();
            } catch (Throwable t) {
                // Never fatal: the fixed table is what ran before this existed.
                sState = null;
                note("Material You: wallpaper palette unavailable (" + t + "); fixed palette");
            }
        }
        return sState;
    }

    /** For ResourceIndex: the overlay to lay over its fixed table, or null to keep it. */
    public static Map<String, Integer> systemPaletteOverlay() {
        State s = state();
        return s == null ? null : s.overlay;
    }

    private static State resolve() {
        if (!enabled()) {
            // Recorded, not logged: the gate-off path stays the pre-change one, log included.
            sOutcome = "gate off (MACAPK_MONET_WALLPAPER_SEED=0); fixed palette";
            return null;
        }
        String pin = System.getenv("MACAPK_MONET_SEED");
        if (pin != null && !pin.isEmpty()) {
            int seed = parseSeed(pin);
            State s = new State(seed, new int[] {seed}, 0,
                    MonetOverlay.overlayFor(seed, CONTRAST), "MACAPK_MONET_SEED");
            note("Material You seed #" + hex6(seed) + " pinned by MACAPK_MONET_SEED");
            return s;
        }
        if (!followsHostAppearance()) {
            note("Material You: MACAPK_FOLLOW_SYSTEM_APPEARANCE=0, the host look is not followed;"
                    + " fixed palette");
            return null;
        }
        HostWallpaper.Desktop d;
        try {
            d = HostWallpaper.query();
        } catch (UnsatisfiedLinkError e) {
            note("Material You: wallpaper natives unbound (RegisterWallpaperBridgeMethods was not"
                    + " called); fixed palette");
            return null;
        }
        if (d == null) {
            note("Material You: no readable desktop picture; fixed palette");
            return null;
        }
        String key = d.identity(ALGO + "/c" + CONTRAST);
        File cache = cacheFile();
        State hit = readCache(cache, key);
        if (hit != null) {
            note("Material You seed #" + hex6(hit.seed) + " from " + d + " (cached)");
            return hit;
        }
        long t0 = System.nanoTime();
        WallpaperColorsData colors = HostWallpaper.extract(d);
        if (colors == null) {
            note("Material You: could not render " + d + "; fixed palette");
            return null;
        }
        State s = fromColors(colors, "wallpaper " + d.path);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        writeCache(cache, key, s);
        note("Material You seed #" + hex6(s.seed) + " from " + d + " (computed in " + ms + " ms)");
        return s;
    }

    /** ThemeOverlayController's path from a WallpaperColors to the three overlays. */
    public static State fromColors(WallpaperColorsData colors, String source) {
        int seed = ColorScheme.getSeedColor(colors);
        List<Integer> main = colors.getMainColorInts();
        int[] mainColors = new int[main.size()];
        for (int i = 0; i < mainColors.length; i++) mainColors[i] = main.get(i);
        return new State(seed, mainColors, colors.getColorHints(),
                MonetOverlay.overlayFor(seed, CONTRAST), source);
    }

    // ---- the on-disk cache ---------------------------------------------------------------

    private static final String MAGIC = "macapk-monet-cache 1";

    static File cacheFile() {
        String home = com.macapk.loader.SharedStorage.hostHome();
        return new File(home, "Library/Caches/Mac.apk/monet/palette.txt");
    }

    static State readCache(File f, String key) {
        if (key.indexOf('\n') >= 0 || !f.isFile() || f.length() > 64 * 1024) return null;
        try {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return parseCache(text, key);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Strict parse; any deviation is a miss. */
    static State parseCache(String text, String key) throws IOException {
        BufferedReader r = new BufferedReader(new StringReader(text));
        if (!MAGIC.equals(r.readLine())) return null;
        String k = r.readLine();
        if (k == null || !k.equals("key " + key)) return null;
        String sl = r.readLine(), hl = r.readLine(), ml = r.readLine();
        if (sl == null || hl == null || ml == null
                || !sl.startsWith("seed ") || !hl.startsWith("hints ") || !ml.startsWith("main ")) {
            return null;
        }
        int seed = (int) Long.parseLong(sl.substring(5), 16);
        int hints = Integer.parseInt(hl.substring(6));
        String[] mains = ml.substring(5).split(",");
        int[] main = new int[mains.length];
        for (int i = 0; i < mains.length; i++) main[i] = (int) Long.parseLong(mains[i], 16);
        Map<String, Integer> overlay = new LinkedHashMap<>();
        String line;
        while ((line = r.readLine()) != null) {
            if (line.equals("end")) {
                if (overlay.size() != MonetOverlay.expectedSize()) return null;
                return new State(seed, main, hints, overlay, "cache");
            }
            String[] p = line.split(" ");
            if (p.length != 3 || !p[0].equals("c") || !MonetOverlay.isOverlayName(p[1])
                    || overlay.containsKey(p[1])) {
                return null;
            }
            overlay.put(p[1], (int) Long.parseLong(p[2], 16));
        }
        return null;   // no "end": a torn or truncated file
    }

    static String formatCache(String key, State s) {
        StringBuilder sb = new StringBuilder(8 * 1024);
        sb.append(MAGIC).append('\n');
        sb.append("key ").append(key).append('\n');
        sb.append("seed ").append(Integer.toHexString(s.seed)).append('\n');
        sb.append("hints ").append(s.hints).append('\n');
        sb.append("main ");
        for (int i = 0; i < s.mainColors.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Integer.toHexString(s.mainColors[i]));
        }
        sb.append('\n');
        for (Map.Entry<String, Integer> e : s.overlay.entrySet()) {
            sb.append("c ").append(e.getKey()).append(' ')
              .append(Integer.toHexString(e.getValue())).append('\n');
        }
        sb.append("end\n");
        return sb.toString();
    }

    /** Write-then-rename, so a concurrent reader (several Mac.apk apps launching at once)
     *  sees the old file or the new one, never a torn one. Best effort. */
    static void writeCache(File f, String key, State s) {
        if (key.indexOf('\n') >= 0 || s.mainColors.length == 0) return;
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
            File tmp = File.createTempFile("palette", ".tmp", dir);
            try {
                Files.write(tmp.toPath(), formatCache(key, s).getBytes(StandardCharsets.UTF_8));
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                tmp.delete();
            }
        } catch (IOException | RuntimeException e) {
            // A cache we cannot write only costs the next launch the same computation.
        }
    }

    // ---- small helpers -------------------------------------------------------------------

    static int parseSeed(String s) {
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2);
        long v = Long.parseLong(h, 16);
        return h.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
    }

    static String hex6(int argb) {
        return String.format("%06X", argb & 0xFFFFFF);
    }

    /** Record the resolve's outcome, then log it. */
    private static void note(String msg) {
        sOutcome = msg;
        log(msg);
    }

    /** NativeLog.i, or stderr on a bare JVM (offline gates), where print is unbound. */
    static void log(String msg) {
        try {
            com.macapk.loader.NativeLog.i(TAG, msg);
        } catch (Throwable t) {
            System.err.println("[I] " + TAG + ": " + msg);
        }
    }
}
