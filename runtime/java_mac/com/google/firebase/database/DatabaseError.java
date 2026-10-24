package com.google.firebase.database;

// com.google.firebase.database.DatabaseError -- the error type delivered to
// ValueEventListener.onCancelled / completion callbacks. On our offline-only
// (de-Googled) shim we never round-trip to a backend, so reads simply return
// empty rather than cancelling; this type is kept so apps that reference the
// error codes / construct one compile and so listener signatures match the
// published API exactly.
//
// Signatures mirror the Firebase Realtime Database Android reference.
public class DatabaseError {

    // Status codes (values match the published Firebase RTDB SDK constants).
    public static final int DATA_STALE = -1;
    public static final int OPERATION_FAILED = -2;
    public static final int PERMISSION_DENIED = -3;
    public static final int DISCONNECTED = -4;
    public static final int EXPIRED_TOKEN = -6;
    public static final int INVALID_TOKEN = -7;
    public static final int MAX_RETRIES = -8;
    public static final int OVERRIDDEN_BY_SET = -9;
    public static final int UNAVAILABLE = -10;
    public static final int USER_CODE_EXCEPTION = -11;
    public static final int NETWORK_ERROR = -24;
    public static final int WRITE_CANCELED = -25;
    public static final int UNKNOWN_ERROR = -999;

    private final int code;
    private final String message;
    private final String details;

    DatabaseError(int code, String message, String details) {
        this.code = code;
        this.message = message;
        this.details = details == null ? "" : details;
    }

    public static DatabaseError fromException(Throwable e) {
        return new DatabaseError(USER_CODE_EXCEPTION,
                e == null ? "" : String.valueOf(e.getMessage()), "");
    }

    public int getCode() { return code; }

    public String getMessage() { return message; }

    public String getDetails() { return details; }

    public DatabaseException toException() {
        return new DatabaseException(message);
    }

    @Override public String toString() {
        return "DatabaseError: " + message;
    }
}
