package android.hardware.input;

// android.hardware.input.InputManager -- the SINGLE source of truth for which
// input devices exist. Mirrors AOSP's role split
// (frameworks/base/core/java/android/hardware/input/InputManager.java): the
// manager owns the device registry, and android.view.InputDevice's statics are
// nothing but delegations to it (InputDevice.java:482-492).
//
// That split was broken here until 2026-08-09. InputManager answered [-1] while
// android.view.InputDevice.getDeviceIds() had a hardcoded `return new int[0]`
// and getDevice(id) manufactured a device for ANY id -- and since apps call
// android.view.InputDevice, the manager's (correct) answer was unreachable.
// The framework-answer differential measured the result: every single
// InputDevice capability question answered 0 while the emulator answered 1.
// Do not reintroduce a second device table anywhere.
//
// Unity also registers a bitter.jnibridge java.lang.reflect.Proxy implementing
// InputDeviceListener during native bringup; without the nested interface type,
// Proxy.newProxyInstance throws ClassNotFound and aborts the bringup before the
// render Choreographer FrameCallback is armed (the Crossy black-screen gate).
public final class InputManager {

    public interface InputDeviceListener {
        void onInputDeviceAdded(int deviceId);
        void onInputDeviceRemoved(int deviceId);
        void onInputDeviceChanged(int deviceId);
    }

    public InputManager() {}

    /** AOSP InputManager.getInstance(): the process-wide instance. Every piece
     *  of state below is static, so the instance returned here and the one
     *  Context.getSystemService(INPUT_SERVICE) hands an app see exactly the
     *  same device registry -- which is the property that was missing. */
    private static InputManager sInstance;
    public static InputManager getInstance() {
        synchronized (InputManager.class) {
            if (sInstance == null) sInstance = new InputManager();
            return sInstance;
        }
    }

    /** AOSP InputManagerGlobal.registerInputDeviceListener (android14-release
     *  :328-338): one delegate per listener (a second registration of the same
     *  listener is ignored), whose Handler runs on `handler`'s looper, or the
     *  caller's own looper when `handler` is null (InputDeviceListenerDelegate
     *  :286-289). Added/removed fire for the HOST GAME CONTROLLERS
     *  (com.macapk.loader.GamepadBridge) -- the only devices that come and go.
     *
     *  Was a no-op while the device set was fixed. Two deliberate differences:
     *  - A caller with no looper and no handler gets the MAIN looper. AOSP
     *    would throw from `new Handler(null)`; on our threading a framework
     *    caller can land on a looper-less thread where Android had one
     *    (Unity's jnibridge proxy registers during native bringup), and a
     *    throw there would abort the bringup, which the old no-op never did.
     *  - The touchscreen's axis bounds follow the window (see the rebuild
     *    below), which on real Android would be an onInputDeviceChanged; that
     *    is still not fired: no Mac.apk app has been observed to re-query on
     *    it, and it would run app callbacks from whatever thread resized the
     *    window. */
    public void registerInputDeviceListener(InputDeviceListener listener,
                                            android.os.Handler handler) {
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        android.os.Looper looper = handler != null ? handler.getLooper() : android.os.Looper.myLooper();
        if (looper == null) looper = android.os.Looper.getMainLooper();
        synchronized (sListeners) {
            for (ListenerDelegate d : sListeners) if (d.listener == listener) return;
            sListeners.add(new ListenerDelegate(listener, new android.os.Handler(looper)));
        }
    }

    public void unregisterInputDeviceListener(InputDeviceListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        synchronized (sListeners) {
            for (int i = 0; i < sListeners.size(); i++) {
                if (sListeners.get(i).listener == listener) { sListeners.remove(i); return; }
            }
        }
    }

    private static final class ListenerDelegate {
        final InputDeviceListener listener;
        final android.os.Handler handler;
        ListenerDelegate(InputDeviceListener l, android.os.Handler h) { listener = l; handler = h; }
    }
    private static final java.util.ArrayList<ListenerDelegate> sListeners = new java.util.ArrayList<>();

    private static final int MSG_DEVICE_ADDED = 1;
    private static final int MSG_DEVICE_REMOVED = 2;

    /** AOSP InputManagerGlobal.onInputDevicesChanged -> sendMessage on every
     *  delegate's handler: each listener hears about the change on its own
     *  looper, never on the thread that discovered it. */
    private static void notifyListeners(final int what, final int deviceId) {
        java.util.ArrayList<ListenerDelegate> snap;
        synchronized (sListeners) { snap = new java.util.ArrayList<>(sListeners); }
        for (final ListenerDelegate d : snap) {
            d.handler.post(() -> {
                if (what == MSG_DEVICE_ADDED) d.listener.onInputDeviceAdded(deviceId);
                else d.listener.onInputDeviceRemoved(deviceId);
            });
        }
    }

    // ------------------------------------------------------------------
    // The device registry
    // ------------------------------------------------------------------
    //
    // THE RULE THIS TABLE OBEYS: advertise exactly what Mac.apk actually
    // delivers, and nothing more. Claiming a capability we do not deliver is
    // strictly WORSE than claiming nothing, because an app that believes it
    // then enables a control path that receives no events, with no fallback.
    //
    // What we deliver, and therefore what is listed:
    //
    //  * A TOUCHSCREEN. Every MotionEvent Mac.apk synthesizes carries
    //    InputDevice.SOURCE_TOUCHSCREEN (android.view.MotionEvent's `source`
    //    field default) and TOOL_TYPE_FINGER, from the host mouse/trackpad via
    //    cocoa_bridge -> TouchDispatcher; multi-pointer works through
    //    TouchDispatcher's held-pointer slots. Real X/Y in window pixels and a
    //    pressure of 1.0, so those three axes get MotionRanges and nothing else
    //    does (see the axis note in buildDevices).
    //
    //  * A VIRTUAL KEYBOARD at id KeyCharacterMap.VIRTUAL_KEYBOARD (-1), the id
    //    AOSP reserves for the platform's own virtual keyboard and the id our
    //    KeyEvents already report. Host keystrokes really do arrive as Android
    //    KeyEvents with real KEYCODE_* values (cocoa_bridge
    //    MacKeyEventToAndroid -> TouchDispatcher.dispatchKey ->
    //    Activity.dispatchKeyEvent), including KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT
    //    from the host arrow keys -- which is why the mask carries SOURCE_DPAD
    //    as well as SOURCE_KEYBOARD, exactly like AOSP's own "Virtual" device.
    //    It reports isVirtual() == true (AOSP: id < 0). Since 2026-10-01 host
    //    keystrokes no longer come from it (MACAPK_HOST_KEYBOARD below): they come
    //    from
    //
    //  * the HOST KEYBOARD at id 2 -- a physical, external, alphabetic keyboard,
    //    which is what the Mac's keyboard is, so the correct "physical keyboard
    //    attached" test now answers true, and Configuration says so too
    //    (KEYBOARD_QWERTY / HARDKEYBOARDHIDDEN_NO, as AOSP derives it).
    //
    // What we deliberately do NOT list:
    //
    //  * A MOUSE. The host mouse is delivered as single-pointer TOUCH: there
    //    are no SOURCE_MOUSE MotionEvents, no button state, no ACTION_SCROLL
    //    and no relative motion. Claiming SOURCE_MOUSE would make a game enable
    //    mouse-look controls that receive nothing. See
    //    docs/keyboard-mouse-passthrough_2026-07-10.md for the remaining steps
    //    (that work also has to touch MotionEvent/cocoa_bridge). The reference
    //    emulator has no mouse device either.
    //
    //  * A STYLUS. We never emit TOOL_TYPE_STYLUS, tilt or distance, so the
    //    touchscreen's mask is SOURCE_TOUCHSCREEN alone and not the
    //    TOUCHSCREEN|STYLUS a goldfish multi-touch device reports.
    //
    //  * A DPAD-as-a-separate-device / ROTARY_ENCODER / TRACKBALL / TOUCHPAD.
    //    Nothing delivers any of them.
    //
    // What comes and goes (2026-10-02, MACAPK_GAMEPAD):
    //
    //  * HOST GAME CONTROLLERS. Each controller macOS's GameController.framework
    //    reports is registered here while it is connected
    //    (com.macapk.loader.GamepadBridge builds the InputDevice the way
    //    InputReader does for a HID gamepad), at a fresh id from
    //    macapkNextExternalDeviceId(); listeners hear onInputDeviceAdded /
    //    onInputDeviceRemoved. Ids are never reused within a run, as EventHub's
    //    mNextDeviceId never reuses one.
    //
    // Device ids. The keyboard must stay at -1: it is what our KeyEvents carry
    // and what makes isVirtual() true. The touchscreen must NOT be 0 -- AOSP's
    // InputEvent.getDeviceId() contract is that "an id of zero indicates that
    // the event didn't come from a physical device", so 0 is reserved and
    // getDevice(0) must answer null. 1 is the lowest id that is neither.
    private static final int TOUCHSCREEN_ID = 1;

    /** THE HOST KEYBOARD (MACAPK_HOST_KEYBOARD, default ON; amongus-key-tap-move
     *  lane, 2026-10-01; docs/host-keyboard-device_2026-10-01.md).
     *
     *  The Mac's keyboard is a PHYSICAL keyboard, and on Android a physical
     *  keyboard's keystrokes never carry the virtual keyboard's id. AOSP reserves
     *  -1 for "a generic virtual keyboard with a full layout that can be used to
     *  synthesize key events" (KeyCharacterMap.VIRTUAL_KEYBOARD) -- the IME's and
     *  `input keyevent`'s device -- while InputReader stamps a hardware key with
     *  the id of the device it was read from (KeyboardInputMapper::processKey,
     *  getDeviceId()). Mac.apk stamped every host keystroke -1, i.e. reported
     *  "no hardware keyboard" (the Virtual device is skipped by
     *  DisplayContent.computeScreenConfiguration, "Ignore virtual input device")
     *  while delivering hardware key traffic.
     *
     *  That contradiction is visible to engines. Measured on Among Us (Unity
     *  2022.3 + Rewired): libunity attaches a JOYSTICK for the device of every
     *  DPAD key it sees (FUN_006cb3f0 -> FUN_006cad64, named after the device:
     *  "Virtual"), and on our id -1 that joystick re-"connected" on EVERY arrow
     *  press -- Rewired "New controller connected, updating current input
     *  state", the game flipping its control scheme between the keyboard and a
     *  phantom gamepad per keystroke. HYPOTHESIS (UNVERIFIED): that flip is why a
     *  short key press did not move the crewmate while a held key did -- the
     *  owner's session shows one reconnect per press group, but no harness
     *  keystroke could move the crewmate under EITHER surface, so that a reconnect
     *  eats a tap is not proven. With the keystrokes on a real keyboard device:
     *  0 reconnects (measured). Falsifier: on a promoted build a ~100 ms arrow/WASD
     *  tap in Freeplay moves the crewmate; if not, A/B with MACAPK_HOST_KEYBOARD=0.
     *
     *  The device is what EventHub/InputReader build for an ordinary USB or
     *  Bluetooth keyboard: KEYBOARD + ALPHAKEY classes, so SOURCE_KEYBOARD and
     *  KEYBOARD_TYPE_ALPHABETIC; NOT SOURCE_DPAD, because EventHub grants the
     *  DPAD class only to a device that also has DPAD_CENTER (scan 353,
     *  KEY_SELECT -- android14 EventHub.cpp:2394-2401). We CHOSE the minimal
     *  keyboard without KEY_SELECT (SOURCE_KEYBOARD, 0x101), so its arrows arrive
     *  as KEYCODE_DPAD_* from a SOURCE_KEYBOARD device exactly as such a USB
     *  keyboard's do. A 0x301 keyboard (one whose HID descriptor advertises
     *  KEY_SELECT, as many consumer-control keyboards do) would put SOURCE_DPAD
     *  back on host arrows, which is the bit libunity's joystick attach keys on. External (a keyboard attached to
     *  the phone Mac.apk presents), id 2: ids 0 and 1 are taken (0 = "not from a
     *  physical device", 1 = the touchscreen). =0 restores the pre-change
     *  surface byte for byte: no such device, keystrokes from -1 with
     *  KEYBOARD|DPAD, Configuration KEYBOARD_NOKEYS / HARDKEYBOARDHIDDEN_YES. */
    public static final boolean MACAPK_HOST_KEYBOARD =
            !"0".equals(System.getenv("MACAPK_HOST_KEYBOARD"));
    /** The host keyboard's device id (see above). */
    public static final int HOST_KEYBOARD_ID = 2;

    /** The device id a host keystroke carries: the host keyboard, or (=0) the
     *  virtual keyboard it used to be stamped with. */
    public static int macapkHostKeyDeviceId() {
        return MACAPK_HOST_KEYBOARD ? HOST_KEYBOARD_ID : android.view.KeyCharacterMap.VIRTUAL_KEYBOARD;
    }

    /** The InputDevice.SOURCE_* a host keystroke carries -- the source mask of
     *  the device it comes from (KeyboardInputMapper applies its mSource to
     *  every key): SOURCE_KEYBOARD for the host keyboard, KEYBOARD|DPAD for the
     *  virtual keyboard under =0. */
    public static int macapkHostKeySource() {
        return MACAPK_HOST_KEYBOARD ? android.view.InputDevice.SOURCE_KEYBOARD
                : android.view.InputDevice.SOURCE_KEYBOARD | android.view.InputDevice.SOURCE_DPAD;
    }

    private static final Object sDeviceLock = new Object();
    private static int[] sIds;
    private static android.view.InputDevice[] sDevices;
    private static int sBuiltWidth;
    private static int sBuiltHeight;

    /** Build (or rebuild) the registry. The touchscreen's X/Y MotionRanges are
     *  the window's pixel bounds, which change on an orientation flip, so the
     *  cached devices are invalidated when the extent moves. */
    private static void ensureDevices() {
        final int[] extent = displayExtent();
        synchronized (sDeviceLock) {
            if (sDevices != null && extent[0] == sBuiltWidth && extent[1] == sBuiltHeight) return;
            buildDevices(extent[0], extent[1]);
            sBuiltWidth = extent[0];
            sBuiltHeight = extent[1];
        }
    }

    private static void buildDevices(int width, int height) {
        final android.view.InputDevice keyboard = new android.view.InputDevice(
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,                       // generation
                0,                       // controllerNumber: not a game controller
                "Virtual",               // AOSP names its own virtual keyboard device this
                0,                       // vendorId: AOSP uses 0 when unavailable
                0,                       // productId
                "macapk:virtual-keyboard",
                false,                   // isExternal: built in, not user-attached
                android.view.InputDevice.SOURCE_KEYBOARD | android.view.InputDevice.SOURCE_DPAD,
                android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                android.view.KeyCharacterMap.load(
                        android.view.KeyCharacterMap.VIRTUAL_KEYBOARD),
                false,                   // hasVibrator
                false,                   // hasMicrophone
                false);                  // hasButtonUnderPad

        final android.view.InputDevice touchscreen = new android.view.InputDevice(
                TOUCHSCREEN_ID,
                0,
                0,
                "macapk-touchscreen",
                0,
                0,
                "macapk:touchscreen",
                false,
                android.view.InputDevice.SOURCE_TOUCHSCREEN,
                android.view.InputDevice.KEYBOARD_TYPE_NONE,
                android.view.KeyCharacterMap.load(TOUCHSCREEN_ID),
                false,
                false,
                false);

        // Axes. ONLY the three we actually put in a MotionEvent get a range.
        //
        // ⚠ This used to be justified by a GUESS ("worse than the clean null
        // AOSP returns"). The actual rule is TouchInputMapper::resolveCalibration
        // (frameworks/native/services/inputflinger/reader/mapper/TouchInputMapper.cpp
        // :1201-1227): an axis is published IFF the kernel device reports the
        // corresponding ABS code. The reference's nine decompose exactly as
        // three always-on, five more from ABS_MT_TOUCH_MAJOR and one from
        // ABS_MT_ORIENTATION, which QEMU's virtio-input-multi-touch.c does
        // declare. A synthesized Cocoa pointer declares none of them, so THREE
        // is the correct output of AOSP's own rule applied to our hardware --
        // not a shortfall to be closed. Do not "fix" this toward nine.
        //
        // The practical consequence is the same either way: a non-null
        // MotionRange tells an app "this device reports contact size", after
        // which it divides by a number that is always zero.
        //
        // flat (dead zone) and fuzz (noise) are 0 because a synthesized pointer
        // has neither; resolution is the real device dpi in units per
        // millimetre, which is what AOSP documents the field to mean.
        final float mmPerPx = pixelsPerMillimetre();
        touchscreen.addMotionRange(android.view.MotionEvent.AXIS_X,
                android.view.InputDevice.SOURCE_TOUCHSCREEN,
                0f, Math.max(0f, width - 1f), 0f, 0f, mmPerPx);
        touchscreen.addMotionRange(android.view.MotionEvent.AXIS_Y,
                android.view.InputDevice.SOURCE_TOUCHSCREEN,
                0f, Math.max(0f, height - 1f), 0f, 0f, mmPerPx);
        // AOSP: AXIS_PRESSURE is "normalized to a range from 0 (no pressure)
        // to 1 (normal pressure)". We report a constant 1.0, which is exactly
        // what a binary-contact touchscreen reports, so the axis is real.
        touchscreen.addMotionRange(android.view.MotionEvent.AXIS_PRESSURE,
                android.view.InputDevice.SOURCE_TOUCHSCREEN,
                0f, 1f, 0f, 0f, 0f);

        // Ascending id order. AOSP does not promise an enumeration order, but
        // every caller that pairs getInputDeviceIds() with getInputDevice()
        // reads better against a stable one, and it puts the virtual keyboard
        // (-1) first the way a real device list does.
        if (!MACAPK_HOST_KEYBOARD) {
            sIds = new int[] { android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, TOUCHSCREEN_ID };
            sDevices = new android.view.InputDevice[] { keyboard, touchscreen };
            return;
        }
        // The host keyboard (see MACAPK_HOST_KEYBOARD): the Virtual device stays
        // registered alongside it, as on every Android device -- EventHub creates
        // it unconditionally (createVirtualKeyboardLocked) and IMEs keep using it.
        final android.view.InputDevice hostKeyboard = new android.view.InputDevice(
                HOST_KEYBOARD_ID,
                0,                       // generation
                0,                       // controllerNumber: not a game controller
                "macapk-keyboard",
                0,                       // vendorId
                0,                       // productId
                "macapk:keyboard",
                true,                    // isExternal: a keyboard attached to the phone
                android.view.InputDevice.SOURCE_KEYBOARD,
                android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                android.view.KeyCharacterMap.load(HOST_KEYBOARD_ID),
                false,
                false,
                false);
        sIds = new int[] { android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, TOUCHSCREEN_ID,
                           HOST_KEYBOARD_ID };
        sDevices = new android.view.InputDevice[] { keyboard, touchscreen, hostKeyboard };
    }

    /** The window's pixel extent, which is the coordinate space our touch
     *  MotionEvents are delivered in. android.view.Display reads it from the
     *  live Cocoa window; the default DisplayMetrics is the fallback for the
     *  window-less case (early boot, headless translation runs). */
    private static int[] displayExtent() {
        try {
            android.view.Display d = new android.view.Display();
            int w = d.getWidth();
            int h = d.getHeight();
            if (w > 0 && h > 0) return new int[] { w, h };
        } catch (Throwable ignored) { }
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        dm.setToDefaults();
        // ⚠ v1.0.939: the REAL display. A touchscreen's MotionRange spans the
        // whole panel, including under the navigation bar -- and since this
        // version DisplayMetrics reports the smaller app area, reading it here
        // would silently shrink the reported touch range by a nav strip.
        int rw = android.view.Display.macapkRealWidthPx();
        int rh = android.view.Display.macapkRealHeightPx();
        return new int[] { rw > 0 ? rw : dm.widthPixels, rh > 0 ? rh : dm.heightPixels };
    }

    /** AOSP MotionRange.getResolution(): "the resolution of the axis in units
     *  per millimeter" -- for X/Y that is pixels per millimetre. */
    private static float pixelsPerMillimetre() {
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        dm.setToDefaults();
        float dpi = (dm.xdpi > 0f) ? dm.xdpi : dm.densityDpi;
        return dpi / 25.4f;
    }

    // ------------------------------------------------------------------
    // External devices that come and go (host game controllers)
    // ------------------------------------------------------------------

    /** Connected external devices, in connection order, and the key codes each
     *  one can produce (what deviceHasKeys answers from: AOSP reads the same
     *  answer out of the device's key layout map). Guarded by sDeviceLock. */
    private static final java.util.LinkedHashMap<Integer, android.view.InputDevice> sExternal =
            new java.util.LinkedHashMap<>();
    private static final java.util.HashMap<Integer, int[]> sExternalKeys = new java.util.HashMap<>();
    /** The first id after the fixed devices (-1, 1, 2). */
    private static int sNextExternalId = HOST_KEYBOARD_ID + 1;

    /** A fresh device id, never handed out before in this run. */
    public static int macapkNextExternalDeviceId() {
        synchronized (sDeviceLock) { return sNextExternalId++; }
    }

    /** Register a connected external device and tell the listeners. */
    public static void macapkAddExternalDevice(android.view.InputDevice device, int[] keyCodes) {
        synchronized (sDeviceLock) {
            sExternal.put(device.getId(), device);
            sExternalKeys.put(device.getId(), keyCodes == null ? new int[0] : keyCodes.clone());
        }
        notifyListeners(MSG_DEVICE_ADDED, device.getId());
    }

    /** Unregister a disconnected external device and tell the listeners. */
    public static void macapkRemoveExternalDevice(int deviceId) {
        boolean removed;
        synchronized (sDeviceLock) {
            removed = sExternal.remove(deviceId) != null;
            sExternalKeys.remove(deviceId);
        }
        if (removed) notifyListeners(MSG_DEVICE_REMOVED, deviceId);
    }

    /** AOSP: "Gets the ids of all input devices in the system." */
    public int[] getInputDeviceIds() {
        ensureDevices();
        synchronized (sDeviceLock) {
            if (sExternal.isEmpty()) return sIds.clone();
            int[] out = java.util.Arrays.copyOf(sIds, sIds.length + sExternal.size());
            int i = sIds.length;
            for (Integer id : sExternal.keySet()) out[i++] = id;
            return out;
        }
    }

    /** AOSP: "The input device or null if not found." An id we do not know --
     *  including 0, which means "not from a physical device" -- MUST answer
     *  null; engines probe unvalidated ids during startup and rely on the null
     *  to stop scanning. */
    public android.view.InputDevice getInputDevice(int id) {
        ensureDevices();
        synchronized (sDeviceLock) {
            for (int i = 0; i < sIds.length; i++) {
                if (sIds[i] == id) return sDevices[i];
            }
            return sExternal.get(id);
        }
    }

    /** AOSP InputManager.deviceHasKeys, which InputDevice.hasKeys() delegates
     *  to (InputDevice.java:697-699). It is backed by the kernel key bitmap for
     *  one device; we have no per-device key inventory, so every probe answers
     *  false. That is the conservative direction -- claiming a key exists makes
     *  an app wait for input that can never arrive (the classic
     *  hasKeys(KEYCODE_BUTTON_A) gamepad probe), while false makes it take its
     *  no-such-button path.
     *
     *  The host game controllers DO have a key inventory -- the buttons
     *  GamepadBridge emits for that controller -- so a controller id answers
     *  from it, and -1 ("any device", AOSP's union) answers from all of them.
     *  The fixed devices keep the all-false answer. */
    public boolean[] deviceHasKeys(int deviceId, int[] keyCodes) {
        boolean[] out = new boolean[keyCodes == null ? 0 : keyCodes.length];
        if (out.length == 0) return out;
        synchronized (sDeviceLock) {
            for (java.util.Map.Entry<Integer, int[]> e : sExternalKeys.entrySet()) {
                if (deviceId != -1 && e.getKey() != deviceId) continue;
                for (int k : e.getValue()) {
                    for (int i = 0; i < out.length; i++) if (keyCodes[i] == k) out[i] = true;
                }
            }
        }
        return out;
    }
    /** AOSP's one-arg form (InputManager.java): "Queries whether any device has
     *  the specified key" -- i.e. the union across devices, spelled
     *  `deviceHasKeys(-1, keyCodes)`. KeyCharacterMap's static deviceHasKey(s)
     *  delegate here; without this overload they had nothing to call and
     *  answered a hardcoded true. */
    public boolean[] deviceHasKeys(int[] keyCodes) {
        return deviceHasKeys(-1, keyCodes);
    }
}
