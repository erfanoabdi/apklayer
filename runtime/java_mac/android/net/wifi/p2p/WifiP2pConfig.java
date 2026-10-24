// android.net.wifi.p2p.WifiP2pConfig -- the parameters for a Wi-Fi Direct
// connect() request.
//
// A REAL value object: AOSP exposes public FIELDS and apps set them directly
// (Signal sets deviceAddress, wps and groupOwnerIntent). Building a config
// succeeds here exactly as on Android; it is connect() that reports failure,
// because there is no Wi-Fi Direct radio to connect with.
//
// The GROUP_OWNER_INTENT_* and GROUP_OWNER_BAND_* constants carry AOSP's exact
// values, and groupOwnerIntent defaults to GROUP_OWNER_INTENT_AUTO (-1) as on
// AOSP -- defaulting it to 0 would silently mean "never be group owner".
//
// Mirrors wifi/java/android/net/wifi/p2p/WifiP2pConfig.java (android11-release).
package android.net.wifi.p2p;

import android.net.wifi.WpsInfo;
import android.os.Parcel;
import android.os.Parcelable;

public class WifiP2pConfig implements Parcelable {

    public String deviceAddress = "";
    public WpsInfo wps;
    public String networkName = "";
    public String passphrase = "";

    public static final int GROUP_OWNER_BAND_AUTO = 0;
    public static final int GROUP_OWNER_BAND_2GHZ = 1;
    public static final int GROUP_OWNER_BAND_5GHZ = 2;

    public int groupOwnerBand = GROUP_OWNER_BAND_AUTO;

    public static final int GROUP_OWNER_INTENT_MIN = 0;
    public static final int GROUP_OWNER_INTENT_MAX = 15;
    public static final int GROUP_OWNER_INTENT_AUTO = -1;

    public int groupOwnerIntent = GROUP_OWNER_INTENT_AUTO;

    /** @hide on AOSP. */
    public int netId = WifiP2pGroup.NETWORK_ID_PERSISTENT;

    public WifiP2pConfig() {
        wps = new WpsInfo();
        wps.setup = WpsInfo.PBC;
    }

    public WifiP2pConfig(WifiP2pConfig source) {
        if (source != null) {
            deviceAddress = source.deviceAddress;
            wps = new WpsInfo(source.wps);
            groupOwnerIntent = source.groupOwnerIntent;
            netId = source.netId;
            networkName = source.networkName;
            passphrase = source.passphrase;
            groupOwnerBand = source.groupOwnerBand;
        }
    }

    public String getNetworkName() { return networkName; }

    public String getPassphrase() { return passphrase; }

    public int getGroupOwnerBand() { return groupOwnerBand; }

    /** @hide on AOSP. */
    public int getNetworkId() { return netId; }

    @Override public String toString() {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("\n address: ").append(deviceAddress);
        sbuf.append("\n wps: ").append(wps);
        sbuf.append("\n groupOwnerIntent: ").append(groupOwnerIntent);
        sbuf.append("\n persist: ").append(netId);
        sbuf.append("\n networkName: ").append(networkName);
        sbuf.append("\n groupOwnerBand: ").append(groupOwnerBand);
        return sbuf.toString();
    }

    @Override public int describeContents() { return 0; }

    // AOSP writes the WpsInfo with writeParcelable. android.net.wifi.WpsInfo
    // in THIS tree is a plain class that does not implement Parcelable, so its
    // three public fields are marshalled INLINE instead. That is a faithful
    // round trip -- a WpsInfo is exactly (setup, BSSID, pin) -- rather than a
    // dropped field, so nothing is lost; only the wire FORMAT differs from
    // AOSP's, which is unobservable because nothing outside this process ever
    // reads these bytes (there is no Wi-Fi P2P service to send them to).
    //
    // REMOVAL CONDITION: when android/net/wifi/WpsInfo.java implements
    // Parcelable, collapse these three writes back to writeParcelable(wps,
    // flags). That file is outside this lane's ownership, so it is reported
    // rather than changed here.
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(deviceAddress);
        dest.writeInt(wps == null ? -1 : wps.setup);
        dest.writeString(wps == null ? null : wps.BSSID);
        dest.writeString(wps == null ? null : wps.pin);
        dest.writeInt(groupOwnerIntent);
        dest.writeInt(netId);
        dest.writeString(networkName);
        dest.writeString(passphrase);
        dest.writeInt(groupOwnerBand);
    }

    public static final Creator<WifiP2pConfig> CREATOR = new Creator<WifiP2pConfig>() {
        @Override public WifiP2pConfig createFromParcel(Parcel in) {
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = in.readString();
            // Mirror of the inline WpsInfo marshalling in writeToParcel above.
            int wpsSetup = in.readInt();
            String wpsBssid = in.readString();
            String wpsPin = in.readString();
            if (wpsSetup >= 0) {
                config.wps = new WpsInfo();
                config.wps.setup = wpsSetup;
                config.wps.BSSID = wpsBssid;
                config.wps.pin = wpsPin;
            } else {
                config.wps = null;
            }
            config.groupOwnerIntent = in.readInt();
            config.netId = in.readInt();
            config.networkName = in.readString();
            config.passphrase = in.readString();
            config.groupOwnerBand = in.readInt();
            return config;
        }

        @Override public WifiP2pConfig[] newArray(int size) { return new WifiP2pConfig[size]; }
    };

    /** AOSP's API-29 builder. Value-object only; no device dependency. */
    public static final class Builder {
        private String mDeviceAddress = "";
        private String mNetworkName = "";
        private String mPassphrase = "";
        private int mGroupOwnerBand = GROUP_OWNER_BAND_AUTO;
        private int mNetId = WifiP2pGroup.NETWORK_ID_PERSISTENT;

        public Builder setDeviceAddress(android.net.MacAddress deviceAddress) {
            mDeviceAddress = deviceAddress == null ? "" : deviceAddress.toString();
            return this;
        }

        public Builder setNetworkName(String networkName) {
            mNetworkName = networkName;
            return this;
        }

        public Builder setPassphrase(String passphrase) {
            mPassphrase = passphrase;
            return this;
        }

        public Builder setGroupOperatingBand(int band) {
            mGroupOwnerBand = band;
            return this;
        }

        public Builder setGroupOperatingFrequency(int frequency) {
            mGroupOwnerBand = frequency;
            return this;
        }

        public Builder enablePersistentMode(boolean persistent) {
            mNetId = persistent
                    ? WifiP2pGroup.NETWORK_ID_PERSISTENT : WifiP2pGroup.NETWORK_ID_TEMPORARY;
            return this;
        }

        public WifiP2pConfig build() {
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = mDeviceAddress;
            config.networkName = mNetworkName;
            config.passphrase = mPassphrase;
            config.groupOwnerBand = mGroupOwnerBand;
            config.netId = mNetId;
            return config;
        }
    }
}
