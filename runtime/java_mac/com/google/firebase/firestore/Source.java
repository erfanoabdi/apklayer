package com.google.firebase.firestore;

// com.google.firebase.firestore.Source -- where a get() should read from.
// On our de-Googled (offline-only) shim every read resolves locally and
// returns empty regardless of the chosen Source, but apps still reference
// the enum constants so we ship the exact published set.
public enum Source {
    DEFAULT,
    SERVER,
    CACHE
}
