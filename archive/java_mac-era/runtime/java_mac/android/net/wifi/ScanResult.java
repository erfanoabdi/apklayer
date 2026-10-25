// android.net.wifi.ScanResult
//
// One Wi-Fi access point seen in a scan. Mac.apk never scans (WifiManager
// .getScanResults is empty), so instances are not produced from a radio, but
// apps reference ScanResult fields/constants when iterating the (empty) result
// list, so the public fields + channel-width constants must resolve. Mirrors
// frameworks/base wifi android.net.wifi.ScanResult public API.
package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;

public final class ScanResult implements Parcelable {

    public static final int UNSPECIFIED = -1;

    public static final int CHANNEL_WIDTH_20MHZ = 0;
    public static final int CHANNEL_WIDTH_40MHZ = 1;
    public static final int CHANNEL_WIDTH_80MHZ = 2;
    public static final int CHANNEL_WIDTH_160MHZ = 3;
    public static final int CHANNEL_WIDTH_80MHZ_PLUS_MHZ = 4;
    public static final int CHANNEL_WIDTH_320MHZ = 5;

    public static final int WIFI_STANDARD_UNKNOWN = 0;
    public static final int WIFI_STANDARD_LEGACY = 1;
    public static final int WIFI_STANDARD_11N = 4;
    public static final int WIFI_STANDARD_11AC = 5;
    public static final int WIFI_STANDARD_11AX = 6;
    public static final int WIFI_STANDARD_11BE = 8;

    public String SSID;
    public String BSSID;
    public String capabilities;
    public int level;
    public int frequency;
    public int channelWidth = CHANNEL_WIDTH_20MHZ;
    public long timestamp;

    public ScanResult() {}

    public CharSequence getWifiSsid() { return SSID; }
    public int getWifiStandard() { return WIFI_STANDARD_UNKNOWN; }
    public boolean is80211mcResponder() { return false; }

    @Override public String toString() {
        return "SSID: " + SSID + ", BSSID: " + BSSID + ", level: " + level
                + ", frequency: " + frequency;
    }

    // PARCELABLE on AOSP, and load-bearing here for the same reason as
    // android.bluetooth.le.ScanResult: android.companion's
    // DeviceFilter<D extends Parcelable> is declared over this type, so
    // WifiDeviceFilter cannot be declared unless the bound is satisfied.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(SSID);
        dest.writeString(BSSID);
        dest.writeString(capabilities);
        dest.writeInt(level);
        dest.writeInt(frequency);
        dest.writeInt(channelWidth);
        dest.writeLong(timestamp);
    }

    public static final Creator<ScanResult> CREATOR = new Creator<ScanResult>() {
        @Override public ScanResult createFromParcel(Parcel source) {
            ScanResult r = new ScanResult();
            r.SSID = source.readString();
            r.BSSID = source.readString();
            r.capabilities = source.readString();
            r.level = source.readInt();
            r.frequency = source.readInt();
            r.channelWidth = source.readInt();
            r.timestamp = source.readLong();
            return r;
        }

        @Override public ScanResult[] newArray(int size) {
            return new ScanResult[size];
        }
    };
}
