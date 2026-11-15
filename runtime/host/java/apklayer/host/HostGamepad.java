package apklayer.host;

import android.hardware.input.InputManagerGlobal;
import android.os.VibrationEffect;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.util.ReflectionHelpers;

/**
 * Game controllers. The shell (bridge/shell/gamepad.c) forwards a Linux gamepad's evdev events; this makes what Android's InputReader makes
 * of them: an InputDevice (SOURCE_GAMEPAD | SOURCE_JOYSTICK, and SOURCE_DPAD with a hat) that InputManager lists and announces
 * (onInputDeviceAdded / Removed), a KeyEvent per button (BUTTON_A, BUTTON_L1, ..., from the pad's .kl key layout: Android's own files in
 * resources/keylayout, the vendor's and product's, else Generic.kl) and a joystick MotionEvent per report with the axes the layout names
 * (AXIS_X/Y/Z/RZ sticks, AXIS_LTRIGGER/RTRIGGER, AXIS_HAT_X/Y). The window's ViewRootImpl turns the hat and the sticks into DPAD keys itself
 * (SyntheticJoystickHandler), as on Android. Rumble: InputDevice.getVibratorManager() works through a stand-in IInputManager (the shadowed
 * InputManagerGlobal has none), which sends FF_RUMBLE to the shell.
 */
final class HostGamepad {
    private static final int EV_SYN = 0, EV_KEY = 1, EV_ABS = 3;
    /** Buttons some kernels report as keys 0x220..0x223 where others report the hat. */
    private static final int[] DPAD_KEYS = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT};

    private final HostInput input;
    ShellClient shell;
    private final SparseArray<Pad> pads = new SparseArray<>();
    private boolean imInstalled;

    HostGamepad(HostInput input) {
        this.input = input;
    }

    /** One .kl file: evdev key code -> Android keycode; evdev axis -> Android axis (and whether it is inverted, flat override). */
    static final class Layout {
        final Map<Integer, Integer> keys = new HashMap<>();
        final Map<Integer, int[]> axes = new HashMap<>();  // evdev code -> {Android axis, invert, flat (-1: the device's)}
    }

    private static final class Pad {
        int dev;
        String name;
        boolean rumble;
        Layout layout;
        int[] absCode, absMin, absMax, absValue;
        InputDevice device;
        final Map<Integer, Float> axis = new HashMap<>();  // Android axis -> value
        boolean dirty;
        long downTime;
        boolean vibrating;
    }

    // ---- key layouts ----

    private static Integer keyCode(String label) {  // KeyEvent's KEYCODE_<label> constant (keyCodeToString has no names in this environment's KeyEvent)
        try {
            return KeyEvent.class.getField("KEYCODE_" + label).getInt(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static final String[] AXIS_NAMES = {"X", "Y", "Z", "RX", "RY", "RZ", "HAT_X", "HAT_Y", "LTRIGGER", "RTRIGGER", "THROTTLE", "RUDDER", "WHEEL", "GAS", "BRAKE"};
    private static final int[] AXES = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_THROTTLE, MotionEvent.AXIS_RUDDER,
            MotionEvent.AXIS_WHEEL, MotionEvent.AXIS_GAS, MotionEvent.AXIS_BRAKE};

    private static int axisCode(String label) {
        for (int i = 0; i < AXIS_NAMES.length; i++) if (AXIS_NAMES[i].equals(label)) return AXES[i];
        return -1;
    }

    private static final Map<String, Layout> LAYOUTS = new HashMap<>();

    /** Android's lookup order (EventHub): vendor, product and version; vendor and product; Generic. */
    static synchronized Layout layout(int vendor, int product, int version) {
        for (String name : new String[] {String.format("Vendor_%04x_Product_%04x_Version_%04x", vendor, product, version),
                String.format("Vendor_%04x_Product_%04x", vendor, product), "Generic"}) {
            Layout l = LAYOUTS.get(name);
            if (l == null) {
                try (InputStream in = HostGamepad.class.getResourceAsStream("/keylayout/" + name + ".kl")) {
                    if (in == null) continue;
                    l = parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
                    LAYOUTS.put(name, l);
                } catch (java.io.IOException e) {
                    continue;
                }
            }
            return l;
        }
        return new Layout();
    }

    /** The KeyLayoutMap grammar this needs: `key <code> <LABEL>`, `axis <code> [invert] <LABEL> [flat <n>]`; usage, sensor and other lines are ignored. */
    static Layout parse(BufferedReader r) throws java.io.IOException {
        Layout l = new Layout();
        for (String line; (line = r.readLine()) != null; ) {
            int hash = line.indexOf('#');
            String[] t = (hash >= 0 ? line.substring(0, hash) : line).trim().split("\\s+");
            try {
                if (t[0].equals("key") && t.length >= 3 && !t[1].equals("usage")) {
                    Integer k = keyCode(t[2]);
                    if (k != null) l.keys.put(Integer.decode(t[1]), k);
                } else if (t[0].equals("axis") && t.length >= 3 && !t[2].equals("split")) {
                    boolean invert = t[2].equals("invert");
                    int axis = axisCode(t[invert ? 3 : 2]), flat = -1;
                    for (int i = 3; i + 1 < t.length; i++) if (t[i].equals("flat")) flat = Integer.parseInt(t[i + 1]);
                    if (axis >= 0) l.axes.put(Integer.decode(t[1]), new int[] {axis, invert ? 1 : 0, flat});
                }
            } catch (NumberFormatException e) {
                // a line this does not understand is left out, as the framework's parser warns and goes on
            }
        }
        return l;
    }

    // ---- shell events ----

    /** {18, dev, added, vendor, product, version, naxes, rumble, {code, min, max, flat, value}*, name bytes...}, {18, dev, 0}, {19, dev, {type, code, value}*}. */
    void onEvent(long now, int[] ev) {
        if (ev[0] == 19) { events(now, ev); return; }
        int dev = ev[1];
        if (ev[2] == 0) { remove(dev); return; }
        Pad p = new Pad();
        p.dev = dev;
        int naxes = ev[6];
        p.rumble = ev[7] != 0;
        p.absCode = new int[naxes];
        p.absMin = new int[naxes];
        p.absMax = new int[naxes];
        p.absValue = new int[naxes];
        InputDevice.Builder b = new InputDevice.Builder();
        p.layout = layout(ev[3], ev[4], ev[5]);
        int sources = InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK;
        for (int i = 0; i < naxes; i++) {
            int o = 8 + 5 * i, code = ev[o], min = ev[o + 1], max = ev[o + 2];
            p.absCode[i] = code;
            p.absMin[i] = min;
            p.absMax[i] = max;
            p.absValue[i] = ev[o + 4];
            int[] a = p.layout.axes.get(code);
            if (a == null || max <= min) continue;
            boolean hat = a[0] == MotionEvent.AXIS_HAT_X || a[0] == MotionEvent.AXIS_HAT_Y;
            if (hat) sources |= InputDevice.SOURCE_DPAD;
            float scale = 2f / (max - min);
            b.addMotionRange(a[0], InputDevice.SOURCE_JOYSTICK, trigger(a[0]) ? 0 : -1, 1, (a[2] >= 0 ? a[2] : ev[o + 3]) * scale, 0, 0);
        }
        StringBuilder name = new StringBuilder();
        for (int i = 8 + 5 * naxes; i < ev.length; i++) name.append((char) ev[i]);
        p.name = name.toString();
        for (int k : p.layout.keys.values()) if (k == KeyEvent.KEYCODE_DPAD_UP) sources |= InputDevice.SOURCE_DPAD;
        int number = 1;
        for (int i = 0; i < pads.size(); i++) if (pads.valueAt(i).device.getControllerNumber() == number) { number++; i = -1; }
        p.device = b.setId(dev).setName(p.name).setVendorId(ev[3]).setProductId(ev[4]).setDescriptor("apklayer-pad-" + ev[3] + "-" + ev[4] + "-" + p.name)
                .setControllerNumber(number).setSources(sources).setKeyboardType(InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC).setExternal(true)
                .setHasVibrator(p.rumble).build();
        pads.put(dev, p);
        for (int i = 0; i < naxes; i++) apply(p, p.absCode[i], p.absValue[i]);
        System.out.println("[apklayer] gamepad " + dev + " added: " + p.name + " (" + Integer.toHexString(ev[3]) + ":" + Integer.toHexString(ev[4]) + ")");
        register();
    }

    private static boolean trigger(int axis) {
        return axis == MotionEvent.AXIS_LTRIGGER || axis == MotionEvent.AXIS_RTRIGGER || axis == MotionEvent.AXIS_GAS || axis == MotionEvent.AXIS_BRAKE || axis == MotionEvent.AXIS_THROTTLE;
    }

    private void remove(int dev) {
        if (pads.indexOfKey(dev) < 0) return;
        pads.remove(dev);
        System.out.println("[apklayer] gamepad " + dev + " removed");
        register();
    }

    /** InputManager's device list after a change: the real InputManagerGlobal compares it with its own and calls the listeners (added, removed, changed). */
    private void register() {
        try {
            InputManagerGlobal global = InputManagerGlobal.getInstance();
            global.getInputDeviceIds();  // the shadow creates the (empty) device list on first use
            SparseArray<InputDevice> devices = ReflectionHelpers.getField(global, "mInputDevices");
            Object lock = ReflectionHelpers.getField(global, "mInputDeviceListeners");
            if (!imInstalled) {
                ReflectionHelpers.setField(global, "mIm", Proxy.newProxyInstance(HostGamepad.class.getClassLoader(), new Class<?>[] {android.hardware.input.IInputManager.class},
                        (proxy, method, args) -> manager(method.getName(), args, method.getReturnType())));
                imInstalled = true;
            }
            synchronized (lock) {
                List<Integer> ids = new ArrayList<>();
                for (int i = 0; i < devices.size(); i++) if (devices.keyAt(i) == 0) ids.add(devices.keyAt(i));
                for (int i = 0; i < pads.size(); i++) ids.add(pads.keyAt(i));
                int[] idAndGeneration = new int[ids.size() * 2];
                for (int i = 0; i < ids.size(); i++) {
                    InputDevice d = devices.get(ids.get(i));
                    idAndGeneration[2 * i] = ids.get(i);
                    idAndGeneration[2 * i + 1] = d != null ? d.getGeneration() : 0;
                }
                ReflectionHelpers.callInstanceMethod(global, "onInputDevicesChanged", ReflectionHelpers.ClassParameter.from(int[].class, idAndGeneration));
                for (int i = 0; i < pads.size(); i++) devices.put(pads.keyAt(i), pads.valueAt(i).device);
            }
        } catch (Throwable e) {
            System.out.println("[apklayer] gamepad not registered: " + e);
        }
    }

    /** The IInputManager calls InputManagerGlobal makes for a pad: the device itself, its vibrator. */
    private Object manager(String method, Object[] args, Class<?> returns) {
        switch (method) {
            case "getInputDevice": { Pad p = pads.get((Integer) args[0]); return p != null ? p.device : null; }
            case "getVibratorIds": { Pad p = pads.get((Integer) args[0]); return p != null && p.rumble ? new int[] {0} : new int[0]; }
            case "vibrate": rumble((Integer) args[0], (VibrationEffect) args[1]); return null;
            case "vibrateCombined": {
                android.os.CombinedVibration c = (android.os.CombinedVibration) args[1];
                if (c instanceof android.os.CombinedVibration.Mono) rumble((Integer) args[0], ((android.os.CombinedVibration.Mono) c).getEffect());
                return null;
            }
            case "cancelVibrate": rumble((Integer) args[0], null); return null;
            case "isVibrating": { Pad p = pads.get((Integer) args[0]); return p != null && p.vibrating; }
            default:
                return returns == boolean.class ? (Object) Boolean.FALSE : returns == int.class ? (Object) 0 : returns == long.class ? (Object) 0L : returns == float.class ? (Object) 0f : null;
        }
    }

    /** An effect as the pad's two motors: the loudest step is both magnitudes, its length the effect's (Prebuilt and primitives: half strength). */
    private void rumble(int dev, VibrationEffect effect) {
        Pad p = pads.get(dev);
        if (p == null || shell == null) return;
        int ms = 0, mag = 0;
        if (effect != null) {
            long d = effect.getDuration();
            ms = d <= 0 || d > 5000 ? (d == 0 ? 0 : 1000) : (int) d;
            float amp = 0.5f;
            if (effect instanceof VibrationEffect.Composed) {
                float max = 0;
                for (VibrationEffectSegment s : ((VibrationEffect.Composed) effect).getSegments())
                    if (s instanceof StepSegment) max = Math.max(max, ((StepSegment) s).getAmplitude() < 0 ? 1f : ((StepSegment) s).getAmplitude());
                if (max > 0) amp = max;
            }
            mag = (int) (amp * 65535);
        }
        p.vibrating = ms > 0;
        System.out.println("[apklayer] gamepad " + dev + " rumble " + mag + " for " + ms + " ms");
        shell.sendPadRumble(dev, mag, mag, ms);
    }

    private void events(long now, int[] ev) {
        Pad p = pads.get(ev[1]);
        if (p == null) return;
        for (int i = 2; i + 2 < ev.length; i += 3) {
            int type = ev[i], code = ev[i + 1], value = ev[i + 2];
            if (type == EV_KEY && value != 2) key(p, now, code, value);
            else if (type == EV_ABS) apply(p, code, value);
            else if (type == EV_SYN && p.dirty) motion(p, now);
        }
    }

    private void key(Pad p, long now, int code, int value) {
        Integer k = p.layout.keys.get(code);
        if (k == null && code >= 0x220 && code <= 0x223) k = DPAD_KEYS[code - 0x220];
        if (Boolean.getBoolean("apklayer.debugkeys")) System.out.println("[apklayer] gamepad " + p.dev + " evdev key " + code + " -> " + (k == null ? "unmapped" : KeyEvent.keyCodeToString(k)) + " " + value + " (" + p.layout.keys.size() + " keys in the layout)");
        if (k == null) return;
        if (value == 1) p.downTime = now;
        if (Boolean.getBoolean("apklayer.debugkeys")) System.out.println("[apklayer] gamepad " + p.dev + " key " + KeyEvent.keyCodeToString(k) + " " + value);
        input.injectKey(new KeyEvent(p.downTime, now, value == 0 ? KeyEvent.ACTION_UP : KeyEvent.ACTION_DOWN, k, 0, 0, p.dev, code, 0, InputDevice.SOURCE_GAMEPAD), false);
    }

    /** A stick is -1..1 over the device's range, a trigger 0..1, a hat -1, 0 or 1 as it is (InputReader's JoystickInputMapper scale and offset). */
    private void apply(Pad p, int code, int raw) {
        int[] a = p.layout.axes.get(code);
        if (a == null) return;
        int i = 0;
        while (i < p.absCode.length && p.absCode[i] != code) i++;
        if (i == p.absCode.length || p.absMax[i] <= p.absMin[i]) return;
        float v = trigger(a[0]) ? (raw - p.absMin[i]) / (float) (p.absMax[i] - p.absMin[i]) : (raw - p.absMin[i]) * 2f / (p.absMax[i] - p.absMin[i]) - 1f;
        if (a[1] != 0) v = -v;
        Float old = p.axis.put(a[0], v);
        if (old == null || old != v) p.dirty = true;
    }

    private void motion(Pad p, long now) {
        p.dirty = false;
        MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
        MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
        for (Map.Entry<Integer, Float> e : p.axis.entrySet()) pc.setAxisValue(e.getKey(), e.getValue());
        MotionEvent m = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1, new MotionEvent.PointerProperties[] {pp}, new MotionEvent.PointerCoords[] {pc},
                0, 0, 1f, 1f, p.dev, 0, InputDevice.SOURCE_JOYSTICK, 0);
        if (Boolean.getBoolean("apklayer.debugkeys"))
            System.out.println("[apklayer] gamepad " + p.dev + " motion " + p.axis);
        input.injectKey(m, false);
    }
}
