package android.net.http;
/** DNS options. Source: frameworks/base .../net/http/DnsOptions.java */
public final class DnsOptions {
    private DnsOptions() { }
    public static final class Builder {
        public Builder() { }
        public Builder setUseHttpStackDnsResolver(int useHttpStackDnsResolver) { return this; }
        public Builder setPersistHostCache(int persistHostCache) { return this; }
        public Builder setPersistHostCachePeriod(java.time.Duration persistHostCachePeriod) { return this; }
        public Builder setStaleDns(int staleDns) { return this; }
        public Builder setPreestablishConnectionsToStaleDnsResults(int p) { return this; }
        public Builder setStaleDnsOptions(StaleDnsOptions staleDnsOptions) { return this; }
        public DnsOptions build() { return new DnsOptions(); }
    }
    /** Stale-DNS behavior (nested). */
    public static final class StaleDnsOptions {
        private StaleDnsOptions() {}
        public static final class Builder {
            public Builder() {}
            public Builder setAllowCrossNetworkUsage(int allowCrossNetworkUsage) { return this; }
            public Builder setFreshLookupTimeout(java.time.Duration freshLookupTimeout) { return this; }
            public Builder setMaxExpiredDelay(java.time.Duration maxExpiredDelay) { return this; }
            public Builder setUseStaleOnNameNotResolved(int useStaleOnNameNotResolved) { return this; }
            public StaleDnsOptions build() { return new StaleDnsOptions(); }
        }
    }
}
