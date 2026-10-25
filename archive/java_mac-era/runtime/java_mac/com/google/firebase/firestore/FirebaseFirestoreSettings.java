package com.google.firebase.firestore;

// com.google.firebase.firestore.FirebaseFirestoreSettings -- client settings.
// Faithful value/builder type; the values are accepted and stored but have no
// observable effect on the offline-only shim (no backend to point a host at,
// no cache layer to size). Apps build these and pass them to
// setFirestoreSettings(), so the builder surface must match exactly.
public final class FirebaseFirestoreSettings {

    // Published sentinel meaning "use the platform default cache size".
    public static final long CACHE_SIZE_UNLIMITED = -1L;

    // Default host for the production backend (unreachable on our shim).
    static final String DEFAULT_HOST = "firestore.googleapis.com";

    private final String host;
    private final boolean sslEnabled;
    private final boolean persistenceEnabled;
    private final long cacheSizeBytes;

    private FirebaseFirestoreSettings(Builder builder) {
        this.host = builder.host;
        this.sslEnabled = builder.sslEnabled;
        this.persistenceEnabled = builder.persistenceEnabled;
        this.cacheSizeBytes = builder.cacheSizeBytes;
    }

    public String getHost() { return host; }

    public boolean isSslEnabled() { return sslEnabled; }

    public boolean isPersistenceEnabled() { return persistenceEnabled; }

    public long getCacheSizeBytes() { return cacheSizeBytes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FirebaseFirestoreSettings)) return false;
        FirebaseFirestoreSettings s = (FirebaseFirestoreSettings) o;
        return sslEnabled == s.sslEnabled
            && persistenceEnabled == s.persistenceEnabled
            && cacheSizeBytes == s.cacheSizeBytes
            && host.equals(s.host);
    }

    @Override
    public int hashCode() {
        int result = host.hashCode();
        result = 31 * result + (sslEnabled ? 1 : 0);
        result = 31 * result + (persistenceEnabled ? 1 : 0);
        result = 31 * result + (int) (cacheSizeBytes ^ (cacheSizeBytes >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "FirebaseFirestoreSettings{host=" + host + ", sslEnabled=" + sslEnabled
            + ", persistenceEnabled=" + persistenceEnabled
            + ", cacheSizeBytes=" + cacheSizeBytes + "}";
    }

    public static final class Builder {
        private String host = DEFAULT_HOST;
        private boolean sslEnabled = true;
        private boolean persistenceEnabled = true;
        // Default 100 MB, matching the published default.
        private long cacheSizeBytes = 100L * 1024 * 1024;

        public Builder() {}

        public Builder(FirebaseFirestoreSettings settings) {
            this.host = settings.host;
            this.sslEnabled = settings.sslEnabled;
            this.persistenceEnabled = settings.persistenceEnabled;
            this.cacheSizeBytes = settings.cacheSizeBytes;
        }

        public Builder setHost(String host) {
            if (host == null) throw new NullPointerException("Provided host must not be null.");
            this.host = host;
            return this;
        }

        public Builder setSslEnabled(boolean value) {
            this.sslEnabled = value;
            return this;
        }

        public Builder setPersistenceEnabled(boolean value) {
            this.persistenceEnabled = value;
            return this;
        }

        public Builder setCacheSizeBytes(long value) {
            this.cacheSizeBytes = value;
            return this;
        }

        public String getHost() { return host; }

        public boolean isSslEnabled() { return sslEnabled; }

        public boolean isPersistenceEnabled() { return persistenceEnabled; }

        public long getCacheSizeBytes() { return cacheSizeBytes; }

        public FirebaseFirestoreSettings build() {
            return new FirebaseFirestoreSettings(this);
        }
    }
}
