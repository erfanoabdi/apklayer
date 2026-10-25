// android.net.wifi.WifiManager -- Wi-Fi service. Apps reach for it
// to read the connection MAC for analytics. We claim "always on" and
// return the same WifiInfo every time.
package android.net.wifi;

import android.net.DhcpInfo;
import android.os.WorkSource;
import java.util.Collections;
import java.util.List;

public class WifiManager {
    public static final int WIFI_STATE_DISABLING = 0;
    public static final int WIFI_STATE_DISABLED  = 1;
    public static final int WIFI_STATE_ENABLING  = 2;
    public static final int WIFI_STATE_ENABLED   = 3;
    public static final int WIFI_STATE_UNKNOWN   = 4;

    // Action constants -- F-Droid's WifiStateChangeService and similar
    // listeners register IntentFilters with these.
    public static final String WIFI_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_STATE_CHANGED";
    public static final String NETWORK_STATE_CHANGED_ACTION = "android.net.wifi.STATE_CHANGE";
    public static final String SCAN_RESULTS_AVAILABLE_ACTION = "android.net.wifi.SCAN_RESULTS";
    public static final String NETWORK_IDS_CHANGED_ACTION = "android.net.wifi.NETWORK_IDS_CHANGED";
    public static final String SUPPLICANT_STATE_CHANGED_ACTION = "android.net.wifi.supplicant.STATE_CHANGE";
    public static final String SUPPLICANT_CONNECTION_CHANGE_ACTION = "android.net.wifi.supplicant.CONNECTION_CHANGE";
    public static final String CONFIGURED_NETWORKS_CHANGED_ACTION = "android.net.wifi.CONFIGURED_NETWORKS_CHANGE";
    public static final String RSSI_CHANGED_ACTION = "android.net.wifi.RSSI_CHANGED";

    public static final String EXTRA_WIFI_STATE = "wifi_state";
    public static final String EXTRA_PREVIOUS_WIFI_STATE = "previous_wifi_state";
    public static final String EXTRA_NEW_STATE = "newState";
    public static final String EXTRA_NETWORK_INFO = "networkInfo";
    public static final String EXTRA_BSSID = "bssid";
    public static final String EXTRA_WIFI_INFO = "wifiInfo";
    public static final String EXTRA_NEW_RSSI = "newRssi";
    public static final String EXTRA_SUPPLICANT_CONNECTED = "connected";

    public static final int WIFI_MODE_FULL = 1;
    public static final int WIFI_MODE_SCAN_ONLY = 2;
    public static final int WIFI_MODE_FULL_HIGH_PERF = 3;
    public static final int WIFI_MODE_FULL_LOW_LATENCY = 4;

    private final WifiInfo info = new WifiInfo();
    private final DhcpInfo dhcp = new DhcpInfo();

    public WifiManager() {}

    public boolean isWifiEnabled()           { return true; }
    public int     getWifiState()            { return WIFI_STATE_ENABLED; }
    public boolean setWifiEnabled(boolean on) { return false; }

    public WifiInfo getConnectionInfo()       { return info; }
    // AOSP: getDhcpInfo returns the current DHCP-supplied connection
    // info as a DhcpInfo (all fields are zero on macOS since we don't
    // expose underlying DHCP state).
    public DhcpInfo getDhcpInfo()             { return dhcp; }
    public List<?>  getScanResults()          { return Collections.emptyList(); }
    public boolean  startScan()               { return false; }
    public boolean  reconnect()               { return false; }
    public boolean  reassociate()             { return false; }
    public boolean  disconnect()              { return false; }
    public boolean  pingSupplicant()          { return false; }
    public boolean  saveConfiguration()       { return false; }
    public List<?>  getConfiguredNetworks()   { return Collections.emptyList(); }
    // AOSP: addNetwork(WifiConfiguration)/updateNetwork(WifiConfiguration) --
    // verified against android-emulator/android-sdk/platforms/android-30/android.jar
    // (`javap -p android.net.wifi.WifiManager`): both take the concrete
    // WifiConfiguration type, not Object. A guest javac-compiles a call site as
    // invokevirtual .../WifiManager.addNetwork(Landroid/net/wifi/WifiConfiguration;)I;
    // our previous `(Ljava/lang/Object;)I` descriptor does not match that call site
    // at all, so it threw NoSuchMethodError before this method could ever run (same
    // descriptor-mismatch bug class as the v1.0.406 NetworkRequest/ConnectivityManager
    // fixes). AOSP returns -1 ("network could not be added") on failure, matching the
    // "no supplicant here" answer this class already gives every other Wi-Fi control.
    public int      addNetwork(WifiConfiguration config) { return -1; }
    public int      updateNetwork(WifiConfiguration config) { return -1; }
    public boolean  removeNetwork(int id)     { return false; }
    public boolean  enableNetwork(int id, boolean disableOthers) { return false; }
    public boolean  disableNetwork(int id)    { return false; }
    public boolean  is5GHzBandSupported()     { return true; }
    public boolean  isP2pSupported()          { return false; }
    public boolean  isScanAlwaysAvailable()   { return false; }

    public WifiLock createWifiLock(int lockType, String tag) { return new WifiLock(); }
    public WifiLock createWifiLock(String tag)               { return new WifiLock(); }

    public MulticastLock createMulticastLock(String tag) { return new MulticastLock(); }

    public static class WifiLock {
        public void acquire() {}
        public void release() {}
        public boolean isHeld() { return false; }
        public void setReferenceCounted(boolean on) {}
        // AOSP: setWorkSource(android.os.WorkSource) -- verified against
        // android-emulator/android-sdk/platforms/android-30/android.jar
        // (`javap -p android.net.wifi.WifiManager$WifiLock`). Same
        // descriptor-mismatch bug class as addNetwork/updateNetwork above: a
        // guest's invokevirtual site carries the WorkSource descriptor and
        // would NoSuchMethodError against the old Object-typed overload.
        public void setWorkSource(WorkSource ws) {}
    }

    public static class MulticastLock {
        public void acquire() {}
        public void release() {}
        public boolean isHeld() { return false; }
        public void setReferenceCounted(boolean on) {}
    }

    public static int calculateSignalLevel(int rssi, int numLevels) {
        if (rssi <= -100) return 0;
        if (rssi >= -55) return numLevels - 1;
        float inputRange = (-55) - (-100);
        float outputRange = numLevels - 1;
        return (int) ((rssi - (-100)) * outputRange / inputRange);
    }
}

