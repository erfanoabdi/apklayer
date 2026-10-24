package com.google.firebase.firestore;

import java.util.Map;

// com.google.firebase.firestore.QueryDocumentSnapshot -- a DocumentSnapshot
// known to exist (the element type of a QuerySnapshot). On our offline-only
// shim QuerySnapshots are always empty, so no instance of this is ever handed
// to an app, but apps reference the type (e.g. for-each loop variable type,
// toObject calls), so the shadow must exist with the published surface. Its
// getData() override returns a non-null map per the published contract (a
// query-result doc always has data).
public class QueryDocumentSnapshot extends DocumentSnapshot {

    QueryDocumentSnapshot(DocumentReference reference,
                          String id,
                          Map<String, Object> data,
                          SnapshotMetadata metadata) {
        super(reference, id, true, data, metadata);
    }

    // The published API narrows getData() to non-null on QueryDocumentSnapshot.
    @Override
    public Map<String, Object> getData() {
        Map<String, Object> d = super.getData();
        return d;
    }

    @Override
    public Map<String, Object> getData(ServerTimestampBehavior serverTimestampBehavior) {
        return getData();
    }
}
