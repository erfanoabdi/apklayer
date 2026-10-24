// android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo -- a Bonjour/DNS-SD service
// to advertise over Wi-Fi Direct.
//
// A REAL implementation: newInstance() does the actual DNS name encoding, which
// is a pure string/byte transform with no device dependency. It is kept real
// rather than stubbed because it VALIDATES its input (AOSP throws on an empty
// instance name or service type), and an app that relies on that check must
// still get it.
//
// Mirrors wifi/java/android/net/wifi/p2p/nsd/WifiP2pDnsSdServiceInfo.java
// (android11-release).
package android.net.wifi.p2p.nsd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WifiP2pDnsSdServiceInfo extends WifiP2pServiceInfo {

    /** @hide on AOSP. */
    public static final int VERSION_1 = 0x01;
    /** @hide on AOSP. */
    public static final int DNS_TYPE_PTR = 12;
    /** @hide on AOSP. */
    public static final int DNS_TYPE_TXT = 16;

    private WifiP2pDnsSdServiceInfo(List<String> queryList) {
        super(queryList);
    }

    public static WifiP2pDnsSdServiceInfo newInstance(String instanceName,
            String serviceType, Map<String, String> txtMap) {
        if (instanceName == null || instanceName.length() == 0
                || serviceType == null || serviceType.length() == 0) {
            throw new IllegalArgumentException(
                    "instance name or service type cannot be empty");
        }

        // AOSP builds two supplicant queries: a PTR record naming the instance
        // and a TXT record carrying the key/value pairs. Both are plain string
        // encodings, so they are produced faithfully here even though nothing
        // will broadcast them.
        List<String> queries = new ArrayList<String>();
        queries.add(createPtrServiceQuery(instanceName, serviceType));
        queries.add(createTxtServiceQuery(instanceName, serviceType, txtMap));
        return new WifiP2pDnsSdServiceInfo(queries);
    }

    private static String createPtrServiceQuery(String instanceName, String serviceType) {
        StringBuffer sb = new StringBuffer();
        sb.append("bonjour ");
        sb.append(createRequest(serviceType + ".local.", DNS_TYPE_PTR, VERSION_1));
        sb.append(" ");
        byte[] name = instanceName.getBytes();
        sb.append(String.format(java.util.Locale.US, "%02x", name.length));
        sb.append(toHex(name));
        sb.append("c027");
        return sb.toString();
    }

    private static String createTxtServiceQuery(String instanceName, String serviceType,
            Map<String, String> txtMap) {
        StringBuffer sb = new StringBuffer();
        sb.append("bonjour ");
        sb.append(createRequest(instanceName + "." + serviceType + ".local.",
                DNS_TYPE_TXT, VERSION_1));
        sb.append(" ");
        if (txtMap == null || txtMap.isEmpty()) {
            sb.append("00");
        } else {
            for (Map.Entry<String, String> e : txtMap.entrySet()) {
                String entry = e.getKey() + "=" + e.getValue();
                byte[] bytes = entry.getBytes();
                sb.append(String.format(java.util.Locale.US, "%02x", bytes.length));
                sb.append(toHex(bytes));
            }
        }
        return sb.toString();
    }

    /** AOSP's DNS name compression: each label is length-prefixed. */
    private static String createRequest(String dnsName, int dnsType, int version) {
        StringBuffer sb = new StringBuffer();
        if (dnsType == DNS_TYPE_TXT) {
            dnsName = dnsName.toLowerCase(java.util.Locale.ROOT);
        }
        for (String label : dnsName.split("\\.")) {
            if (label.length() == 0) continue;
            byte[] bytes = label.getBytes();
            sb.append(String.format(java.util.Locale.US, "%02x", bytes.length));
            sb.append(toHex(bytes));
        }
        sb.append("00");
        sb.append(String.format(java.util.Locale.US, "%04x", dnsType));
        sb.append(String.format(java.util.Locale.US, "%02x", version));
        return sb.toString();
    }

    private static String toHex(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for (byte b : bytes) {
            sb.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }
}
