// android.content.ClipboardManager -- the system clipboard service
// (Context.getSystemService(CLIPBOARD_SERVICE)). Apps set/get the
// primary clip for copy/paste (e.g. a "Copy" menu item writing the
// selected text, a text field reading the clip on paste).
//
// AOSP ref (android14-release):
//   frameworks/base/core/java/android/content/ClipboardManager.java
//   frameworks/base/services/core/java/com/android/server/clipboard/ClipboardService.java
//   frameworks/base/services/core/java/com/android/server/clipboard/EmulatorClipboardMonitor.java
// AOSP's ClipboardManager extends android.text.ClipboardManager (a legacy
// text-only API) and talks to the system clipboard service over Binder,
// so on real Android any two apps (or an app and the system) share ONE
// clipboard.
//
// ---- THE HOST BRIDGE (MACAPK_CLIPBOARD_HOST, default ON) -------------------
// Mac.apk runs one guest app per process, so there is no Binder service to
// talk to -- but the host still has exactly one real system clipboard, the
// macOS general pasteboard, and every other app on the machine plays the part
// of "another Android app". So the SERVICE half of AOSP lives in this file
// (nested class Service: the ClipboardService rules that are observable from
// an app) and its STORAGE is the pasteboard itself, reached through
// runtime/src/clipboard_bridge.mm (host-first rung 1: NSPasteboard, not AWT).
// What that buys, per AOSP:
//   * setPrimaryClip writes plain text + HTML (+ a web URL, + the
//     EXTRA_IS_SENSITIVE marker) to the pasteboard, so Safari/Notes/Mail paste
//     it; getPrimaryClip returns what they copied. The whole ClipData (label,
//     every item, intents, content: URIs, extras) round-trips losslessly inside
//     the process while the pasteboard still holds our write.
//   * A clip from outside is shaped the way AOSP's own host bridge shapes one:
//     EmulatorClipboardMonitor builds `new ClipData("host clipboard",
//     new String[]{"text/plain"}, new ClipData.Item(str))` with extras
//     com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY=true. We keep that label,
//     that extra and text/plain as the primary type, and add text/html beside
//     it when the pasteboard carries HTML (see Service.fromHost).
//   * OnPrimaryClipChangedListener fires on the app's MAIN looper
//     (AOSP: `mHandler.post(() -> reportPrimaryClipChanged())`, mHandler being
//     the main thread's handler), for the app's own copies AND for copies made
//     in any other Mac app. macOS has no pasteboard-changed notification, only
//     NSPasteboard.changeCount, so while at least one listener is registered a
//     daemon polls changeCount -- metadata only, never content.
//   * Reads are refused to an app that is not focused (Android 10+:
//     ClipboardService.clipboardAccessAllowed(OP_READ_CLIPBOARD) requires
//     `mWm.isUidFocused(uid)`, and logs "Denying clipboard access to ...").
//     The host equivalent of "our window has focus" is "our process is the
//     active macOS app". Gate MACAPK_CLIPBOARD_FOCUS.
//   * Reading ANOTHER app's clip shows Android 12+'s once-per-clip notice,
//     "<App> pasted from your clipboard" (ClipboardService
//     .showAccessNotificationLocked). A clip copied in a Mac app is another
//     app's clip, exactly as a host clip is on the emulator (its primaryClipUid
//     is SYSTEM_UID there). Gate MACAPK_CLIPBOARD_ACCESS_NOTICE.
//   * setPrimaryClip(null) is an NPE and a clip with no items is
//     IllegalArgumentException("No items"), as the service throws.
// Deliberately NOT done: Android 13's one-hour auto-clear
// (DEFAULT_CLIPBOARD_TIMEOUT_MILLIS = 3600000). On the emulator it propagates
// to the host and blanks the developer's own clipboard; here the clipboard IS
// the Mac's, so that is an owner decision, recorded in
// docs/clipboard-host-bridge_2026-09-26.md rather than taken silently.
//
// MACAPK_CLIPBOARD_HOST=0 (or a runtime whose native table lacks the bridge,
// or no pasteboard) restores the pre-bridge ClipboardManager path: the legacy*
// methods below are the previous bodies -- per-instance clip, synchronous
// listener dispatch, AWT text-only host sync -- with ONE addition, the
// timestamp stamp (legacyStamp). What that gate does NOT restore is the
// ClipData / ClipDescription API fixes that landed with the bridge; those are
// AOSP contract fixes, deliberately ungated, and two of them are visible
// through the legacy bodies:
//   * ClipDescription's constructor no longer stamps a timestamp (AOSP's does
//     not; ClipboardService stamps the clip it stores). The pre-bridge path
//     used to read the construction time, so legacyStamp stamps the clip it
//     stores instead, as the service does -- otherwise it would report 0,
//     which no clip read from a device's clipboard ever carries.
//   * Item.coerceToText now answers "" for a content:, file: or
//     android.resource: URI (AOSP) where it used to answer uri.toString(), and
//     intent.toUri(...) for an Intent. So the legacy AWT push now sends ""
//     for a content-URI-only clip -- which is also exactly what AOSP's
//     EmulatorClipboardMonitor sends the host for it (getClipString).
//
// ---- the pre-bridge history, kept because it explains the AWT path ---------
// Previously a bare stub -> setPrimaryClip/getPrimaryClip/listeners all
// NoSuchMethodError. Implemented the full app-reachable surface, then
// wired the text path to the host pasteboard through java.awt.datatransfer.
// Any AWT failure (headless JVM, no display connection, security manager)
// leaves macapkHostClipboard null and every AWT bridge call becomes a no-op,
// so the in-process clip still behaves as it did before that bridge existed.
package android.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Extends the legacy supertype, as AOSP does. The three abstract methods it declares
// (getText/setText/hasText) were ALREADY implemented below -- what was missing was the
// supertype itself, so a pre-API-11 app's
//   (android.text.ClipboardManager) ctx.getSystemService(CLIPBOARD_SERVICE)
// could not cast. Measured references: OpenTTD, klooni.
// AOSP fidelity: the deprecation usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("deprecation")
public class ClipboardManager extends android.text.ClipboardManager {

    /** Bisect gate: MACAPK_CLIPBOARD_HOST=0 restores the pre-bridge
     *  ClipboardManager path (AWT text sync, per-instance clip, synchronous
     *  listeners). The ClipData/ClipDescription API fixes are NOT gated; see
     *  the header for the two that show through it. */
    static final boolean MACAPK_CLIPBOARD_HOST =
        !"0".equals(System.getenv("MACAPK_CLIPBOARD_HOST"));
    /** Bisect gate: MACAPK_CLIPBOARD_FOCUS=0 lets an unfocused app read the
     *  clipboard (Android 9 and earlier behaviour); default ON is Android 10+. */
    static final boolean MACAPK_CLIPBOARD_FOCUS =
        !"0".equals(System.getenv("MACAPK_CLIPBOARD_FOCUS"));
    /** Bisect gate: MACAPK_CLIPBOARD_ACCESS_NOTICE=0 drops the Android 12+
     *  "<App> pasted from your clipboard" notice (log line + Toast). */
    static final boolean MACAPK_CLIPBOARD_ACCESS_NOTICE =
        !"0".equals(System.getenv("MACAPK_CLIPBOARD_ACCESS_NOTICE"));

    /** TEST HOOK ONLY (debug/clipboard_gate.sh sets it by reflection): when
     *  non-null it replaces the host focus probe, because a bare JVM is never
     *  the active macOS app. Nothing in the runtime writes it. */
    static volatile Boolean sMacApkFocusOverrideForTest;

    private final Context mContext;
    // AOSP: the main thread's Handler, handed in by SystemServiceRegistry.
    // Created lazily here, and only once a main thread exists -- see postReport.
    private volatile android.os.Handler mHandler;
    private final ArrayList<OnPrimaryClipChangedListener> mPrimaryClipChangedListeners =
        new ArrayList<OnPrimaryClipChangedListener>();

    // ---- pre-bridge state: used ONLY by the legacy* path -------------------
    private ClipData mPrimaryClip;
    private final List<OnPrimaryClipChangedListener> mListeners = new ArrayList<>();

    // Host-pasteboard bridge (java.awt.datatransfer, not a custom native
    // shim -- see header). LEGACY PATH ONLY.
    //
    // ⚠ RESOLVED LAZILY, ON FIRST CLIPBOARD USE -- NEVER IN THE CONSTRUCTOR.
    // This is a deadlock fix, not a style choice (2026-08-31, v1.0.1134).
    //
    // `java.awt.Toolkit.getDefaultToolkit()` on macOS constructs `LWCToolkit`,
    // whose ctor calls the native `LWCToolkit.initAppkit` and BLOCKS until the
    // AppKit (Cocoa) main thread services it. Doing that from a constructor
    // meant it ran during `getSystemService(CLIPBOARD_SERVICE)` -- which apps
    // routinely call from `onCreate` -- and produced a three-way deadlock that
    // left ~half of all Minecraft Bedrock launches on a permanently black
    // window. Captured with jstack on a live wedged process:
    //
    //   1. Java "main" runs MainActivity.onCreate -> getSystemService(CLIPBOARD)
    //      -> Application.getSystemService, which LOCKS the app's Application
    //      monitor <0x424628980>, -> new ClipboardManager() -> Toolkit
    //      .getDefaultToolkit(), which LOCKS the java.awt.Toolkit class-init
    //      monitor and then blocks in initAppkit waiting for the Cocoa main
    //      thread.
    //   2. The Cocoa main thread is inside MTKView drawInMTKView: -> JNI ->
    //      SurfaceView.macapkSurfaceChanged -> the app's surfaceCreated -> the
    //      guest's AGDK android_app_set_window, which queues INIT_WINDOW and
    //      cond-waits for the game thread to process it. So it cannot service
    //      AppKit. (That park is NORMAL -- healthy runs do it too.)
    //   3. The game thread is BLOCKED entering that same Application monitor
    //      (AppExitInfoHelper.readyForAppExitInfo -> Application
    //      .getSystemService), so it never reaches its first ALooper poll and
    //      never processes INIT_WINDOW.
    //
    // Each party waits on the next; nothing ever moves, no crash, no frame.
    // Whether it happens is a pure interleaving race, which is why it hit
    // ~50% of launches and why stderr I/O latency measurably shifted the odds.
    //
    // Lazy resolution is ALSO the AOSP-faithful behaviour: on a device,
    // getSystemService(CLIPBOARD_SERVICE) hands back a lightweight manager and
    // does not initialise a window toolkit. Nothing here forces AWT until an
    // app actually reads or writes the clipboard. (The host bridge above never
    // touches AWT at all, and never waits for the Cocoa main thread.)
    //
    // Bisect escape hatch (restores the old eager ctor): MACAPK_CLIPBOARD_EAGER=1.
    private volatile java.awt.datatransfer.Clipboard macapkHostClipboard;
    private volatile boolean macapkHostClipboardResolved;
    // The text we last WROTE to the host pasteboard, so a subsequent read
    // can tell "the host still shows what we just copied" (our own echo)
    // apart from "some other Mac app copied something new".
    private String macapkLastSyncedText;

    /** AOSP nested listener fired whenever the primary clip changes. */
    public interface OnPrimaryClipChangedListener {
        void onPrimaryClipChanged();
    }

    /** What Context.getSystemService(CLIPBOARD_SERVICE) constructs (by
     *  reflection, with no arguments). */
    public ClipboardManager() {
        this(null, null);
    }

    /** AOSP's own constructor (@UnsupportedAppUsage; SystemServiceRegistry
     *  passes the outer context and the main thread's Handler). A null
     *  handler means "the main looper's, once there is a main thread". */
    public ClipboardManager(Context context, android.os.Handler handler) {
        mContext = context;
        mHandler = handler;
        // Deliberately does NO AWT and NO native work -- see the field comment
        // above. The old eager body is kept only behind the bisect gate.
        if (System.getenv("MACAPK_CLIPBOARD_EAGER") != null) {
            macapkHostClipboard();
        }
        // MACAPK_CLIPBOARD_DELAY_MS=<n>: sleep here WITHOUT doing any AWT work.
        //
        // This exists to settle one specific open question, and it is a
        // measurement instrument, not a fix. Removing the eager
        // getDefaultToolkit() call above also removed a large, RELIABLE delay
        // (~620ms of LWCToolkit.initAppkit) from the middle of the app's
        // onCreate. The MCPE 54% sign-in stall is a timing race, and on the day
        // this landed the cross rate measured 0/19 against a historical 26-40%
        // (P(0/19 | p=0.26) ~ 0.003) -- so the delay may have been accidentally
        // WINNING that race for us. This codebase has the precedent: v658
        // recorded that MCPE crossed ~40% BECAUSE of a coarse 50ms
        // mutex-fairness sleep, and removing it made the stall deterministic.
        //
        // Setting this reproduces the TIMING without reintroducing the
        // deadlock, which is the only way to test the hypothesis in isolation.
        // If an interleaved A/B (N>=12) at ~600ms restores the cross rate, the
        // answer is NOT to restore the AWT call -- it is to replace an
        // accidental delay with a deliberate, documented scheduling nudge at
        // the right layer. If it does not, look at app state (xal/ tokens) and
        // the live service side before blaming this change.
        // Default OFF; absent env = zero cost.
        // ⚠ HOW TO READ AN A/B THAT USES THIS: the sleep runs inside the ctor,
        // which Application.getSystemService invokes while HOLDING the app's
        // Application monitor -- the same monitor the guest game thread blocks on
        // via AppExitInfoHelper. So =600 reproduces 600ms of HOLDING THAT MONITOR,
        // not 600ms of neutral wall clock. That is arguably the better instrument
        // (it is closer to the pre-fix state, which also held it), but a positive
        // result cannot distinguish "the delay won the race" from "the monitor
        // hold changed the interleaving". Via Context.getSystemService (NOT
        // synchronized) it degrades to a pure sleep.
        String d = System.getenv("MACAPK_CLIPBOARD_DELAY_MS");
        if (d != null) {
            try { Thread.sleep(Long.parseLong(d.trim())); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            catch (RuntimeException ignored) { }
        }
    }

    /** True when this process runs the host bridge; decided once, at the
     *  first clipboard CALL (never in the constructor). */
    private static boolean hostPath() {
        return MACAPK_CLIPBOARD_HOST && Service.available();
    }

    // =====================================================================
    // Public API. Each method is AOSP's, delegating to Service the way AOSP
    // delegates to IClipboard; with the bridge off it runs the pre-bridge body.
    // =====================================================================

    /** AOSP ClipboardManager.setPrimaryClip(ClipData): requireNonNull, then
     *  the service rejects a clip with no items ("No items"), stamps the
     *  timestamp on ITS copy, and notifies listeners. */
    public void setPrimaryClip(ClipData clip) {
        if (!hostPath()) { legacySetPrimaryClip(clip); return; }
        Objects.requireNonNull(clip);
        Service.setPrimaryClip(clip);
    }

    /** AOSP API-28 clearPrimaryClip(). Real Android's clear is system-wide,
     *  so this clears the macOS pasteboard too (a password manager scrubbing
     *  what it just copied is the common caller). */
    public void clearPrimaryClip() {
        if (!hostPath()) { legacyClearPrimaryClip(); return; }
        Service.clearPrimaryClip();
    }

    /** AOSP getPrimaryClip(): the current primary clip, a fresh copy per call
     *  (it arrives unparceled over Binder), or null -- including when the app
     *  is not focused. */
    public ClipData getPrimaryClip() {
        if (!hostPath()) return legacyGetPrimaryClip();
        return Service.getPrimaryClip(this);
    }

    /** AOSP getPrimaryClipDescription(): focus-gated like getPrimaryClip but
     *  it never raises the access notice (the service uses checkOp, not
     *  noteOp, and skips showAccessNotificationLocked). */
    public ClipDescription getPrimaryClipDescription() {
        if (!hostPath()) return legacyGetPrimaryClipDescription();
        return Service.getPrimaryClipDescription();
    }

    /** AOSP hasPrimaryClip(). */
    public boolean hasPrimaryClip() {
        if (!hostPath()) return legacyHasPrimaryClip();
        return Service.hasPrimaryClip();
    }

    /** AOSP hasText() -> IClipboard.hasClipboardText: the FIRST item's
     *  getText() is non-empty (not coerceToText: a URI-only clip is false). */
    public boolean hasText() {
        if (!hostPath()) return legacyHasText();
        return Service.hasClipboardText();
    }

    /** AOSP getText(): getPrimaryClip() then coerceToText(mContext). */
    public CharSequence getText() {
        if (!hostPath()) return legacyGetText();
        ClipData clip = getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            return clip.getItemAt(0).coerceToText(mContext);
        }
        return null;
    }

    /** AOSP setText(CharSequence): setPrimaryClip(ClipData.newPlainText(null, text)). */
    public void setText(CharSequence text) {
        setPrimaryClip(ClipData.newPlainText(null, text));
    }

    /** AOSP: the first listener registers this manager with the service; the
     *  list keeps duplicates, as AOSP's ArrayList.add does.
     *  Registering settles any change nobody has reported yet, and reporting it
     *  to OTHER managers happens after this manager's list lock is released:
     *  before a main thread exists postReport runs a report inline, which takes
     *  the other manager's list lock, so dispatching under ours would let two
     *  managers registering at once on two threads take the pair in opposite
     *  orders. (On a device the service call never reports synchronously.) */
    public void addPrimaryClipChangedListener(OnPrimaryClipChangedListener what) {
        if (!hostPath()) { legacyAddPrimaryClipChangedListener(what); return; }
        List<ClipboardManager> pending = null;
        synchronized (mPrimaryClipChangedListeners) {
            if (mPrimaryClipChangedListeners.isEmpty()) {
                pending = Service.addListener(this);
            }
            mPrimaryClipChangedListeners.add(what);
        }
        if (pending != null) Service.dispatch(pending);
    }

    public void removePrimaryClipChangedListener(OnPrimaryClipChangedListener what) {
        if (!hostPath()) { legacyRemovePrimaryClipChangedListener(what); return; }
        synchronized (mPrimaryClipChangedListeners) {
            mPrimaryClipChangedListeners.remove(what);
            if (mPrimaryClipChangedListeners.isEmpty()) {
                Service.removeListener(this);
            }
        }
    }

    /** AOSP reportPrimaryClipChanged (@UnsupportedAppUsage, package-private):
     *  snapshot under the lock, call outside it. A listener that throws
     *  propagates, as on a device (there it reaches the main looper). */
    void reportPrimaryClipChanged() {
        Object[] listeners;
        synchronized (mPrimaryClipChangedListeners) {
            final int n = mPrimaryClipChangedListeners.size();
            if (n <= 0) {
                return;
            }
            listeners = mPrimaryClipChangedListeners.toArray();
        }
        for (int i = 0; i < listeners.length; i++) {
            ((OnPrimaryClipChangedListener) listeners[i]).onPrimaryClipChanged();
        }
    }

    /** AOSP IOnPrimaryClipChangedListener.dispatchPrimaryClipChanged:
     *  `mHandler.post(() -> reportPrimaryClipChanged())`.
     *
     *  The Handler is made on first use and only once a main thread exists:
     *  Looper.getMainLooper() PINS the main thread to its caller when none is
     *  registered, and our caller here can be the poll daemon (see
     *  Looper.macapkHasMainThread). With no main thread yet there is no main
     *  looper to post to, so the report runs inline, as other internal posters
     *  do. */
    private void postReport() {
        android.os.Handler h = mHandler;
        if (h == null) {
            if (!android.os.Looper.macapkHasMainThread()) {
                try {
                    reportPrimaryClipChanged();
                } catch (RuntimeException e) {
                    android.util.Log.e(Service.TAG, "primary-clip listener threw", e);
                }
                return;
            }
            h = new android.os.Handler(android.os.Looper.getMainLooper());
            mHandler = h;
        }
        h.post(this::reportPrimaryClipChanged);
    }

    // =====================================================================
    // Service: the observable half of AOSP's ClipboardService, one per
    // process (AOSP keeps the clip in system_server, shared by every manager;
    // getSystemService here hands out a new manager per call, so the clip must
    // not live on the instance). Storage is the host pasteboard.
    // =====================================================================
    private static final class Service {
        static final String TAG = "ClipboardService";
        // AOSP EmulatorClipboardMonitor's label for a clip that came from the host.
        static final String HOST_LABEL = "host clipboard";
        // The extra EmulatorClipboardMonitor puts on a host clip so SystemUI
        // does not show its "copied" overlay for it.
        static final String EXTRA_SUPPRESS_OVERLAY = "com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY";
        // core/res/res/values/strings.xml (android14-release):
        //   <string name="pasted_from_clipboard">%1$s pasted from your clipboard</string>
        static final String PASTED_FROM_CLIPBOARD = "%1$s pasted from your clipboard";
        static final int REPRESENTABLE = MacApkHostPasteboard.TYPE_TEXT
            | MacApkHostPasteboard.TYPE_HTML | MacApkHostPasteboard.TYPE_URL;

        // How often the listener poll samples NSPasteboard.changeCount. macOS
        // posts no pasteboard-changed notification, so every observer on the
        // platform polls; 500 ms keeps a copy made in another app well under a
        // second from its listener callback while costing one metadata IPC per
        // tick, and only while a listener is registered.
        // MACAPK_CLIPBOARD_POLL_MS overrides it (clamped to 50..5000).
        static final long POLL_MS = pollMs();

        static final Object LOCK = new Object();
        // -1 unknown, 0 no bridge this run, 1 bridge live.
        static volatile int sAvail = -1;
        // Everything below is guarded by LOCK.
        static boolean sInit;
        static long sKnownCount;          // pasteboard changeCount the state below describes
        static boolean sOurs;             // this process wrote/cleared at sKnownCount
        static boolean sMaterialized;     // sClip reflects sKnownCount
        static ClipData sClip;            // the service's copy of the primary clip
        static String sSource;            // package that set it; null = a non-Mac.apk macOS app
        static boolean sNotified;         // AOSP Clipboard.mNotifiedUids for our one uid
        static long sAdoptedAtMs;         // when an outside clip was first seen (its timestamp)
        static final ArrayList<ClipboardManager> sListening = new ArrayList<>();
        static Thread sPoller;

        static long pollMs() {
            String v = System.getenv("MACAPK_CLIPBOARD_POLL_MS");
            if (v != null) {
                try { return Math.max(50L, Math.min(5000L, Long.parseLong(v.trim()))); }
                catch (NumberFormatException ignored) { }
            }
            return 500L;
        }

        static boolean available() {
            int a = sAvail;
            if (a < 0) {
                boolean ok;
                String why = null;
                try {
                    ok = MacApkHostPasteboard.nativeChangeCount() >= 0;
                    if (!ok) why = "no pasteboard";
                } catch (UnsatisfiedLinkError e) {
                    ok = false;
                    why = "bridge natives not registered (runtime older than the framework jar?)";
                }
                a = ok ? 1 : 0;
                sAvail = a;
                if (!ok) {
                    android.util.Log.w(TAG, "macOS pasteboard bridge unavailable: " + why
                        + "; using the pre-bridge clipboard path");
                }
            }
            return a == 1;
        }

        static String ourPackage() {
            try {
                return android.app.ActivityThread.currentPackageName();
            } catch (Throwable t) {
                return "";
            }
        }

        /** AOSP clipboardAccessAllowed(OP_READ_CLIPBOARD): focused, or no read. */
        static boolean uidFocused() {
            if (!MACAPK_CLIPBOARD_FOCUS) return true;
            Boolean o = sMacApkFocusOverrideForTest;
            if (o != null) return o.booleanValue();
            try {
                return MacApkHostPasteboard.nativeHostFocused();
            } catch (UnsatisfiedLinkError e) {
                return true;
            }
        }

        // Denials logged so far. ClipboardService logs every one; logcat
        // absorbs that on a device, but here each line is a write to the
        // launcher's log, and an engine that asks "is there clipboard text?"
        // once per frame while the window is in the background (SDL's
        // SDL_HasClipboardText -> ClipboardManager.hasText) would write 60 a
        // second. So: the first few verbatim, then one per 1000 with a count.
        static final java.util.concurrent.atomic.AtomicLong sDenials =
            new java.util.concurrent.atomic.AtomicLong();

        static boolean readAllowed() {
            if (uidFocused()) return true;
            long n = sDenials.incrementAndGet();
            if (n <= 5 || n % 1000 == 0) {
                // ClipboardService.clipboardAccessAllowed, verbatim message.
                android.util.Log.e(TAG, "Denying clipboard access to " + ourPackage()
                    + ", application is not in focus nor is it a system service for user 0"
                    + (n > 5 ? " (" + n + " denials so far)" : ""));
            }
            return false;
        }

        // ---- state machine (all *Locked methods need LOCK) ----

        static void ensureInitLocked() {
            if (sInit) return;
            sInit = true;
            // The pasteboard as it stands at our first look is simply the
            // current clip: nothing has CHANGED, so nothing is reported.
            sKnownCount = MacApkHostPasteboard.nativeChangeCount();
            sOurs = false;
            sMaterialized = false;
            sClip = null;
            sSource = null;
            sNotified = false;
            sAdoptedAtMs = System.currentTimeMillis();
        }

        /** Another macOS app (or another Mac.apk app) changed the pasteboard:
         *  the service's primary clip is replaced and, as
         *  setPrimaryClipInternalNoClassifyLocked does, listeners hear of it. */
        static void adoptExternalLocked(long count, List<ClipboardManager> pending) {
            sKnownCount = count;
            sOurs = false;
            sMaterialized = false;
            sClip = null;
            sSource = null;
            sNotified = false;
            sAdoptedAtMs = System.currentTimeMillis();
            broadcastLocked(pending);
        }

        static void refreshLocked(List<ClipboardManager> pending) {
            ensureInitLocked();
            long c = MacApkHostPasteboard.nativeChangeCount();
            if (c >= 0 && c != sKnownCount) adoptExternalLocked(c, pending);
        }

        /** AOSP sendClipChangedBroadcast: every registered listener whose app
         *  may read the clipboard right now (i.e. is focused) is told. */
        static void broadcastLocked(List<ClipboardManager> pending) {
            if (sListening.isEmpty() || !uidFocused()) return;
            pending.addAll(sListening);
        }

        static void dispatch(List<ClipboardManager> pending) {
            for (ClipboardManager m : pending) m.postReport();
        }

        /** Build the service's clip for an outside change, reading content
         *  only now (the app asked for it). */
        static void materializeLocked(List<ClipboardManager> pending) {
            if (sMaterialized) return;
            long[] out = new long[2];
            String[] r = MacApkHostPasteboard.nativeRead(out);
            if (r == null) return;                         // transient; retry next call
            if (out[0] != sKnownCount) adoptExternalLocked(out[0], pending);
            sClip = fromHost(r, (int) out[1], sAdoptedAtMs);
            sSource = r[3];
            sMaterialized = true;
        }

        static boolean hasClipLocked() {
            if (sOurs || sMaterialized) return sClip != null;
            int t = MacApkHostPasteboard.nativeTypes();
            return t > 0 && (t & REPRESENTABLE) != 0;
        }

        static ClipData fromHost(String[] r, int types, long timestamp) {
            String text = r[0], html = r[1], url = r[2], source = r[3], label = r[4];
            boolean fromMacApk = source != null;
            ClipData.Item item;
            String[] mimes;
            if (html != null) {
                if (text == null) {
                    try { text = android.text.Html.fromHtml(html).toString(); }
                    catch (RuntimeException e) { text = null; }
                }
                item = new ClipData.Item(text, html);
                // A Mac copy that carries HTML (every Safari, Chrome and Notes
                // copy does) is ALSO plain text, and it must say so: AOSP's own
                // host bridge types every host clip text/plain
                // (EmulatorClipboardMonitor: `new String[]{"text/plain"}`), and
                // apps decide whether Paste is offered with
                // getPrimaryClipDescription().hasMimeType("text/plain") -- the
                // developer guide's pattern, and older Compose's
                // AndroidClipboardManager.hasText() (read from the Compose that
                // Subway Surf bundles). Typed text/html alone, a browser copy
                // would not be pasteable in those apps. So text/plain stays
                // PRIMARY (index 0, the emulator's type) and text/html is
                // offered beside it for an app that wants the markup.
                // A clip another Mac.apk guest wrote keeps text/html alone: the
                // only public factory for a text+HTML item is newHtmlText, and
                // that is the type a reader on a device sees for it.
                mimes = (text != null && !fromMacApk)
                    ? new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN,
                                     ClipDescription.MIMETYPE_TEXT_HTML }
                    : new String[] { ClipDescription.MIMETYPE_TEXT_HTML };
            } else if (text != null) {
                item = new ClipData.Item(text);
                mimes = new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN };
            } else if (url != null) {
                item = new ClipData.Item(android.net.Uri.parse(url));
                mimes = new String[] { ClipDescription.MIMETYPE_TEXT_URILIST };
            } else {
                return null;                               // nothing an Android app can hold
            }
            ClipDescription d = new ClipDescription(fromMacApk ? label : HOST_LABEL, mimes);
            android.os.PersistableBundle extras = null;
            if ((types & MacApkHostPasteboard.TYPE_CONCEALED) != 0) {
                extras = new android.os.PersistableBundle();
                extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            }
            if (!fromMacApk) {
                if (extras == null) extras = new android.os.PersistableBundle();
                extras.putBoolean(EXTRA_SUPPRESS_OVERLAY, true);
            }
            if (extras != null) d.setExtras(extras);
            d.setTimestamp(timestamp);
            return new ClipData(d, item);
        }

        // ---- the IClipboard calls ----

        static void setPrimaryClip(ClipData clip) {
            // ClipboardService.checkAndSetPrimaryClip.
            if (clip.getItemCount() <= 0) {
                throw new IllegalArgumentException("No items");
            }
            // The service works on its own (unparceled) copy and stamps the
            // timestamp on THAT copy; the app's object is left alone.
            ClipData copy = clip.macapkCopyForTransfer();
            ClipDescription desc = copy.getDescription();
            if (desc != null) desc.setTimestamp(System.currentTimeMillis());

            // What the other macOS apps get: the FIRST item, as AOSP's own
            // host bridge sends (EmulatorClipboardMonitor.getClipString uses
            // getItemAt(0)). Beyond it: the HTML, and a URI only when a Mac app
            // can open it (coerceToText's rule -- content:, file: and
            // android.resource: mean nothing outside this process).
            ClipData.Item first = copy.getItemAt(0);
            CharSequence t = first.getText();
            String text = t != null ? t.toString() : null;
            String html = first.getHtmlText();
            String url = null;
            android.net.Uri uri = first.getUri();
            if (uri != null) {
                String scheme = uri.getScheme();
                if (scheme != null && !ContentResolver.SCHEME_CONTENT.equals(scheme)
                        && !ContentResolver.SCHEME_FILE.equals(scheme)
                        && !ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) {
                    url = uri.toString();
                    if (text == null) text = url;
                }
            }
            android.os.PersistableBundle ex = desc != null ? desc.getExtras() : null;
            boolean sensitive = ex != null && ex.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false);
            CharSequence lbl = desc != null ? desc.getLabel() : null;
            String pkg = ourPackage();

            List<ClipboardManager> pending = new ArrayList<>();
            synchronized (LOCK) {
                // A Mac copy the poll has not seen yet still HAPPENED before
                // ours: report it first, as the eager host monitor would have.
                refreshLocked(pending);
                long c = MacApkHostPasteboard.nativeWrite(text, html, url, pkg,
                        lbl != null ? lbl.toString() : null, sensitive);
                // A refused write keeps the clip in-process (the count stays
                // put, so the next refresh still reports it as ours).
                if (c >= 0) sKnownCount = c;
                sOurs = true;
                sMaterialized = true;
                sClip = copy;
                sSource = pkg;
                sNotified = false;
                broadcastLocked(pending);
            }
            dispatch(pending);
        }

        static void clearPrimaryClip() {
            List<ClipboardManager> pending = new ArrayList<>();
            synchronized (LOCK) {
                refreshLocked(pending);
                boolean had = hasClipLocked();
                long c = MacApkHostPasteboard.nativeClear();
                if (c >= 0) sKnownCount = c;
                sOurs = true;
                sMaterialized = true;
                sClip = null;
                sSource = ourPackage();
                sNotified = false;
                // setPrimaryClipInternalNoClassifyLocked: `if (clip == null &&
                // clipboard.primaryClip == null) return;` -- clearing an
                // already-empty clipboard notifies nobody.
                if (had) broadcastLocked(pending);
            }
            dispatch(pending);
        }

        static ClipData getPrimaryClip(ClipboardManager caller) {
            if (!readAllowed()) return null;
            List<ClipboardManager> pending = new ArrayList<>();
            ClipData out = null;
            boolean notice = false;
            synchronized (LOCK) {
                refreshLocked(pending);
                materializeLocked(pending);
                if (sClip != null) {
                    // showAccessNotificationLocked: not for the clip's own app,
                    // and once per clip per app.
                    String me = ourPackage();
                    if (MACAPK_CLIPBOARD_ACCESS_NOTICE && !sNotified
                            && !(sSource != null && sSource.equals(me))) {
                        sNotified = true;
                        notice = true;
                    }
                    out = sClip.macapkCopyForTransfer();
                }
            }
            dispatch(pending);
            if (notice) showAccessNotification(caller);
            return out;
        }

        static ClipDescription getPrimaryClipDescription() {
            if (!readAllowed()) return null;
            List<ClipboardManager> pending = new ArrayList<>();
            ClipDescription out = null;
            synchronized (LOCK) {
                refreshLocked(pending);
                materializeLocked(pending);
                if (sClip != null && sClip.getDescription() != null) {
                    out = sClip.getDescription().macapkCopyForTransfer();
                }
            }
            dispatch(pending);
            return out;
        }

        static boolean hasPrimaryClip() {
            if (!readAllowed()) return false;
            List<ClipboardManager> pending = new ArrayList<>();
            boolean has;
            synchronized (LOCK) {
                refreshLocked(pending);
                has = hasClipLocked();
            }
            dispatch(pending);
            return has;
        }

        static boolean hasClipboardText() {
            if (!readAllowed()) return false;
            List<ClipboardManager> pending = new ArrayList<>();
            boolean has;
            synchronized (LOCK) {
                refreshLocked(pending);
                if (sOurs || sMaterialized) {
                    CharSequence t = (sClip != null && sClip.getItemCount() > 0)
                        ? sClip.getItemAt(0).getText() : null;
                    has = t != null && t.length() > 0;
                } else {
                    // Answered from the pasteboard's TYPES, without reading
                    // content (an HTML-only clip still yields text).
                    int types = MacApkHostPasteboard.nativeTypes();
                    has = types > 0 && (types & (MacApkHostPasteboard.TYPE_TEXT
                                                 | MacApkHostPasteboard.TYPE_HTML)) != 0;
                }
            }
            dispatch(pending);
            return has;
        }

        // ---- listeners ----

        /** Returns the managers to tell about a change settled on the way in;
         *  the caller dispatches them once it holds no lock (see
         *  addPrimaryClipChangedListener). */
        static List<ClipboardManager> addListener(ClipboardManager m) {
            List<ClipboardManager> pending = new ArrayList<>();
            synchronized (LOCK) {
                // Settle any change made while nobody listened BEFORE this
                // manager joins, so it is not told about a copy that predates it.
                refreshLocked(pending);
                sListening.add(m);
                if (sPoller == null) {
                    Thread t = new Thread(Service::pollLoop, "macapk-clipboard-poll");
                    t.setDaemon(true);
                    sPoller = t;
                    t.start();
                }
            }
            return pending;
        }

        static void removeListener(ClipboardManager m) {
            synchronized (LOCK) {
                sListening.remove(m);
                if (sListening.isEmpty() && sPoller != null) {
                    Thread t = sPoller;
                    sPoller = null;
                    t.interrupt();
                }
            }
        }

        static void pollLoop() {
            final Thread me = Thread.currentThread();
            for (;;) {
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException e) {
                    // removeListener stopped us, or will be re-checked below.
                }
                List<ClipboardManager> pending = new ArrayList<>();
                synchronized (LOCK) {
                    if (sPoller != me) return;
                    refreshLocked(pending);
                }
                dispatch(pending);
            }
        }

        // ---- Android 12+ access notice ----

        /** ClipboardService.showAccessNotificationLocked: log
         *  "<label> pasted from your clipboard" and show it as a short Toast.
         *  AOSP resolves the label with getApplicationLabel and gives up
         *  silently when it cannot; the window-title label the launcher passes
         *  (MACAPK_APP_LABEL) is the same name, used when no Application is up. */
        static void showAccessNotification(ClipboardManager caller) {
            android.app.Application app = null;
            String label = null;
            try {
                app = android.app.Application.macapk_get();
                if (app != null) {
                    CharSequence l = app.getPackageManager().getApplicationLabel(app.getApplicationInfo());
                    if (l != null && l.length() > 0) label = l.toString();
                }
            } catch (Throwable ignored) {
                label = null;
            }
            if (label == null) {
                String env = System.getenv("MACAPK_APP_LABEL");
                if (env != null && !env.isEmpty()) label = env;
            }
            if (label == null || label.isEmpty()) return;
            final String message = String.format(PASTED_FROM_CLIPBOARD, label);
            android.util.Log.i(TAG, message);
            final Context toastCtx = app != null ? app : caller.mContext;
            if (toastCtx == null || !android.os.Looper.macapkHasMainThread()) return;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    android.widget.Toast.makeText(toastCtx, message,
                            android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    android.util.Log.w(TAG, "access notice toast failed: " + t);
                }
            });
        }
    }

    // =====================================================================
    // The JNI seam to runtime/src/clipboard_bridge.mm. Registered by
    // macapk::clipboard::RegisterClipboardBridgeMethods; every descriptor here
    // must match that file's kClipboardMethods table exactly.
    // =====================================================================
    static final class MacApkHostPasteboard {
        private MacApkHostPasteboard() { }

        static final int TYPE_TEXT      = 1;   // public.utf8-plain-text
        static final int TYPE_HTML      = 2;   // public.html
        static final int TYPE_URL       = 4;   // public.url / public.file-url
        static final int TYPE_CONCEALED = 8;   // org.nspasteboard.ConcealedType
        static final int TYPE_MACAPK    = 16;  // written by a Mac.apk guest

        /** NSPasteboard.changeCount, or -1 with no pasteboard. Metadata only. */
        static native long nativeChangeCount();
        /** TYPE_* bitmask of what the pasteboard holds, or -1. No content read. */
        static native int nativeTypes();
        /** Replace the pasteboard with one item; returns the new changeCount or -1. */
        static native long nativeWrite(String text, String html, String url,
                                       String sourcePackage, String label, boolean sensitive);
        /** clearContents; returns the new changeCount or -1. */
        static native long nativeClear();
        /** {text, html, url, sourcePackage, label}; out = {changeCount, TYPE_* mask}. */
        static native String[] nativeRead(long[] countAndTypesOut);
        /** Is this process the active macOS app (the host's "window has focus")? */
        static native boolean nativeHostFocused();
        /** The pasteboard's name (a test asserts it is not the general one). */
        static native String nativePasteboardName();
    }

    // =====================================================================
    // THE PRE-BRIDGE PATH (MACAPK_CLIPBOARD_HOST=0). Bodies unchanged from
    // before the host bridge (only their names moved) except for the two
    // legacyStamp calls; see the header for why they exist.
    // =====================================================================

    /** The one addition to the pre-bridge bodies. ClipDescription used to
     *  stamp System.currentTimeMillis() at construction; AOSP's does not, and
     *  now neither does ours, so the clip this path stores is stamped here the
     *  way ClipboardService stamps the clip it stores. Only a clip still at 0,
     *  so an explicit timestamp survives. (Unlike the service, this path keeps
     *  the app's own object rather than a copy -- it always did -- so the stamp
     *  lands on that object, which is where the old constructor put it too.) */
    private static void legacyStamp(ClipData clip) {
        ClipDescription d = clip != null ? clip.getDescription() : null;
        if (d != null && d.getTimestamp() == 0) d.setTimestamp(System.currentTimeMillis());
    }

    /** The host pasteboard, resolved on first use. Returns null on any host
     *  that cannot hand one back (headless JVM, no display connection), in
     *  which case every bridge call below no-ops and the in-process clip is
     *  still fully AOSP-correct within a single app.
     *
     *  Benignly racy by design: two threads may both resolve, and the second
     *  simply overwrites with an equivalent handle. It is NOT synchronized on
     *  purpose -- holding a lock across `getDefaultToolkit()` is exactly the
     *  shape that deadlocked (see the field comment), so this must never add
     *  a monitor of its own around the blocking call. */
    private java.awt.datatransfer.Clipboard macapkHostClipboard() {
        if (!macapkHostClipboardResolved) {
            java.awt.datatransfer.Clipboard cb;
            try {
                cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            } catch (Throwable ignored) {
                cb = null;
            }
            // Do NOT let a failing resolve overwrite a good one. Two threads can
            // both enter here (Toolkit.getDefaultToolkit() is itself static
            // synchronized, so the second usually just blocks and returns the
            // cached toolkit) -- but if the second one throws for any reason, a
            // blind store would latch null and silently kill the host-pasteboard
            // bridge for the rest of the process even though it works.
            // Caught by an adversarial review, 2026-08-31.
            if (cb != null || macapkHostClipboard == null) macapkHostClipboard = cb;
            macapkHostClipboardResolved = true;
        }
        return macapkHostClipboard;
    }

    private void legacySetPrimaryClip(ClipData clip) {
        legacyStamp(clip);
        mPrimaryClip = clip;
        macapkPushToHostClipboard(clip);
        dispatchPrimaryClipChanged();
    }

    private void legacyClearPrimaryClip() {
        mPrimaryClip = null;
        macapkPushToHostClipboard(ClipData.newPlainText(null, ""));
        dispatchPrimaryClipChanged();
    }

    private ClipData legacyGetPrimaryClip() {
        macapkSyncFromHostClipboard();
        return mPrimaryClip;
    }

    private ClipDescription legacyGetPrimaryClipDescription() {
        macapkSyncFromHostClipboard();
        return mPrimaryClip != null ? mPrimaryClip.getDescription() : null;
    }

    private boolean legacyHasPrimaryClip() {
        macapkSyncFromHostClipboard();
        return mPrimaryClip != null;
    }

    private boolean legacyHasText() {
        CharSequence t = getText();
        return t != null && t.length() > 0;
    }

    private CharSequence legacyGetText() {
        macapkSyncFromHostClipboard();
        if (mPrimaryClip == null || mPrimaryClip.getItemCount() == 0) return null;
        ClipData.Item item = mPrimaryClip.getItemAt(0);
        return item != null ? item.coerceToText(null) : null;
    }

    private void legacyAddPrimaryClipChangedListener(OnPrimaryClipChangedListener what) {
        if (what != null && !mListeners.contains(what)) mListeners.add(what);
    }

    private void legacyRemovePrimaryClipChangedListener(OnPrimaryClipChangedListener what) {
        mListeners.remove(what);
    }

    private void dispatchPrimaryClipChanged() {
        // Copy to avoid CME if a listener mutates the list during dispatch.
        for (OnPrimaryClipChangedListener l : new ArrayList<>(mListeners)) {
            try { l.onPrimaryClipChanged(); } catch (Throwable ignored) {}
        }
    }

    /** Writes the clip's first text item to the host pasteboard, so a Mac
     *  app can paste what the guest just copied. Best-effort: any AWT
     *  failure leaves the in-process clip (already set by the caller)
     *  untouched. */
    private void macapkPushToHostClipboard(ClipData clip) {
        if (clip == null || clip.getItemCount() == 0) return;
        java.awt.datatransfer.Clipboard hostCb = macapkHostClipboard();
        if (hostCb == null) return;
        try {
            ClipData.Item item = clip.getItemAt(0);
            CharSequence text = item != null ? item.coerceToText(null) : null;
            if (text == null) return;
            String s = text.toString();
            macapkLastSyncedText = s;
            hostCb.setContents(new java.awt.datatransfer.StringSelection(s), null);
        } catch (Throwable ignored) {}
    }

    /** Pulls a NEW host-pasteboard text into the primary clip, so pasting
     *  inside the guest app sees what was just copied in a Mac app (Safari,
     *  Mail, Notes, ...). Skips when the pasteboard still holds exactly
     *  what we last wrote (our own copy echoing back, not a new one) or
     *  already matches the current primary clip's text, so this never
     *  fires a spurious listener callback for a no-op read. */
    private void macapkSyncFromHostClipboard() {
        java.awt.datatransfer.Clipboard hostCb = macapkHostClipboard();
        if (hostCb == null) return;
        try {
            java.awt.datatransfer.Transferable t = hostCb.getContents(null);
            if (t == null || !t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                return;
            }
            Object data = t.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
            if (!(data instanceof String)) return;
            String s = (String) data;
            if (s.equals(macapkLastSyncedText)) return;
            CharSequence current = null;
            if (mPrimaryClip != null && mPrimaryClip.getItemCount() > 0) {
                ClipData.Item it = mPrimaryClip.getItemAt(0);
                current = (it != null) ? it.coerceToText(null) : null;
            }
            if (current != null && s.contentEquals(current)) return;
            macapkLastSyncedText = s;
            mPrimaryClip = ClipData.newPlainText(null, s);
            legacyStamp(mPrimaryClip);
            dispatchPrimaryClipChanged();
        } catch (Throwable ignored) {}
    }
}
