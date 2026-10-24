// android.companion.WifiDeviceFilter -- narrows a companion association
// request to Wi-Fi scan results matching an SSID pattern or a BSSID (+mask).
//
// Same standing as BluetoothDeviceFilter (read that header): a real
// Builder-built value type; nothing scans, because there is no companion
// pairing UI to run the scan.
//
// Mirrors frameworks/base/core/java/android/companion/WifiDeviceFilter.java;
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.companion;

import android.net.MacAddress;
import android.net.wifi.ScanResult;
import android.os.Parcel;

import java.util.regex.Pattern;

public final class WifiDeviceFilter implements DeviceFilter<ScanResult> {

    private final Pattern mNamePattern;
    private final MacAddress mBssid;
    private final MacAddress mBssidMask;

    private WifiDeviceFilter(Builder b) {
        mNamePattern = b.mNamePattern;
        mBssid = b.mBssid;
        mBssidMask = b.mBssidMask;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNamePattern == null ? null : mNamePattern.pattern());
        dest.writeString(mBssid == null ? null : mBssid.toString());
        dest.writeString(mBssidMask == null ? null : mBssidMask.toString());
    }

    public static final Creator<WifiDeviceFilter> CREATOR = new Creator<WifiDeviceFilter>() {
        @Override public WifiDeviceFilter createFromParcel(Parcel source) {
            Builder b = new Builder();
            String pattern = source.readString();
            if (pattern != null) {
                b.setNamePattern(Pattern.compile(pattern));
            }
            String bssid = source.readString();
            if (bssid != null) {
                b.setBssid(MacAddress.fromString(bssid));
            }
            String mask = source.readString();
            if (mask != null) {
                b.setBssidMask(MacAddress.fromString(mask));
            }
            return b.build();
        }

        @Override public WifiDeviceFilter[] newArray(int size) {
            return new WifiDeviceFilter[size];
        }
    };

    public static final class Builder {
        /** AOSP's default mask matches every bit of the BSSID. */
        private static final MacAddress MAC_ADDRESS_MASK_ALL =
                MacAddress.fromString("ff:ff:ff:ff:ff:ff");

        private Pattern mNamePattern;
        private MacAddress mBssid;
        private MacAddress mBssidMask = MAC_ADDRESS_MASK_ALL;

        public Builder() {}

        public Builder setNamePattern(Pattern re) {
            mNamePattern = re;
            return this;
        }

        public Builder setBssid(MacAddress bssid) {
            java.util.Objects.requireNonNull(bssid);
            mBssid = bssid;
            return this;
        }

        public Builder setBssidMask(MacAddress bssidMask) {
            java.util.Objects.requireNonNull(bssidMask);
            mBssidMask = bssidMask;
            return this;
        }

        public WifiDeviceFilter build() {
            return new WifiDeviceFilter(this);
        }
    }
}
