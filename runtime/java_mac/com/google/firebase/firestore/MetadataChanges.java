package com.google.firebase.firestore;

// com.google.firebase.firestore.MetadataChanges -- controls whether a snapshot
// listener also fires for metadata-only changes (e.g. hasPendingWrites
// transitions). Published enum; faithful constant set.
public enum MetadataChanges {
    EXCLUDE,
    INCLUDE
}
