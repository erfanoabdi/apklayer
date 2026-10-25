// android.net.LinkProperties -- the link-layer config of a Network (DNS
// servers, search domains, private-DNS state, routes).
//
// THESE ANSWERS COME FROM THE HOST. The interface name, its addresses and its
// MTU are read from java.net.NetworkInterface for whichever interface carries
// macOS's default route, and the DNS servers from `scutil --dns` -- see
// MacApkHostNetwork. Previously every method here returned empty/null, which
// told a caller such as Chromium cronet's AndroidNetworkLibrary.getDnsStatus
// that the device had no resolver at all; a real Android device always reports
// one, so the empty answer was both untrue and the less-tested path.
//
// What is still absent: private-DNS state (macOS has no equivalent setting to
// report), the per-link ROUTE table, and the NAT64 prefix. Each returns
// AOSP's documented "none" value rather than a guess.
package android.net;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

public class LinkProperties {
    public LinkProperties() {}

    /** The resolvers macOS is actually using, in order. */
    public List<InetAddress> getDnsServers() {
        return MacApkHostNetwork.get().dnsServers;
    }

    /** macOS's search-domain list is not surfaced yet; AOSP's "none" is null. */
    public String getDomains() { return null; }

    /** macOS has no DNS-over-TLS setting to report, so this is not "off by
     *  assumption" -- there is genuinely no private-DNS mode configured. */
    public boolean isPrivateDnsActive() { return false; }

    public String getPrivateDnsServerName() { return null; }

    /** The BSD name of the interface carrying the default route (e.g. "en0"). */
    public String getInterfaceName() {
        return MacApkHostNetwork.get().interfaceName;
    }

    // AOSP getLinkAddresses(): the IP addresses assigned on this link. Real:
    // read off the host interface, with the prefix length macOS reports.
    public List<LinkAddress> getLinkAddresses() {
        return MacApkHostNetwork.get().linkAddresses;
    }

    // AOSP getRoutes(): the routing table for this link. Empty here.
    public List<RouteInfo> getRoutes() { return Collections.emptyList(); }

    // AOSP getHttpProxy(): the per-network HTTP proxy, or null. We expose the
    // host system proxy if one is set (mirrors ConnectivityManager.getProxyForNetwork).
    public ProxyInfo getHttpProxy() { return ProxyInfo.macapkSystemProxyOrNull(); }

    // AOSP getMtu(): the link MTU, 0 if unknown. Read from the host interface.
    public int getMtu() { return MacApkHostNetwork.get().mtu; }

    // AOSP getNat64Prefix(): the NAT64 prefix for CLAT, or null when none.
    public IpPrefix getNat64Prefix() { return null; }
}
