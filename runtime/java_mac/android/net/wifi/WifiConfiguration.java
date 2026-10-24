// android.net.wifi.WifiConfiguration -- a saved Wi-Fi network's configuration.
//
// A REAL, COMPLETE value type, and this one matters even though Mac.apk has no
// guest Wi-Fi control. A WifiConfiguration is built ENTIRELY by the app:
//
//     WifiConfiguration c = new WifiConfiguration();
//     c.SSID = "\"net\"";
//     c.preSharedKey = "\"secret\"";
//     c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
//
// Every one of those is a plain field or a plain BitSet, with no device
// involved. This file used to be an auto-generated stub carrying THREE fields,
// so that third line threw NoSuchFieldError on `allowedKeyManagement` and
// KeyMgmt did not exist at all -- an app crashed while merely DESCRIBING a
// network, long before it asked the platform to do anything with it.
//
// What remains honestly absent is WifiManager: adding or enabling the network
// is what needs a Wi-Fi stack we do not bridge, and that is where the refusal
// belongs.
//
// The BitSets are initialised non-null (as AOSP does), because `.set(...)` on
// a null BitSet is the crash this class exists to avoid.
//
// Mirrors packages/modules/Wifi framework/java/android/net/wifi/
// WifiConfiguration.java; public surface and constant values confirmed against
// the android.jar API-30 reference in android-emulator/.
package android.net.wifi;

import android.net.MacAddress;
import android.net.ProxyInfo;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.BitSet;

public class WifiConfiguration implements Parcelable {

    public static final int SECURITY_TYPE_OPEN = 0;
    public static final int SECURITY_TYPE_WEP = 1;
    public static final int SECURITY_TYPE_PSK = 2;
    public static final int SECURITY_TYPE_EAP = 3;
    public static final int SECURITY_TYPE_SAE = 4;
    public static final int SECURITY_TYPE_EAP_SUITE_B = 5;
    public static final int SECURITY_TYPE_OWE = 6;
    public static final int SECURITY_TYPE_WAPI_PSK = 7;
    public static final int SECURITY_TYPE_WAPI_CERT = 8;

    /** Recognized key-management schemes. */
    public static class KeyMgmt {
        private KeyMgmt() {}

        public static final int NONE = 0;
        public static final int WPA_PSK = 1;
        public static final int WPA_EAP = 2;
        public static final int IEEE8021X = 3;
        public static final int SAE = 8;
        public static final int OWE = 9;
        public static final int SUITE_B_192 = 10;

        public static final String varName = "key_mgmt";

        public static final String[] strings = {
                "NONE", "WPA_PSK", "WPA_EAP", "IEEE8021X", "WPA2_PSK", "OSEN", "FT_PSK",
                "FT_EAP", "SAE", "OWE", "SUITE_B_192", "WPA_PSK_SHA256", "WPA_EAP_SHA256",
                "WAPI_PSK", "WAPI_CERT", "FILS_SHA256", "FILS_SHA384" };
    }

    /** Recognized security protocols. */
    public static class Protocol {
        private Protocol() {}

        public static final int WPA = 0;
        public static final int RSN = 1;
        public static final int WAPI = 3;

        public static final String varName = "proto";

        public static final String[] strings = { "WPA", "RSN", "OSEN", "WAPI" };
    }

    /** Recognized IEEE 802.11 authentication algorithms. */
    public static class AuthAlgorithm {
        private AuthAlgorithm() {}

        public static final int OPEN = 0;
        public static final int SHARED = 1;
        public static final int LEAP = 2;
        public static final int SAE = 3;

        public static final String varName = "auth_alg";

        public static final String[] strings = { "OPEN", "SHARED", "LEAP", "SAE" };
    }

    /** Recognized pairwise ciphers for WPA. */
    public static class PairwiseCipher {
        private PairwiseCipher() {}

        public static final int NONE = 0;
        public static final int TKIP = 1;
        public static final int CCMP = 2;
        public static final int GCMP_256 = 3;
        public static final int SMS4 = 4;

        public static final String varName = "pairwise";

        public static final String[] strings = { "NONE", "TKIP", "CCMP", "GCMP_256", "SMS4" };
    }

    /** Recognized group ciphers. */
    public static class GroupCipher {
        private GroupCipher() {}

        public static final int WEP40 = 0;
        public static final int WEP104 = 1;
        public static final int TKIP = 2;
        public static final int CCMP = 3;
        public static final int GTK_NOT_USED = 4;
        public static final int GCMP_256 = 5;
        public static final int SMS4 = 6;

        public static final String varName = "group";

        public static final String[] strings = { "WEP40", "WEP104", "TKIP", "CCMP",
                "GTK_NOT_USED", "GCMP_256", "SMS4" };
    }

    /** Recognized group-management ciphers (802.11w). */
    public static class GroupMgmtCipher {
        private GroupMgmtCipher() {}

        public static final int BIP_CMAC_256 = 0;
        public static final int BIP_GMAC_128 = 1;
        public static final int BIP_GMAC_256 = 2;
    }

    /** Possible status of a network configuration. */
    public static class Status {
        private Status() {}

        public static final int CURRENT = 0;
        public static final int DISABLED = 1;
        public static final int ENABLED = 2;

        public static final String[] strings = { "current", "disabled", "enabled" };
    }

    public int networkId;
    public int status;

    /**
     * AOSP's contract, and a trap worth naming: the SSID is stored with
     * SURROUNDING DOUBLE QUOTES when it is UTF-8, and unquoted when it is hex.
     * We store exactly what the app hands us, so its own round trip is
     * faithful.
     */
    public String SSID;
    public String BSSID;
    public String preSharedKey;
    public String[] wepKeys = new String[4];
    public int wepTxKeyIndex;
    public int priority;
    public boolean hiddenSSID;

    public String FQDN;
    public String providerFriendlyName;
    public boolean isHomeProviderNetwork;
    public long[] roamingConsortiumIds = new long[0];

    // Non-null on purpose: AOSP initialises each of these, and an app's very
    // first action is normally `allowedKeyManagement.set(...)`, which would
    // NPE on a null BitSet.
    public BitSet allowedKeyManagement = new BitSet();
    public BitSet allowedProtocols = new BitSet();
    public BitSet allowedAuthAlgorithms = new BitSet();
    public BitSet allowedPairwiseCiphers = new BitSet();
    public BitSet allowedGroupCiphers = new BitSet();
    public BitSet allowedGroupManagementCiphers = new BitSet();
    public BitSet allowedSuiteBCiphers = new BitSet();

    public WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();

    private ProxyInfo mHttpProxy;

    public WifiConfiguration() {
        networkId = -1;
        status = Status.DISABLED;
        wepTxKeyIndex = 0;
        priority = 0;
        hiddenSSID = false;
    }

    /** AOSP's copy constructor. Deep-copies the BitSets and the key array, so
     *  mutating the copy cannot reach back into the original. */
    public WifiConfiguration(WifiConfiguration source) {
        if (source == null) {
            return;
        }
        networkId = source.networkId;
        status = source.status;
        SSID = source.SSID;
        BSSID = source.BSSID;
        preSharedKey = source.preSharedKey;
        wepKeys = source.wepKeys == null ? new String[4] : source.wepKeys.clone();
        wepTxKeyIndex = source.wepTxKeyIndex;
        priority = source.priority;
        hiddenSSID = source.hiddenSSID;
        FQDN = source.FQDN;
        providerFriendlyName = source.providerFriendlyName;
        isHomeProviderNetwork = source.isHomeProviderNetwork;
        roamingConsortiumIds = source.roamingConsortiumIds == null
                ? new long[0] : source.roamingConsortiumIds.clone();
        allowedKeyManagement = (BitSet) source.allowedKeyManagement.clone();
        allowedProtocols = (BitSet) source.allowedProtocols.clone();
        allowedAuthAlgorithms = (BitSet) source.allowedAuthAlgorithms.clone();
        allowedPairwiseCiphers = (BitSet) source.allowedPairwiseCiphers.clone();
        allowedGroupCiphers = (BitSet) source.allowedGroupCiphers.clone();
        allowedGroupManagementCiphers = (BitSet) source.allowedGroupManagementCiphers.clone();
        allowedSuiteBCiphers = (BitSet) source.allowedSuiteBCiphers.clone();
        enterpriseConfig = source.enterpriseConfig;
        mHttpProxy = source.mHttpProxy;
    }

    /**
     * AOSP's shorthand: clears every allowed-* set and re-populates them for
     * the requested security type. Pure bit manipulation over this object's
     * own fields, so it is implemented for real.
     */
    public void setSecurityParams(int securityType) {
        allowedKeyManagement.clear();
        allowedProtocols.clear();
        allowedAuthAlgorithms.clear();
        allowedPairwiseCiphers.clear();
        allowedGroupCiphers.clear();
        allowedGroupManagementCiphers.clear();
        allowedSuiteBCiphers.clear();

        switch (securityType) {
            case SECURITY_TYPE_OPEN:
                allowedKeyManagement.set(KeyMgmt.NONE);
                break;
            case SECURITY_TYPE_WEP:
                allowedKeyManagement.set(KeyMgmt.NONE);
                allowedAuthAlgorithms.set(AuthAlgorithm.OPEN);
                allowedAuthAlgorithms.set(AuthAlgorithm.SHARED);
                break;
            case SECURITY_TYPE_PSK:
                allowedKeyManagement.set(KeyMgmt.WPA_PSK);
                break;
            case SECURITY_TYPE_EAP:
                allowedKeyManagement.set(KeyMgmt.WPA_EAP);
                allowedKeyManagement.set(KeyMgmt.IEEE8021X);
                break;
            case SECURITY_TYPE_SAE:
                allowedKeyManagement.set(KeyMgmt.SAE);
                allowedPairwiseCiphers.set(PairwiseCipher.GCMP_256);
                allowedGroupCiphers.set(GroupCipher.GCMP_256);
                break;
            case SECURITY_TYPE_EAP_SUITE_B:
                allowedKeyManagement.set(KeyMgmt.SUITE_B_192);
                allowedPairwiseCiphers.set(PairwiseCipher.GCMP_256);
                allowedGroupCiphers.set(GroupCipher.GCMP_256);
                allowedGroupManagementCiphers.set(GroupMgmtCipher.BIP_GMAC_256);
                break;
            case SECURITY_TYPE_OWE:
                allowedKeyManagement.set(KeyMgmt.OWE);
                allowedPairwiseCiphers.set(PairwiseCipher.CCMP);
                allowedGroupCiphers.set(GroupCipher.CCMP);
                break;
            case SECURITY_TYPE_WAPI_PSK:
                allowedKeyManagement.set(KeyMgmt.NONE);
                allowedProtocols.set(Protocol.WAPI);
                allowedPairwiseCiphers.set(PairwiseCipher.SMS4);
                allowedGroupCiphers.set(GroupCipher.SMS4);
                break;
            case SECURITY_TYPE_WAPI_CERT:
                allowedKeyManagement.set(KeyMgmt.NONE);
                allowedProtocols.set(Protocol.WAPI);
                allowedPairwiseCiphers.set(PairwiseCipher.SMS4);
                allowedGroupCiphers.set(GroupCipher.SMS4);
                break;
            default:
                throw new IllegalArgumentException("unknown security type " + securityType);
        }
    }

    /** AOSP: a Passpoint configuration is identified by having an FQDN. */
    public boolean isPasspoint() {
        return FQDN != null && providerFriendlyName != null
                && enterpriseConfig != null
                && allowedKeyManagement.get(KeyMgmt.WPA_EAP);
    }

    /**
     * AOSP returns the per-network randomized MAC. Nothing here randomizes a
     * MAC (there is no Wi-Fi stack to assign one), so this reports AOSP's own
     * default value for "no randomized MAC assigned", 02:00:00:00:00:00 --
     * the same all-zero locally-administered address AOSP uses, and the same
     * one BluetoothAdapter.getAddress() reports for a redacted address. It is
     * not a fabricated hardware address: 02:00:00:00:00:00 is precisely the
     * value that means "none".
     */
    public MacAddress getRandomizedMacAddress() {
        return MacAddress.fromString("02:00:00:00:00:00");
    }

    /** AOSP: the per-network proxy, or null. */
    public ProxyInfo getHttpProxy() { return mHttpProxy; }

    public void setHttpProxy(ProxyInfo httpProxy) { mHttpProxy = httpProxy; }

    /** AOSP's identity key: SSID plus the key-management scheme. */
    public String getKey() {
        String key = SSID == null ? "" : SSID;
        if (allowedKeyManagement.get(KeyMgmt.WPA_PSK)) {
            key = key + KeyMgmt.strings[KeyMgmt.WPA_PSK];
        } else if (allowedKeyManagement.get(KeyMgmt.WPA_EAP)
                || allowedKeyManagement.get(KeyMgmt.IEEE8021X)) {
            key = key + KeyMgmt.strings[KeyMgmt.WPA_EAP];
        } else if (allowedKeyManagement.get(KeyMgmt.SAE)) {
            key = key + KeyMgmt.strings[KeyMgmt.SAE];
        } else if (allowedKeyManagement.get(KeyMgmt.OWE)) {
            key = key + KeyMgmt.strings[KeyMgmt.OWE];
        } else if (allowedKeyManagement.get(KeyMgmt.SUITE_B_192)) {
            key = key + KeyMgmt.strings[KeyMgmt.SUITE_B_192];
        } else {
            key = key + KeyMgmt.strings[KeyMgmt.NONE];
        }
        return key;
    }

    @Override public String toString() {
        return "ID: " + networkId + " SSID: " + SSID + " PROVIDER-NAME: " + providerFriendlyName
                + " BSSID: " + BSSID + " FQDN: " + FQDN;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(networkId);
        dest.writeInt(status);
        dest.writeString(SSID);
        dest.writeString(BSSID);
        dest.writeString(preSharedKey);
        dest.writeStringArray(wepKeys);
        dest.writeInt(wepTxKeyIndex);
        dest.writeInt(priority);
        dest.writeInt(hiddenSSID ? 1 : 0);
        dest.writeString(FQDN);
        dest.writeString(providerFriendlyName);
        dest.writeInt(isHomeProviderNetwork ? 1 : 0);
        dest.writeLongArray(roamingConsortiumIds);
        writeBitSet(dest, allowedKeyManagement);
        writeBitSet(dest, allowedProtocols);
        writeBitSet(dest, allowedAuthAlgorithms);
        writeBitSet(dest, allowedPairwiseCiphers);
        writeBitSet(dest, allowedGroupCiphers);
        writeBitSet(dest, allowedGroupManagementCiphers);
        writeBitSet(dest, allowedSuiteBCiphers);
    }

    private static void writeBitSet(Parcel dest, BitSet set) {
        dest.writeLongArray(set.toLongArray());
    }

    private static BitSet readBitSet(Parcel in) {
        long[] words = in.createLongArray();
        return words == null ? new BitSet() : BitSet.valueOf(words);
    }

    public static final Creator<WifiConfiguration> CREATOR = new Creator<WifiConfiguration>() {
        @Override public WifiConfiguration createFromParcel(Parcel in) {
            WifiConfiguration c = new WifiConfiguration();
            c.networkId = in.readInt();
            c.status = in.readInt();
            c.SSID = in.readString();
            c.BSSID = in.readString();
            c.preSharedKey = in.readString();
            c.wepKeys = in.createStringArray();
            c.wepTxKeyIndex = in.readInt();
            c.priority = in.readInt();
            c.hiddenSSID = in.readInt() != 0;
            c.FQDN = in.readString();
            c.providerFriendlyName = in.readString();
            c.isHomeProviderNetwork = in.readInt() != 0;
            c.roamingConsortiumIds = in.createLongArray();
            c.allowedKeyManagement = readBitSet(in);
            c.allowedProtocols = readBitSet(in);
            c.allowedAuthAlgorithms = readBitSet(in);
            c.allowedPairwiseCiphers = readBitSet(in);
            c.allowedGroupCiphers = readBitSet(in);
            c.allowedGroupManagementCiphers = readBitSet(in);
            c.allowedSuiteBCiphers = readBitSet(in);
            return c;
        }

        @Override public WifiConfiguration[] newArray(int size) {
            return new WifiConfiguration[size];
        }
    };
}
