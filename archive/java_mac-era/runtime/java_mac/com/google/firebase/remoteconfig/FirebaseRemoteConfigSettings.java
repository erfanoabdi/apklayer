package com.google.firebase.remoteconfig;

// Minimal settings stub so apps can configure RC without crashing.
// We don't honor any of the values -- our stub is offline by
// definition, so fetch intervals and timeouts are irrelevant.
public final class FirebaseRemoteConfigSettings {
    private final long fetchTimeoutInSeconds;
    private final long minimumFetchIntervalInSeconds;

    private FirebaseRemoteConfigSettings(Builder b) {
        this.fetchTimeoutInSeconds         = b.fetchTimeoutInSeconds;
        this.minimumFetchIntervalInSeconds = b.minimumFetchIntervalInSeconds;
    }

    public long getFetchTimeoutInSeconds()        { return fetchTimeoutInSeconds; }
    public long getMinimumFetchIntervalInSeconds(){ return minimumFetchIntervalInSeconds; }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.fetchTimeoutInSeconds         = this.fetchTimeoutInSeconds;
        b.minimumFetchIntervalInSeconds = this.minimumFetchIntervalInSeconds;
        return b;
    }

    public static final class Builder {
        long fetchTimeoutInSeconds         = 60L;
        long minimumFetchIntervalInSeconds = 12L * 60 * 60;
        public Builder setFetchTimeoutInSeconds(long s) { fetchTimeoutInSeconds = s; return this; }
        public Builder setMinimumFetchIntervalInSeconds(long s) { minimumFetchIntervalInSeconds = s; return this; }
        public FirebaseRemoteConfigSettings build() { return new FirebaseRemoteConfigSettings(this); }
    }
}
