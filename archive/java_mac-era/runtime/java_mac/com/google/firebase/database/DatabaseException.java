package com.google.firebase.database;

// com.google.firebase.database.DatabaseException -- thrown for malformed paths
// and value-marshalling errors in the Firebase Realtime Database SDK. Extends
// RuntimeException, mirroring the published API.
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message) { super(message); }
    public DatabaseException(String message, Throwable cause) { super(message, cause); }
}
