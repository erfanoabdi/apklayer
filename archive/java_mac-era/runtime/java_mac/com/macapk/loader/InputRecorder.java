package com.macapk.loader;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** INPUT RECORDER (2026-10-01): the owner's hand session as a MACAPK_INJECT_FILE
 *  script we can replay exactly. docs/input-recorder_2026-10-01.md has the format,
 *  the switches and the privacy rules; this header is the contract the code keeps.
 *
 *  WHERE IT RECORDS. At the Java layer every host input source already funnels
 *  through, and nowhere else, so each source is recorded exactly once -- AT
 *  DELIVERY, on the thread that hands the event to the app (the loop for a Canvas
 *  tree, the AppKit thread for a tree that keeps host input there), not at the
 *  entry that merely queues it:
 *    TouchDispatcher.doDispatch(action, x, y, viewW, viewH)  -- what the JNI call
 *        dispatch() queues for the left mouse, the scroll-wheel/trackpad scroll drag
 *        cocoa_bridge.mm synthesizes, MACAPK_DEBUG_AUTOCLICK and the teardown CANCEL;
 *    TouchDispatcher.deliverKey -> doDispatchKey(action, keyCode, unicode, meta) --
 *        every key, the title-bar / Esc / Cmd+[ BACK, and every NSTextInputClient
 *        commit (an IME conversion arrives as keyCode 0 + the character);
 *    TouchDispatcher.deliverTrackpadNow(motions)  -- the OUTPUT of the trackpad
 *        multi-pointer mapper (two-finger hold, pinch, claim CANCEL): the Android
 *        stream itself, so a replay does not depend on the mapper's settle timers.
 *  Why delivery and not entry (review 2026-10-01, issue 1): the password decision
 *  reads the focused editor, and the focus change that makes a field the editor is
 *  APPLIED on the loop. A hook at the entry runs on the AppKit thread before the
 *  queued focus change has run, so keys typed right after a tap on a password
 *  field (a janky login screen, a password manager's auto-type) would be judged
 *  against the PREVIOUS editor and written in clear. At delivery the FIFO order of
 *  the loop IS the file's order, and the check reads the focus the key is
 *  delivered into, on the thread that writes it.
 *  Keyboard-to-touch bindings are derived inside doDispatchKey, so they are
 *  re-derived on replay from the recorded keys rather than recorded twice.
 *  Not host input, so not recorded: the harness injectors (macapkInjectTap /
 *  Key / Text) -- and host actions that bypass these paths (Dock re-activation,
 *  a key-binding profile switch, a window resize) are NOTED as `# not recorded`.
 *
 *  FORMAT. Exactly the inject-file grammar (TouchDispatcher.macapkInjectRoute is
 *  the table), plus the minimum it lacked:
 *    inputrec 1 package=.. window=WxH scale=.. surface=WxH density=.. orientation=.. version=..
 *                     the header (FIRST line of the file; a file whose first line is
 *                     the header is replayed read-only by the inject driver)
 *    +<ms> <line>     relative timing: ms after the app's first activity
 *    ptr <action> <sx> <sy>          one host-pointer event, Android SURFACE px (top-left)
 *    keyev <action> <keyCode> <unicode> <meta>   one host key event
 *    mptr <action> <index> <id>:<x>:<y>...       one trackpad multi-pointer motion
 *    window <W> <H>   the window changed size mid-session (points)
 *    type-redacted <n>   n characters typed into a PASSWORD field: never written
 *    inputrec-end ... the session closed cleanly
 *
 *  OFF BY DEFAULT. ON is a static final read once at class init: every hook site
 *  is `if (InputRecorder.ON) ...`, so a disabled recorder costs one constant test
 *  and allocates nothing. Two switches:
 *    MACAPK_RECORD_INPUT=<file | dir/>   explicit (=0 forces OFF, even with ENABLED);
 *    ~/Library/Application Support/Mac.apk/recordings/ENABLED  for Dock-launched
 *        INSTALLED apps, which have no environment: each launch writes
 *        recordings/<pkg>_<YYYYmmdd-HHMMSS>.inject. Honoured ONLY when the runtime's
 *        android root is an installed bundle's (`<Name>.app/Contents/android`), and
 *        never under a harness env (MACAPK_INJECT_FILE / MACAPK_DEBUG_AUTOCLICK /
 *        MACAPK_DEBUG_AUTOKEY), so a sweep, a drive or any debug/ script running a
 *        scratch root never writes into, or prunes, the owner's folder -- the
 *        bundle test is what covers the harnesses that drive real CGEvent input
 *        with none of those envs set.
 *  Retention (folder mode): KEEP_PER_APP newest per package, DIR_CAP_BYTES total;
 *  one recording stops at FILE_CAP_BYTES. Files are 0600, a created folder 0700.
 *
 *  PRIVACY. While the editor a key is DELIVERED into is a password field (AOSP
 *  TextView.isAnyPasswordInputType or a PasswordTransformationMethod, or the bound
 *  EditorInfo's inputType), no key that can carry text is written: the characters
 *  become ONE `type-redacted <n>` line and a replay types nothing for it. Only
 *  editing/navigation keys with no character (DEL, ENTER, TAB, arrows, BACK, Esc)
 *  are kept there. Taps are always recorded, so an app's OWN on-screen PIN pad is
 *  NOT protected (we cannot tell its buttons from any other). */
public final class InputRecorder {
    private InputRecorder() {}
    private static final String TAG = "Mac.apk.InputRec";

    static final int FORMAT = 1;
    static final int KEEP_PER_APP = 20;
    static final long DIR_CAP_BYTES = 64L << 20;
    static final long FILE_CAP_BYTES = 16L << 20;

    /** The ONE switch (see the header). */
    static final boolean ON;
    /** MACAPK_RECORD_INPUT's value, or null when the ENABLED folder switched us on. */
    private static final String SPEC;
    static {
        String spec = null;
        boolean on = false;
        final String env = System.getenv("MACAPK_RECORD_INPUT");
        if (env != null && !env.isEmpty()) {
            if (!"0".equals(env)) { on = true; spec = env; }
        } else if (enabledByFolder()) {
            on = true;
        }
        ON = on;
        SPEC = spec;
    }

    /** Hook invocations (gate witness: stays 0 while OFF). */
    static final java.util.concurrent.atomic.AtomicInteger HOOK_CALLS =
        new java.util.concurrent.atomic.AtomicInteger();

    static Path recordingsDir() {
        return Paths.get(System.getProperty("user.home"), "Library", "Application Support",
                         "Mac.apk", "recordings");
    }

    private static boolean enabledByFolder() {
        for (String h : new String[]{"MACAPK_INJECT_FILE", "MACAPK_DEBUG_AUTOCLICK",
                                     "MACAPK_DEBUG_AUTOKEY"}) {
            String v = System.getenv(h);
            if (v != null && !v.isEmpty()) return false;
        }
        // Installed apps only: a harness (debug/*.sh driving real CGEvent clicks
        // with none of the envs above) runs a scratch android root, and must
        // neither write into the owner's folder nor prune it.
        if (!isInstalledBundleRoot(System.getProperty("macapk.android.root"))) return false;
        try { return Files.exists(recordingsDir().resolve("ENABLED")); }
        catch (Throwable t) { return false; }
    }

    /** `<Name>.app/Contents/android`: the android root of an INSTALLED bundle
     *  (launcher/BundleGenerator.swift: `url/Contents/android`). */
    static boolean isInstalledBundleRoot(String root) {
        if (root == null) return false;
        String r = root;
        while (r.length() > 1 && r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r.endsWith(".app/Contents/android");
    }

    // ---- recorder state (guarded by InputRecorder.class) ----
    private static String sPkg = "unknown";
    private static long sT0;                 // System.nanoTime() of the first activity
    private static Writer sOut;
    private static Path sPath;
    private static boolean sDead;
    private static long sBytes;
    private static int sEvents;
    private static float sWinW = -1f, sWinH = -1f;
    private static int sRedacted;
    private static long sRedactedAt;
    private static final java.util.HashSet<String> sNoted = new java.util.HashSet<>();
    /** `# not recorded` notes raised before the file was opened (a profile switch
     *  at boot): written right after the header, so they are never lost. */
    private static final List<String> sPendingNotes = new ArrayList<>();

    /** Boot, once the application exists. Anchors the clock on the first activity
     *  (the moment the inject driver starts replaying) and announces the switch. */
    public static void start(String pkg) {
        if (!ON) return;
        synchronized (InputRecorder.class) {
            if (pkg != null && !pkg.isEmpty()) sPkg = pkg;
        }
        Thread th = new Thread(() -> {
            try { while (ActivityHost.peek() == null) Thread.sleep(50); }
            catch (InterruptedException ie) { return; }
            synchronized (InputRecorder.class) { if (sT0 == 0L) sT0 = System.nanoTime(); }
        }, "macapk-inputrec-anchor");
        th.setDaemon(true);
        th.start();
        try { Runtime.getRuntime().addShutdownHook(new Thread(InputRecorder::close, "macapk-inputrec-close")); }
        catch (Throwable ignored) {}
        NativeLog.i(TAG, "input recording ON -> " + (SPEC != null ? SPEC
            : recordingsDir() + "/" + sanitize(sPkg) + "_<time>.inject")
            + " (" + (SPEC != null ? "MACAPK_RECORD_INPUT" : "recordings/ENABLED")
            + "); text typed into a password field is never written");
    }

    // ---- hooks (callers test ON first) ----

    /** TouchDispatcher.dispatch: Cocoa view POINTS (y up) -> the Android surface
     *  pixels doDispatch computes, with doDispatch's own arithmetic. */
    static void pointer(int action, float x, float y, float viewW, float viewH) {
        HOOK_CALLS.incrementAndGet();
        final float scale = TouchDispatcher.macapkBackingScale();
        // GATE-MUTANT(inputrec:android-coords) begin -- debug/inputrec_gate.sh C2
        final float px = x * scale;
        final float py = (viewH - y) * scale;
        // GATE-MUTANT(inputrec:android-coords) end
        // GATE-HOOK(inputrec:pointer) -- debug/inputrec_gate.sh C1 inserts a MOVE drop here
        synchronized (InputRecorder.class) {
            if (!ready(viewW, viewH)) return;
            if (Math.abs(viewW - sWinW) > 0.5f || Math.abs(viewH - sWinH) > 0.5f) {
                sWinW = viewW; sWinH = viewH;
                emit("window " + num(viewW) + " " + num(viewH));
            }
            emit("ptr " + action + " " + num(px) + " " + num(py));
        }
    }

    /** TouchDispatcher.dispatchKey. */
    static void key(int action, int keyCode, int unicode, int meta) {
        HOOK_CALLS.incrementAndGet();
        synchronized (InputRecorder.class) {
            if (!ready(-1f, -1f)) return;
            // GATE-MUTANT(inputrec:redact) begin -- debug/inputrec_gate.sh C3
            if (passwordTarget() && !passwordSafeKey(keyCode, unicode)) {
                if (action == 0 && unicode >= 32 && unicode != 127) {
                    if (sRedacted++ == 0) sRedactedAt = elapsedMs();
                }
                return;
            }
            // GATE-MUTANT(inputrec:redact) end
            emit("keyev " + action + " " + keyCode + " " + unicode + " " + meta);
        }
    }

    /** TouchDispatcher.deliverTrackpad: surface-pixel Motions, already Android-side. */
    static void motions(List<TrackpadGestureMapper.Motion> evs) {
        HOOK_CALLS.incrementAndGet();
        synchronized (InputRecorder.class) {
            if (!ready(-1f, -1f)) return;
            for (TrackpadGestureMapper.Motion m : evs) {
                StringBuilder sb = new StringBuilder("mptr ").append(m.action).append(' ').append(m.index);
                for (int i = 0; i < m.ids.length; i++)
                    sb.append(' ').append(m.ids[i]).append(':').append(num(m.xs[i]))
                      .append(':').append(num(m.ys[i]));
                emit(sb.toString());
            }
        }
    }

    /** A host action this recorder does not capture (a Dock re-activation, a
     *  key-binding profile switch, a window resize, the legacy pinch): said once per
     *  distinct text, with its time, as a `# +<ms> not recorded: ...` comment, so a
     *  replay that diverges after it is explained rather than silent. A note raised
     *  before the first event is held until the file is opened. */
    static void unrecorded(String what) {
        HOOK_CALLS.incrementAndGet();
        synchronized (InputRecorder.class) {
            if (sDead || !sNoted.add(what)) return;
            final String note = "# +" + (sT0 == 0L ? 0L : Math.max(0L, (System.nanoTime() - sT0) / 1_000_000L))
                + " not recorded: " + what.replace('\n', ' ') + "\n";
            if (sOut == null) { sPendingNotes.add(note); return; }
            try { sOut.write(note); sOut.flush(); }
            catch (IOException e) { fail(e); }
        }
    }

    /** Flush a pending redaction and write `inputrec-end`. Idempotent. Called from
     *  ActivityHost.macapkTerminate (window close, Cmd-Q, Dock Quit and an app's
     *  System.exit all leave through macapk_terminate_now -> ::_exit, which runs
     *  NO Java shutdown hook) and from a JVM shutdown hook as the second path. */
    static void close() {
        synchronized (InputRecorder.class) {
            if (sOut == null) return;
            try {
                flushRedacted();
                write(elapsedMs(), "inputrec-end events=" + sEvents);
                sOut.close();
            } catch (IOException ignored) {
            } finally {
                sOut = null;
                sDead = true;
            }
        }
    }

    static synchronized Path path() { return sPath; }

    // ---- privacy ----

    /** The focused editor is a password field (InputMethodManager's view of it). */
    static boolean passwordTarget() {
        try { return android.view.inputmethod.InputMethodManager.macapkPasswordTargetActive(); }
        catch (Throwable t) { return false; }
    }

    /** Keys that carry no character and so may be written even in a password
     *  field: BACK, DPAD 19-23, TAB 61, ENTER 66, DEL 67, PAGE 92/93, ESCAPE 111,
     *  FORWARD_DEL 112, MOVE_HOME/END 122/123, NUMPAD_ENTER 160 -- and only with
     *  no printable unicode (control codes 8/9/10/13/27/127 are not text). */
    static boolean passwordSafeKey(int keyCode, int unicode) {
        if (unicode >= 32 && unicode != 127) return false;
        switch (keyCode) {
            case 4: case 19: case 20: case 21: case 22: case 23: case 61: case 66: case 67:
            case 92: case 93: case 111: case 112: case 122: case 123: case 160:
                return true;
            default:
                return false;
        }
    }

    // ---- writer (all under the class lock) ----

    private static void emit(String body) {
        flushRedacted();
        write(elapsedMs(), body);
    }

    private static void flushRedacted() {
        if (sRedacted <= 0) return;
        int n = sRedacted;
        sRedacted = 0;
        write(sRedactedAt, "type-redacted " + n);
    }

    private static void write(long t, String body) {
        if (sOut == null) return;
        try {
            String ln = "+" + t + " " + body + "\n";
            sOut.write(ln);
            sOut.flush();          // a crash is exactly when the recording matters
            sBytes += ln.length();
            sEvents++;
            if (sBytes > FILE_CAP_BYTES) {
                sOut.write("# recording stopped: " + FILE_CAP_BYTES + "-byte cap reached\n");
                sOut.close();
                sOut = null;
                sDead = true;
                NativeLog.w(TAG, "input recording stopped at the " + FILE_CAP_BYTES + "-byte cap: " + sPath);
            }
        } catch (IOException e) {
            fail(e);
        }
    }

    private static void fail(IOException e) {
        NativeLog.w(TAG, "input recording disabled: " + e);
        try { if (sOut != null) sOut.close(); } catch (IOException ignored) {}
        sOut = null;
        sDead = true;
    }

    private static long elapsedMs() {
        long now = System.nanoTime();
        if (sT0 == 0L) sT0 = now;          // input before the anchor thread saw an activity
        return Math.max(0L, (now - sT0) / 1_000_000L);
    }

    static String num(float v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.indexOf('.') >= 0) {
            int e = s.length();
            while (s.charAt(e - 1) == '0') e--;
            if (s.charAt(e - 1) == '.') e--;
            s = s.substring(0, e);
        }
        return "-0".equals(s) ? "0" : s;
    }

    private static String sanitize(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append((Character.isLetterOrDigit(c) && c < 128) || c == '.' || c == '-' ? c : '_');
        }
        return b.length() == 0 ? "unknown" : b.toString();
    }

    private static final Set<PosixFilePermission> FILE_0600 = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> DIR_0700 = PosixFilePermissions.fromString("rwx------");

    /** Open the recording on the first event and write the header. */
    private static boolean ready(float viewW, float viewH) {
        if (sOut != null) return true;
        if (sDead) return false;
        try {
            final Path p = target();
            final java.nio.channels.SeekableByteChannel ch = Files.newByteChannel(p,
                EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                           StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(FILE_0600));
            // The attribute applies only when the file is CREATED; an existing file
            // named by MACAPK_RECORD_INPUT is narrowed before anything is written.
            Files.setPosixFilePermissions(p, FILE_0600);
            sOut = new OutputStreamWriter(Channels.newOutputStream(ch), StandardCharsets.UTF_8);
            sPath = p;
            if (viewW > 0f && viewH > 0f) { sWinW = viewW; sWinH = viewH; }
            else {
                try { sWinW = NativeCocoa.phoneWidth(); sWinH = NativeCocoa.phoneHeight(); }
                catch (Throwable t) { sWinW = -1f; sWinH = -1f; }
            }
            sOut.write(header() + "\n"
                + "# Mac.apk input recording (docs/input-recorder_2026-10-01.md). +<ms> = ms after"
                + " the app's first activity; ptr/mptr coordinates are Android surface pixels.\n"
                + "# Replay: debug/drive_app.sh replay <this file>, or MACAPK_EXTERNAL_URLS=0"
                + " MACAPK_INJECT_FILE=<this file> (replayed once, read-only).\n"
                + "# Text typed into a password field is never written: it is type-redacted <n>.\n");
            for (String n : sPendingNotes) sOut.write(n);
            sPendingNotes.clear();
            sOut.flush();
            NativeLog.i(TAG, "input recording -> " + p);
            return true;
        } catch (IOException | RuntimeException e) {
            NativeLog.w(TAG, "input recording could not start: " + e);
            sOut = null;
            sDead = true;
            return false;
        }
    }

    private static String header() {
        float scale = TouchDispatcher.macapkBackingScale();
        String density = "?";
        try {
            density = num(android.content.res.Resources.getSystem().getDisplayMetrics().density);
        } catch (Throwable ignored) {}
        boolean known = sWinW > 0f && sWinH > 0f;
        String win = known ? num(sWinW) + "x" + num(sWinH) : "?";
        String surf = known ? Math.round(sWinW * scale) + "x" + Math.round(sWinH * scale) : "?";
        String orient = !known ? "?" : (sWinW > sWinH ? "landscape" : "portrait");
        return "inputrec " + FORMAT + " package=" + sanitize(sPkg) + " window=" + win
            + " scale=" + num(scale) + " surface=" + surf + " density=" + density
            + " orientation=" + orient
            + " version=" + System.getProperty("macapk.runtime.version", "?")
            + " created=" + new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
                                .format(new java.util.Date());
    }

    private static Path target() throws IOException {
        if (SPEC != null) {
            Path s = Paths.get(SPEC);
            if (!SPEC.endsWith("/") && !Files.isDirectory(s)) {
                Path parent = s.toAbsolutePath().getParent();
                if (parent != null && !Files.isDirectory(parent)) mkdir700(parent);
                return s;
            }
            return inFolder(s);
        }
        return inFolder(recordingsDir());
    }

    private static Path inFolder(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) mkdir700(dir);
        final String pkg = sanitize(sPkg);
        prune(dir, pkg);
        final String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
                                 .format(new java.util.Date());
        Path p = dir.resolve(pkg + "_" + stamp + ".inject");
        for (int i = 2; Files.exists(p) && i < 100; i++)
            p = dir.resolve(pkg + "_" + stamp + "-" + i + ".inject");
        return p;
    }

    private static void mkdir700(Path dir) throws IOException {
        Files.createDirectories(dir);
        try { Files.setPosixFilePermissions(dir, DIR_0700); } catch (IOException ignored) {}
    }

    /** A file this recorder made: <pkg>_<YYYYmmdd>-<HHMMSS>[-n].inject. Nothing else
     *  in the folder (ENABLED, a note) is ever pruned. */
    static boolean isRecordingName(String name) {
        return name.matches("[A-Za-z0-9._-]+_\\d{8}-\\d{6}(-\\d+)?\\.inject");
    }

    /** Keep the newest KEEP_PER_APP-1 recordings of `pkg` (the new one makes
     *  KEEP_PER_APP) and the whole folder under DIR_CAP_BYTES - FILE_CAP_BYTES, so the
     *  new recording at its cap still fits. Oldest first. */
    static void prune(Path dir, String pkg) {
        final List<Path> all = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) if (isRecordingName(p.getFileName().toString())) all.add(p);
        } catch (IOException e) {
            return;
        }
        final java.util.Map<Path, Long> mt = new java.util.HashMap<>();
        for (Path p : all) {
            try { mt.put(p, Files.getLastModifiedTime(p).toMillis()); } catch (IOException e) { mt.put(p, 0L); }
        }
        all.sort((a, b) -> Long.compare(mt.get(b), mt.get(a)));          // newest first
        int mine = 0;
        final List<Path> keep = new ArrayList<>();
        // Exactly <pkg>_<stamp>[-n].inject: a package name may itself contain '_',
        // so a bare startsWith(pkg + "_") would also count com.foo_bar's files as
        // com.foo's and delete them.
        final java.util.regex.Pattern ours = java.util.regex.Pattern.compile(
            java.util.regex.Pattern.quote(pkg) + "_\\d{8}-\\d{6}(-\\d+)?\\.inject");
        for (Path p : all) {
            if (ours.matcher(p.getFileName().toString()).matches() && ++mine >= KEEP_PER_APP) {
                del(p);
            } else {
                keep.add(p);
            }
        }
        long total = 0L;
        for (Path p : keep) { try { total += Files.size(p); } catch (IOException ignored) {} }
        for (int i = keep.size() - 1; i >= 0 && total > DIR_CAP_BYTES - FILE_CAP_BYTES; i--) {
            try { total -= Files.size(keep.get(i)); } catch (IOException ignored) {}
            del(keep.get(i));
        }
    }

    private static void del(Path p) {
        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
    }

    // =====================================================================
    // REPLAY: one recording being run by the inject driver
    // (TouchDispatcher.macapkRunInjectLines owns the loop and the timing prefix).
    // =====================================================================

    /** Where replayed events go. Production: TouchDispatcher.ReplaySink (the real
     *  entry points, on the thread the host would have used). */
    interface Sink {
        void pointer(int action, float sx, float sy);
        void key(int action, int keyCode, int unicode, int meta);
        void motions(List<TrackpadGestureMapper.Motion> m);
        /** The current window in POINTS, or null when unknown. */
        float[] windowPts();
        /** The running app's package, or null when unknown. */
        String packageName();
        /** The window's backing scale (surface px per point). */
        float scale();
    }

    static final class Replay {
        private final long base = System.nanoTime();
        final Sink sink;
        boolean refused;
        int events, skipped, redacted, late16;
        long lateMax;

        private Replay(Sink sink) { this.sink = sink; }

        /** The header line. A refusal is LOUD and makes the rest of this recording
         *  a no-op (counted as skipped), never a partial, mis-placed replay. */
        static Replay begin(String header, Sink sink) {
            final Replay r = new Replay(sink);
            final String[] p = header.trim().split("\\s+");
            final java.util.Map<String, String> kv = new java.util.HashMap<>();
            for (int i = 2; i < p.length; i++) {
                int eq = p[i].indexOf('=');
                if (eq > 0) kv.put(p[i].substring(0, eq), p[i].substring(eq + 1));
            }
            final String fmt = p.length > 1 ? p[1] : "?";
            if (!String.valueOf(FORMAT).equals(fmt)) {
                r.refuse("format '" + fmt + "', this runtime replays format " + FORMAT);
                return r;
            }
            // GATE-MUTANT(inputrec:urls-guard) begin
            if (!"0".equals(System.getenv("MACAPK_EXTERNAL_URLS"))) {
                r.refuse("set MACAPK_EXTERNAL_URLS=0: replayed input enters where a person's"
                    + " does (noteRealInput), which would let the app open the host browser");
                return r;
            }
            // GATE-MUTANT(inputrec:urls-guard) end
            final String recPkg = kv.get("package"), curPkg = sink.packageName();
            if (recPkg != null && curPkg != null && !recPkg.equals(sanitize(curPkg))) {
                r.refuse("recorded on " + recPkg + ", this app is " + curPkg);
                return r;
            }
            if (!r.windowMatches(kv.get("window"), "header")) return r;
            if (!r.scaleMatches(kv.get("scale"))) return r;
            final String recVer = kv.get("version"),
                         curVer = System.getProperty("macapk.runtime.version", "?");
            NativeLog.i(TAG, "inputrec: replaying package=" + recPkg + " window=" + kv.get("window")
                + " recorded-version=" + recVer
                + (recVer != null && !recVer.equals(curVer) ? " (this runtime is " + curVer
                    + ": a different build may diverge)" : ""));
            return r;
        }

        /** `window W H` (or the header's WxH): the replay window must be the
         *  recording's, within 1 point. A different size reflows the layout, so no
         *  scaling of coordinates can put a tap on the same widget: refuse instead. */
        boolean windowMatches(String wxh, String where) {
            if (wxh == null || "?".equals(wxh)) return true;      // keys-only start; a window line follows
            final String[] wh = wxh.split("[x ]");
            final float[] cur = sink.windowPts();
            try {
                final float w = Float.parseFloat(wh[0]), h = Float.parseFloat(wh[1]);
                if (cur == null) {
                    refuse("this runtime cannot tell its window size; the recording needs " + wxh);
                    return false;
                }
                if (Math.abs(cur[0] - w) > 1f || Math.abs(cur[1] - h) > 1f) {
                    refuse("recorded in a " + num(w) + "x" + num(h) + "-point window (" + where
                        + "), this window is " + num(cur[0]) + "x" + num(cur[1])
                        + ": the layout reflows at another size, so no coordinate scaling is right;"
                        + " relaunch at that size (debug/drive_app.sh replay reads it from the header)");
                    return false;
                }
                return true;
            } catch (RuntimeException e) {
                refuse("unreadable window '" + wxh + "'");
                return false;
            }
        }

        /** The recording's backing scale must be this window's. Window POINTS can
         *  match across a 1x external display and a 2x built-in, but a GL /
         *  SurfaceView app's surface pixels follow the real scale (the header's
         *  `surface=` is round(window * scale)), so replaying at the other scale
         *  would hand the app the recorded pixels on a surface of another size and
         *  mis-place every tap, silently. Refuse, naming both. */
        boolean scaleMatches(String recorded) {
            if (recorded == null || "?".equals(recorded)) return true;
            final float cur = sink.scale();
            try {
                final float rec = Float.parseFloat(recorded);
                if (Math.abs(rec - cur) > 0.01f) {
                    refuse("recorded at a backing scale of " + num(rec) + "x, this window is " + num(cur)
                        + "x (a different display): surface pixels would land on other widgets;"
                        + " replay on a display of the same scale (the built-in vs an external monitor)");
                    return false;
                }
                return true;
            } catch (RuntimeException e) {
                refuse("unreadable scale '" + recorded + "'");
                return false;
            }
        }

        void refuse(String why) {
            refused = true;
            NativeLog.w(TAG, "inputrec: REFUSED: " + why);
        }

        void waitUntil(long ms) throws InterruptedException {
            final long due = base + ms * 1_000_000L;
            long now;
            while ((now = System.nanoTime()) < due)
                Thread.sleep(Math.max(1L, (due - now) / 1_000_000L));
            final long late = (now - due) / 1_000_000L;
            lateMax = Math.max(lateMax, late);
            if (late > 16L) late16++;      // more than a frame late: timing-sensitive input (a fling) may differ
        }

        /** Recording-only verbs. 0 = not one (dispatch it), 1 = handled, 2 = ended. */
        int handle(String ln) {
            final String[] p = ln.split("\\s+");
            switch (p[0]) {
                case "window":
                    if (p.length >= 3) windowMatches(p[1] + "x" + p[2], "mid-session resize");
                    return 1;
                case "type-redacted":
                    try { redacted += Integer.parseInt(p[1]); } catch (RuntimeException ignored) {}
                    NativeLog.w(TAG, "inputrec: " + (p.length > 1 ? p[1] : "?") + " password"
                        + " character(s) were never recorded; nothing is typed for them");
                    return 1;
                case "inputrec-end":
                    done("inputrec-end");
                    return 2;
                default:
                    return 0;
            }
        }

        void done(String why) {
            NativeLog.i(TAG, "inputrec: replay done events=" + events + " skipped=" + skipped
                + " redacted=" + redacted + " late_max=" + lateMax + "ms late_over_16ms=" + late16
                + " (" + why + ")"
                + (refused ? " REFUSED" : ""));
        }
    }

    /** A file whose FIRST line is the header is a recording. */
    static boolean isRecordingFile(Path f) {
        try (java.io.BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            String first = r.readLine();
            return first != null && first.startsWith("inputrec ");
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
