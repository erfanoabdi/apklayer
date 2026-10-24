package com.google.firebase.firestore;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.Map;
import java.util.concurrent.Executor;

// com.google.firebase.firestore.DocumentReference -- a handle to a single
// document. On our offline-only (de-Googled) shim:
//   - get() resolves to a NOT-EXISTS snapshot (no backend, no fabricated store)
//   - set/update/delete resolve SUCCESSFULLY but only locally; we acknowledge
//     the write (matching the SDK's offline "local write accepted" semantics)
//     and do NOT round-trip it to a backend. We deliberately do not persist it
//     into a fake store that a later get() would read back, because that would
//     be fabricating data the real offline cache only holds transiently; the
//     honest behavior is "write accepted, reads still empty".
// This lets data-driven apps init, issue writes without exceptions, and read
// empty -- the graceful-degrade path -- instead of NoClassDefFound / hang.
public class DocumentReference {

    private final FirebaseFirestore firestore;
    private final String path;

    DocumentReference(FirebaseFirestore firestore, String path) {
        this.firestore = firestore;
        this.path = path;
    }

    public FirebaseFirestore getFirestore() { return firestore; }

    public String getId() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    public String getPath() { return path; }

    // The parent collection of this document.
    public CollectionReference getParent() {
        int slash = path.lastIndexOf('/');
        String parentPath = slash < 0 ? "" : path.substring(0, slash);
        return new CollectionReference(firestore, parentPath);
    }

    public CollectionReference collection(String collectionPath) {
        return new CollectionReference(firestore, path + "/" + collectionPath);
    }

    // ---- writes (succeed locally; never round-trip) -----------------------

    public Task<Void> set(Object data) {
        return Tasks.forResult(null);
    }

    public Task<Void> set(Object data, SetOptions options) {
        return Tasks.forResult(null);
    }

    public Task<Void> update(Map<String, Object> data) {
        return Tasks.forResult(null);
    }

    public Task<Void> update(String field, Object value, Object... moreFieldsAndValues) {
        return Tasks.forResult(null);
    }

    public Task<Void> update(FieldPath fieldPath, Object value, Object... moreFieldsAndValues) {
        return Tasks.forResult(null);
    }

    public Task<Void> delete() {
        return Tasks.forResult(null);
    }

    // ---- reads (always NOT-EXISTS) ---------------------------------------

    public Task<DocumentSnapshot> get() {
        return Tasks.forResult(DocumentSnapshot.notExists(this));
    }

    public Task<DocumentSnapshot> get(Source source) {
        return Tasks.forResult(DocumentSnapshot.notExists(this));
    }

    // ---- snapshot listeners (fire once not-exists, then nothing) ----------

    public ListenerRegistration addSnapshotListener(EventListener<DocumentSnapshot> listener) {
        fireNotExists(listener);
        return Query.NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotListener(MetadataChanges metadataChanges,
                                                    EventListener<DocumentSnapshot> listener) {
        fireNotExists(listener);
        return Query.NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotListener(Executor executor,
                                                    EventListener<DocumentSnapshot> listener) {
        fireNotExists(listener);
        return Query.NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotListener(Executor executor,
                                                    MetadataChanges metadataChanges,
                                                    EventListener<DocumentSnapshot> listener) {
        fireNotExists(listener);
        return Query.NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotListener(android.app.Activity activity,
                                                    EventListener<DocumentSnapshot> listener) {
        fireNotExists(listener);
        return Query.NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotListener(android.app.Activity activity,
                                                    MetadataChanges metadataChanges,
                                                    EventListener<DocumentSnapshot> listener) {
        fireNotExists(listener);
        return Query.NoopRegistration.INSTANCE;
    }

    private void fireNotExists(EventListener<DocumentSnapshot> listener) {
        if (listener != null) {
            listener.onEvent(DocumentSnapshot.notExists(this), null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentReference)) return false;
        DocumentReference r = (DocumentReference) o;
        return path.equals(r.path) && firestore == r.firestore;
    }

    @Override
    public int hashCode() {
        return 31 * (firestore != null ? firestore.hashCode() : 0) + path.hashCode();
    }

    @Override
    public String toString() {
        return "DocumentReference{path=" + path + "}";
    }
}
