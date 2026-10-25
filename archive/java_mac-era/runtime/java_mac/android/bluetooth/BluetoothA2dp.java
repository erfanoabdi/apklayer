// android.bluetooth.BluetoothA2dp -- the A2DP (audio sink) profile proxy.
//
// Mac.apk exposes no Bluetooth bridge, so no profile proxy is ever DELIVERED:
// BluetoothAdapter.getProfileProxy() returns false without calling the
// ServiceListener, which is AOSP's answer for a profile the device does not
// support. The constructor is package-private, as on AOSP, so an app cannot
// fabricate one. Every method below is therefore unreachable, and each answers
// the way a device with no connected devices does.
//
// The class exists so an app that NAMES the type links -- a cast or a field of
// this type is resolved at class definition time.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/BluetoothA2dp.java.
package android.bluetooth;

import java.util.Collections;
import java.util.List;

public final class BluetoothA2dp implements BluetoothProfile {

    public static final String ACTION_CONNECTION_STATE_CHANGED =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED";
    public static final String ACTION_PLAYING_STATE_CHANGED =
            "android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED";

    public static final int STATE_PLAYING = 10;
    public static final int STATE_NOT_PLAYING = 11;

    BluetoothA2dp() {}

    public boolean isA2dpPlaying(BluetoothDevice device) { return false; }

    @Override public int getConnectionState(BluetoothDevice device) { return STATE_DISCONNECTED; }

    @Override public List<BluetoothDevice> getConnectedDevices() { return Collections.emptyList(); }

    @Override public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return Collections.emptyList();
    }
}
