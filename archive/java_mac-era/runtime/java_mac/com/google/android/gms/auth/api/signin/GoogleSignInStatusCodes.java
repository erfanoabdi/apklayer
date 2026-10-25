package com.google.android.gms.auth.api.signin;

import com.google.android.gms.common.api.CommonStatusCodes;

// com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes -- the
// sign-in-specific status codes returned by failed sign-in Tasks. In real GMS
// this class EXTENDS CommonStatusCodes, but our CommonStatusCodes is final
// (matching nothing in particular -- it just was), so we cannot subclass it
// here. Apps reference these constants by value, not by the inheritance
// relationship, so re-declaring the shared codes with the same numeric values
// from CommonStatusCodes preserves every app-observable contract. The
// sign-in-only codes (SIGN_IN_*) start at 12500 per the published reference.
//
// Ref: developers.google.com/android/reference/com/google/android/gms/auth/api/
// signin/GoogleSignInStatusCodes
public final class GoogleSignInStatusCodes {

    private GoogleSignInStatusCodes() {}

    // Shared codes -- numeric values mirror CommonStatusCodes exactly.
    public static final int SUCCESS = CommonStatusCodes.SUCCESS;
    public static final int SUCCESS_CACHE = CommonStatusCodes.SUCCESS_CACHE;
    public static final int SIGN_IN_REQUIRED = CommonStatusCodes.SIGN_IN_REQUIRED;
    public static final int INVALID_ACCOUNT = CommonStatusCodes.INVALID_ACCOUNT;
    public static final int NETWORK_ERROR = CommonStatusCodes.NETWORK_ERROR;
    public static final int INTERNAL_ERROR = CommonStatusCodes.INTERNAL_ERROR;
    public static final int DEVELOPER_ERROR = CommonStatusCodes.DEVELOPER_ERROR;
    public static final int ERROR = CommonStatusCodes.ERROR;
    public static final int INTERRUPTED = CommonStatusCodes.INTERRUPTED;
    public static final int TIMEOUT = CommonStatusCodes.TIMEOUT;
    public static final int CANCELED = CommonStatusCodes.CANCELED;
    public static final int API_NOT_CONNECTED = CommonStatusCodes.API_NOT_CONNECTED;

    // Sign-in-only codes.
    public static final int SIGN_IN_FAILED = 12500;
    public static final int SIGN_IN_CANCELLED = 12501;
    public static final int SIGN_IN_CURRENTLY_IN_PROGRESS = 12502;

    public static String getStatusCodeString(int statusCode) {
        switch (statusCode) {
            case SIGN_IN_CANCELLED:
                return "The sign in was cancelled by the user.";
            case SIGN_IN_CURRENTLY_IN_PROGRESS:
                return "A sign in process is currently in progress and the current one cannot continue.";
            case SIGN_IN_FAILED:
                return "The sign in attempt didn't succeed with the current account.";
            default:
                return CommonStatusCodes.getStatusCodeString(statusCode);
        }
    }
}
