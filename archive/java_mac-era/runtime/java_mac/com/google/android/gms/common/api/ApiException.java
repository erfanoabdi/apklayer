package com.google.android.gms.common.api;

// com.google.android.gms.common.api.ApiException -- the exception that a failed
// GMS Task carries (task.getException() returns this). Apps catch ApiException
// and branch on getStatusCode() (e.g. SIGN_IN_REQUIRED vs SERVICE_MISSING) to
// choose their degrade path, so the type and its Status accessor must be exact.
public class ApiException extends Exception {

    protected final Status mStatus;

    public ApiException(Status status) {
        super(status.getStatusCode() + ": "
                + (status.getStatusMessage() != null ? status.getStatusMessage()
                                                     : CommonStatusCodes.getStatusCodeString(
                                                            status.getStatusCode())));
        this.mStatus = status;
    }

    public Status getStatus() { return mStatus; }
    public int getStatusCode() { return mStatus.getStatusCode(); }

    /** @deprecated use {@link #getStatusCode()} instead. */
    @Deprecated
    public String getStatusMessage() { return mStatus.getStatusMessage(); }
}
