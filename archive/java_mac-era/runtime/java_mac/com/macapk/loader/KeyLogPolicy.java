package com.macapk.loader;

import android.view.KeyEvent;

/** What the runtime log may say about a keystroke or about text the user typed.
 *
 *  WHY THIS EXISTS (2026-10-01, docs/key-log-redaction_2026-10-01.md). The
 *  runtime log is a file the user keeps (~/Library/Logs/macapk/app_<pkg>_<ver>.log)
 *  and attaches to bug reports. TouchDispatcher.doDispatchKey used to log
 *  `dispatchKey action= keyCode= unicode= meta=` for the first 64 keys of EVERY
 *  session, so the first characters a user typed -- a login form's password
 *  included -- landed in that file, and ViewCanvasDriver's checkpoint tree dump
 *  (frames 1/60/300, on by default) printed every EditText's raw contents. Real
 *  Android logs neither by default: InputDispatcher::notifyKey's per-key line is
 *  ALOGD_IF(debugInboundEventDetails(), ...) (android14-release
 *  services/inputflinger/dispatcher/InputDispatcher.cpp:4220), enabled only by
 *  "adb shell setprop log.tag.InputDispatcherInboundEvent DEBUG" (DebugConfig.h:32-38).
 *  MACAPK_TRACE_KEYS is our equivalent of that setprop.
 *
 *  THE RULE. The DEFAULT log carries no typed content:
 *    - a key that produces a character is logged as `key=<printable>` -- no
 *      keyCode (KEYCODE_A..Z and the digits name the letter by themselves), no
 *      unicode, and no meta (SHIFT would give away the case pattern);
 *    - a navigation / control / modifier / function key keeps its keyCode,
 *      unicode and meta -- they carry no text and are what a key-routing
 *      diagnosis needs (BACK, TAB, ENTER, the arrows, DEL, ESCAPE, ...);
 *    - a Ctrl or Meta chord keeps them too. AOSP decides that from the key
 *      character map: KeyCharacterMap::matchesMetaState (frameworks/native
 *      libs/input/KeyCharacterMap.cpp:442-470) requires a behaviour to match
 *      CTRL / ALT / META EXACTLY, and Generic.kcm defines no ctrl or meta
 *      behaviour that yields a character -- so a Ctrl/Meta chord types nothing.
 *      ALT does (Generic.kcm `key C { alt: 'ç' }`), so an Alt chord is
 *      redacted like any printing key;
 *    - editor text (an EditText's contents) is logged as `<redacted:N>`.
 *  The keyCode test is an ALLOW-list: a keyCode nobody listed is redacted, so a
 *  key we did not think of fails closed.
 *
 *  MACAPK_TRACE_KEYS=1 opts in to the full values again, for a key-routing
 *  diagnosis on a harness run -- but NEVER while the editor the keyboard types
 *  into is a password field (InputMethodManager.macapkPasswordTargetActive(),
 *  AOSP's own predicates), and never for a password TextView's text. */
public final class KeyLogPolicy {
    private KeyLogPolicy() {}

    static final String TAG = "Mac.apk.Touch";

    /** MACAPK_TRACE_KEYS=1: log full key values and editor text (password targets excepted). */
    static final boolean TRACE_KEYS = "1".equals(System.getenv("MACAPK_TRACE_KEYS"));

    /** The token a redacted printing key is logged as. Consumers grep for it. */
    public static final String REDACTED_KEY = "key=<printable>";

    // AOSP KeyEvent.META_CTRL_MASK / META_META_MASK (android/view/KeyEvent.java:1690-1698).
    private static final int META_CTRL_MASK =
        KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_RIGHT_ON;
    private static final int META_META_MASK =
        KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_RIGHT_ON;

    private static volatile boolean sBannerLogged;

    /** One loud line the first time MACAPK_TRACE_KEYS is consulted while ON, so a
     *  log that carries key values says why (debug/owner_issue_scan.py keys off it). */
    private static void banner() {
        if (sBannerLogged) return;
        sBannerLogged = true;
        NativeLog.w(TAG, "MACAPK_TRACE_KEYS=1: key values and editor text are logged"
            + " (never while a password field is the input target)");
    }

    /** True when a character code is TEXT: anything from SPACE up except DEL (0x7f).
     *  The same printable test doDispatchKey routes text with. */
    static boolean printable(int unicode) {
        return unicode >= 32 && unicode != 127;
    }

    /** True for a keyCode that types nothing on its own: navigation, editing
     *  control, modifiers, system and function keys. An allow-list -- see the
     *  class comment. */
    static boolean controlKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_UNKNOWN:
            case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_ALT_LEFT: case KeyEvent.KEYCODE_ALT_RIGHT:
            case KeyEvent.KEYCODE_SHIFT_LEFT: case KeyEvent.KEYCODE_SHIFT_RIGHT:
            case KeyEvent.KEYCODE_CTRL_LEFT: case KeyEvent.KEYCODE_CTRL_RIGHT:
            case KeyEvent.KEYCODE_META_LEFT: case KeyEvent.KEYCODE_META_RIGHT:
            case KeyEvent.KEYCODE_CAPS_LOCK: case KeyEvent.KEYCODE_SCROLL_LOCK:
            case KeyEvent.KEYCODE_NUM_LOCK: case KeyEvent.KEYCODE_FUNCTION:
            case KeyEvent.KEYCODE_TAB: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DEL: case KeyEvent.KEYCODE_FORWARD_DEL:
            case KeyEvent.KEYCODE_ESCAPE: case KeyEvent.KEYCODE_INSERT:
            case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MOVE_HOME: case KeyEvent.KEYCODE_MOVE_END:
            case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_SYSRQ: case KeyEvent.KEYCODE_BREAK:
            case KeyEvent.KEYCODE_APP_SWITCH:
                return true;
            default:
                // F1..F12 are contiguous (131..142).
                return keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12;
        }
    }

    /** True when the key's identity says nothing about what was typed. */
    static boolean identityIsSafe(int keyCode, int unicode, int meta) {
        if (printable(unicode)) return false;
        if (controlKey(keyCode)) return true;
        // A Ctrl / Meta chord types nothing (class comment, KeyCharacterMap.cpp).
        return (meta & (META_CTRL_MASK | META_META_MASK)) != 0;
    }

    /** Is the editor the keyboard types into a password field? Fails CLOSED: if
     *  the question cannot be answered, the answer is yes. Must be asked on the
     *  thread that delivers the key (see macapkPasswordTargetActive's THREAD note). */
    static boolean passwordTarget() {
        try { return android.view.inputmethod.InputMethodManager.macapkPasswordTargetActive(); }
        catch (Throwable t) { return true; }
    }

    /** The key fields for one log line, under the live policy. */
    public static String key(int keyCode, int unicode, int meta) {
        final boolean trace = TRACE_KEYS;
        if (trace) banner();
        return describeKey(keyCode, unicode, meta, trace, trace && passwordTarget());
    }

    /** Pure form of {@link #key}, for the offline gate. */
    static String describeKey(int keyCode, int unicode, int meta, boolean traceKeys, boolean password) {
        if ((traceKeys && !password) || identityIsSafe(keyCode, unicode, meta)) {
            return "keyCode=" + keyCode + " unicode=" + unicode + " meta=" + meta;
        }
        return REDACTED_KEY;
    }

    /** A TextView's text for a log line: labels verbatim, an EDITOR's contents
     *  (typed text) as `<redacted:N>` unless MACAPK_TRACE_KEYS=1, and a password
     *  field's contents redacted always -- including, under MACAPK_TRACE_KEYS=1,
     *  every editor's contents while a password field is the input target. */
    public static String viewText(android.widget.TextView tv, CharSequence text) {
        if (text == null || text.length() == 0) return "";
        boolean password = false, editor = false;
        try {
            password = tv.isAnyPasswordInputType() || tv.macapkHasPasswordTransformationMethod();
        } catch (Throwable t) { password = true; }
        if (!password) {
            try {
                editor = tv instanceof android.widget.EditText || tv.onCheckIsTextEditor()
                    || text instanceof android.text.Editable;
            } catch (Throwable t) { editor = true; }
        }
        if (!password && !editor) return text.toString();
        // The opt-in never reveals text while the editor the keyboard types into is a
        // password field, even when THIS view is not one: a custom PIN / OTP view marks
        // itself a password only in the EditorInfo its onCreateInputConnection fills in,
        // and macapkPasswordTargetActive() is the predicate that sees that.
        if (!password && TRACE_KEYS && !passwordTarget()) { banner(); return text.toString(); }
        return redactedText(text.length());
    }

    static String redactedText(int n) {
        return "<redacted:" + n + ">";
    }

    /** An inject-file / input-recording line for a log line. A recording's
     *  `keyev <action> <keyCode> <unicode> <meta>` line is the user's own
     *  keystroke (InputRecorder writes it), so its key fields go through
     *  {@link #key}; every other verb is harness-authored and logs verbatim. */
    public static String injectLine(String ln) {
        if (ln == null) return "null";
        final String[] p = ln.trim().split("\\s+");
        int v = (p.length > 0 && p[0].startsWith("+")) ? 1 : 0;
        if (p.length < v + 1 || !"keyev".equals(p[v])) return ln;
        try {
            if (p.length < v + 5) throw new NumberFormatException("short");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < v; i++) b.append(p[i]).append(' ');
            b.append("keyev ").append(p[v + 1]).append(' ')
             .append(key(Integer.parseInt(p[v + 2]), Integer.parseInt(p[v + 3]),
                         Integer.parseInt(p[v + 4])));
            return b.toString();
        } catch (RuntimeException e) {
            // Unparseable: it may still hold a key value, so say nothing about it.
            return (v == 1 ? p[0] + " " : "") + "keyev <unparsed>";
        }
    }
}
