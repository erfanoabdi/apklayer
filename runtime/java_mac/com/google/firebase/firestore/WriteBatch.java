package com.google.firebase.firestore;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.Map;

// com.google.firebase.firestore.WriteBatch -- batches set/update/delete into a
// single atomic commit. On our offline-only shim each staged op is a no-op
// (writes are accepted locally, never round-tripped) and commit() resolves
// SUCCESSFULLY. Every staging method returns this for chaining, matching the
// published fluent API.
public class WriteBatch {

    private final FirebaseFirestore firestore;

    WriteBatch(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    // com.google.firebase.firestore.WriteBatch.Function -- the body passed to
    // FirebaseFirestore.runBatch(); stages writes onto the supplied batch.
    public interface Function {
        void apply(WriteBatch batch);
    }

    public WriteBatch set(DocumentReference documentRef, Object data) {
        return this;
    }

    public WriteBatch set(DocumentReference documentRef, Object data, SetOptions options) {
        return this;
    }

    public WriteBatch update(DocumentReference documentRef, Map<String, Object> data) {
        return this;
    }

    public WriteBatch update(DocumentReference documentRef, String field, Object value,
                             Object... moreFieldsAndValues) {
        return this;
    }

    public WriteBatch update(DocumentReference documentRef, FieldPath fieldPath, Object value,
                             Object... moreFieldsAndValues) {
        return this;
    }

    public WriteBatch delete(DocumentReference documentRef) {
        return this;
    }

    public Task<Void> commit() {
        return Tasks.forResult(null);
    }
}
