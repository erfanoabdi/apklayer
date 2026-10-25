// android.bluetooth.BluetoothProfile
//
// The interface implemented by all Bluetooth profile proxies; its real value
// here is the STATE_* / profile-id constant set that apps and BluetoothGatt
// reference. Mac.apk has no radio, so connections are always DISCONNECTED.
// Mirrors frameworks/base/core/java/android/bluetooth/BluetoothProfile.java.
package android.bluetooth;

import java.util.List;

public interface BluetoothProfile {

    int STATE_DISCONNECTED = 0;
    int STATE_CONNECTING = 1;
    int STATE_CONNECTED = 2;
    int STATE_DISCONNECTING = 3;

    int HEADSET = 1;
    int A2DP = 2;
    int HEALTH = 3;
    int GATT = 7;
    int GATT_SERVER = 8;
    int SAP = 10;
    int HID_DEVICE = 19;
    int HEARING_AID = 21;

    /** The state extras carried on every profile's connection-state
     *  broadcast. Plain strings, mirrored verbatim. */
    String EXTRA_STATE = "android.bluetooth.profile.extra.STATE";
    String EXTRA_PREVIOUS_STATE = "android.bluetooth.profile.extra.PREVIOUS_STATE";

    int getConnectionState(BluetoothDevice device);
    List<BluetoothDevice> getConnectedDevices();
    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states);

    interface ServiceListener {
        void onServiceConnected(int profile, BluetoothProfile proxy);
        void onServiceDisconnected(int profile);
    }
}
