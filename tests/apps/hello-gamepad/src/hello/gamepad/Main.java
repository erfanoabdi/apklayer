package hello.gamepad;

import android.app.Activity;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.VibratorManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** tests/vm/gamepad.py: what an Android game sees of a controller: the device list and its listener, buttons, sticks, triggers, the hat and rumble. */
public class Main extends Activity {
    private static void log(String s) { System.out.println("[hello-gamepad] " + s); }

    private static boolean pad(InputDevice d) {
        return d != null && (d.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
    }

    private static void describe(String what, int id) {
        InputDevice d = InputDevice.getDevice(id);
        if (!pad(d)) return;
        StringBuilder r = new StringBuilder();
        for (InputDevice.MotionRange m : d.getMotionRanges())
            r.append(MotionEvent.axisToString(m.getAxis())).append('[').append(m.getMin()).append(',').append(m.getMax()).append(',').append(m.getFlat()).append("] ");
        log(what + " id=" + id + " name=" + d.getName() + " vendor=" + Integer.toHexString(d.getVendorId()) + " product=" + Integer.toHexString(d.getProductId())
                + " sources=0x" + Integer.toHexString(d.getSources()) + " controller=" + d.getControllerNumber() + " vibrators=" + d.getVibratorManager().getVibratorIds().length
                + " external=" + d.isExternal() + " ranges=" + r);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        InputManager im = getSystemService(InputManager.class);
        im.registerInputDeviceListener(new InputManager.InputDeviceListener() {
            public void onInputDeviceAdded(int id) { describe("added", id); }
            public void onInputDeviceRemoved(int id) { log("removed id=" + id + " device=" + InputDevice.getDevice(id)); }
            public void onInputDeviceChanged(int id) { log("changed id=" + id); }
        }, null);
        for (int id : InputDevice.getDeviceIds()) describe("present", id);
        log("ready");
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (!pad(e.getDevice())) return super.dispatchKeyEvent(e);
        log("key " + (e.getAction() == KeyEvent.ACTION_DOWN ? "ACTION_DOWN" : "ACTION_UP") + " " + e.getKeyCode() + " dev=" + e.getDeviceId() + " source=0x" + Integer.toHexString(e.getSource()));
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) {  // A rumbles, B stops it
            VibratorManager vm = e.getDevice().getVibratorManager();
            vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(300, 128));
            log("rumble requested");
        } else if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
            e.getDevice().getVibratorManager().cancel();
            log("rumble cancelled");
        }
        return true;
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return super.dispatchGenericMotionEvent(e);
        log(String.format("motion x=%.3f y=%.3f z=%.3f rz=%.3f lt=%.3f rt=%.3f hx=%.0f hy=%.0f dev=%d source=0x%x", e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y),
                e.getAxisValue(MotionEvent.AXIS_Z), e.getAxisValue(MotionEvent.AXIS_RZ), e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                e.getAxisValue(MotionEvent.AXIS_HAT_X), e.getAxisValue(MotionEvent.AXIS_HAT_Y), e.getDeviceId(), e.getSource()));
        return false;  // unhandled: the window turns the hat and sticks into DPAD keys, as Android does
    }
}
