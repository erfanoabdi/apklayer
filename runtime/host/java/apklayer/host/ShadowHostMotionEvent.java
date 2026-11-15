package apklayer.host;

import android.view.InputDevice;
import android.view.MotionEvent;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowMotionEvent;

/** InputEvent.getDevice() is the device of the event's id, as on Android (see ShadowHostKeyEvent). */
@Implements(MotionEvent.class)
public class ShadowHostMotionEvent extends ShadowMotionEvent {
    @RealObject private MotionEvent hostMotionEvent;

    @Implementation
    protected InputDevice getDevice() {
        return InputDevice.getDevice(hostMotionEvent.getDeviceId());
    }
}
