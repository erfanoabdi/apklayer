package android.adservices;

// android.adservices.AdServicesState -- the availability gate ad SDKs check
// before touching any AdServices API. Mac.apk is a de-Googled device with no
// AdServices module, so this is false and compliant SDKs skip the whole path.
// See [[gms-absent-device-contract]].
public final class AdServicesState {
    private AdServicesState() { }
    public static boolean isAdServicesStateEnabled() { return false; }
}
