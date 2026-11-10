package apklayer.host;

import android.hardware.input.InputManagerGlobal;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.MotionEvent;
import org.robolectric.util.ReflectionHelpers;

/**
 * The input devices an app sees (InputManager.getInputDevice/getInputDeviceIds, MotionEvent.getDevice): one virtual device under id 0,
 * the id every event apklayer injects carries (HostInput: the shell's pointer, touch, wheel and keys). Robolectric's InputManagerGlobal
 * shadow starts with no device at all, so getInputDevice(event.getDeviceId()) was null; Unity's native input (libunity.so calls
 * InputManager.getInputDevice through JNI, then the device's sources and motion ranges) dereferenced it and the game died (PGR, SIGSEGV
 * at 0x20 on UnityMain at the first touch). Its motion ranges are the window's pixels, as a touchscreen's are the panel's.
 */
final class HostInputDevices {
    static final int ID = 0;

    private HostInputDevices() {}

    /** Registers (or updates, after a resize) the device for a window of w x h pixels. */
    static void install(int w, int h) {
        try {
            InputDevice device = device(Math.max(1, w), Math.max(1, h));
            InputManagerGlobal global = InputManagerGlobal.getInstance();
            global.getInputDeviceIds();  // the shadow creates the (empty) device list on first use
            SparseArray<InputDevice> devices = ReflectionHelpers.getField(global, "mInputDevices");
            synchronized (devices) { devices.put(ID, device); }
        } catch (Throwable e) {
            System.out.println("[apklayer] input device not registered: " + e);
        }
    }

    private static InputDevice device(int w, int h) {
        InputDevice.Builder b = new InputDevice.Builder()
                .setId(ID)
                .setName("apklayer input")
                .setDescriptor("apklayer-virtual-input")
                .setSources(InputDevice.SOURCE_TOUCHSCREEN | InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_KEYBOARD)
                .setKeyboardType(InputDevice.KEYBOARD_TYPE_ALPHABETIC);
        for (int source : new int[] {InputDevice.SOURCE_TOUCHSCREEN, InputDevice.SOURCE_MOUSE}) {
            b.addMotionRange(MotionEvent.AXIS_X, source, 0, w - 1, 0, 0, 0);
            b.addMotionRange(MotionEvent.AXIS_Y, source, 0, h - 1, 0, 0, 0);
            b.addMotionRange(MotionEvent.AXIS_PRESSURE, source, 0, 1, 0, 0, 0);
            b.addMotionRange(MotionEvent.AXIS_SIZE, source, 0, 1, 0, 0, 0);
        }
        b.addMotionRange(MotionEvent.AXIS_VSCROLL, InputDevice.SOURCE_MOUSE, -1, 1, 0, 0, 0);
        b.addMotionRange(MotionEvent.AXIS_HSCROLL, InputDevice.SOURCE_MOUSE, -1, 1, 0, 0, 0);
        return b.build();
    }
}
