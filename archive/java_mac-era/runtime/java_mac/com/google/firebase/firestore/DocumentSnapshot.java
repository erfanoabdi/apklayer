package com.google.firebase.firestore;

import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.Map;

// com.google.firebase.firestore.DocumentSnapshot -- the result of a
// DocumentReference.get(). On our offline-only (de-Googled) shim, a get()
// always resolves to a NOT-EXISTS snapshot (there is no backend and no local
// cache that we populate from remote data), so exists()=false, getData()=null,
// and every typed field getter returns null per the published contract for an
// absent field. Writes done locally are NOT reflected back into a subsequent
// get() because we deliberately do not fabricate a document store; the SDK's
// contract only guarantees local persistence across the offline cache, which
// we do not emulate. This is the honest negative result, never fabricated data.
public class DocumentSnapshot {

    // ServerTimestampBehavior mirrors the published enum; irrelevant on our
    // shim (no server timestamps to resolve) but apps pass it to get().
    public enum ServerTimestampBehavior {
        NONE,
        ESTIMATE,
        PREVIOUS;

        static final ServerTimestampBehavior DEFAULT = NONE;
    }

    private final DocumentReference reference;
    private final String id;
    private final boolean exists;
    private final Map<String, Object> data;
    private final SnapshotMetadata metadata;

    DocumentSnapshot(DocumentReference reference,
                     String id,
                     boolean exists,
                     Map<String, Object> data,
                     SnapshotMetadata metadata) {
        this.reference = reference;
        this.id = id;
        this.exists = exists;
        this.data = data;
        this.metadata = metadata;
    }

    // Factory for the common case: an absent document at a reference.
    static DocumentSnapshot notExists(DocumentReference reference) {
        return new DocumentSnapshot(reference, reference.getId(), false, null,
            new SnapshotMetadata(false, true));
    }

    public boolean exists() { return exists; }

    public String getId() { return id; }

    public DocumentReference getReference() { return reference; }

    public SnapshotMetadata getMetadata() { return metadata; }

    public Map<String, Object> getData() { return data; }

    public Map<String, Object> getData(ServerTimestampBehavior serverTimestampBehavior) {
        return data;
    }

    public boolean contains(String field) {
        return false;
    }

    public boolean contains(FieldPath fieldPath) {
        return false;
    }

    public Object get(String field) {
        return null;
    }

    public Object get(String field, ServerTimestampBehavior serverTimestampBehavior) {
        return null;
    }

    public Object get(FieldPath fieldPath) {
        return null;
    }

    public Object get(FieldPath fieldPath, ServerTimestampBehavior serverTimestampBehavior) {
        return null;
    }

    public <T> T get(String field, Class<T> valueType) {
        return null;
    }

    public <T> T get(String field, Class<T> valueType,
                     ServerTimestampBehavior serverTimestampBehavior) {
        return null;
    }

    public <T> T get(FieldPath fieldPath, Class<T> valueType) {
        return null;
    }

    public <T> T get(FieldPath fieldPath, Class<T> valueType,
                     ServerTimestampBehavior serverTimestampBehavior) {
        return null;
    }

    public Boolean getBoolean(String field) { return null; }

    public Double getDouble(String field) { return null; }

    public String getString(String field) { return null; }

    public Long getLong(String field) { return null; }

    public Date getDate(String field) { return null; }

    public Date getDate(String field, ServerTimestampBehavior serverTimestampBehavior) {
        return null;
    }

    public Timestamp getTimestamp(String field) { return null; }

    public Timestamp getTimestamp(String field, ServerTimestampBehavior serverTimestampBehavior) {
        return null;
    }

    public Blob getBlob(String field) { return null; }

    public GeoPoint getGeoPoint(String field) { return null; }

    public DocumentReference getDocumentReference(String field) { return null; }

    public <T> T toObject(Class<T> valueType) { return null; }

    public <T> T toObject(Class<T> valueType, ServerTimestampBehavior serverTimestampBehavior) {
        return null;
    }

    @Override
    public String toString() {
        return "DocumentSnapshot{key=" + id + ", metadata=" + metadata
            + ", doc=" + (exists ? "present" : "absent") + "}";
    }
}
