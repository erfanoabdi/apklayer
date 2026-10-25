package com.google.firebase.firestore;

// com.google.firebase.firestore.AggregateSource -- where an aggregate query
// reads from. Only SERVER is published today; ship it for app compile-time
// references. On the offline-only shim the aggregate always resolves to 0.
public enum AggregateSource {
    SERVER
}
