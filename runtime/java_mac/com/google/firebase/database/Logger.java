package com.google.firebase.database;

// com.google.firebase.database.Logger -- holds the Level enum accepted by
// FirebaseDatabase.setLogLevel. The published SDK exposes this as an interface
// with a nested Level enum; we keep just the enum surface apps reference.
public interface Logger {
    enum Level {
        DEBUG, INFO, WARN, ERROR, NONE
    }
}
