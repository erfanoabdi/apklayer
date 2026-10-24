package com.google.firebase.firestore;

import com.google.firebase.FirebaseException;

// com.google.firebase.firestore.FirebaseFirestoreException -- the exception
// type all Firestore Task failures and listener errors carry. We rarely
// produce one on the offline-only shim (reads succeed-empty, writes succeed
// locally), but apps catch it and reference Code, so ship the full surface.
public class FirebaseFirestoreException extends FirebaseException {

    // The canonical gRPC-status-derived code set, value-for-value as published.
    public enum Code {
        OK(0),
        CANCELLED(1),
        UNKNOWN(2),
        INVALID_ARGUMENT(3),
        DEADLINE_EXCEEDED(4),
        NOT_FOUND(5),
        ALREADY_EXISTS(6),
        PERMISSION_DENIED(7),
        RESOURCE_EXHAUSTED(8),
        FAILED_PRECONDITION(9),
        ABORTED(10),
        OUT_OF_RANGE(11),
        UNIMPLEMENTED(12),
        INTERNAL(13),
        UNAVAILABLE(14),
        DATA_LOSS(15),
        UNAUTHENTICATED(16);

        private final int value;
        Code(int value) { this.value = value; }
        public int value() { return value; }

        public static Code fromValue(int value) {
            for (Code c : values()) {
                if (c.value == value) return c;
            }
            return UNKNOWN;
        }
    }

    private final Code code;

    public FirebaseFirestoreException(String detailMessage, Code code) {
        super(detailMessage);
        if (code == null) {
            throw new NullPointerException("Provided code must not be null.");
        }
        if (code == Code.OK) {
            throw new IllegalArgumentException("A FirebaseFirestoreException should never be "
                + "thrown for OK");
        }
        this.code = code;
    }

    public FirebaseFirestoreException(String detailMessage, Code code, Throwable cause) {
        super(detailMessage, cause);
        if (code == null) {
            throw new NullPointerException("Provided code must not be null.");
        }
        if (code == Code.OK) {
            throw new IllegalArgumentException("A FirebaseFirestoreException should never be "
                + "thrown for OK");
        }
        this.code = code;
    }

    public Code getCode() { return code; }
}
