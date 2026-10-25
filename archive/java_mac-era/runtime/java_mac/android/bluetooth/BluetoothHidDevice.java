// android.bluetooth.BluetoothHidDevice -- the HID DEVICE profile proxy (this
// machine acting as a Bluetooth keyboard / mouse / gamepad).
//
// Mac.apk exposes no Bluetooth bridge, so this proxy is never delivered:
// BluetoothAdapter.getProfileProxy() returns false without calling the
// ServiceListener, which is AOSP's answer for a profile the device does not
// support. The constructor is package-private, as on AOSP, so an app cannot
// fabricate one. registerApp() returns FALSE rather than true -- an app that
// believed its HID app was registered would then wait for onAppStatusChanged
// and for host reports that cannot come.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/BluetoothHidDevice.java.
package android.bluetooth;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public final class BluetoothHidDevice implements BluetoothProfile {

    public static final String ACTION_CONNECTION_STATE_CHANGED =
            "android.bluetooth.hiddevice.profile.action.CONNECTION_STATE_CHANGED";

    public static final byte SUBCLASS1_NONE = (byte) 0x00;
    public static final byte SUBCLASS1_KEYBOARD = (byte) 0x40;
    public static final byte SUBCLASS1_MOUSE = (byte) 0x80;
    public static final byte SUBCLASS1_COMBO = (byte) 0xC0;

    public static final byte SUBCLASS2_UNCATEGORIZED = (byte) 0x00;
    public static final byte SUBCLASS2_JOYSTICK = (byte) 0x01;
    public static final byte SUBCLASS2_GAMEPAD = (byte) 0x02;
    public static final byte SUBCLASS2_REMOTE_CONTROL = (byte) 0x03;
    public static final byte SUBCLASS2_SENSING_DEVICE = (byte) 0x04;
    public static final byte SUBCLASS2_DIGITIZER_TABLET = (byte) 0x05;
    public static final byte SUBCLASS2_CARD_READER = (byte) 0x06;

    public static final byte REPORT_TYPE_INPUT = (byte) 1;
    public static final byte REPORT_TYPE_OUTPUT = (byte) 2;
    public static final byte REPORT_TYPE_FEATURE = (byte) 3;

    public static final byte ERROR_RSP_SUCCESS = (byte) 0;
    public static final byte ERROR_RSP_NOT_READY = (byte) 1;
    public static final byte ERROR_RSP_INVALID_RPT_ID = (byte) 2;
    public static final byte ERROR_RSP_UNSUPPORTED_REQ = (byte) 3;
    public static final byte ERROR_RSP_INVALID_PARAM = (byte) 4;
    public static final byte ERROR_RSP_UNKNOWN = (byte) 14;

    public static final byte PROTOCOL_BOOT_MODE = (byte) 0;
    public static final byte PROTOCOL_REPORT_MODE = (byte) 1;

    /**
     * Callbacks from the HID host. Never invoked here (registerApp() always
     * fails), but present to be overridden -- which is why the type must link.
     */
    public abstract static class Callback {
        public Callback() {}

        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {}

        public void onConnectionStateChanged(BluetoothDevice device, int state) {}

        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {}

        public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {}

        public void onSetProtocol(BluetoothDevice device, byte protocol) {}

        public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {}

        public void onVirtualCableUnplug(BluetoothDevice device) {}
    }

    BluetoothHidDevice() {}

    /** False: nothing can register a HID app without a Bluetooth stack, and a
     *  true here would leave the app waiting on onAppStatusChanged. */
    public boolean registerApp(BluetoothHidDeviceAppSdpSettings sdp,
            BluetoothHidDeviceAppQosSettings inQos, BluetoothHidDeviceAppQosSettings outQos,
            Executor executor, Callback callback) {
        return false;
    }

    /** A removal: nothing was registered, so there is nothing to unregister. */
    public boolean unregisterApp() { return false; }

    public boolean sendReport(BluetoothDevice device, int id, byte[] data) { return false; }

    public boolean replyReport(BluetoothDevice device, byte type, byte id, byte[] data) {
        return false;
    }

    public boolean reportError(BluetoothDevice device, byte error) { return false; }

    public boolean connect(BluetoothDevice device) { return false; }

    public boolean disconnect(BluetoothDevice device) { return false; }

    @Override public int getConnectionState(BluetoothDevice device) { return STATE_DISCONNECTED; }

    @Override public List<BluetoothDevice> getConnectedDevices() { return Collections.emptyList(); }

    @Override public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return Collections.emptyList();
    }
}
