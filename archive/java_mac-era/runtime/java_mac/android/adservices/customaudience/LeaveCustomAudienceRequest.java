package android.adservices.customaudience;

// android.adservices.customaudience -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class LeaveCustomAudienceRequest {
    private LeaveCustomAudienceRequest() { }
    public static final class Builder {
        public Builder() { }
        public Builder setBuyer(android.adservices.common.AdTechIdentifier value) { return this; }
        public Builder setName(String value) { return this; }
        public LeaveCustomAudienceRequest build() { return new LeaveCustomAudienceRequest(); }
    }
}
