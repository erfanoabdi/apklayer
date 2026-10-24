package com.google.firebase.firestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

// com.google.firebase.firestore.QuerySnapshot -- the result of a Query.get().
// On our offline-only (de-Googled) shim every query resolves to an EMPTY result
// (no backend, and we do not fabricate a local document store). isEmpty()=true,
// size()=0, getDocuments() empty, iteration yields nothing. This is the honest
// negative result the SDK returns when offline with no cached data, never
// fabricated rows.
public class QuerySnapshot implements Iterable<QueryDocumentSnapshot> {

    private final Query query;
    private final List<QueryDocumentSnapshot> documents;
    private final List<DocumentChange> documentChanges;
    private final SnapshotMetadata metadata;

    QuerySnapshot(Query query,
                  List<QueryDocumentSnapshot> documents,
                  List<DocumentChange> documentChanges,
                  SnapshotMetadata metadata) {
        this.query = query;
        this.documents = documents;
        this.documentChanges = documentChanges;
        this.metadata = metadata;
    }

    // The empty-result factory used by every offline read.
    static QuerySnapshot empty(Query query) {
        return new QuerySnapshot(query,
            Collections.<QueryDocumentSnapshot>emptyList(),
            Collections.<DocumentChange>emptyList(),
            new SnapshotMetadata(false, true));
    }

    public Query getQuery() { return query; }

    public SnapshotMetadata getMetadata() { return metadata; }

    public List<DocumentChange> getDocumentChanges() {
        return documentChanges;
    }

    public List<DocumentChange> getDocumentChanges(MetadataChanges metadataChanges) {
        return documentChanges;
    }

    public List<DocumentSnapshot> getDocuments() {
        return new ArrayList<DocumentSnapshot>(documents);
    }

    public <T> List<T> toObjects(Class<T> valueType) {
        // Empty result -> empty list (never fabricated objects).
        return new ArrayList<T>();
    }

    public <T> List<T> toObjects(Class<T> valueType,
                                 DocumentSnapshot.ServerTimestampBehavior serverTimestampBehavior) {
        return new ArrayList<T>();
    }

    public boolean isEmpty() { return documents.isEmpty(); }

    public int size() { return documents.size(); }

    @Override
    public Iterator<QueryDocumentSnapshot> iterator() {
        return documents.iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuerySnapshot)) return false;
        QuerySnapshot q = (QuerySnapshot) o;
        return documents.equals(q.documents)
            && (query == null ? q.query == null : query.equals(q.query));
    }

    @Override
    public int hashCode() {
        int result = query != null ? query.hashCode() : 0;
        result = 31 * result + documents.hashCode();
        return result;
    }
}
