package com.google.firebase.firestore;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.Executor;

// com.google.firebase.firestore.Query -- the chainable query builder and the
// superclass of CollectionReference. Every where*/orderBy/limit/cursor method
// returns a Query so apps can fluently build a query; on our offline-only
// (de-Googled) shim the constraints are accepted and remembered structurally
// but never matched against a backend, because get() always resolves to an
// EMPTY QuerySnapshot (honest negative result, never fabricated rows).
//
// We keep this query immutable-by-copy like the real SDK: each builder method
// returns a new Query rooted at the same FirebaseFirestore + path. We do not
// materially evaluate the constraints (there is no data), so the new instances
// just carry the firestore + path forward; this is enough for app code to
// compile, chain, and degrade gracefully.
public class Query {

    // com.google.firebase.firestore.Query.Direction -- orderBy direction.
    public enum Direction {
        ASCENDING,
        DESCENDING
    }

    final FirebaseFirestore firestore;
    final String path;

    Query(FirebaseFirestore firestore, String path) {
        this.firestore = firestore;
        this.path = path;
    }

    public FirebaseFirestore getFirestore() { return firestore; }

    // Returns a fresh Query carrying the same root. Constraints are not
    // evaluated (no backend / no fabricated store), so a copy suffices.
    private Query copy() {
        return new Query(firestore, path);
    }

    // ---- equality / inequality / range -----------------------------------

    public Query whereEqualTo(String field, Object value) { return copy(); }
    public Query whereEqualTo(FieldPath fieldPath, Object value) { return copy(); }

    public Query whereNotEqualTo(String field, Object value) { return copy(); }
    public Query whereNotEqualTo(FieldPath fieldPath, Object value) { return copy(); }

    public Query whereLessThan(String field, Object value) { return copy(); }
    public Query whereLessThan(FieldPath fieldPath, Object value) { return copy(); }

    public Query whereLessThanOrEqualTo(String field, Object value) { return copy(); }
    public Query whereLessThanOrEqualTo(FieldPath fieldPath, Object value) { return copy(); }

    public Query whereGreaterThan(String field, Object value) { return copy(); }
    public Query whereGreaterThan(FieldPath fieldPath, Object value) { return copy(); }

    public Query whereGreaterThanOrEqualTo(String field, Object value) { return copy(); }
    public Query whereGreaterThanOrEqualTo(FieldPath fieldPath, Object value) { return copy(); }

    // ---- array / membership ----------------------------------------------

    public Query whereArrayContains(String field, Object value) { return copy(); }
    public Query whereArrayContains(FieldPath fieldPath, Object value) { return copy(); }

    public Query whereArrayContainsAny(String field, java.util.List<? extends Object> values) {
        return copy();
    }
    public Query whereArrayContainsAny(FieldPath fieldPath,
                                       java.util.List<? extends Object> values) {
        return copy();
    }

    public Query whereIn(String field, java.util.List<? extends Object> values) { return copy(); }
    public Query whereIn(FieldPath fieldPath, java.util.List<? extends Object> values) {
        return copy();
    }

    public Query whereNotIn(String field, java.util.List<? extends Object> values) { return copy(); }
    public Query whereNotIn(FieldPath fieldPath, java.util.List<? extends Object> values) {
        return copy();
    }

    // ---- ordering / limits -----------------------------------------------

    public Query orderBy(String field) { return copy(); }
    public Query orderBy(String field, Direction direction) { return copy(); }
    public Query orderBy(FieldPath fieldPath) { return copy(); }
    public Query orderBy(FieldPath fieldPath, Direction direction) { return copy(); }

    public Query limit(long limit) { return copy(); }
    public Query limitToLast(long limit) { return copy(); }

    // ---- cursors ----------------------------------------------------------

    public Query startAt(DocumentSnapshot snapshot) { return copy(); }
    public Query startAt(Object... fieldValues) { return copy(); }

    public Query startAfter(DocumentSnapshot snapshot) { return copy(); }
    public Query startAfter(Object... fieldValues) { return copy(); }

    public Query endBefore(DocumentSnapshot snapshot) { return copy(); }
    public Query endBefore(Object... fieldValues) { return copy(); }

    public Query endAt(DocumentSnapshot snapshot) { return copy(); }
    public Query endAt(Object... fieldValues) { return copy(); }

    // ---- reads ------------------------------------------------------------

    // Always resolves EMPTY (no backend, no fabricated store).
    public Task<QuerySnapshot> get() {
        return Tasks.forResult(QuerySnapshot.empty(this));
    }

    public Task<QuerySnapshot> get(Source source) {
        return Tasks.forResult(QuerySnapshot.empty(this));
    }

    // ---- aggregation ------------------------------------------------------

    public AggregateQuery count() {
        return new AggregateQuery(this);
    }

    // ---- snapshot listeners ----------------------------------------------
    // Fire once with the empty snapshot, then never again; remove() is a no-op.

    public ListenerRegistration addSnapshotListener(final EventListener<QuerySnapshot> listener) {
        fireEmpty(listener);
        return NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotListener(MetadataChanges metadataChanges,
                                                    final EventListener<QuerySnapshot> listener) {
        fireEmpty(listener);
        return NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotListener(Executor executor,
                                                    final EventListener<QuerySnapshot> listener) {
        fireEmpty(listener);
        return NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotListener(Executor executor,
                                                    MetadataChanges metadataChanges,
                                                    final EventListener<QuerySnapshot> listener) {
        fireEmpty(listener);
        return NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotListener(android.app.Activity activity,
                                                    final EventListener<QuerySnapshot> listener) {
        fireEmpty(listener);
        return NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotListener(android.app.Activity activity,
                                                    MetadataChanges metadataChanges,
                                                    final EventListener<QuerySnapshot> listener) {
        fireEmpty(listener);
        return NoopRegistration.INSTANCE;
    }

    private void fireEmpty(EventListener<QuerySnapshot> listener) {
        if (listener != null) {
            listener.onEvent(QuerySnapshot.empty(this), null);
        }
    }

    // Shared no-op handle for offline snapshot listeners.
    static final class NoopRegistration implements ListenerRegistration {
        static final NoopRegistration INSTANCE = new NoopRegistration();
        @Override public void remove() { }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Query)) return false;
        Query q = (Query) o;
        return path.equals(q.path) && firestore == q.firestore;
    }

    @Override
    public int hashCode() {
        return 31 * (firestore != null ? firestore.hashCode() : 0) + path.hashCode();
    }
}
