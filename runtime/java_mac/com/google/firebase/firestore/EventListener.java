package com.google.firebase.firestore;

// com.google.firebase.firestore.EventListener<T> -- callback for snapshot
// listeners. Exactly one of (value, error) is non-null per the SDK contract.
public interface EventListener<T> {
    void onEvent(T value, FirebaseFirestoreException error);
}
