package android.net.http;
public final class QuicOptions {
    private QuicOptions() {}
    public static final class Builder {
        public Builder() {}
        public Builder addAllowedQuicHost(String quicHost) { return this; }
        public Builder addEnabledQuicVersion(String quicVersion) { return this; }
        public Builder setHandshakeUserAgent(String handshakeUserAgent) { return this; }
        public Builder setInMemoryServerConfigsCacheSize(int inMemoryServerConfigsCacheSizeInBytes) { return this; }
        public Builder setIdleConnectionTimeout(java.time.Duration idleConnectionTimeout) { return this; }
        public QuicOptions build() { return new QuicOptions(); }
    }
}
