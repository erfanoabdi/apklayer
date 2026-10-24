package android.adservices.appsetid;

// android.adservices.appsetid -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class AppSetId {
    private String m_getId = "";
    private int m_getScope = 1;
    private AppSetId() { }
    public AppSetId(String id, int scope) { }
    public String getId() { return m_getId; }
    public int getScope() { return m_getScope; }
}
