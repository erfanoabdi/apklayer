package android.adservices.adselection;

// android.adservices.adselection -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class AdSelectionConfig {
    private AdSelectionConfig() { }
    public static final class Builder {
        public Builder() { }
        public Builder setAdSelectionSignals(android.adservices.common.AdSelectionSignals value) { return this; }
        public Builder setCustomAudienceBuyers(java.util.List<android.adservices.common.AdTechIdentifier> value) { return this; }
        public Builder setDecisionLogicUri(android.net.Uri value) { return this; }
        public Builder setPerBuyerSignals(java.util.Map<android.adservices.common.AdTechIdentifier, android.adservices.common.AdSelectionSignals> value) { return this; }
        public Builder setSeller(android.adservices.common.AdTechIdentifier value) { return this; }
        public Builder setSellerSignals(android.adservices.common.AdSelectionSignals value) { return this; }
        public Builder setTrustedScoringSignalsUri(android.net.Uri value) { return this; }
        public AdSelectionConfig build() { return new AdSelectionConfig(); }
    }
}
