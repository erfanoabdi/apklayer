// android.net.wifi.p2p.WifiP2pManager -- Wi-Fi Direct (peer-to-peer).
//
// A Mac has no Wi-Fi Direct radio: macOS exposes AWDL/AirDrop, which is not
// Wi-Fi Direct and is not reachable through this API. So every answer here is
// the answer for a device whose P2P support is absent.
//
// ===================================================================
// NEVER SUCCEEDS INERTLY, AND NEVER GOES SILENT.
// ===================================================================
//
// Two distinct failure shapes matter, and AOSP's contract covers both:
//
//   1. ACTION calls (discoverPeers, connect, addLocalService, ...) take an
//      ActionListener and AOSP guarantees exactly one of onSuccess/onFailure.
//      Each one here calls onFailure(P2P_UNSUPPORTED) -- AOSP's own reason
//      code for "P2P is unsupported on the device", which is precisely true.
//
//   2. REQUEST calls (requestPeers, requestGroupInfo, requestConnectionInfo)
//      take an information listener and AOSP guarantees it is invoked. An
//      earlier version of this file left them as empty method bodies, so the
//      listener NEVER fired -- an app waiting on peer results simply hung,
//      with no error to diagnose. They now deliver the honest empty result:
//      an empty WifiP2pDeviceList, a null WifiP2pGroup (AOSP's answer when no
//      group has formed), and a WifiP2pInfo with groupFormed = false.
//
// A listener that is never called is the same defect as a fabricated success:
// in both cases the app is told nothing and cannot fall back.
//
// The callbacks are posted through the Channel's Handler rather than run
// inline, because AOSP delivers them on the Looper passed to initialize() and
// an app may touch UI state from them.
//
// Mirrors wifi/java/android/net/wifi/p2p/WifiP2pManager.java (android11-release).
// The listener signatures now carry AOSP's real parameter types; they
// previously took Object, which meant an app's override did not actually
// override anything.
package android.net.wifi.p2p;

import android.content.Context;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.os.Handler;
import android.os.Looper;

public class WifiP2pManager {

    /** Operation failed for an internal reason. */
    public static final int ERROR = 0;
    /** Operation failed because P2P is unsupported on the device. */
    public static final int P2P_UNSUPPORTED = 1;
    /** Operation failed because the framework is busy. */
    public static final int BUSY = 2;
    /** Operation failed because no service requests were added. */
    public static final int NO_SERVICE_REQUESTS = 3;

    public static final String WIFI_P2P_STATE_CHANGED_ACTION =
            "android.net.wifi.p2p.STATE_CHANGED";
    public static final String EXTRA_WIFI_STATE = "wifi_p2p_state";
    public static final int WIFI_P2P_STATE_DISABLED = 1;
    public static final int WIFI_P2P_STATE_ENABLED = 2;
    public static final String WIFI_P2P_PEERS_CHANGED_ACTION =
            "android.net.wifi.p2p.PEERS_CHANGED";
    public static final String EXTRA_P2P_DEVICE_LIST = "wifiP2pDeviceList";
    public static final String WIFI_P2P_CONNECTION_CHANGED_ACTION =
            "android.net.wifi.p2p.CONNECTION_STATE_CHANGE";
    public static final String EXTRA_WIFI_P2P_INFO = "wifiP2pInfo";
    public static final String EXTRA_NETWORK_INFO = "networkInfo";
    public static final String WIFI_P2P_THIS_DEVICE_CHANGED_ACTION =
            "android.net.wifi.p2p.THIS_DEVICE_CHANGED";
    public static final String EXTRA_WIFI_P2P_DEVICE = "wifiP2pDevice";
    public static final String WIFI_P2P_DISCOVERY_CHANGED_ACTION =
            "android.net.wifi.p2p.DISCOVERY_STATE_CHANGE";
    public static final String EXTRA_DISCOVERY_STATE = "discoveryState";
    public static final int WIFI_P2P_DISCOVERY_STARTED = 2;
    public static final int WIFI_P2P_DISCOVERY_STOPPED = 1;

    /**
     * The per-app P2P channel handle. AOSP's holds a Messenger to the P2P
     * service; ours holds only the Looper it must deliver callbacks on.
     */
    public static class Channel implements java.lang.AutoCloseable {
        private final Handler mHandler;

        Channel(Looper looper) {
            mHandler = new Handler(looper != null ? looper : Looper.getMainLooper());
        }

        void post(Runnable r) {
            mHandler.post(r);
        }

        /**
         * AOSP (API 27+) releases the channel's binding here. There is no
         * binding to release, and close() is documented as best-effort, so
         * doing nothing is complete -- nothing is waiting on a result.
         */
        @Override public void close() { }
    }

    public interface ActionListener {
        void onSuccess();
        void onFailure(int reason);
    }

    public interface ChannelListener {
        void onChannelDisconnected();
    }

    public interface PeerListListener {
        void onPeersAvailable(WifiP2pDeviceList peers);
    }

    public interface ConnectionInfoListener {
        void onConnectionInfoAvailable(WifiP2pInfo info);
    }

    public interface GroupInfoListener {
        void onGroupInfoAvailable(WifiP2pGroup group);
    }

    public interface DeviceInfoListener {
        void onDeviceInfoAvailable(WifiP2pDevice device);
    }

    public interface ServiceResponseListener {
        void onServiceAvailable(int protocolType, byte[] responseData, WifiP2pDevice srcDevice);
    }

    public interface DnsSdServiceResponseListener {
        void onDnsSdServiceAvailable(String instanceName, String registrationType,
                WifiP2pDevice srcDevice);
    }

    public interface DnsSdTxtRecordListener {
        void onDnsSdTxtRecordAvailable(String fullDomainName,
                java.util.Map<String, String> txtRecordMap, WifiP2pDevice srcDevice);
    }

    public interface UpnpServiceResponseListener {
        void onUpnpServiceAvailable(java.util.List<String> uniqueServiceNames,
                WifiP2pDevice srcDevice);
    }

    public WifiP2pManager() {}

    public WifiP2pManager(Context context) {}

    /**
     * AOSP returns a Channel even when P2P is unavailable -- the failure is
     * reported per-operation, not by refusing the channel. Returning null here
     * would NPE apps that (correctly) expect a channel; every operation on it
     * fails honestly instead.
     */
    public Channel initialize(Context srcContext, Looper srcLooper, ChannelListener listener) {
        return new Channel(srcLooper);
    }

    // ---- Action calls: exactly one callback, always onFailure. ----

    public void discoverPeers(Channel c, ActionListener listener) {
        failAction(c, listener);
    }

    public void stopPeerDiscovery(Channel c, ActionListener listener) {
        failAction(c, listener);
    }

    public void connect(Channel c, WifiP2pConfig config, ActionListener listener) {
        failAction(c, listener);
    }

    public void cancelConnect(Channel c, ActionListener listener) {
        failAction(c, listener);
    }

    public void createGroup(Channel c, ActionListener listener) {
        failAction(c, listener);
    }

    public void createGroup(Channel c, WifiP2pConfig config, ActionListener listener) {
        failAction(c, listener);
    }

    public void removeGroup(Channel c, ActionListener listener) {
        failAction(c, listener);
    }

    public void addLocalService(Channel c, WifiP2pServiceInfo servInfo, ActionListener listener) {
        failAction(c, listener);
    }

    public void removeLocalService(Channel c, WifiP2pServiceInfo servInfo,
            ActionListener listener) {
        failAction(c, listener);
    }

    public void clearLocalServices(Channel c, ActionListener listener) {
        failAction(c, listener);
    }

    public void addServiceRequest(Channel c, WifiP2pServiceRequest req, ActionListener listener) {
        failAction(c, listener);
    }

    public void removeServiceRequest(Channel c, WifiP2pServiceRequest req,
            ActionListener listener) {
        failAction(c, listener);
    }

    public void clearServiceRequests(Channel c, ActionListener listener) {
        failAction(c, listener);
    }

    public void discoverServices(Channel c, ActionListener listener) {
        failAction(c, listener);
    }

    /**
     * Registering response listeners is bookkeeping, not an operation: AOSP
     * gives it no ActionListener and no result. The listeners are simply never
     * invoked, because discoverServices() fails and no responses can arrive.
     */
    public void setServiceResponseListener(Channel c, ServiceResponseListener listener) { }

    public void setDnsSdResponseListeners(Channel c, DnsSdServiceResponseListener servListener,
            DnsSdTxtRecordListener txtListener) { }

    public void setUpnpServiceResponseListener(Channel c, UpnpServiceResponseListener listener) { }

    // ---- Request calls: the listener ALWAYS fires, with the honest result. ----

    /** Empty list: no radio, so no peers were found. */
    public void requestPeers(Channel c, PeerListListener listener) {
        if (listener == null) return;
        deliver(c, () -> listener.onPeersAvailable(new WifiP2pDeviceList()));
    }

    /** groupFormed = false: we are not connected to anything. */
    public void requestConnectionInfo(Channel c, ConnectionInfoListener listener) {
        if (listener == null) return;
        deliver(c, () -> listener.onConnectionInfoAvailable(new WifiP2pInfo()));
    }

    /** Null: AOSP's answer when no group has formed, and none can here. */
    public void requestGroupInfo(Channel c, GroupInfoListener listener) {
        if (listener == null) return;
        deliver(c, () -> listener.onGroupInfoAvailable(null));
    }

    /**
     * A device with no P2P interface has no P2P device identity. AOSP itself
     * delivers null when the caller lacks the location permission, so callers
     * already handle a null device.
     */
    public void requestDeviceInfo(Channel c, DeviceInfoListener listener) {
        if (listener == null) return;
        deliver(c, () -> listener.onDeviceInfoAvailable(null));
    }

    private static void failAction(Channel c, ActionListener listener) {
        if (listener == null) return;
        deliver(c, () -> listener.onFailure(P2P_UNSUPPORTED));
    }

    /**
     * Post to the channel's Looper, matching AOSP's delivery thread. Falling
     * back to running inline keeps a null channel from swallowing the callback
     * entirely, which would reintroduce the silent-hang shape.
     */
    private static void deliver(Channel c, Runnable r) {
        if (c != null) {
            c.post(r);
        } else {
            r.run();
        }
    }
}
