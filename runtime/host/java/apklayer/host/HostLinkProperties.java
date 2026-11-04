package apklayer.host;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.RouteInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * LinkProperties of the host's default-route interface, as Android's IpClient fills them: interface name, addresses with their prefix, MTU, the
 * default routes (/proc/net/route, /proc/net/ipv6_route), DNS servers and search domains (/etc/resolv.conf; systemd-resolved's stub 127.0.0.53 is
 * the host's real resolver). WebRTC lists ICE candidates from these addresses, Chromium reads its DnsConfig from them.
 */
final class HostLinkProperties {
    private HostLinkProperties() {}

    static LinkProperties read(String iface) {
        LinkProperties link = new LinkProperties();
        link.setInterfaceName(iface);
        InetAddress gateway6 = defaultGateway6(iface);
        try {
            NetworkInterface device = NetworkInterface.getByName(iface);
            if (device != null) {
                for (InterfaceAddress a : device.getInterfaceAddresses()) {
                    InetAddress address = a.getAddress();
                    // Android keeps global IPv6 only while IPv6 is provisioned (a default route): without one every v6 send fails (ENETUNREACH)
                    if (address instanceof java.net.Inet6Address && !address.isLinkLocalAddress() && gateway6 == null) continue;
                    link.addLinkAddress(new LinkAddress(address, a.getNetworkPrefixLength()));
                }
                link.setMtu(device.getMTU());
            }
        } catch (IOException unreadable) { /* the name and routes still describe the link */ }
        InetAddress gateway4 = defaultGateway4(iface);
        if (gateway4 != null) link.addRoute(new RouteInfo(new IpPrefix(address("0.0.0.0"), 0), gateway4, iface));
        if (gateway6 != null) link.addRoute(new RouteInfo(new IpPrefix(address("::"), 0), gateway6, iface));
        try {
            for (String line : Files.readAllLines(Path.of("/etc/resolv.conf"))) {
                String[] f = line.trim().split("\\s+");
                if (f.length > 1 && f[0].equals("nameserver")) link.addDnsServer(address(f[1].replaceFirst("%.*", "")));
                else if (f.length > 1 && f[0].equals("search") && !f[1].equals(".")) link.setDomains(String.join(" ", List.of(f).subList(1, f.length)));
            }
        } catch (IOException | IllegalArgumentException noResolvConf) { /* no DNS servers known */ }
        return link;
    }

    private static InetAddress address(String numeric) {
        try { return InetAddress.getByName(numeric); } catch (IOException e) { throw new IllegalArgumentException(numeric, e); }  // numeric: never a lookup
    }

    /** /proc/net/route: Iface Destination Gateway Flags ..., addresses in host (little-endian) hex. */
    private static InetAddress defaultGateway4(String iface) {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/net/route"))) {
                String[] f = line.trim().split("\\s+");
                if (f.length > 3 && f[0].equals(iface) && f[1].equals("00000000") && (Integer.parseInt(f[3], 16) & 1) != 0) {
                    int g = Integer.reverseBytes((int) Long.parseLong(f[2], 16));
                    return InetAddress.getByAddress(new byte[] {(byte) (g >>> 24), (byte) (g >>> 16), (byte) (g >>> 8), (byte) g});
                }
            }
        } catch (IOException | NumberFormatException unreadable) { /* none */ }
        return null;
    }

    /** /proc/net/ipv6_route: dest destlen src srclen nexthop metric refcnt use flags iface, addresses as 32 hex digits. */
    private static InetAddress defaultGateway6(String iface) {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/net/ipv6_route"))) {
                String[] f = line.trim().split("\\s+");
                if (f.length > 9 && f[9].equals(iface) && f[0].matches("0{32}") && f[1].equals("00") && (Integer.parseInt(f[8], 16) & 1) != 0) {
                    return InetAddress.getByAddress(java.util.HexFormat.of().parseHex(f[4]));
                }
            }
        } catch (IOException | NumberFormatException unreadable) { /* none */ }
        return null;
    }
}
