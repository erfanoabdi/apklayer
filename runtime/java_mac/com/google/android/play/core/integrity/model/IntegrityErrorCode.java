package com.google.android.play.core.integrity.model;

// com.google.android.play.core.integrity.model.IntegrityErrorCode -- the error
// codes an IntegrityServiceException carries. On a de-Googled device there is no
// Play Integrity backend to attest against, and per project policy we NEVER
// forge an attestation token; requestIntegrityToken() therefore FAILS its Task
// with an IntegrityServiceException carrying one of these codes
// (PLAY_STORE_NOT_FOUND / API_NOT_AVAILABLE -- the honest "no backend" answer).
// Values match the published Play Integrity @IntDef.
public final class IntegrityErrorCode {
    private IntegrityErrorCode() {}

    public static final int NO_ERROR = 0;
    public static final int API_NOT_AVAILABLE = -1;
    public static final int PLAY_STORE_NOT_FOUND = -2;
    public static final int NETWORK_ERROR = -3;
    public static final int PLAY_STORE_ACCOUNT_NOT_FOUND = -4;
    public static final int APP_NOT_INSTALLED = -5;
    public static final int PLAY_SERVICES_NOT_FOUND = -6;
    public static final int APP_UID_MISMATCH = -7;
    public static final int TOO_MANY_REQUESTS = -8;
    public static final int CANNOT_BIND_TO_SERVICE = -9;
    public static final int NONCE_TOO_SHORT = -10;
    public static final int NONCE_TOO_LONG = -11;
    public static final int GOOGLE_SERVER_UNAVAILABLE = -12;
    public static final int NONCE_IS_NOT_BASE64 = -13;
    public static final int PLAY_STORE_VERSION_OUTDATED = -14;
    public static final int PLAY_SERVICES_VERSION_OUTDATED = -15;
    public static final int CLOUD_PROJECT_NUMBER_IS_INVALID = -16;
    public static final int CLIENT_TRANSIENT_ERROR = -17;
    public static final int INTEGRITY_TOKEN_PROVIDER_INVALID = -18;
    public static final int INTERNAL_ERROR = -100;
}
