package com.google.android.gms.common.api;

// com.google.android.gms.common.api.CommonStatusCodes -- the status codes shared
// by every GMS API surface. Values are baked into the play-services SDK and must
// match exactly: code paths that compare a Status.getStatusCode() against these
// constants (and apps that catch ApiException and branch on getStatusCode())
// depend on the canonical integers. NOTE these differ from
// com.google.android.gms.common.ConnectionResult's constants (e.g. here
// CANCELED=16 and TIMEOUT=15, ERROR=13) -- the two enumerations are distinct in
// real GMS and must not be conflated.
public final class CommonStatusCodes {
    private CommonStatusCodes() {}

    public static final int SUCCESS_CACHE = -1;
    public static final int SUCCESS = 0;
    public static final int SERVICE_MISSING = 1;
    public static final int SERVICE_VERSION_UPDATE_REQUIRED = 2;
    public static final int SERVICE_DISABLED = 3;
    public static final int SIGN_IN_REQUIRED = 4;
    public static final int INVALID_ACCOUNT = 5;
    public static final int RESOLUTION_REQUIRED = 6;
    public static final int NETWORK_ERROR = 7;
    public static final int INTERNAL_ERROR = 8;
    public static final int SERVICE_INVALID = 9;
    public static final int DEVELOPER_ERROR = 10;
    public static final int LICENSE_CHECK_FAILED = 11;
    public static final int ERROR = 13;
    public static final int INTERRUPTED = 14;
    public static final int TIMEOUT = 15;
    public static final int CANCELED = 16;
    public static final int API_NOT_CONNECTED = 17;
    public static final int DEAD_CLIENT = 18;
    public static final int REMOTE_EXCEPTION = 19;
    public static final int CONNECTION_SUSPENDED_DURING_CALL = 20;
    public static final int RECONNECTION_TIMED_OUT_DURING_UPDATE = 21;
    public static final int RECONNECTION_TIMED_OUT = 22;

    public static String getStatusCodeString(int statusCode) {
        switch (statusCode) {
            case SUCCESS_CACHE: return "SUCCESS_CACHE";
            case SUCCESS: return "SUCCESS";
            case SERVICE_MISSING: return "SERVICE_MISSING";
            case SERVICE_VERSION_UPDATE_REQUIRED: return "SERVICE_VERSION_UPDATE_REQUIRED";
            case SERVICE_DISABLED: return "SERVICE_DISABLED";
            case SIGN_IN_REQUIRED: return "SIGN_IN_REQUIRED";
            case INVALID_ACCOUNT: return "INVALID_ACCOUNT";
            case RESOLUTION_REQUIRED: return "RESOLUTION_REQUIRED";
            case NETWORK_ERROR: return "NETWORK_ERROR";
            case INTERNAL_ERROR: return "INTERNAL_ERROR";
            case SERVICE_INVALID: return "SERVICE_INVALID";
            case DEVELOPER_ERROR: return "DEVELOPER_ERROR";
            case LICENSE_CHECK_FAILED: return "LICENSE_CHECK_FAILED";
            case ERROR: return "ERROR";
            case INTERRUPTED: return "INTERRUPTED";
            case TIMEOUT: return "TIMEOUT";
            case CANCELED: return "CANCELED";
            case API_NOT_CONNECTED: return "API_NOT_CONNECTED";
            case DEAD_CLIENT: return "DEAD_CLIENT";
            case REMOTE_EXCEPTION: return "REMOTE_EXCEPTION";
            case CONNECTION_SUSPENDED_DURING_CALL: return "CONNECTION_SUSPENDED_DURING_CALL";
            case RECONNECTION_TIMED_OUT_DURING_UPDATE: return "RECONNECTION_TIMED_OUT_DURING_UPDATE";
            case RECONNECTION_TIMED_OUT: return "RECONNECTION_TIMED_OUT";
            default: return "unknown status code: " + statusCode;
        }
    }
}
