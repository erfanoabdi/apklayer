package com.macapk.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Tilt Control: the ONE orientation state the motion sensors are derived from
 * when the user turns it on (Controls menu -> "Tilt Control").
 *
 * WHY THIS EXISTS. android.hardware.SensorManager advertises ACCELEROMETER,
 * GRAVITY, LINEAR_ACCELERATION, ROTATION_VECTOR, GAME_ROTATION_VECTOR,
 * ORIENTATION and GYROSCOPE, and with no motion hardware on a Mac it reports a
 * device at rest (the owner-approved 1g upright pose, SensorManager POSE_ACCEL_*).
 * A tilt-steered game therefore starts and cannot be steered. With Tilt Control
 * on, the keyboard becomes the transducer: the values are real user input, the
 * same way KeyBindProfiles turns keys into real touches for touch-only games.
 * docs/future_implementations.md "Keyboard-driven accelerometer" is the spec;
 * docs/tilt-control_2026-10-02.md records the design and the math.
 *
 * THE MODEL. Two angles, in the user's terms, applied on top of the resting
 * pose (screen upright, facing the user, leaned back ~4.8 degrees):
 *   roll  > 0  tilt RIGHT  (clockwise as the user sees the screen, right edge down)
 *   pitch > 0  tilt FORWARD (top edge away from the user)
 * Arrow keys set a TARGET while held (Left/Right = roll, Up/Down = pitch, both
 * axes at once for a diagonal); the CURRENT angle slews toward the target at
 * RAMP_DEG_PER_S and springs back to rest at RETURN_DEG_PER_S on release, so a
 * longer press is a larger tilt -- what a steering game needs from a digital key.
 *
 * OTHER TRANSDUCERS. setTarget / setPose are the public entry points for any
 * other input that can move the virtual device (a gamepad stick, a real gyro):
 * they write the SAME state, so every sensor stays coherent whatever drives it.
 *
 * Thread model: keys arrive on the loop thread, the sensor timer samples on its
 * own thread, the Controls menu toggles from the Cocoa main thread. Every
 * mutable field is guarded by the class lock; enabled is volatile so the hot
 * "is it on" check in SensorManager takes no lock.
 */
public final class TiltControl {
    private TiltControl() {}

    private static final String TAG = "Mac.apk.Tilt";

    /** The largest tilt either axis reaches, in degrees. 30 is a firm phone tilt:
     *  enough for full lock in a steering game, small enough that the device
     *  never approaches flat (where heading becomes degenerate). */
    public static final float MAX_TILT_DEG = 30f;
    /** Slew toward a held key's target: full tilt in 0.4 s. */
    public static final float RAMP_DEG_PER_S = 75f;
    /** Slew back toward rest (or toward a smaller target): twice as fast, the
     *  "spring back" of a released key. */
    public static final float RETURN_DEG_PER_S = 150f;

    // Android KeyEvent codes (AOSP KeyEvent.java).
    private static final int KEYCODE_DPAD_UP = 19;
    private static final int KEYCODE_DPAD_DOWN = 20;
    private static final int KEYCODE_DPAD_LEFT = 21;
    private static final int KEYCODE_DPAD_RIGHT = 22;

    private static final int HELD_LEFT = 1, HELD_RIGHT = 2, HELD_UP = 4, HELD_DOWN = 8;

    private static volatile boolean sEnabled;
    private static String sPackage;

    // Guarded by TiltControl.class.
    private static int sHeld;
    private static float sTargetRoll, sTargetPitch;
    private static float sRoll, sPitch;
    private static float sRollRate, sPitchRate;   // deg/s over the last step
    private static long sLastNanos;

    // ---- on / off ----------------------------------------------------------

    /** Hot path for SensorManager: no lock. */
    public static boolean isEnabled() { return sEnabled; }

    /** Turn Tilt Control on or off and remember the choice for this app.
     *  Turning it off returns the device to rest IMMEDIATELY, so the sensors
     *  are back to the exact at-rest values on the very next event. */
    public static void setEnabled(boolean on) {
        // The Controls menu is one group of input modes (KeyBindProfiles
        // .selectFromMenu): turning this on turns the others off.
        if (on && ArrowPad.isEnabled()) ArrowPad.setEnabled(false);
        if (on && SpaceTap.isEnabled()) SpaceTap.setEnabled(false);
        if (on && KeyBindProfiles.active() != null) KeyBindProfiles.setActive(null);
        synchronized (TiltControl.class) {
            sEnabled = on;
            resetLocked();
        }
        NativeLog.i(TAG, "Tilt Control " + (on ? "ON" : "OFF")
            + (sPackage != null ? " for " + sPackage : ""));
        persist(on);
    }

    /** The Controls menu's action: flip, and report the new state. */
    public static boolean toggle() {
        setEnabled(!sEnabled);
        return sEnabled;
    }

    /** Read this app's remembered choice. Called once from Boot, next to
     *  KeyBindProfiles.autoSelectForPackage. MACAPK_TILT_CONTROL=1/0 forces it
     *  (harness runs), and is not persisted. */
    public static void loadForPackage(String pkg) {
        sPackage = pkg;
        String forced = System.getenv("MACAPK_TILT_CONTROL");
        boolean on;
        if ("1".equals(forced)) on = true;
        else if ("0".equals(forced)) on = false;
        else on = pkg != null && "1".equals(store().getProperty(pkg));
        synchronized (TiltControl.class) {
            sEnabled = on;
            resetLocked();
        }
        // One input mode at a time: a remembered Tilt Control beats a key-binding
        // profile auto-selected for this package.
        if (on && KeyBindProfiles.active() != null) KeyBindProfiles.setActive(null);
        if (on) NativeLog.i(TAG, "Tilt Control ON for " + pkg
            + ("1".equals(forced) ? " (MACAPK_TILT_CONTROL=1)" : " (remembered)"));
    }

    // ---- inputs ------------------------------------------------------------

    /** Key path, called from TouchDispatcher.doDispatchKey AFTER the text-input
     *  paths and BEFORE key bindings and the guest. Returns true when the key
     *  was consumed (so the guest does not also see it). Only the four arrows
     *  are claimed, and only while Tilt Control is on. */
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
        synchronized (TiltControl.class) {
            advanceLocked(now());
            if (action == 0 /* ACTION_DOWN, incl. auto-repeat */) sHeld |= bit;
            else if (action == 1 /* ACTION_UP */) sHeld &= ~bit;
            else return true;
            // Opposite keys cancel, like a real stick centred between them.
            float r = 0f, p = 0f;
            if ((sHeld & HELD_RIGHT) != 0) r += MAX_TILT_DEG;
            if ((sHeld & HELD_LEFT) != 0)  r -= MAX_TILT_DEG;
            if ((sHeld & HELD_UP) != 0)    p += MAX_TILT_DEG;
            if ((sHeld & HELD_DOWN) != 0)  p -= MAX_TILT_DEG;
            sTargetRoll = r;
            sTargetPitch = p;
        }
        return true;
    }

    /** Release every held key (the host window lost focus, so AppKit will never
     *  deliver their keyUp:). The device springs back to rest. */
    public static void releaseKeys() {
        synchronized (TiltControl.class) {
            if (sHeld == 0) return;
            advanceLocked(now());
            sHeld = 0;
            sTargetRoll = 0f;
            sTargetPitch = 0f;
        }
    }

    /** Public entry point for another transducer (a gamepad stick): set where
     *  the device should tilt to, in degrees (roll > 0 = right, pitch > 0 =
     *  forward), clamped to +-MAX_TILT_DEG. The current pose slews there at the
     *  same rates the keys use. A no-op while Tilt Control is off. */
    public static void setTarget(float rollDeg, float pitchDeg) {
        if (!sEnabled) return;
        synchronized (TiltControl.class) {
            advanceLocked(now());
            sTargetRoll = clamp(rollDeg);
            sTargetPitch = clamp(pitchDeg);
        }
    }

    /** setTarget for a normalised stick: x, y in [-1, 1], x > 0 = right,
     *  y > 0 = forward (push the stick up). */
    public static void setStick(float x, float y) {
        setTarget(x * MAX_TILT_DEG, y * MAX_TILT_DEG);
    }

    /** Public entry point for an ABSOLUTE transducer (a real gyro / attitude
     *  source): jump straight to this pose, no slew. A no-op while off. */
    public static void setPose(float rollDeg, float pitchDeg) {
        if (!sEnabled) return;
        synchronized (TiltControl.class) {
            long t = now();
            advanceLocked(t);
            float r = clamp(rollDeg), p = clamp(pitchDeg);
            sRoll = r; sPitch = p;
            sTargetRoll = r; sTargetPitch = p;
            sRollRate = 0f; sPitchRate = 0f;
            sLastNanos = t;
        }
    }

    // ---- output ------------------------------------------------------------

    /** Advance the slew to nowNanos and copy out
     *  {roll deg, pitch deg, roll rate deg/s, pitch rate deg/s}. */
    public static void sample(long nowNanos, float[] out) {
        synchronized (TiltControl.class) {
            advanceLocked(nowNanos);
            out[0] = sRoll;
            out[1] = sPitch;
            out[2] = sRollRate;
            out[3] = sPitchRate;
        }
    }

    // ---- internals ---------------------------------------------------------

    private static long now() { return System.nanoTime(); }

    private static float clamp(float v) {
        if (v != v) return 0f;   // NaN from a misbehaving transducer
        return Math.max(-MAX_TILT_DEG, Math.min(MAX_TILT_DEG, v));
    }

    private static void resetLocked() {
        sHeld = 0;
        sTargetRoll = sTargetPitch = 0f;
        sRoll = sPitch = 0f;
        sRollRate = sPitchRate = 0f;
        sLastNanos = 0L;
    }

    /** Move the current angles toward the target for the time since the last
     *  step. The rate recorded is the one actually travelled, so the gyroscope
     *  reads zero once the pose has settled. */
    private static void advanceLocked(long t) {
        if (sLastNanos == 0L || t <= sLastNanos) {
            if (sLastNanos == 0L) sLastNanos = t;
            return;
        }
        float dt = (t - sLastNanos) / 1e9f;
        sLastNanos = t;
        float nr = slew(sRoll, sTargetRoll, dt);
        float np = slew(sPitch, sTargetPitch, dt);
        sRollRate = (nr - sRoll) / dt;
        sPitchRate = (np - sPitch) / dt;
        sRoll = nr;
        sPitch = np;
    }

    private static float slew(float cur, float target, float dt) {
        if (cur == target) return cur;
        // Moving away from rest (|target| beyond |cur| on the same side) ramps;
        // anything that brings the device back toward rest springs.
        boolean outward = Math.abs(target) > Math.abs(cur)
            && (cur == 0f || Math.signum(target) == Math.signum(cur));
        float step = (outward ? RAMP_DEG_PER_S : RETURN_DEG_PER_S) * dt;
        if (Math.abs(target - cur) <= step) return target;
        return cur + Math.copySign(step, target - cur);
    }

    // ---- persistence (per app) ----------------------------------------------

    private static File storeFile() {
        String home = System.getProperty("user.home");
        return new File(home + "/Library/Application Support/Mac.apk/tilt-control.properties");
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
        if (pkg == null || "1".equals(System.getenv("MACAPK_TILT_NO_PERSIST"))) return;
        synchronized (TiltControl.class) {
            try {
                Properties p = store();
                if (on) p.setProperty(pkg, "1"); else p.remove(pkg);
                File f = storeFile();
                File dir = f.getParentFile();
                if (dir != null) dir.mkdirs();
                // Write-then-rename so a crash mid-write cannot lose every app's choice.
                File tmp = new File(f.getPath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    p.store(out, "Mac.apk Tilt Control: packages with it turned on");
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
