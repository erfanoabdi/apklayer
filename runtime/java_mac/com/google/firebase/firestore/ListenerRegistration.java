package com.google.firebase.firestore;

// com.google.firebase.firestore.ListenerRegistration -- handle returned by the
// various addSnapshotListener() calls; remove() detaches the listener. On our
// offline-only shim a snapshot listener fires exactly once (an empty / not-
// exists snapshot) and then never again, so remove() is a clean no-op.
public interface ListenerRegistration {
    void remove();
}
