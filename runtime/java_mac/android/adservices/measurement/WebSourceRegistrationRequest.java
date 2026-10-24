package android.adservices.measurement;

// android.adservices.measurement -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class WebSourceRegistrationRequest {
    private WebSourceRegistrationRequest() { }
    public static final class Builder {
        public Builder(java.util.List<android.adservices.measurement.WebSourceParams> webSourceParams, android.net.Uri topOriginUri) { }
        public Builder setAppDestination(android.net.Uri value) { return this; }
        public Builder setInputEvent(android.view.InputEvent value) { return this; }
        public Builder setVerifiedDestination(android.net.Uri value) { return this; }
        public Builder setWebDestination(android.net.Uri value) { return this; }
        public WebSourceRegistrationRequest build() { return new WebSourceRegistrationRequest(); }
    }
}
