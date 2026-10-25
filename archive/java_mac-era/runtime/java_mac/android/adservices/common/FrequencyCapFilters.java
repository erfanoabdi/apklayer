package android.adservices.common;

// android.adservices.common -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class FrequencyCapFilters {
    private FrequencyCapFilters() { }
    public static final class Builder {
        public Builder() { }
        public Builder setKeyedFrequencyCapsForClickEvents(java.util.List<android.adservices.common.KeyedFrequencyCap> value) { return this; }
        public Builder setKeyedFrequencyCapsForImpressionEvents(java.util.List<android.adservices.common.KeyedFrequencyCap> value) { return this; }
        public Builder setKeyedFrequencyCapsForViewEvents(java.util.List<android.adservices.common.KeyedFrequencyCap> value) { return this; }
        public Builder setKeyedFrequencyCapsForWinEvents(java.util.List<android.adservices.common.KeyedFrequencyCap> value) { return this; }
        public FrequencyCapFilters build() { return new FrequencyCapFilters(); }
    }
}
