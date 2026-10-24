// android.companion.AssociatedDevice -- the scan result / device object behind
// an AssociationInfo.
//
// NO PUBLIC CONSTRUCTOR on AOSP either: only the system creates one, as part
// of completing an association through the companion pairing UI. Mac.apk has
// no such UI and no companion transport, so no AssociationInfo and therefore
// no AssociatedDevice is ever produced (see CompanionDeviceManager). The type
// is here so an app that names it in a signature can link.
//
// Mirrors frameworks/base/core/java/android/companion/AssociatedDevice.java;
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.companion;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;

public final class AssociatedDevice implements Parcelable {

    private final android.bluetooth.le.ScanResult mBleDevice;
    private final BluetoothDevice mBluetoothDevice;
    private final android.net.wifi.ScanResult mWifiDevice;

    AssociatedDevice(android.bluetooth.le.ScanResult bleDevice,
            BluetoothDevice bluetoothDevice, android.net.wifi.ScanResult wifiDevice) {
        mBleDevice = bleDevice;
        mBluetoothDevice = bluetoothDevice;
        mWifiDevice = wifiDevice;
    }

    /** At most one of the three is set on AOSP; all are null here. */
    public android.bluetooth.le.ScanResult getBleDevice() { return mBleDevice; }

    public BluetoothDevice getBluetoothDevice() { return mBluetoothDevice; }

    public android.net.wifi.ScanResult getWifiDevice() { return mWifiDevice; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        // Nothing to marshal: an AssociatedDevice cannot be produced here, so
        // there is never a non-null device to write. Reading back an empty
        // record yields the same all-null object, which keeps the round trip
        // consistent rather than half-written.
    }

    public static final Creator<AssociatedDevice> CREATOR = new Creator<AssociatedDevice>() {
        @Override public AssociatedDevice createFromParcel(Parcel source) {
            return new AssociatedDevice(null, null, null);
        }

        @Override public AssociatedDevice[] newArray(int size) {
            return new AssociatedDevice[size];
        }
    };
}
