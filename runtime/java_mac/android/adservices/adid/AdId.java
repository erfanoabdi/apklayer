package android.adservices.adid;

// android.adservices.adid -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class AdId {
    private String m_getAdId = "00000000-0000-0000-0000-000000000000";
    private boolean m_isLimitAdTrackingEnabled = true;
    private AdId() { }
    public AdId(String adId, boolean limitAdTrackingEnabled) { }
    public String getAdId() { return m_getAdId; }
    public boolean isLimitAdTrackingEnabled() { return m_isLimitAdTrackingEnabled; }
}
