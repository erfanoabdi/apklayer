// android.net.wifi.WifiNetworkSpecifier
//
// A NetworkSpecifier that requests a peer-to-peer connection to a specific
// Wi-Fi network (the API 29+ "connect to this AP" flow). Mac.apk has no Wi-Fi
// stack, so a request carrying one can never be satisfied, but apps build it
// via the Builder and attach it to a NetworkRequest, so the type + Builder must
// resolve. Mirrors AOSP android.net.wifi.WifiNetworkSpecifier public API.
package android.net.wifi;

import android.net.MacAddress;
import android.net.NetworkSpecifier;
import android.os.PatternMatcher;

public final class WifiNetworkSpecifier extends NetworkSpecifier {

    private final String mSsid;
    private final String mBssid;

    private WifiNetworkSpecifier(String ssid, String bssid) {
        mSsid = ssid;
        mBssid = bssid;
    }

    public String getSsid() { return mSsid; }

    public static final class Builder {
        private String mSsid;
        private String mSsidPattern;
        private MacAddress mBssid;
        private String mWpa2Passphrase;
        private String mWpa3Passphrase;

        public Builder() {}

        public Builder setSsid(String ssid) { mSsid = ssid; return this; }
        // AOSP: setSsidPattern(android.os.PatternMatcher) -- verified against
        // android-emulator/android-sdk/platforms/android-30/android.jar
        // (`javap -p android.net.wifi.WifiNetworkSpecifier$Builder`). The prior
        // `Object`-typed overload does not match a guest's javac-emitted
        // invokevirtual descriptor (Landroid/os/PatternMatcher;) -- same
        // descriptor-mismatch bug class as the NsdManager/WifiManager fixes in
        // this same pass. PatternMatcher.getPattern() gives back the raw pattern
        // string for our existing String-typed mSsidPattern field.
        public Builder setSsidPattern(PatternMatcher ssidPattern) {
            mSsidPattern = ssidPattern == null ? null : ssidPattern.getPattern();
            return this;
        }
        public Builder setBssid(MacAddress bssid) { mBssid = bssid; return this; }
        public Builder setWpa2Passphrase(String passphrase) {
            mWpa2Passphrase = passphrase; return this;
        }
        public Builder setWpa3Passphrase(String passphrase) {
            mWpa3Passphrase = passphrase; return this;
        }
        public Builder setIsEnhancedOpen(boolean isEnhancedOpen) { return this; }
        public Builder setIsHiddenSsid(boolean isHiddenSsid) { return this; }

        public WifiNetworkSpecifier build() {
            return new WifiNetworkSpecifier(mSsid, mBssid == null ? null : mBssid.toString());
        }
    }
}
