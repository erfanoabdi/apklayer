package android.adservices.measurement;

// android.adservices.measurement -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class WebTriggerRegistrationRequest {
    private WebTriggerRegistrationRequest() { }
    public static final class Builder {
        public Builder(java.util.List<android.adservices.measurement.WebTriggerParams> webTriggerParams, android.net.Uri destination) { }
        public WebTriggerRegistrationRequest build() { return new WebTriggerRegistrationRequest(); }
    }
}
