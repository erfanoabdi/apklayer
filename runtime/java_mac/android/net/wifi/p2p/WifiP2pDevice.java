// android.net.wifi.p2p.WifiP2pDevice -- one peer seen over Wi-Fi Direct.
//
// A REAL value object with no device dependency: AOSP exposes it as public
// FIELDS, and an app reads deviceAddress/deviceName straight off it. What
// never happens here is one being PRODUCED -- a Mac has no Wi-Fi Direct radio,
// so peer discovery finds nothing and WifiP2pDeviceList stays empty.
//
// Mirrors wifi/java/android/net/wifi/p2p/WifiP2pDevice.java (android11-release).
// The status constants are AOSP's exact values; `status` defaults to
// UNAVAILABLE, which is both AOSP's default and the truthful state here.
package android.net.wifi.p2p;

import android.net.wifi.WpsInfo;
import android.os.Parcel;
import android.os.Parcelable;

public class WifiP2pDevice implements Parcelable {

    public String deviceName = "";
    public String deviceAddress = "";
    public String primaryDeviceType;
    public String secondaryDeviceType;

    public static final int CONNECTED   = 0;
    public static final int INVITED     = 1;
    public static final int FAILED      = 2;
    public static final int AVAILABLE   = 3;
    public static final int UNAVAILABLE = 4;

    public int wpsConfigMethodsSupported;
    public int deviceCapability;
    public int groupCapability;
    public int status = UNAVAILABLE;

    public WifiP2pDevice() { }

    public WifiP2pDevice(String string) throws IllegalArgumentException {
        // AOSP parses a supplicant event line here. Nothing generates
        // supplicant events on a Mac, so the only honest thing a caller can be
        // told about an unparseable input is that it was unparseable.
        throw new IllegalArgumentException("Malformed supplicant event: " + string);
    }

    public WifiP2pDevice(WifiP2pDevice source) {
        if (source != null) {
            deviceName = source.deviceName;
            deviceAddress = source.deviceAddress;
            primaryDeviceType = source.primaryDeviceType;
            secondaryDeviceType = source.secondaryDeviceType;
            wpsConfigMethodsSupported = source.wpsConfigMethodsSupported;
            deviceCapability = source.deviceCapability;
            groupCapability = source.groupCapability;
            status = source.status;
        }
    }

    public String getDeviceName() { return deviceName; }

    public String getDeviceAddress() { return deviceAddress; }

    public String getPrimaryDeviceType() { return primaryDeviceType; }

    public String getSecondaryDeviceType() { return secondaryDeviceType; }

    /** AOSP: bit 0 of deviceCapability. */
    public boolean isServiceDiscoveryCapable() {
        return (deviceCapability & 1) != 0;
    }

    /** AOSP: bit 0 of groupCapability. */
    public boolean isGroupOwner() {
        return (groupCapability & 1) != 0;
    }

    public boolean wpsPbcSupported() { return (wpsConfigMethodsSupported & 0x0080) != 0; }

    public boolean wpsKeypadSupported() { return (wpsConfigMethodsSupported & 0x0100) != 0; }

    public boolean wpsDisplaySupported() { return (wpsConfigMethodsSupported & 0x0008) != 0; }

    /** @hide on AOSP; kept because WpsInfo is part of the same public flow. */
    public WpsInfo getWpsInfo() { return null; }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof WifiP2pDevice)) return false;
        WifiP2pDevice other = (WifiP2pDevice) obj;
        if (other.deviceAddress == null) return deviceAddress == null;
        return other.deviceAddress.equals(deviceAddress);
    }

    @Override public int hashCode() {
        return deviceAddress == null ? 0 : deviceAddress.hashCode();
    }

    @Override public String toString() {
        return "Device: " + deviceName + "\n deviceAddress: " + deviceAddress
                + "\n primary type: " + primaryDeviceType
                + "\n secondary type: " + secondaryDeviceType
                + "\n wps: " + wpsConfigMethodsSupported
                + "\n grpcapab: " + groupCapability
                + "\n devcapab: " + deviceCapability
                + "\n status: " + status;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(deviceName);
        dest.writeString(deviceAddress);
        dest.writeString(primaryDeviceType);
        dest.writeString(secondaryDeviceType);
        dest.writeInt(wpsConfigMethodsSupported);
        dest.writeInt(deviceCapability);
        dest.writeInt(groupCapability);
        dest.writeInt(status);
    }

    public static final Creator<WifiP2pDevice> CREATOR = new Creator<WifiP2pDevice>() {
        @Override public WifiP2pDevice createFromParcel(Parcel in) {
            WifiP2pDevice device = new WifiP2pDevice();
            device.deviceName = in.readString();
            device.deviceAddress = in.readString();
            device.primaryDeviceType = in.readString();
            device.secondaryDeviceType = in.readString();
            device.wpsConfigMethodsSupported = in.readInt();
            device.deviceCapability = in.readInt();
            device.groupCapability = in.readInt();
            device.status = in.readInt();
            return device;
        }

        @Override public WifiP2pDevice[] newArray(int size) { return new WifiP2pDevice[size]; }
    };
}
