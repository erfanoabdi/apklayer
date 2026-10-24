package com.macapk.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * "Space Bar Taps Center" (Controls menu): while it is on, the Space bar stops
 * being a keyboard key and becomes a finger held at the centre of the app's
 * surface. Down presses, up lifts, so a hold is a long-press and a tap is a tap.
 *
 * WHY. Many one-button games (tap to jump, tap to flap, tap to start) take
 * their only input as a touch anywhere on the screen, and the natural Mac
 * gesture for "the button" is Space. A key-binding profile can already do this,
 * but needs a JSON file per app; this is the same held-touch path
 * (TouchDispatcher.macapkKeyBindDown/Up) with a fixed point, offered as one
 * menu item. Generic: a user choice per app, default OFF, no per-APK rule.
 *
 * One of the Controls menu's input modes: turning it on turns Tilt Control,
 * Arrow Keys as D-pad and any key-binding profile off
 * (KeyBindProfiles.selectFromMenu).
 *
 * Thread model: keys arrive on the input thread, the menu toggles from the
 * AppKit main thread; sEnabled and sHeld are volatile, and the held pointer
 * itself is TouchDispatcher's (guarded by its class lock).
 */
public final class SpaceTap {
    private SpaceTap() {}

    private static final String TAG = "Mac.apk.SpaceTap";

    private static final int KEYCODE_SPACE = 62;

    private static volatile boolean sEnabled;
    private static volatile boolean sHeld;
    private static String sPackage;

    public static boolean isEnabled() { return sEnabled; }

    /** Turn it on or off and remember the choice for this app. */
    public static void setEnabled(boolean on) {
        // One input mode at a time (KeyBindProfiles.selectFromMenu).
        if (on && TiltControl.isEnabled()) TiltControl.setEnabled(false);
        if (on && ArrowPad.isEnabled()) ArrowPad.setEnabled(false);
        if (on && KeyBindProfiles.active() != null) KeyBindProfiles.setActive(null);
        if (!on) releaseKeys();
        sEnabled = on;
        NativeLog.i(TAG, "Space Bar Taps Center " + (on ? "ON" : "OFF")
            + (sPackage != null ? " for " + sPackage : ""));
        persist(on);
    }

    /** The Controls menu's action: flip, and report the new state. */
    public static boolean toggle() {
        setEnabled(!sEnabled);
        return sEnabled;
    }

    /** Read this app's remembered choice (Boot, after ArrowPad).
     *  MACAPK_SPACE_TAP=1/0 forces it for a harness run and is not persisted. */
    public static void loadForPackage(String pkg) {
        sPackage = pkg;
        String forced = System.getenv("MACAPK_SPACE_TAP");
        boolean on;
        if ("1".equals(forced)) on = true;
        else if ("0".equals(forced)) on = false;
        else on = pkg != null && "1".equals(store().getProperty(pkg));
        if (on && TiltControl.isEnabled()) TiltControl.setEnabled(false);
        if (on && ArrowPad.isEnabled()) ArrowPad.setEnabled(false);
        if (on && KeyBindProfiles.active() != null) KeyBindProfiles.setActive(null);
        sEnabled = on;
        if (on) NativeLog.i(TAG, "Space Bar Taps Center ON for " + pkg
            + ("1".equals(forced) ? " (MACAPK_SPACE_TAP=1)" : " (remembered)"));
    }

    /** Key path, called from TouchDispatcher.doDispatchKey in the same slot as
     *  Tilt Control and Arrow Keys as D-pad (after the text-input paths, so
     *  Space still types a space in a focused field). `w`/`h` are the surface
     *  size in pixels. Returns true when the key was consumed. Auto-repeat
     *  DOWNs are absorbed by macapkKeyBindDown, which is idempotent per key. */
    public static boolean handleKey(int action, int keyCode, int w, int h) {
        if (!sEnabled || keyCode != KEYCODE_SPACE) return false;
        if (action == 0 /* ACTION_DOWN */) {
            if (w <= 0 || h <= 0) return true;   // no surface yet: swallow, nothing to tap
            sHeld = true;
            TouchDispatcher.macapkKeyBindDown(KEYCODE_SPACE, w * 0.5f, h * 0.5f);
        } else if (action == 1 /* ACTION_UP */) {
            sHeld = false;
            TouchDispatcher.macapkKeyBindUp(KEYCODE_SPACE);
        }
        return true;
    }

    /** The window lost focus: AppKit will not deliver keyUp: for a Space still
     *  held, so lift the finger rather than leave it pressed. */
    public static void releaseKeys() {
        if (!sHeld) return;
        sHeld = false;
        TouchDispatcher.macapkKeyBindUp(KEYCODE_SPACE);
    }

    // ---- persistence (per app), the same shape as ArrowPad's ---------------

    private static File storeFile() {
        return new File(System.getProperty("user.home")
            + "/Library/Application Support/Mac.apk/space-tap.properties");
    }

    private static Properties store() {
        Properties p = new Properties();
        File f = storeFile();
        if (f.isFile()) {
            try (FileInputStream in = new FileInputStream(f)) { p.load(in); }
            catch (Throwable t) { NativeLog.w(TAG, "read " + f + ": " + t); }
        }
        return p;
    }

    private static void persist(boolean on) {
        String pkg = sPackage;
        if (pkg == null || "1".equals(System.getenv("MACAPK_SPACE_TAP_NO_PERSIST"))) return;
        synchronized (SpaceTap.class) {
            try {
                Properties p = store();
                if (on) p.setProperty(pkg, "1"); else p.remove(pkg);
                File f = storeFile();
                File dir = f.getParentFile();
                if (dir != null) dir.mkdirs();
                File tmp = new File(f.getPath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    p.store(out, "Mac.apk Space Bar Taps Center: packages with it turned on");
                }
                if (!tmp.renameTo(f)) {
                    f.delete();
                    tmp.renameTo(f);
                }
            } catch (Throwable t) {
                NativeLog.w(TAG, "persist: " + t);
            }
        }
    }
}
