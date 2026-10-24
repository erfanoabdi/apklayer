package com.google.android.gms.common;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.IntentSender;

// com.google.android.gms.common.ConnectionResult: the result of a Google Play
// services connection attempt. Mac.apk has no real Play services; code paths
// that construct/inspect a ConnectionResult get SUCCESS so callers take the
// "services available" branch. Public surface mirrors the GMS SDK.
public final class ConnectionResult {
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
    public static final int CANCELED = 13;
    public static final int TIMEOUT = 14;
    public static final int INTERRUPTED = 15;
    public static final int API_UNAVAILABLE = 16;
    public static final int SIGN_IN_FAILED = 17;
    public static final int SERVICE_UPDATING = 18;
    public static final int SERVICE_MISSING_PERMISSION = 19;
    public static final int RESTRICTED_PROFILE = 20;
    public static final int API_DISABLED = 23;

    private final int statusCode;
    private final PendingIntent pendingIntent;
    private final String message;

    public ConnectionResult(int statusCode) { this(statusCode, null, null); }
    public ConnectionResult(int statusCode, PendingIntent pendingIntent) { this(statusCode, pendingIntent, null); }
    public ConnectionResult(int statusCode, PendingIntent pendingIntent, String message) {
        this.statusCode = statusCode;
        this.pendingIntent = pendingIntent;
        this.message = message;
    }

    public int getErrorCode()              { return statusCode; }
    public boolean isSuccess()             { return statusCode == SUCCESS; }
    public boolean hasResolution()         { return pendingIntent != null; }
    public PendingIntent getResolution()   { return pendingIntent; }
    public String getErrorMessage()        { return message; }

    /** Real GMS launches the resolution PendingIntent (e.g. an account picker)
     *  for a RESOLUTION_REQUIRED result. On Mac.apk there is no Play Services
     *  system app, so no result ever carries a resolution PendingIntent; this is
     *  a clean no-op exactly like a de-Googled device. The checked-exception
     *  declaration matches the GMS signature so callers that wrap it compile. */
    public void startResolutionForResult(Activity activity, int requestCode)
            throws IntentSender.SendIntentException {
        // pendingIntent is always null here; nothing to launch.
    }

    @Override public String toString()     { return "ConnectionResult{statusCode=" + statusCode + "}"; }
}
