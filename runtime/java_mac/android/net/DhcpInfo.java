// android.net.DhcpInfo -- DHCP configuration info from a connected
// network. Apps reach for this for connectivity diagnostics. Per AOSP
// https://developer.android.com/reference/android/net/DhcpInfo all
// fields are public ints in network byte order.
package android.net;

public class DhcpInfo {
    public int ipAddress;
    public int gateway;
    public int netmask;
    public int dns1;
    public int dns2;
    public int serverAddress;
    public int leaseDuration;

    public DhcpInfo() {}

    @Override
    public String toString() {
        return "ipaddr " + intToString(ipAddress)
            + " gateway " + intToString(gateway)
            + " netmask " + intToString(netmask)
            + " dns1 " + intToString(dns1)
            + " dns2 " + intToString(dns2)
            + " DHCP server " + intToString(serverAddress)
            + " lease " + leaseDuration + " seconds";
    }

    private static String intToString(int ip) {
        return (ip & 0xff) + "." + ((ip >> 8) & 0xff)
            + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
    }
}
