// android.net.wifi.WifiInfo -- snapshot of the active Wi-Fi connection.
// Apps fingerprint by MAC address; we hand back a steady stub.
package android.net.wifi;

public class WifiInfo implements android.net.TransportInfo {
    /** AOSP WifiInfo sentinel constants. */
    public static final int LINK_SPEED_UNKNOWN = -1;
    public static final int INVALID_RSSI = -127;
    /** AOSP returns this for getSSID() when the SSID is unavailable. */
    public static final String UNKNOWN_SSID = "<unknown ssid>";

    public WifiInfo() {}
    public String getMacAddress()  { return "02:00:00:00:00:00"; }
    public String getBSSID()       { return ""; }
    // AOSP returns the SSID wrapped in double-quotes for UTF-8 SSIDs; an empty
    // string here keeps callers that strip quotes happy while reporting "no
    // joined network identity surfaced".
    public String getSSID()        { return UNKNOWN_SSID; }
    public int    getRssi()        { return -60; }
    public int    getLinkSpeed()   { return 100; }     // Mbps
    public int    getTxLinkSpeedMbps() { return 100; }
    public int    getRxLinkSpeedMbps() { return 100; }
    /** AOSP getFrequency(): the channel frequency in MHz (0 if unknown).
     *  We report a typical 2.4 GHz channel-1 center so range checks pass. */
    public int    getFrequency()   { return 2412; }
    public int    getNetworkId()   { return -1; }
    /** AOSP getIpAddress() (deprecated): the IPv4 address as a little-endian
     *  int, 0 if unknown. We don't surface the host IP here -> 0. */
    public int    getIpAddress()   { return 0; }
    public boolean getHiddenSSID() { return false; }
    /** AOSP getSupplicantState(): the wpa_supplicant association state.
     *  SupplicantState.java is already a complete, correctly-shaped enum;
     *  COMPLETED is the honest answer given every other accessor on this
     *  class (getRssi/getLinkSpeed/getSSID) already reports a healthy,
     *  connected network -- DISCONNECTED or UNINITIALIZED would contradict
     *  them and break "is Wi-Fi actually associated" gates some SDKs run
     *  before assuming the network is usable. */
    public SupplicantState getSupplicantState() { return SupplicantState.COMPLETED; }
}
