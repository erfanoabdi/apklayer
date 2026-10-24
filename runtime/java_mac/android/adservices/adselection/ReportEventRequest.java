package android.adservices.adselection;

// android.adservices.adselection -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class ReportEventRequest {
    private ReportEventRequest() { }
    public static final class Builder {
        public Builder(long adSelectionId, String eventKey, String eventData, int reportingDestinations) { }
        public Builder setInputEvent(android.view.InputEvent value) { return this; }
        public ReportEventRequest build() { return new ReportEventRequest(); }
    }
}
