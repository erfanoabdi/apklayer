package android.adservices.adselection;

// android.adservices.adselection -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class GetAdSelectionDataOutcome {
    private byte[] m_getAdSelectionData = new byte[0];
    private long m_getAdSelectionId = 0L;
    private GetAdSelectionDataOutcome() { }
    public byte[] getAdSelectionData() { return m_getAdSelectionData; }
    public long getAdSelectionId() { return m_getAdSelectionId; }
}
