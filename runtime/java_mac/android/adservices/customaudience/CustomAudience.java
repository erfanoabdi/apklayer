package android.adservices.customaudience;

// android.adservices.customaudience -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class CustomAudience {
    private CustomAudience() { }
    public static final class Builder {
        public Builder() { }
        public Builder setActivationTime(java.time.Instant value) { return this; }
        public Builder setAds(java.util.List<android.adservices.common.AdData> value) { return this; }
        public Builder setBiddingLogicUri(android.net.Uri value) { return this; }
        public Builder setBuyer(android.adservices.common.AdTechIdentifier value) { return this; }
        public Builder setDailyUpdateUri(android.net.Uri value) { return this; }
        public Builder setExpirationTime(java.time.Instant value) { return this; }
        public Builder setName(String value) { return this; }
        public Builder setTrustedBiddingData(android.adservices.customaudience.TrustedBiddingData value) { return this; }
        public Builder setUserBiddingSignals(android.adservices.common.AdSelectionSignals value) { return this; }
        public CustomAudience build() { return new CustomAudience(); }
    }
}
