// android.net.StaticIpConfiguration
//
// A static IP setup (address + DNS + gateway + domains). Pure value type +
// Builder. Mac.apk does not configure interfaces, but apps build these to pass
// to (no-op) network config, so the type + Builder must resolve. Mirrors AOSP
// android.net.StaticIpConfiguration public API.
package android.net;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public final class StaticIpConfiguration {

    public LinkAddress ipAddress;
    public InetAddress gateway;
    public final ArrayList<InetAddress> dnsServers = new ArrayList<>();
    public String domains;

    public StaticIpConfiguration() {}

    public LinkAddress getIpAddress() { return ipAddress; }
    public InetAddress getGateway() { return gateway; }
    public List<InetAddress> getDnsServers() { return dnsServers; }
    public String getDomains() { return domains; }

    public static final class Builder {
        private LinkAddress mIpAddress;
        private InetAddress mGateway;
        private final ArrayList<InetAddress> mDnsServers = new ArrayList<>();
        private String mDomains;

        public Builder() {}

        public Builder setIpAddress(LinkAddress ipAddress) { mIpAddress = ipAddress; return this; }
        public Builder setGateway(InetAddress gateway) { mGateway = gateway; return this; }
        public Builder setDnsServers(Iterable<InetAddress> dnsServers) {
            mDnsServers.clear();
            if (dnsServers != null) for (InetAddress a : dnsServers) mDnsServers.add(a);
            return this;
        }
        public Builder setDomains(String newDomains) { mDomains = newDomains; return this; }

        public StaticIpConfiguration build() {
            StaticIpConfiguration cfg = new StaticIpConfiguration();
            cfg.ipAddress = mIpAddress;
            cfg.gateway = mGateway;
            cfg.dnsServers.addAll(mDnsServers);
            cfg.domains = mDomains;
            return cfg;
        }
    }
}
