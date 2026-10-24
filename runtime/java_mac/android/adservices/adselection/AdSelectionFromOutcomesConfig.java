package android.adservices.adselection;

// android.adservices.adselection -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class AdSelectionFromOutcomesConfig {
    private AdSelectionFromOutcomesConfig() { }
    public static final class Builder {
        public Builder() { }
        public Builder setAdSelectionIds(java.util.List<Long> value) { return this; }
        public Builder setSelectionLogicUri(android.net.Uri value) { return this; }
        public Builder setSelectionSignals(android.adservices.common.AdSelectionSignals value) { return this; }
        public Builder setSeller(android.adservices.common.AdTechIdentifier value) { return this; }
        public AdSelectionFromOutcomesConfig build() { return new AdSelectionFromOutcomesConfig(); }
    }
}
