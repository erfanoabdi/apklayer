package android.adservices.customaudience;

// android.adservices.customaudience -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class JoinCustomAudienceRequest {
    private JoinCustomAudienceRequest() { }
    public static final class Builder {
        public Builder() { }
        public Builder setCustomAudience(android.adservices.customaudience.CustomAudience value) { return this; }
        public JoinCustomAudienceRequest build() { return new JoinCustomAudienceRequest(); }
    }
}
