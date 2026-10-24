package android.adservices.customaudience;

// android.adservices.customaudience -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class TrustedBiddingData {
    private TrustedBiddingData() { }
    public static final class Builder {
        public Builder() { }
        public Builder setTrustedBiddingKeys(java.util.List<String> value) { return this; }
        public Builder setTrustedBiddingUri(android.net.Uri value) { return this; }
        public TrustedBiddingData build() { return new TrustedBiddingData(); }
    }
}
