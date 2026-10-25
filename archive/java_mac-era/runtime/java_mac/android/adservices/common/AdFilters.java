package android.adservices.common;

// android.adservices.common -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class AdFilters {
    private AdFilters() { }
    public static final class Builder {
        public Builder() { }
        public Builder setFrequencyCapFilters(android.adservices.common.FrequencyCapFilters value) { return this; }
        public AdFilters build() { return new AdFilters(); }
    }
}
