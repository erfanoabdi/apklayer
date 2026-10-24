package android.adservices.common;

// android.adservices.common -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class AdData {
    private AdData() { }
    public static final class Builder {
        public Builder() { }
        public Builder setAdCounterKeys(java.util.Set<Integer> value) { return this; }
        public Builder setAdFilters(android.adservices.common.AdFilters value) { return this; }
        public Builder setAdRenderId(String value) { return this; }
        public Builder setMetadata(String value) { return this; }
        public Builder setRenderUri(android.net.Uri value) { return this; }
        public AdData build() { return new AdData(); }
    }
}
