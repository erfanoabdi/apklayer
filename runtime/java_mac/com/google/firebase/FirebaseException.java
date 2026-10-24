package com.google.firebase;
// Base class for Firebase-specific exceptions. Real SDK extends
// AndroidException; we just extend Exception to dodge the dependency.
public class FirebaseException extends Exception {
    public FirebaseException(String detailMessage) { super(detailMessage); }
    public FirebaseException(String detailMessage, Throwable cause) { super(detailMessage, cause); }
}
