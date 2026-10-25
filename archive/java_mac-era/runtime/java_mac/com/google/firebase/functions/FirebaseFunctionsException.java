package com.google.firebase.functions;

// com.google.firebase.functions.FirebaseFunctionsException -- the exception a
// failed callable Task carries (task.getException() returns this). Apps catch it
// and branch on getCode(). On Mac.apk there is no Cloud Functions backend
// (de-Googled device), so every call fails with Code.INTERNAL -- we never
// fabricate a function result.
//
// Signatures mirror the published Firebase Functions Android reference
// (developer.android.com/reference/com/google/firebase/functions/FirebaseFunctionsException).
public class FirebaseFunctionsException extends com.google.firebase.FirebaseException {

    // The canonical gRPC-derived status enum. Ordinals/names match the published
    // reference so apps that switch on Code resolve correctly.
    public enum Code {
        OK,
        CANCELLED,
        UNKNOWN,
        INVALID_ARGUMENT,
        DEADLINE_EXCEEDED,
        NOT_FOUND,
        ALREADY_EXISTS,
        PERMISSION_DENIED,
        RESOURCE_EXHAUSTED,
        FAILED_PRECONDITION,
        ABORTED,
        OUT_OF_RANGE,
        UNIMPLEMENTED,
        INTERNAL,
        UNAVAILABLE,
        DATA_LOSS,
        UNAUTHENTICATED;

        // The published API maps an HTTP status code to a Code. Kept faithful so
        // callers that use it get the same mapping.
        public static Code fromHttpStatus(int status) {
            switch (status) {
                case 0: return INTERNAL;
                case 200: return OK;
                case 400: return INVALID_ARGUMENT;
                case 401: return UNAUTHENTICATED;
                case 403: return PERMISSION_DENIED;
                case 404: return NOT_FOUND;
                case 409: return ABORTED;
                case 429: return RESOURCE_EXHAUSTED;
                case 499: return CANCELLED;
                case 500: return INTERNAL;
                case 501: return UNIMPLEMENTED;
                case 503: return UNAVAILABLE;
                case 504: return DEADLINE_EXCEEDED;
                default: return INTERNAL;
            }
        }
    }

    private final Code code;
    private final Object details;

    FirebaseFunctionsException(String message, Code code, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    FirebaseFunctionsException(String message, Code code, Object details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details;
    }

    public Code getCode() { return code; }

    public Object getDetails() { return details; }
}
