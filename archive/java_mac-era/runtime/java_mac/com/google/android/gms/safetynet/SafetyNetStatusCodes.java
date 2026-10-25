package com.google.android.gms.safetynet;

// com.google.android.gms.safetynet.SafetyNetStatusCodes -- status codes specific
// to the (legacy, deprecated) SafetyNet attestation API. On real GMS this type
// extends CommonStatusCodes; our CommonStatusCodes has a private constructor and
// lives in another package, so we re-declare the small set of shared codes a
// SafetyNet caller actually compares against (SUCCESS / errors) alongside the
// SafetyNet-specific ones, rather than subclassing. Values match the published
// SafetyNetStatusCodes / CommonStatusCodes. On a de-Googled device there is no
// SafetyNet backend and per project policy we never forge an attestation, so
// SafetyNetClient calls fail with SERVICE_MISSING.
public final class SafetyNetStatusCodes {
    private SafetyNetStatusCodes() {}

    // Shared CommonStatusCodes values SafetyNet callers commonly reference.
    public static final int SUCCESS = 0;
    public static final int SERVICE_MISSING = 1;
    public static final int SIGN_IN_REQUIRED = 4;
    public static final int NETWORK_ERROR = 7;
    public static final int INTERNAL_ERROR = 8;
    public static final int DEVELOPER_ERROR = 10;
    public static final int ERROR = 13;
    public static final int TIMEOUT = 15;
    public static final int CANCELED = 16;
    public static final int API_NOT_CONNECTED = 17;

    // SafetyNet-specific codes.
    public static final int SAFE_BROWSING_API_NOT_AVAILABLE = 12000;
    public static final int VERIFY_APPS_NOT_AVAILABLE = 12001;
    public static final int UNSUPPORTED_SAFE_BROWSING_THREAT_TYPES = 12002;
    public static final int SAFE_BROWSING_API_NOT_INITIALIZED = 12003;
    public static final int RECAPTCHA_INVALID_SITEKEY = 12007;
    public static final int RECAPTCHA_INVALID_KEYTYPE = 12008;
    public static final int RECAPTCHA_INTERNAL_ERROR = 12009;
}
