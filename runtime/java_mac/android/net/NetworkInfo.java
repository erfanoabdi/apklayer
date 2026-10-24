// android.net.NetworkInfo -- the "current network" snapshot returned
// by ConnectivityManager.
package android.net;

public class NetworkInfo {
    public enum State { CONNECTED, CONNECTING, DISCONNECTED, DISCONNECTING, UNKNOWN }

    private final int type;
    private final State state;

    public NetworkInfo() { this(ConnectivityManager.TYPE_WIFI, State.CONNECTED); }
    public NetworkInfo(int type, State state) {
        this.type = type;
        this.state = state;
    }

    public int    getType()        { return type; }
    // getSubtype(): for a mobile network this is the TelephonyManager
    // NETWORK_TYPE_* value; for non-mobile (our claimed WIFI) AOSP returns 0
    // (NETWORK_TYPE_UNKNOWN). Firebase's datatransport CctTransportBackend
    // (getNetSubtypeValue) calls this on every telemetry upload -- a missing
    // method threw NoSuchMethodError on a background thread and Crashlytics
    // (ReportUncaughtExceptionsAsFatal) halted the whole process.
    public int    getSubtype()     { return 0; }
    public String getTypeName()    { return type == ConnectivityManager.TYPE_WIFI ? "WIFI" : "MOBILE"; }
    public String getSubtypeName() { return ""; }
    // Pre-API-29 metadata accessors several analytics SDKs read; AOSP returns
    // null/false for an always-up non-failover network.
    public String getReason()     { return null; }
    public String getExtraInfo()  { return null; }
    public boolean isFailover()   { return false; }
    public State  getState()       { return state; }
    public boolean isConnected()       { return state == State.CONNECTED; }
    public boolean isConnectedOrConnecting() { return state == State.CONNECTED || state == State.CONNECTING; }
    public boolean isAvailable()       { return true; }
    public boolean isRoaming()         { return false; }
    public DetailedState getDetailedState() { return DetailedState.CONNECTED; }

    public enum DetailedState {
        IDLE, SCANNING, CONNECTING, AUTHENTICATING, OBTAINING_IPADDR,
        CONNECTED, SUSPENDED, DISCONNECTING, DISCONNECTED, FAILED, BLOCKED, VERIFYING_POOR_LINK,
        CAPTIVE_PORTAL_CHECK
    }
}
