// android.bluetooth.BluetoothHearingAid -- the ASHA hearing-aid profile proxy.
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
// framework/java/android/bluetooth/BluetoothHearingAid.java.
package android.bluetooth;

import java.util.Collections;
import java.util.List;

public final class BluetoothHearingAid implements BluetoothProfile {

    public static final String ACTION_CONNECTION_STATE_CHANGED =
            "android.bluetooth.hearingaid.profile.action.CONNECTION_STATE_CHANGED";

    BluetoothHearingAid() {}

    @Override public int getConnectionState(BluetoothDevice device) { return STATE_DISCONNECTED; }

    @Override public List<BluetoothDevice> getConnectedDevices() { return Collections.emptyList(); }

    @Override public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return Collections.emptyList();
    }
}
