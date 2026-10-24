package com.macapk.loader;

import android.app.Activity;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * HOST GAME CONTROLLERS -> Android gamepads (MACAPK_GAMEPAD, default ON;
 * 2026-10-02; docs/host-game-controllers_2026-10-02.md).
 *
 * The native half (runtime/src/gamecontroller_bridge.mm) watches macOS's
 * GameController.framework and calls the three static entry points below on
 * the AppKit main thread: a controller connected, disconnected, or changed
 * state. This class is everything Android's input stack does between the
 * kernel and the app for a HID gamepad, rung 1 of the decision ladder (macOS
 * already abstracts Xbox / PlayStation / Switch / MFi controllers, so we route
 * to it instead of reading HID ourselves):
 *
 *  - EventHub + InputReader (android14-release EventHub.cpp:2330-2452,
 *    InputDevice.cpp:469-515): the device. Gamepad buttons make it a
 *    KEYBOARD + GAMEPAD class device and its absolute axes a JOYSTICK, so its
 *    sources are KEYBOARD|GAMEPAD|JOYSTICK (0x01000511), its keyboard type
 *    NON_ALPHABETIC, and it gets the next free controller number. NOT
 *    SOURCE_DPAD: EventHub grants that only to a device that has DPAD
 *    UP/DOWN/LEFT/RIGHT/CENTER KEYS, and a modern controller's d-pad is a hat
 *    (ABS_HAT0X/Y) -- which is why it arrives as AXIS_HAT_X/Y below.
 *  - The key layout (data/keyboards/Vendor_045e_Product_02fd.kl for Xbox,
 *    Vendor_054c_Product_0ce6.kl for DualSense): which buttons are keys and
 *    which axes are which. Both agree on the standard map Android documents:
 *    left stick AXIS_X/Y, right stick AXIS_Z/RZ, triggers AXIS_LTRIGGER/
 *    RTRIGGER, d-pad AXIS_HAT_X/Y, buttons KEYCODE_BUTTON_A/B/X/Y/L1/R1/
 *    THUMBL/THUMBR/START/SELECT/MODE. Only the PlayStation layouts also map
 *    L2/R2 as keys (0x138/0x139), so those two keys are sent for a Sony
 *    controller (vendor 0x054c) only -- the same per-DEVICE data AOSP keeps in
 *    per-device .kl files; nothing here is per-APK.
 *  - KeyboardInputMapper: a button is a KeyEvent from the device, source
 *    KEYBOARD|GAMEPAD, with the Linux BTN_* scan code.
 *  - JoystickInputMapper (JoystickInputMapper.cpp): one ACTION_MOVE, source
 *    SOURCE_JOYSTICK, carrying EVERY axis, whenever one changes; centered
 *    axes -1..1 (Y down-positive), triggers 0..1, and the compat mirrors
 *    AXIS_BRAKE = LTRIGGER and AXIS_GAS = RTRIGGER (getCompatAxis :59-68).
 *  - InputDispatcher key repeat (InputDispatcher.cpp:1650-1688): the last key
 *    held repeats after ViewConfiguration.getKeyRepeatTimeout(), then every
 *    getKeyRepeatDelay(); repeat 1 carries FLAG_LONG_PRESS.
 *  - PhoneWindowManager.dispatchUnhandledKey (:3380-3453): a button the
 *    focused window does not handle falls back to the key its map declares
 *    (KeyCharacterMap.getFallbackAction): B -> BACK, A -> DPAD_CENTER, ...
 *  - ViewRootImpl.SyntheticJoystickHandler (:7534-7740): a joystick event the
 *    window does not handle becomes DPAD key presses (stick or hat past 0.5),
 *    with key repeat.
 *
 * Delivery follows the host keyboard exactly (TouchDispatcher.dispatchHostKey):
 * the focused window gets it, on the activity's loop thread unless the content
 * tree's input lives on the AppKit thread (TouchDispatcher.hostInputTree).
 *
 * =0 never starts the native watcher: no controller is ever registered, which
 * is the pre-change surface (getInputDeviceIds() has no gamepad).
 */
public final class GamepadBridge {
    private static final String TAG = "Mac.apk.Gamepad";

    public static final boolean ON = !"0".equals(System.getenv("MACAPK_GAMEPAD"));

    /** Starts GameController.framework observation (connect/disconnect + the
     *  controllers already connected). Idempotent; registered by
     *  gamecontroller_bridge.mm. */
    private static native void nativeStart();

    private static boolean sStarted;

    /** Called once by Boot after the touch dispatcher is registered. */
    public static synchronized void macapkStart() {
        if (sStarted) return;
        sStarted = true;
        if (!ON) {
            NativeLog.i(TAG, "MACAPK_GAMEPAD=0: host game controllers are not bridged");
            return;
        }
        try {
            nativeStart();
        } catch (Throwable t) {
            NativeLog.w(TAG, "host game controllers unavailable: " + t);
        }
    }

    // ---- the native contract (gamecontroller_bridge.mm keeps the same bits) ----
    static final int BTN_A = 1 << 0, BTN_B = 1 << 1, BTN_X = 1 << 2, BTN_Y = 1 << 3,
            BTN_L1 = 1 << 4, BTN_R1 = 1 << 5, BTN_L2 = 1 << 6, BTN_R2 = 1 << 7,
            BTN_THUMBL = 1 << 8, BTN_THUMBR = 1 << 9, BTN_START = 1 << 10,
            BTN_SELECT = 1 << 11, BTN_MODE = 1 << 12;
    static final int CAP_OPTIONS = 1, CAP_HOME = 2, CAP_THUMB_BUTTONS = 4;

    /** Bit -> key code -> Linux scan code, in bit order. Scan codes are the
     *  BTN_* the key layouts above map from (BTN_SOUTH 0x130 .. BTN_THUMBR
     *  0x13e; X is BTN_WEST 0x134 and Y BTN_NORTH 0x133, as in the DualSense
     *  layout and the kernel's positional naming). */
    private static final int[][] BUTTONS = {
        { BTN_A,      KeyEvent.KEYCODE_BUTTON_A,      0x130 },
        { BTN_B,      KeyEvent.KEYCODE_BUTTON_B,      0x131 },
        { BTN_X,      KeyEvent.KEYCODE_BUTTON_X,      0x134 },
        { BTN_Y,      KeyEvent.KEYCODE_BUTTON_Y,      0x133 },
        { BTN_L1,     KeyEvent.KEYCODE_BUTTON_L1,     0x136 },
        { BTN_R1,     KeyEvent.KEYCODE_BUTTON_R1,     0x137 },
        { BTN_L2,     KeyEvent.KEYCODE_BUTTON_L2,     0x138 },
        { BTN_R2,     KeyEvent.KEYCODE_BUTTON_R2,     0x139 },
        { BTN_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBL, 0x13d },
        { BTN_THUMBR, KeyEvent.KEYCODE_BUTTON_THUMBR, 0x13e },
        { BTN_START,  KeyEvent.KEYCODE_BUTTON_START,  0x13b },
        { BTN_SELECT, KeyEvent.KEYCODE_BUTTON_SELECT, 0x13a },
        { BTN_MODE,   KeyEvent.KEYCODE_BUTTON_MODE,   0x13c },
    };

    static final int SOURCE_KEYS = InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_GAMEPAD;
    static final int SOURCES = SOURCE_KEYS | InputDevice.SOURCE_JOYSTICK;

    // Axis slots in the state the native side sends (Android sign convention
    // already applied there: Y axes are down-positive).
    private static final int AX_X = 0, AX_Y = 1, AX_Z = 2, AX_RZ = 3,
            AX_LT = 4, AX_RT = 5, AX_HAT_X = 6, AX_HAT_Y = 7, AX_COUNT = 8;
    private static final int[] AXIS_IDS = {
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
    };

    static final class Pad {
        final long handle;
        final int deviceId;
        final int controllerNumber;
        /** The BTN_* bits this controller can produce. */
        final int buttonMask;
        int buttons;
        final float[] axes = new float[AX_COUNT];
        final long[] downTime = new long[BUTTONS.length];
        Pad(long handle, int deviceId, int controllerNumber, int buttonMask) {
            this.handle = handle; this.deviceId = deviceId;
            this.controllerNumber = controllerNumber; this.buttonMask = buttonMask;
        }
    }

    /** Connected controllers by native handle. Touched only on the AppKit
     *  thread (the native callbacks), but read by the repeat timer, so every
     *  access holds the class lock. */
    private static final java.util.HashMap<Long, Pad> sPads = new java.util.HashMap<>();
    private static int sLoggedEvents;
    private static final int LOG_CAP = 32;

    // ------------------------------------------------------------------
    // Native entry points (AppKit main thread)
    // ------------------------------------------------------------------

    /** A controller connected. `vendor`/`product` are the USB/Bluetooth ids
     *  read from the controller's IOHIDDevice, or 0 when the native side could
     *  not pair the controller with one (AOSP: "0 where a vendor id is not
     *  available"); `uniqueId` likewise (its serial number / address). */
    static int onConnected(long handle, String name, int vendor, int product,
                           String uniqueId, int caps) {
        final Pad pad;
        final InputDevice device;
        final int[] keys;
        synchronized (GamepadBridge.class) {
            Pad known = sPads.get(handle);
            if (known != null) return known.controllerNumber;
            int mask = BTN_A | BTN_B | BTN_X | BTN_Y | BTN_L1 | BTN_R1 | BTN_START;
            if ((caps & CAP_THUMB_BUTTONS) != 0) mask |= BTN_THUMBL | BTN_THUMBR;
            if ((caps & CAP_OPTIONS) != 0) mask |= BTN_SELECT;
            if ((caps & CAP_HOME) != 0) mask |= BTN_MODE;
            // PlayStation key layouts map the trigger clicks as keys too.
            if (vendor == 0x054c) mask |= BTN_L2 | BTN_R2;

            final int id = InputManager.macapkNextExternalDeviceId();
            final int number = nextControllerNumber();
            pad = new Pad(handle, id, number, mask);
            final String devName = (name == null || name.isEmpty()) ? "Gamepad" : name;
            device = new InputDevice(id, 0, number, devName, vendor, product,
                    descriptor(vendor, product, uniqueId, devName),
                    true,                                   // isExternal
                    SOURCES, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC,
                    KeyCharacterMap.load(id),
                    false,                                  // hasVibrator: rumble is not bridged
                    false, false);
            // JoystickInputMapper::populateDeviceInfo + the compat mirrors.
            // flat/fuzz 0: GameController applies its own dead zone and noise
            // filtering before we see a value, so a resting stick already
            // reads exactly 0 -- there is no residual band to describe.
            //
            // ORDER MATTERS: engines index axes by their position in
            // getMotionRanges() (libGDX's AndroidController builds its axis
            // array from it, and its default mapping says right stick = 2/3).
            // AOSP's order is JoystickInputMapper's mAxes, keyed by raw ABS code,
            // with each compat axis right after its axis; through the Xbox
            // layout (02fd.kl: 0x00 X, 0x01 Y, 0x02 Z, 0x05 RZ, 0x09 RTRIGGER,
            // 0x0a LTRIGGER, 0x10/0x11 HAT) that is the order below, which is
            // also the one Generic.kl gives a standard HID gamepad.
            device.addMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f);
            device.addMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f);
            device.addMotionRange(MotionEvent.AXIS_Z, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f);
            device.addMotionRange(MotionEvent.AXIS_RZ, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f);
            device.addMotionRange(MotionEvent.AXIS_RTRIGGER, InputDevice.SOURCE_JOYSTICK, 0f, 1f, 0f, 0f, 0f);
            device.addMotionRange(MotionEvent.AXIS_GAS, InputDevice.SOURCE_JOYSTICK, 0f, 1f, 0f, 0f, 0f);
            device.addMotionRange(MotionEvent.AXIS_LTRIGGER, InputDevice.SOURCE_JOYSTICK, 0f, 1f, 0f, 0f, 0f);
            device.addMotionRange(MotionEvent.AXIS_BRAKE, InputDevice.SOURCE_JOYSTICK, 0f, 1f, 0f, 0f, 0f);
            device.addMotionRange(MotionEvent.AXIS_HAT_X, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f);
            device.addMotionRange(MotionEvent.AXIS_HAT_Y, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f);

            int n = 0;
            int[] k = new int[BUTTONS.length];
            for (int[] b : BUTTONS) if ((mask & b[0]) != 0) k[n++] = b[1];
            keys = java.util.Arrays.copyOf(k, n);
            sPads.put(handle, pad);
        }
        InputManager.macapkAddExternalDevice(device, keys);
        NativeLog.i(TAG, "controller connected: id=" + pad.deviceId + " \"" + device.getName()
                + "\" vendor=0x" + Integer.toHexString(vendor) + " product=0x"
                + Integer.toHexString(product) + " controllerNumber=" + pad.controllerNumber
                + " keys=" + keys.length);
        // The native side lights the controller's player LED from this, as
        // EventHub's setLedForControllerLocked does on Linux.
        return pad.controllerNumber;
    }

    /** A controller disconnected. AOSP's reset on removal
     *  (KeyboardInputMapper::reset -> cancelAllDownKeys, JoystickInputMapper::
     *  reset -> a forced sync of zeroed axes) releases whatever was held
     *  before the device goes away, so an app never keeps a button stuck. */
    static void onDisconnected(long handle) {
        final Pad pad;
        synchronized (GamepadBridge.class) {
            pad = sPads.remove(handle);
            if (pad == null) return;
        }
        final long now = android.os.SystemClock.uptimeMillis();
        final java.util.ArrayList<KeyEvent> ups = new java.util.ArrayList<>();
        boolean moved = false;
        synchronized (GamepadBridge.class) {
            for (int i = 0; i < BUTTONS.length; i++) {
                if ((pad.buttons & BUTTONS[i][0]) == 0) continue;
                ups.add(new KeyEvent(pad.downTime[i], now, KeyEvent.ACTION_UP, BUTTONS[i][1], 0, 0,
                        pad.deviceId, BUTTONS[i][2],
                        KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_CANCELED, SOURCE_KEYS));
            }
            pad.buttons = 0;
            for (int i = 0; i < AX_COUNT; i++) {
                if (pad.axes[i] != 0f) { pad.axes[i] = 0f; moved = true; }
            }
            cancelRepeatFor(pad.deviceId);
        }
        final MotionEvent zero = moved ? joystickEvent(pad, now) : null;
        route(() -> {
            for (KeyEvent up : ups) dispatchKey(up);
            if (zero != null) dispatchJoystick(zero);
            Synthetic.cancel(pad.deviceId);
        });
        InputManager.macapkRemoveExternalDevice(pad.deviceId);
        NativeLog.i(TAG, "controller disconnected: id=" + pad.deviceId);
    }

    /** The controller's whole state after a change: the BTN_* bits held, and
     *  the eight axes in Android's convention. Keys first, then one joystick
     *  event, the order InputReader produces them in (EV_KEY is mapped as it
     *  arrives; the joystick motion is emitted at the SYN_REPORT). */
    static void onState(long handle, int buttons, float lx, float ly, float rx, float ry,
                        float lt, float rt, float hatX, float hatY) {
        onState(handle, buttons, lx, ly, rx, ry, lt, rt, hatX, hatY, true);
    }

    /** MACAPK_GAMEPAD_REAL_INPUT (default ON; =0 = the old path, where a button
     *  never counted): a button press on a PHYSICAL controller is user input,
     *  so it opens ActivityHost's real-input window that a cross-app or host
     *  hand-off needs (macapkLaunchAllowed), as a click or a host key does. On a
     *  device a gamepad key is an InputDispatcher event like any other, and the
     *  background-activity-launch rules treat the focused app as in use. A
     *  frontend driven only by a controller (iiSU, ES-DE on a handheld) had
     *  every emulator launch silently dropped. The test / virtual-pad entry
     *  (macapkTestState) passes real = false: a harness press must not count. */
    private static final boolean REAL_INPUT =
        !"0".equals(System.getenv("MACAPK_GAMEPAD_REAL_INPUT"));

    private static void onState(long handle, int buttons, float lx, float ly, float rx, float ry,
                        float lt, float rt, float hatX, float hatY, boolean real) {
        final long now = android.os.SystemClock.uptimeMillis();
        final java.util.ArrayList<android.view.InputEvent> out = new java.util.ArrayList<>(4);
        boolean stickMoved = false;
        float stickX = 0f, stickY = 0f;
        final Pad pad;
        synchronized (GamepadBridge.class) {
            pad = sPads.get(handle);
            if (pad == null) return;
            buttons &= pad.buttonMask;
            final int changed = pad.buttons ^ buttons;
            for (int i = 0; changed != 0 && i < BUTTONS.length; i++) {
                final int bit = BUTTONS[i][0];
                if ((changed & bit) == 0) continue;
                final boolean down = (buttons & bit) != 0;
                if (down) pad.downTime[i] = now;
                if (down && real && REAL_INPUT) TouchDispatcher.noteRealInputFromHostView();
                KeyEvent ev = new KeyEvent(pad.downTime[i], now,
                        down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, BUTTONS[i][1], 0, 0,
                        pad.deviceId, BUTTONS[i][2], KeyEvent.FLAG_FROM_SYSTEM, SOURCE_KEYS);
                out.add(ev);
                if (down) armRepeat(ev);
                else cancelRepeat(pad.deviceId, BUTTONS[i][1]);
            }
            pad.buttons = buttons;
            final float[] v = { lx, ly, rx, ry, lt, rt, hatX, hatY };
            boolean moved = false;
            for (int i = 0; i < AX_COUNT; i++) {
                // JoystickInputMapper::filterAxes with a filter of 0 (flat and
                // fuzz are 0, see onConnected): any change is significant.
                if (v[i] != pad.axes[i]) {
                    if (i == AX_X || i == AX_Y) stickMoved = true;
                    pad.axes[i] = v[i];
                    moved = true;
                }
            }
            if (moved) out.add(joystickEvent(pad, now));
            stickX = pad.axes[AX_X];
            stickY = pad.axes[AX_Y];
        }
        // Tilt Control (Controls menu): a real controller's LEFT STICK also
        // tilts the virtual device, the way the arrow keys do -- right = roll
        // right, pushed up (Android AXIS_Y negative) = pitch forward. Only when
        // the stick itself moved, so a button press cannot re-centre a tilt the
        // arrow keys are holding. The stick still reaches the app as a joystick
        // too; a tilt-steered game simply does not read one. Not the virtual
        // "Mac Arrow Keys" pad (it has no stick, and it excludes Tilt anyway).
        if (stickMoved && handle != ArrowPad.HANDLE && TiltControl.isEnabled()) {
            TiltControl.setStick(stickX, -stickY);
        }
        if (out.isEmpty()) return;
        if (sLoggedEvents < LOG_CAP) {
            sLoggedEvents++;
            StringBuilder sb = new StringBuilder("state id=").append(pad.deviceId);
            for (android.view.InputEvent e : out) {
                if (e instanceof KeyEvent k) {
                    sb.append(' ').append(KeyEvent.keyCodeToString(k.getKeyCode()))
                      .append(k.getAction() == KeyEvent.ACTION_DOWN ? "-down" : "-up");
                } else {
                    MotionEvent m = (MotionEvent) e;
                    sb.append(" joystick");
                    for (int i = 0; i < AX_COUNT; i++) {
                        float v = m.getAxisValue(AXIS_IDS[i]);
                        if (v != 0f) sb.append(' ').append(MotionEvent.axisToString(AXIS_IDS[i]))
                                       .append('=').append(v);
                    }
                }
            }
            NativeLog.i(TAG, sb.toString());
        }
        route(() -> {
            for (android.view.InputEvent e : out) {
                if (e instanceof KeyEvent k) dispatchKey(k);
                else dispatchJoystick((MotionEvent) e);
            }
        });
    }

    // ------------------------------------------------------------------
    // Device identity
    // ------------------------------------------------------------------

    /** AOSP EventHub::getNextControllerNumberLocked: the lowest number not in
     *  use, from 1 (mControllerNumbers.markFirstUnmarkedBit() + 1). */
    private static int nextControllerNumber() {
        int n = 1;
        boolean taken;
        do {
            taken = false;
            for (Pad p : sPads.values()) if (p.controllerNumber == n) { taken = true; n++; break; }
        } while (taken);
        return n;
    }

    /** AOSP EventHub generateDescriptor (EventHub.cpp:1443-1470) +
     *  assignDescriptorLocked's nonce: sha1(":vvvv:pppp:" [+ "uniqueId:" id]
     *  [+ "nonce:%04x"] [+ "name:" name when both ids are 0]). */
    private static String descriptor(int vendor, int product, String uniqueId, String name) {
        for (int nonce = 0; ; nonce++) {
            StringBuilder raw = new StringBuilder(String.format(":%04x:%04x:", vendor, product));
            if (uniqueId != null && !uniqueId.isEmpty()) raw.append("uniqueId:").append(uniqueId);
            if (nonce != 0) raw.append(String.format("nonce:%04x", nonce));
            if (vendor == 0 && product == 0) raw.append("name:").append(name);
            String d = sha1(raw.toString());
            boolean dup = false;
            for (int id : InputManager.getInstance().getInputDeviceIds()) {
                InputDevice o = InputManager.getInstance().getInputDevice(id);
                if (o != null && d.equals(o.getDescriptor())) { dup = true; break; }
            }
            if (!dup) return d;
        }
    }

    private static String sha1(String s) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(40);
            for (byte b : h) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "macapk:gamepad:" + s;
        }
    }

    private static MotionEvent joystickEvent(Pad pad, long now) {
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        for (int i = 0; i < AX_COUNT; i++) pc.setAxisValue(AXIS_IDS[i], pad.axes[i]);
        pc.setAxisValue(MotionEvent.AXIS_BRAKE, pad.axes[AX_LT]);
        pc.setAxisValue(MotionEvent.AXIS_GAS, pad.axes[AX_RT]);
        return MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1,
                new MotionEvent.PointerProperties[] { pp }, new MotionEvent.PointerCoords[] { pc },
                0, 0, 1f, 1f, pad.deviceId, 0, InputDevice.SOURCE_JOYSTICK, 0);
    }

    // ------------------------------------------------------------------
    // Threading: where the event is delivered
    // ------------------------------------------------------------------

    private static android.os.Handler sMain;
    private static synchronized android.os.Handler mainHandler() {
        if (sMain == null) sMain = new android.os.Handler(android.os.Looper.getMainLooper());
        return sMain;
    }

    /** The thread the focused window's input runs on: TouchDispatcher's
     *  routeToLoop rule, so a controller lands exactly where a host keystroke
     *  does. Called on the AppKit thread, `r` runs inline when the content
     *  tree's input belongs there; from a timer (the loop thread) it is handed
     *  to the AppKit thread's queue instead. */
    static void route(Runnable r) {
        final Activity active = ActivityHost.peek();
        boolean toLoop = false;
        if (android.os.Looper.MACAPK_REAL_LOOPER && active != null) {
            View root = active.macapkContentView();
            toLoop = root != null && !TouchDispatcher.hostInputTree(root);
        }
        if (toLoop) {
            if (android.os.Looper.macapkIsActivityThread()) r.run();
            else mainHandler().post(r);
        } else if (android.os.Looper.macapkIsActivityThread()) {
            android.os.Handler.enqueueGlThread(r);
        } else {
            r.run();
        }
    }

    // ------------------------------------------------------------------
    // Key delivery: the focused window, then the fallback key
    // ------------------------------------------------------------------

    /** The fallback each held key is producing (PhoneWindowManager
     *  mFallbackActions), keyed by device and key code. */
    private static final java.util.HashMap<Long, Integer> sFallbacks = new java.util.HashMap<>();

    private static long fbKey(int deviceId, int keyCode) {
        return ((long) deviceId << 32) | (keyCode & 0xffffffffL);
    }

    /** Deliver one gamepad key to the focused window; if it is unhandled and
     *  not itself a fallback, run PhoneWindowManager.dispatchUnhandledKey's
     *  fallback for it. Returns whether the key (or its fallback) was handled. */
    static boolean dispatchKey(KeyEvent ev) {
        final Activity active = ActivityHost.peek();
        boolean handled = deliverToFocusedWindow(active, ev);
        if (sLoggedDispatch < LOG_CAP) {
            sLoggedDispatch++;
            NativeLog.i(TAG, "  " + KeyEvent.keyCodeToString(ev.getKeyCode())
                    + (ev.getAction() == KeyEvent.ACTION_DOWN ? " down" : " up")
                    + ((ev.getFlags() & KeyEvent.FLAG_FALLBACK) != 0 ? " (fallback)" : "")
                    + " id=" + ev.getDeviceId() + " -> handled=" + handled + focusNote(active));
        }
        if ((ev.getFlags() & KeyEvent.FLAG_FALLBACK) != 0) return handled;

        final long key = fbKey(ev.getDeviceId(), ev.getKeyCode());
        final boolean initialDown = ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0;
        Integer fallback;
        synchronized (sFallbacks) {
            if (initialDown) {
                sFallbacks.remove(key);
                if (!handled) {
                    KeyCharacterMap.FallbackAction fa = ev.getKeyCharacterMap()
                            .getFallbackAction(ev.getKeyCode(), ev.getMetaState());
                    if (fa != null) sFallbacks.put(key, fa.keyCode);
                }
            }
            fallback = sFallbacks.get(key);
            if (fallback != null && ev.getAction() == KeyEvent.ACTION_UP) sFallbacks.remove(key);
        }
        if (fallback == null) return handled;
        int flags = ev.getFlags() | KeyEvent.FLAG_FALLBACK;
        if (handled) {
            // InputDispatcher afterKeyEventLockedInterruptable: the app took
            // the original key after its fallback started -- cancel the
            // fallback (an UP with FLAG_CANCELED) and stop producing it.
            synchronized (sFallbacks) { sFallbacks.remove(key); }
            if (ev.getAction() == KeyEvent.ACTION_UP) return true;
            flags |= KeyEvent.FLAG_CANCELED;
            deliverFallback(active, new KeyEvent(ev.getDownTime(), ev.getEventTime(),
                    KeyEvent.ACTION_UP, fallback, 0, ev.getMetaState(), ev.getDeviceId(),
                    ev.getScanCode(), flags, ev.getSource()));
            return true;
        }
        return deliverFallback(active, new KeyEvent(ev.getDownTime(), ev.getEventTime(),
                ev.getAction(), fallback, ev.getRepeatCount(), ev.getMetaState(),
                ev.getDeviceId(), ev.getScanCode(), flags, ev.getSource()));
    }

    /** HOME is the one fallback no app ever receives: PhoneWindowManager
     *  intercepts it before dispatch and goes to the launcher. On a Mac the
     *  controller's Home button already belongs to macOS (it opens the Games
     *  overlay / Launchpad), so the fallback is consumed here. Everything else,
     *  BACK included, takes the host keyboard's full key path
     *  (TouchDispatcher.dispatchKeyToFocusedWindow: predictive back, open
     *  dialogs and popups first, AppCompat's window callback). */
    private static boolean deliverFallback(Activity active, KeyEvent fb) {
        if (fb.getKeyCode() == KeyEvent.KEYCODE_HOME) return true;
        try {
            return TouchDispatcher.dispatchKeyToFocusedWindow(active, fb);
        } catch (Throwable t) {
            NativeLog.w(TAG, "fallback key threw: " + t);
            return false;
        }
    }

    /** The focused window's key path. A window whose input queue was taken
     *  (NativeActivity, and so every Unreal GameActivity) gets the event in
     *  its native AInputQueue first, exactly as a host key does
     *  (TouchDispatcher.deliverToTakenInputQueue, AOSP's NativePreIme /
     *  NativePostIme stages); what the native side finishes unhandled goes on
     *  to the activity's normal dispatch from there. That answer arrives
     *  asynchronously, so for such a window the fallback key and the
     *  synthetic d-pad stage below do not run -- the queue owns the event. */
    private static boolean deliverToFocusedWindow(Activity active, KeyEvent ev) {
        try {
            int ov = WindowOverlayStack.deliverKeyResult(ev);
            if (ov != WindowOverlayStack.OVERLAY_NONE) return ov == WindowOverlayStack.OVERLAY_HANDLED;
            if (active == null) return false;
            if (TouchDispatcher.deliverToTakenInputQueue(active, ev)) return true;   // see below
            android.view.Window w = active.getWindow();
            android.view.Window.Callback cb = w != null ? w.getCallback() : null;
            return cb != null ? cb.dispatchKeyEvent(ev) : active.dispatchKeyEvent(ev);
        } catch (Throwable t) {
            NativeLog.w(TAG, "dispatchKeyEvent threw: " + t);
            return false;
        }
    }

    /** A joystick event to the focused window (Activity.dispatchGenericMotionEvent
     *  -> the focused view chain -> onGenericMotionEvent); unhandled, it feeds
     *  the synthetic DPAD keys (ViewRootImpl.SyntheticInputStage). */
    static void dispatchJoystick(MotionEvent ev) {
        final Activity active = ActivityHost.peek();
        boolean handled = false;
        try {
            int ov = WindowOverlayStack.deliverGenericMotion(ev);
            if (ov != WindowOverlayStack.OVERLAY_NONE) {
                handled = ov == WindowOverlayStack.OVERLAY_HANDLED;
            } else if (active != null && TouchDispatcher.deliverToTakenInputQueue(active, ev)) {
                handled = true;   // see deliverToFocusedWindow
            } else if (active != null) {
                android.view.Window w = active.getWindow();
                android.view.Window.Callback cb = w != null ? w.getCallback() : null;
                handled = cb != null ? cb.dispatchGenericMotionEvent(ev)
                                     : active.dispatchGenericMotionEvent(ev);
            }
        } catch (Throwable t) {
            NativeLog.w(TAG, "dispatchGenericMotionEvent threw: " + t);
        }
        if (sLoggedDispatch < LOG_CAP) {
            sLoggedDispatch++;
            NativeLog.i(TAG, "  joystick id=" + ev.getDeviceId() + " -> handled=" + handled
                    + focusNote(active));
        }
        if (!handled) Synthetic.update(ev);
    }

    private static int sLoggedDispatch;

    /** Where a focused-window event went: the focused view of the activity
     *  window, the thing a "handled=false" most often comes down to. */
    private static String focusNote(Activity active) {
        try {
            if (active == null) return " (no activity)";
            View root = active.macapkContentView();
            View f = root != null ? root.findFocus() : null;
            return " focus=" + (f == null ? "none" : f.getClass().getName());
        } catch (Throwable t) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // Key repeat (InputDispatcher)
    // ------------------------------------------------------------------

    /** The one key InputDispatcher repeats: the most recent DOWN, until its UP
     *  (or a newer DOWN). */
    private static KeyEvent sRepeatKey;
    private static final Runnable sRepeatTick = new Runnable() {
        @Override public void run() {
            final KeyEvent next;
            synchronized (GamepadBridge.class) {
                if (sRepeatKey == null) return;
                long now = android.os.SystemClock.uptimeMillis();
                int rep = sRepeatKey.getRepeatCount() + 1;
                int flags = sRepeatKey.getFlags();
                flags = rep == 1 ? (flags | KeyEvent.FLAG_LONG_PRESS) : (flags & ~KeyEvent.FLAG_LONG_PRESS);
                next = KeyEvent.changeTimeRepeat(sRepeatKey, now, rep, flags);
                sRepeatKey = next;
            }
            mainHandler().postDelayed(this, android.view.ViewConfiguration.getKeyRepeatDelay());
            route(() -> dispatchKey(next));
        }
    };

    private static void armRepeat(KeyEvent down) {
        sRepeatKey = down;
        mainHandler().removeCallbacks(sRepeatTick);
        mainHandler().postDelayed(sRepeatTick, android.view.ViewConfiguration.getKeyRepeatTimeout());
    }

    private static void cancelRepeat(int deviceId, int keyCode) {
        if (sRepeatKey != null && sRepeatKey.getDeviceId() == deviceId
                && sRepeatKey.getKeyCode() == keyCode) {
            sRepeatKey = null;
            mainHandler().removeCallbacks(sRepeatTick);
        }
    }

    private static void cancelRepeatFor(int deviceId) {
        if (sRepeatKey != null && sRepeatKey.getDeviceId() == deviceId) {
            sRepeatKey = null;
            mainHandler().removeCallbacks(sRepeatTick);
        }
    }

    // ------------------------------------------------------------------
    // ViewRootImpl.SyntheticJoystickHandler: unhandled stick/hat -> DPAD keys
    // ------------------------------------------------------------------

    static final class Synthetic {
        private static final int UP_OR_LEFT = -1, NEUTRAL = 0, DOWN_OR_RIGHT = 1;
        /** Per device: {hatX, hatY, stickX, stickY} states. */
        private static final java.util.HashMap<Integer, int[]> sStates = new java.util.HashMap<>();
        /** Per device: the synthesized key currently held on each axis, so a
         *  repeat or a cancel knows what to send ({x, y}). */
        private static final java.util.HashMap<Integer, KeyEvent[]> sHeld = new java.util.HashMap<>();

        static synchronized void update(MotionEvent ev) {
            final long t = ev.getEventTime();
            axis(ev, t, MotionEvent.AXIS_X, ev.getAxisValue(MotionEvent.AXIS_X));
            axis(ev, t, MotionEvent.AXIS_Y, ev.getAxisValue(MotionEvent.AXIS_Y));
            axis(ev, t, MotionEvent.AXIS_HAT_X, ev.getAxisValue(MotionEvent.AXIS_HAT_X));
            axis(ev, t, MotionEvent.AXIS_HAT_Y, ev.getAxisValue(MotionEvent.AXIS_HAT_Y));
        }

        /** SyntheticJoystickHandler.cancel: release what is held. */
        static void cancel(int deviceId) {
            final java.util.ArrayList<KeyEvent> ups = new java.util.ArrayList<>(2);
            synchronized (Synthetic.class) {
                KeyEvent[] held = sHeld.remove(deviceId);
                sStates.remove(deviceId);
                if (held != null) {
                    for (int i = 0; i < 2; i++) {
                        if (held[i] == null) continue;
                        mainHandler().removeCallbacks(repeaters(deviceId)[i]);
                        long now = android.os.SystemClock.uptimeMillis();
                        ups.add(new KeyEvent(now, now, KeyEvent.ACTION_UP, held[i].getKeyCode(), 0,
                                held[i].getMetaState(), deviceId, 0,
                                KeyEvent.FLAG_FALLBACK | KeyEvent.FLAG_CANCELED, held[i].getSource()));
                    }
                }
            }
            for (KeyEvent up : ups) dispatchKey(up);
        }

        private static void axis(MotionEvent ev, long time, int axis, float value) {
            final boolean x = axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_HAT_X;
            final boolean stick = axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y;
            final int dev = ev.getDeviceId();
            int[] st = sStates.computeIfAbsent(dev, k -> new int[4]);
            KeyEvent[] held = sHeld.computeIfAbsent(dev, k -> new KeyEvent[2]);
            final int slot = (stick ? 2 : 0) + (x ? 0 : 1);
            final int axisIdx = x ? 0 : 1;
            final int newState = value >= 0.5f ? DOWN_OR_RIGHT : value <= -0.5f ? UP_OR_LEFT : NEUTRAL;
            final int cur = st[slot];
            if (cur == newState) return;
            final int meta = ev.getMetaState();
            final int source = ev.getSource();
            if (cur != NEUTRAL) {
                dispatchKey(new KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode(x, cur), 0, meta,
                        dev, 0, KeyEvent.FLAG_FALLBACK, source));
                held[axisIdx] = null;
                mainHandler().removeCallbacks(repeaters(dev)[axisIdx]);
            }
            if (newState != NEUTRAL) {
                KeyEvent down = new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode(x, newState), 0,
                        meta, dev, 0, KeyEvent.FLAG_FALLBACK, source);
                dispatchKey(down);
                held[axisIdx] = down;
                mainHandler().postDelayed(repeaters(dev)[axisIdx],
                        android.view.ViewConfiguration.getKeyRepeatTimeout());
            }
            st[slot] = newState;
        }

        private static int keyCode(boolean x, int state) {
            if (x) return state == UP_OR_LEFT ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
            return state == UP_OR_LEFT ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
        }

        /** MSG_ENQUEUE_X/Y_AXIS_KEY_REPEAT, one pair of timers per device. */
        private static final java.util.HashMap<Integer, Runnable[]> sRepeaters = new java.util.HashMap<>();
        private static synchronized Runnable[] repeaters(final int dev) {
            return sRepeaters.computeIfAbsent(dev, k -> new Runnable[] { repeater(dev, 0), repeater(dev, 1) });
        }
        private static Runnable repeater(final int dev, final int axisIdx) {
            return new Runnable() {
                @Override public void run() {
                    final KeyEvent next;
                    synchronized (Synthetic.class) {
                        KeyEvent[] held = sHeld.get(dev);
                        if (held == null || held[axisIdx] == null) return;
                        next = KeyEvent.changeTimeRepeat(held[axisIdx],
                                android.os.SystemClock.uptimeMillis(), held[axisIdx].getRepeatCount() + 1);
                        held[axisIdx] = next;
                    }
                    mainHandler().postDelayed(this, android.view.ViewConfiguration.getKeyRepeatDelay());
                    route(() -> dispatchKey(next));
                }
            };
        }
    }

    // ------------------------------------------------------------------
    // Test entry (harness inject driver / offline gate)
    // ------------------------------------------------------------------

    /** The same three calls the native side makes, for a run with no physical
     *  controller (debug/gamepad_gate.sh drives them). Not reachable from an
     *  app: com.macapk.loader is not on any app's compile classpath. */
    public static void macapkTestConnect(long handle, String name, int vendor, int product) {
        onConnected(handle, name, vendor, product, "", CAP_OPTIONS | CAP_HOME | CAP_THUMB_BUTTONS);
    }
    public static void macapkTestState(long handle, int buttons, float[] axes) {
        onState(handle, buttons, axes[0], axes[1], axes[2], axes[3], axes[4], axes[5], axes[6], axes[7],
                false);
    }
    public static void macapkTestDisconnect(long handle) { onDisconnected(handle); }

    /** A virtual controller driven from inside Mac.apk (ArrowPad, "Arrow Keys
     *  as D-pad"): registered and delivered exactly like a real one; its state
     *  goes through macapkTestState and its removal through
     *  macapkTestDisconnect. Not gated on MACAPK_GAMEPAD: it is a user choice
     *  of its own and touches no host controller. */
    public static void macapkVirtualConnect(long handle, String name) {
        onConnected(handle, name, 0, 0, "", 0);
    }
}
