// android.bluetooth.BluetoothHealthCallback -- the callback for the (long
// deprecated) Health Device Profile.
//
// @deprecated on AOSP since API 29: "Health Device Profile (HDP) and MCAP
// protocol are no longer used." Nothing here ever invokes it -- there is no
// Bluetooth bridge and no HDP stack -- but the type must link for an app whose
// subclass predates the deprecation.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/BluetoothHealthCallback.java.
package android.bluetooth;

import android.os.ParcelFileDescriptor;

@Deprecated
public abstract class BluetoothHealthCallback {

    public BluetoothHealthCallback() {}

    public void onHealthAppConfigurationStatusChange(BluetoothHealthAppConfiguration config,
            int status) {}

    public void onHealthChannelStateChange(BluetoothHealthAppConfiguration config,
            BluetoothDevice device, int prevState, int newState, ParcelFileDescriptor fd,
            int channelId) {}
}
