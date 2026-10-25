package android.adservices.adselection;

// android.adservices.adselection -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class ReportImpressionRequest {
    private ReportImpressionRequest() { }
    public ReportImpressionRequest(long adSelectionId, android.adservices.adselection.AdSelectionConfig adSelectionConfig) { }
    public ReportImpressionRequest(long adSelectionId) { }
}
