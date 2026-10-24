// android.net.wifi.p2p.WifiP2pDeviceList -- the set of Wi-Fi Direct peers.
//
// A REAL container: the collection operations all work. It is simply always
// EMPTY in practice, because a Mac has no Wi-Fi Direct radio to discover peers
// with. Empty is the honest answer and the one AOSP gives before discovery
// finds anything, so an app iterating it behaves correctly.
//
// Mirrors wifi/java/android/net/wifi/p2p/WifiP2pDeviceList.java
// (android11-release).
package android.net.wifi.p2p;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class WifiP2pDeviceList implements Parcelable {

    // LinkedHashMap, as AOSP uses, so peer order is stable across calls.
    private final Map<String, WifiP2pDevice> mDevices = new LinkedHashMap<String, WifiP2pDevice>();

    public WifiP2pDeviceList() { }

    public WifiP2pDeviceList(WifiP2pDeviceList source) {
        if (source != null) {
            for (WifiP2pDevice d : source.getDeviceList()) {
                mDevices.put(d.deviceAddress, new WifiP2pDevice(d));
            }
        }
    }

    /** @hide on AOSP. */
    public WifiP2pDeviceList(ArrayList<WifiP2pDevice> devices) {
        for (WifiP2pDevice device : devices) {
            if (device.deviceAddress != null) {
                mDevices.put(device.deviceAddress, device);
            }
        }
    }

    /** @hide on AOSP. */
    public boolean clear() {
        if (mDevices.isEmpty()) return false;
        mDevices.clear();
        return true;
    }

    /** @hide on AOSP. */
    public void update(WifiP2pDevice device) {
        if (device == null || device.deviceAddress == null) return;
        mDevices.put(device.deviceAddress, device);
    }

    public WifiP2pDevice get(String deviceAddress) {
        if (deviceAddress == null) return null;
        return mDevices.get(deviceAddress);
    }

    /** @hide on AOSP. */
    public boolean remove(WifiP2pDevice device) {
        if (device == null || device.deviceAddress == null) return false;
        return mDevices.remove(device.deviceAddress) != null;
    }

    /** @hide on AOSP. */
    public WifiP2pDevice remove(String deviceAddress) {
        if (deviceAddress == null) return null;
        return mDevices.remove(deviceAddress);
    }

    public Collection<WifiP2pDevice> getDeviceList() {
        return Collections.unmodifiableCollection(mDevices.values());
    }

    /** @hide on AOSP. */
    public boolean isGroupOwner(String deviceAddress) {
        WifiP2pDevice device = mDevices.get(deviceAddress);
        return device != null && device.isGroupOwner();
    }

    @Override public String toString() {
        StringBuilder sbuf = new StringBuilder();
        for (WifiP2pDevice device : mDevices.values()) {
            sbuf.append("\n").append(device);
        }
        return sbuf.toString();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDevices.size());
        for (WifiP2pDevice device : mDevices.values()) {
            dest.writeParcelable(device, flags);
        }
    }

    public static final Creator<WifiP2pDeviceList> CREATOR = new Creator<WifiP2pDeviceList>() {
        @Override public WifiP2pDeviceList createFromParcel(Parcel in) {
            WifiP2pDeviceList deviceList = new WifiP2pDeviceList();
            int deviceCount = in.readInt();
            for (int i = 0; i < deviceCount; i++) {
                deviceList.update((WifiP2pDevice) in.readParcelable(
                        WifiP2pDevice.class.getClassLoader()));
            }
            return deviceList;
        }

        @Override public WifiP2pDeviceList[] newArray(int size) {
            return new WifiP2pDeviceList[size];
        }
    };
}
