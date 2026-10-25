package com.macapk.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * "Arrow Keys as D-pad" (Controls menu): while it is on, the Mac's arrow keys
 * stop being keyboard keys and become the d-pad of a virtual game controller,
 * "Mac Arrow Keys", registered through GamepadBridge exactly like a real
 * controller (an InputDevice with KEYBOARD|GAMEPAD|JOYSTICK, and the d-pad as
 * AXIS_HAT_X / AXIS_HAT_Y joystick motion, which is how a modern HID pad's
 * d-pad reaches an Android app).
 *
 * WHY. Some engines move a character only from a CONTROLLER and ignore the
 * keyboard. Among Us (Unity + Rewired) is the measured case: Rewired ignores a
 * Unity joystick whose name contains "keyboard" and its Android Keyboard scheme
 * binds no movement, so host arrow keys (a physical keyboard device since
 * v1.0.1666) cannot move the crewmate. Giving the arrows to a keyboard device
 * that ALSO advertises a d-pad makes Rewired flip its active controller between
 * keyboard and joystick on every press, resetting input each time (fast
 * direction changes stick). A separate, steady controller that owns the arrows
 * outright is what a player with a real pad has, and it is what this provides.
 * Generic: it is a user choice per app, not a per-APK rule, and default OFF.
 *
 * One of the Controls menu's input modes: turning it on turns Tilt Control,
 * Space Bar Taps Center and any key-binding profile off
 * (KeyBindProfiles.selectFromMenu).
 *
 * Thread model: keys arrive on the input thread (loop or AppKit, see
 * TouchDispatcher.routeToLoop), the menu toggles from the AppKit main thread;
 * state is guarded by the class lock, enabled is volatile.
 */
public final class ArrowPad {
    private ArrowPad() {}

    private static final String TAG = "Mac.apk.ArrowPad";

    /** The controller's name: deliberately NOT containing "keyboard", which
     *  Rewired filters out of its joystick list. */
    static final String DEVICE_NAME = "Mac Arrow Keys";
    /** GamepadBridge handle for the virtual pad. Real controllers' handles are
     *  native object addresses, never negative. */
    static final long HANDLE = -2L;

    private static final int KEYCODE_DPAD_UP = 19;
    private static final int KEYCODE_DPAD_DOWN = 20;
    private static final int KEYCODE_DPAD_LEFT = 21;
    private static final int KEYCODE_DPAD_RIGHT = 22;
    private static final int HELD_LEFT = 1, HELD_RIGHT = 2, HELD_UP = 4, HELD_DOWN = 8;

    private static volatile boolean sEnabled;
    private static String sPackage;
    private static int sHeld;          // guarded by ArrowPad.class
    private static boolean sConnected; // guarded by ArrowPad.class

    public static boolean isEnabled() { return sEnabled; }

    /** Turn it on or off and remember the choice for this app. On connects the
     *  virtual controller (apps hear onInputDeviceAdded), off disconnects it. */
    public static void setEnabled(boolean on) {
        // One input mode at a time (KeyBindProfiles.selectFromMenu).
        if (on && TiltControl.isEnabled()) TiltControl.setEnabled(false);
        if (on && SpaceTap.isEnabled()) SpaceTap.setEnabled(false);
        if (on && KeyBindProfiles.active() != null) KeyBindProfiles.setActive(null);
        apply(on);
        NativeLog.i(TAG, "Arrow Keys as D-pad " + (on ? "ON" : "OFF")
            + (sPackage != null ? " for " + sPackage : ""));
        persist(on);
    }

    /** The Controls menu's action: flip, and report the new state. */
    public static boolean toggle() {
        setEnabled(!sEnabled);
        return sEnabled;
    }

    /** Read this app's remembered choice (Boot, next to TiltControl).
     *  MACAPK_ARROW_PAD=1/0 forces it for a harness run and is not persisted. */
    public static void loadForPackage(String pkg) {
        sPackage = pkg;
        String forced = System.getenv("MACAPK_ARROW_PAD");
        boolean on;
        if ("1".equals(forced)) on = true;
        else if ("0".equals(forced)) on = false;
        else on = pkg != null && "1".equals(store().getProperty(pkg));
        if (on && TiltControl.isEnabled()) TiltControl.setEnabled(false);
        if (on && SpaceTap.isEnabled()) SpaceTap.setEnabled(false);
        if (on && KeyBindProfiles.active() != null) KeyBindProfiles.setActive(null);
        apply(on);
        if (on) NativeLog.i(TAG, "Arrow Keys as D-pad ON for " + pkg
            + ("1".equals(forced) ? " (MACAPK_ARROW_PAD=1)" : " (remembered)"));
    }

    private static void apply(boolean on) {
        boolean connect, disconnect;
        synchronized (ArrowPad.class) {
            sEnabled = on;
            sHeld = 0;
            connect = on && !sConnected;
            disconnect = !on && sConnected;
            sConnected = on;
        }
        if (connect) GamepadBridge.macapkVirtualConnect(HANDLE, DEVICE_NAME);
        if (disconnect) GamepadBridge.macapkTestDisconnect(HANDLE);
    }

    /** Key path, called from TouchDispatcher.doDispatchKey in Tilt Control's
     *  slot (after the text-input paths, before key bindings and the guest).
     *  Returns true when the key was consumed. Only the four arrows, and only
     *  while it is on. Auto-repeat DOWNs change nothing (the hat stays held). */
    public static boolean handleKey(int action, int keyCode) {
        if (!sEnabled) return false;
        int bit;
        switch (keyCode) {
            case KEYCODE_DPAD_LEFT:  bit = HELD_LEFT;  break;
            case KEYCODE_DPAD_RIGHT: bit = HELD_RIGHT; break;
            case KEYCODE_DPAD_UP:    bit = HELD_UP;    break;
            case KEYCODE_DPAD_DOWN:  bit = HELD_DOWN;  break;
            default: return false;
        }
        int held;
        synchronized (ArrowPad.class) {
            if (action == 0 /* ACTION_DOWN */) sHeld |= bit;
            else if (action == 1 /* ACTION_UP */) sHeld &= ~bit;
            else return true;
            held = sHeld;
        }
        send(held);
        return true;
    }

    /** The window lost focus: AppKit will not deliver keyUp: for an arrow
     *  still held, so centre the d-pad rather than leave it pressed. */
    public static void releaseKeys() {
        synchronized (ArrowPad.class) {
            if (sHeld == 0) return;
            sHeld = 0;
        }
        send(0);
    }

    /** Held arrows -> hat position. Opposite arrows cancel, like a real d-pad
     *  rocker that cannot report both; Android's hat Y is down-positive. */
    private static void send(int held) {
        float x = 0f, y = 0f;
        if ((held & HELD_LEFT) != 0)  x -= 1f;
        if ((held & HELD_RIGHT) != 0) x += 1f;
        if ((held & HELD_UP) != 0)    y -= 1f;
        if ((held & HELD_DOWN) != 0)  y += 1f;
        GamepadBridge.macapkTestState(HANDLE, 0, new float[] { 0f, 0f, 0f, 0f, 0f, 0f, x, y });
    }

    // ---- persistence (per app), the same shape as TiltControl's -------------

    private static File storeFile() {
        return new File(System.getProperty("user.home")
            + "/Library/Application Support/Mac.apk/arrow-pad.properties");
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
        if (pkg == null || "1".equals(System.getenv("MACAPK_ARROW_PAD_NO_PERSIST"))) return;
        synchronized (ArrowPad.class) {
            try {
                Properties p = store();
                if (on) p.setProperty(pkg, "1"); else p.remove(pkg);
                File f = storeFile();
                File dir = f.getParentFile();
                if (dir != null) dir.mkdirs();
                File tmp = new File(f.getPath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    p.store(out, "Mac.apk Arrow Keys as D-pad: packages with it turned on");
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
