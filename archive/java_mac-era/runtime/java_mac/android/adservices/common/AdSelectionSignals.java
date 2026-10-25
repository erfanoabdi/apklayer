package android.adservices.common;

// android.adservices.common -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class AdSelectionSignals {
    private AdSelectionSignals() { }
    public static AdSelectionSignals fromString(String source) { return new AdSelectionSignals(); }
}
