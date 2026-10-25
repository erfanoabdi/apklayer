// android.bluetooth.BluetoothHealth -- the (long deprecated) Health Device
// Profile proxy.
//
// @deprecated on AOSP since API 29: "Health Device Profile (HDP) and MCAP
// protocol are no longer used." AOSP's own implementation on a current device
// returns false from every registration and connection method and null from
// getMainChannelFd -- i.e. the answers below are the ones a REAL modern Android
// device gives, not a Mac.apk degradation. There is also no Bluetooth bridge
// here, so the proxy is never delivered in the first place (see BluetoothA2dp's
// header for that mechanism).
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/BluetoothHealth.java.
package android.bluetooth;

import android.os.ParcelFileDescriptor;

import java.util.Collections;
import java.util.List;

@Deprecated
public final class BluetoothHealth implements BluetoothProfile {

    public static final int SOURCE_ROLE = 1 << 0;
    public static final int SINK_ROLE = 1 << 1;

    public static final int CHANNEL_TYPE_RELIABLE = 10;
    public static final int CHANNEL_TYPE_STREAMING = 11;

    public static final int APP_CONFIG_REGISTRATION_SUCCESS = 0;
    public static final int APP_CONFIG_REGISTRATION_FAILURE = 1;
    public static final int APP_CONFIG_UNREGISTRATION_SUCCESS = 2;
    public static final int APP_CONFIG_UNREGISTRATION_FAILURE = 3;

    public static final int STATE_CHANNEL_DISCONNECTED = 0;
    public static final int STATE_CHANNEL_CONNECTING = 1;
    public static final int STATE_CHANNEL_CONNECTED = 2;
    public static final int STATE_CHANNEL_DISCONNECTING = 3;

    BluetoothHealth() {}

    public boolean registerSinkAppConfiguration(String name, int dataType,
            BluetoothHealthCallback callback) {
        return false;
    }

    public boolean unregisterAppConfiguration(BluetoothHealthAppConfiguration config) {
        return false;
    }

    public boolean connectChannelToSource(BluetoothDevice device,
            BluetoothHealthAppConfiguration config) {
        return false;
    }

    public boolean disconnectChannel(BluetoothDevice device,
            BluetoothHealthAppConfiguration config, int channelId) {
        return false;
    }

    /** Null: no channel exists, so there is no descriptor to hand out. */
    public ParcelFileDescriptor getMainChannelFd(BluetoothDevice device,
            BluetoothHealthAppConfiguration config) {
        return null;
    }

    @Override public int getConnectionState(BluetoothDevice device) { return STATE_DISCONNECTED; }

    @Override public List<BluetoothDevice> getConnectedDevices() { return Collections.emptyList(); }

    @Override public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return Collections.emptyList();
    }
}
