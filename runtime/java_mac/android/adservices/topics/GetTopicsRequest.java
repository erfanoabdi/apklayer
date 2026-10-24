package android.adservices.topics;

// android.adservices.topics -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class GetTopicsRequest {
    private GetTopicsRequest() { }
    public static final class Builder {
        public Builder() { }
        public Builder setAdsSdkName(String value) { return this; }
        public Builder setShouldRecordObservation(boolean value) { return this; }
        public GetTopicsRequest build() { return new GetTopicsRequest(); }
    }
}
