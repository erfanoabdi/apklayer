package android.net.http;
/** Connection-migration options. Source: frameworks/base .../net/http/ConnectionMigrationOptions.java */
public final class ConnectionMigrationOptions {
    private ConnectionMigrationOptions() { }
    public static final class Builder {
        public Builder() { }
        public Builder setDefaultNetworkMigration(int enableDefaultNetworkMigration) { return this; }
        public Builder setPathDegradationMigration(int enablePathDegradationMigration) { return this; }
        public Builder setAllowNonDefaultNetworkUsage(int allowNonDefaultNetworkUsage) { return this; }
        public ConnectionMigrationOptions build() { return new ConnectionMigrationOptions(); }
    }
}
