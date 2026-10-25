package com.google.firebase.storage;

// com.google.firebase.storage.StorageException -- the exception a failed Cloud
// Storage Task carries (task.getException() returns this). Apps catch it and
// branch on getErrorCode(). On Mac.apk there is no Storage backend (de-Googled
// device), so every network operation fails with ERROR_UNKNOWN / a clean
// negative -- we never fabricate a successful download or upload.
//
// Signatures mirror the published Firebase Storage Android reference
// (developer.android.com/reference/com/google/firebase/storage/StorageException).
public class StorageException extends com.google.firebase.FirebaseException {

    public static final int ERROR_UNKNOWN = -13000;
    public static final int ERROR_OBJECT_NOT_FOUND = -13010;
    public static final int ERROR_BUCKET_NOT_FOUND = -13011;
    public static final int ERROR_PROJECT_NOT_FOUND = -13012;
    public static final int ERROR_QUOTA_EXCEEDED = -13013;
    public static final int ERROR_NOT_AUTHENTICATED = -13020;
    public static final int ERROR_NOT_AUTHORIZED = -13021;
    public static final int ERROR_RETRY_LIMIT_EXCEEDED = -13030;
    public static final int ERROR_INVALID_CHECKSUM = -13031;
    public static final int ERROR_CANCELED = -13040;

    private final int errorCode;
    private final int httpResultCode;

    // The published ctor is hidden; the SDK builds instances via fromErrorStatus
    // / fromException. We expose a protected ctor so subclassing/our factories
    // work, mirroring observable behavior (getErrorCode / getHttpResultCode).
    StorageException(int errorCode, Throwable inner, int httpResultCode) {
        super(getErrorMessageForCode(errorCode), inner);
        this.errorCode = errorCode;
        this.httpResultCode = httpResultCode;
    }

    public int getErrorCode() { return errorCode; }

    public int getHttpResultCode() { return httpResultCode; }

    public boolean getIsRecoverableException() {
        // Nothing is recoverable without a backend.
        return false;
    }

    public Throwable getCause() { return super.getCause(); }

    // Factory used by the SDK to translate an HTTP status + Throwable into a
    // typed StorageException. On Mac.apk we have no HTTP layer, so callers that
    // need a failure use this to produce a faithful ERROR_UNKNOWN instance.
    public static StorageException fromErrorStatus(
            com.google.android.gms.common.api.Status status) {
        int code = ERROR_UNKNOWN;
        if (status != null && !status.isSuccess()) code = ERROR_UNKNOWN;
        return new StorageException(code, null, 0);
    }

    public static StorageException fromException(Throwable inner) {
        return new StorageException(ERROR_UNKNOWN, inner, 0);
    }

    public static StorageException fromExceptionAndHttpCode(
            Throwable inner, int httpResultCode) {
        int code = ERROR_UNKNOWN;
        if (httpResultCode == 404) code = ERROR_OBJECT_NOT_FOUND;
        else if (httpResultCode == 403) code = ERROR_NOT_AUTHORIZED;
        else if (httpResultCode == 401) code = ERROR_NOT_AUTHENTICATED;
        return new StorageException(code, inner, httpResultCode);
    }

    private static String getErrorMessageForCode(int errorCode) {
        switch (errorCode) {
            case ERROR_OBJECT_NOT_FOUND: return "Object does not exist at location.";
            case ERROR_BUCKET_NOT_FOUND: return "Bucket does not exist.";
            case ERROR_PROJECT_NOT_FOUND: return "Project does not exist.";
            case ERROR_QUOTA_EXCEEDED: return "Quota for bucket exceeded, retry after some time.";
            case ERROR_NOT_AUTHENTICATED: return "User is not authenticated, please authenticate using Firebase Authentication and try again.";
            case ERROR_NOT_AUTHORIZED: return "User does not have permission to access this object.";
            case ERROR_RETRY_LIMIT_EXCEEDED: return "The operation retry limit has been exceeded.";
            case ERROR_INVALID_CHECKSUM: return "Object has a checksum which does not match. Please retry the operation.";
            case ERROR_CANCELED: return "The operation was cancelled.";
            default: return "An unknown error occurred, please check the HTTP result code and inner exception for server response.";
        }
    }
}
