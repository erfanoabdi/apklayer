// android.net.NetworkCapabilities -- the bitset describing what a network can
// do (internet, validated, not-metered, etc.). Apps query hasCapability(...) /
// hasTransport(...) to decide if a network is suitable.
//
// The TRANSPORT is now the host's real one: MacApkHostNetwork reads which
// interface carries the default route and asks macOS's hardware-port table
// whether that is Wi-Fi or Ethernet, so a wired Mac reports TRANSPORT_ETHERNET
// instead of claiming Wi-Fi. (See MacApkHostNetwork for the probe order and
// the one fallback.)
//
// The CAPABILITY bits are still a healthy-network claim, and that is a known
// remaining approximation: NET_CAPABILITY_VALIDATED in particular means "the
// platform proved this network reaches the internet", and nothing here runs
// that probe -- we infer it from the presence of a default route. Same for
// NOT_METERED (macOS does surface a per-interface "low data mode", which we do
// not read yet) and the bandwidth estimates below.
package android.net;

public class NetworkCapabilities {
    // Full NET_CAPABILITY_* set with AOSP-exact values (verified against
    // packages/modules/Connectivity/.../NetworkCapabilities.java). Apps build
    // NetworkRequests and inspect capabilities by these literals, so the
    // numeric values must match exactly.
    public static final int NET_CAPABILITY_MMS          = 0;
    public static final int NET_CAPABILITY_SUPL         = 1;
    public static final int NET_CAPABILITY_DUN          = 2;
    public static final int NET_CAPABILITY_FOTA         = 3;
    public static final int NET_CAPABILITY_IMS          = 4;
    public static final int NET_CAPABILITY_CBS          = 5;
    public static final int NET_CAPABILITY_WIFI_P2P     = 6;
    public static final int NET_CAPABILITY_IA           = 7;
    public static final int NET_CAPABILITY_RCS          = 8;
    public static final int NET_CAPABILITY_XCAP         = 9;
    public static final int NET_CAPABILITY_EIMS         = 10;
    public static final int NET_CAPABILITY_NOT_METERED  = 11;
    public static final int NET_CAPABILITY_INTERNET     = 12;
    public static final int NET_CAPABILITY_NOT_RESTRICTED = 13;
    public static final int NET_CAPABILITY_TRUSTED      = 14;
    public static final int NET_CAPABILITY_NOT_VPN      = 15;
    public static final int NET_CAPABILITY_VALIDATED    = 16;
    public static final int NET_CAPABILITY_CAPTIVE_PORTAL = 17;
    public static final int NET_CAPABILITY_NOT_ROAMING  = 18;
    public static final int NET_CAPABILITY_FOREGROUND   = 19;
    public static final int NET_CAPABILITY_NOT_CONGESTED = 20;
    public static final int NET_CAPABILITY_NOT_SUSPENDED = 21;
    public static final int NET_CAPABILITY_OEM_PAID     = 22;
    public static final int NET_CAPABILITY_MCX          = 23;
    public static final int NET_CAPABILITY_PARTIAL_CONNECTIVITY = 24;
    public static final int NET_CAPABILITY_TEMPORARILY_NOT_METERED = 25;
    public static final int NET_CAPABILITY_OEM_PRIVATE  = 26;
    public static final int NET_CAPABILITY_VEHICLE_INTERNAL = 27;
    public static final int NET_CAPABILITY_NOT_VCN_MANAGED = 28;
    public static final int NET_CAPABILITY_ENTERPRISE   = 29;
    public static final int NET_CAPABILITY_VSIM         = 30;
    public static final int NET_CAPABILITY_BIP          = 31;
    public static final int NET_CAPABILITY_HEAD_UNIT    = 32;
    public static final int NET_CAPABILITY_MMTEL        = 33;
    public static final int NET_CAPABILITY_PRIORITIZE_LATENCY = 34;
    public static final int NET_CAPABILITY_PRIORITIZE_BANDWIDTH = 35;
    public static final int NET_CAPABILITY_LOCAL_NETWORK = 36;
    public static final int NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED = 37;

    public static final int TRANSPORT_CELLULAR  = 0;
    public static final int TRANSPORT_WIFI      = 1;
    public static final int TRANSPORT_BLUETOOTH = 2;
    public static final int TRANSPORT_ETHERNET  = 3;
    public static final int TRANSPORT_VPN       = 4;
    public static final int TRANSPORT_WIFI_AWARE = 5;
    public static final int TRANSPORT_LOWPAN    = 6;
    public static final int TRANSPORT_USB       = 8;
    public static final int TRANSPORT_THREAD    = 9;
    public static final int TRANSPORT_SATELLITE = 10;

    /** AOSP getSignalStrength() returns this when no RSSI is known. */
    public static final int SIGNAL_STRENGTH_UNSPECIFIED = Integer.MIN_VALUE;

    public NetworkCapabilities() {}
    public NetworkCapabilities(NetworkCapabilities other) {}

    public boolean hasCapability(int capability) {
        switch (capability) {
            case NET_CAPABILITY_INTERNET:
            case NET_CAPABILITY_VALIDATED:
            case NET_CAPABILITY_NOT_METERED:
            case NET_CAPABILITY_NOT_RESTRICTED:
            case NET_CAPABILITY_TRUSTED:
            case NET_CAPABILITY_NOT_VPN:
            case NET_CAPABILITY_FOREGROUND:
            // A healthy always-on host network: also "not roaming / not
            // congested / not suspended / not VCN-managed", which connectivity
            // gates frequently require before treating a network as usable.
            case NET_CAPABILITY_NOT_ROAMING:
            case NET_CAPABILITY_NOT_CONGESTED:
            case NET_CAPABILITY_NOT_SUSPENDED:
            case NET_CAPABILITY_NOT_VCN_MANAGED:
                return true;
            default:
                return false;
        }
    }
    /** The host's real default-route transport, not a constant. */
    public boolean hasTransport(int transportType) {
        return transportType == macapkTransport();
    }
    public int[] getCapabilities() {
        // The set hasCapability() reports true for, in ascending order.
        return new int[] {
            NET_CAPABILITY_NOT_METERED, NET_CAPABILITY_INTERNET,
            NET_CAPABILITY_NOT_RESTRICTED, NET_CAPABILITY_TRUSTED,
            NET_CAPABILITY_NOT_VPN, NET_CAPABILITY_VALIDATED,
            NET_CAPABILITY_NOT_ROAMING, NET_CAPABILITY_FOREGROUND,
            NET_CAPABILITY_NOT_CONGESTED, NET_CAPABILITY_NOT_SUSPENDED,
            NET_CAPABILITY_NOT_VCN_MANAGED
        };
    }
    public int[] getTransportTypes() { return new int[] { macapkTransport() }; }

    /** Mac.apk-internal: the transport carrying the host's default route. */
    static int macapkTransport() {
        return MacApkHostNetwork.get().transport;
    }

    public int   getLinkUpstreamBandwidthKbps()   { return 100_000; }
    public int   getLinkDownstreamBandwidthKbps() { return 100_000; }

    /** AOSP getSignalStrength(): RSSI in dBm, or SIGNAL_STRENGTH_UNSPECIFIED
     *  when unknown. We don't surface a host RSSI, so report "unspecified"
     *  (the documented no-data value) rather than a fabricated number. */
    public int getSignalStrength() { return SIGNAL_STRENGTH_UNSPECIFIED; }

    /** AOSP getTransportInfo(): transport-specific info object (e.g. WifiInfo)
     *  or null. We model an opaque WIFI transport with no extra info -> null,
     *  the legal "no transport info" return. */
    public TransportInfo getTransportInfo() { return null; }

    /** AOSP getNetworkSpecifier(): the specifier the request/network was
     *  matched on, or null for the default network (which carries none). */
    public NetworkSpecifier getNetworkSpecifier() { return null; }

    /** AOSP getOwnerUid(): the UID that owns this network (e.g. a VPN app), or
     *  Process.INVALID_UID (-1) when there is no owner. Our network has no
     *  owner. */
    public int getOwnerUid() { return -1; }
}
