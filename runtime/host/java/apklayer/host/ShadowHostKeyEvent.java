package apklayer.host;

import android.view.InputDevice;
import android.view.KeyEvent;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowKeyEvent;

/** InputEvent.getDevice() is the device of the event's id, as on Android (Robolectric's returns a field nothing sets: a game's event.getDevice() was null for a gamepad's keys). */
@Implements(KeyEvent.class)
public class ShadowHostKeyEvent extends ShadowKeyEvent {
    @RealObject private KeyEvent hostKeyEvent;

    @Implementation
    protected InputDevice getDevice() {
        return InputDevice.getDevice(hostKeyEvent.getDeviceId());
    }
}
