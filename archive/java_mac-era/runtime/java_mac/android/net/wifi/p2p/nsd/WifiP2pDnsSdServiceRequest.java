// android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest -- a Bonjour/DNS-SD
// discovery query to broadcast over Wi-Fi Direct.
//
// A REAL value object; the query encoding is a pure string transform. Only
// broadcasting it needs the radio, and WifiP2pManager.addServiceRequest /
// discoverServices report failure honestly rather than going silent.
//
// Mirrors wifi/java/android/net/wifi/p2p/nsd/WifiP2pDnsSdServiceRequest.java
// (android11-release).
package android.net.wifi.p2p.nsd;

public class WifiP2pDnsSdServiceRequest extends WifiP2pServiceRequest {

    private WifiP2pDnsSdServiceRequest(String query) {
        super(WifiP2pServiceInfo.SERVICE_TYPE_BONJOUR, query);
    }

    private WifiP2pDnsSdServiceRequest() {
        super(WifiP2pServiceInfo.SERVICE_TYPE_BONJOUR, null);
    }

    private WifiP2pDnsSdServiceRequest(String dnsQuery, int dnsType, int version) {
        this(createRequest(dnsQuery, dnsType, version));
    }

    /** Discover ALL Bonjour services (AOSP: a null query means "everything"). */
    public static WifiP2pDnsSdServiceRequest newInstance() {
        return new WifiP2pDnsSdServiceRequest();
    }

    public static WifiP2pDnsSdServiceRequest newInstance(String serviceType) {
        if (serviceType == null) {
            throw new IllegalArgumentException("service type cannot be null");
        }
        return new WifiP2pDnsSdServiceRequest(serviceType + ".local.",
                WifiP2pDnsSdServiceInfo.DNS_TYPE_PTR, WifiP2pDnsSdServiceInfo.VERSION_1);
    }

    public static WifiP2pDnsSdServiceRequest newInstance(String instanceName,
            String serviceType) {
        if (instanceName == null || serviceType == null) {
            throw new IllegalArgumentException("instance name or service type cannot be null");
        }
        String fullDomainName = instanceName + "." + serviceType + ".local.";
        return new WifiP2pDnsSdServiceRequest(fullDomainName,
                WifiP2pDnsSdServiceInfo.DNS_TYPE_TXT, WifiP2pDnsSdServiceInfo.VERSION_1);
    }

    private static String createRequest(String dnsName, int dnsType, int version) {
        StringBuffer sb = new StringBuffer();
        if (dnsType == WifiP2pDnsSdServiceInfo.DNS_TYPE_TXT) {
            dnsName = dnsName.toLowerCase(java.util.Locale.ROOT);
        }
        for (String label : dnsName.split("\\.")) {
            if (label.length() == 0) continue;
            byte[] bytes = label.getBytes();
            sb.append(String.format(java.util.Locale.US, "%02x", bytes.length));
            for (byte b : bytes) {
                sb.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
            }
        }
        sb.append("00");
        sb.append(String.format(java.util.Locale.US, "%04x", dnsType));
        sb.append(String.format(java.util.Locale.US, "%02x", version));
        return sb.toString();
    }
}
