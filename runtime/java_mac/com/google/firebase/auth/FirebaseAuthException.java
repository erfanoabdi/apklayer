package com.google.firebase.auth;

import com.google.firebase.FirebaseException;

// FirebaseAuthException: the base auth exception apps catch off a
// failed sign-in / verification Task. Faithful surface from the
// Firebase ref: getErrorCode() returns the string code (e.g.
// "ERROR_INVALID_PHONE_NUMBER"). We use it to deliver the honest
// negative result when an operation needs the (absent) Firebase
// backend.
public class FirebaseAuthException extends FirebaseException {
    private final String errorCode;

    public FirebaseAuthException(String errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
