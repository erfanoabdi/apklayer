package com.google.firebase.firestore;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.Map;
import java.util.UUID;

// com.google.firebase.firestore.CollectionReference -- a handle to a collection;
// extends Query (so all where*/orderBy/limit chaining + get() returning an
// empty QuerySnapshot are inherited). add() and document() create child doc
// references; add() resolves SUCCESSFULLY (local write accepted) with a
// generated id, mirroring the SDK's offline semantics (never round-trips to a
// backend, never fabricates a readable document).
public class CollectionReference extends Query {

    CollectionReference(FirebaseFirestore firestore, String path) {
        super(firestore, path);
    }

    public String getId() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    public String getPath() { return path; }

    // The parent document of this collection, or null for a root collection.
    // A path like "a/b/c" (collection c under doc b under collection a) has a
    // parent document at "a/b"; a root collection ("a") has no parent document.
    public DocumentReference getParent() {
        int slash = path.lastIndexOf('/');
        if (slash < 0) return null;
        String parentPath = path.substring(0, slash);
        if (parentPath.isEmpty()) return null;
        return new DocumentReference(firestore, parentPath);
    }

    // Returns a reference to a new document with an auto-generated id.
    public DocumentReference document() {
        return new DocumentReference(firestore, path + "/" + autoId());
    }

    public DocumentReference document(String documentPath) {
        return new DocumentReference(firestore, path + "/" + documentPath);
    }

    // Adds a new document with auto-id; the write is accepted locally.
    public Task<DocumentReference> add(Object data) {
        final DocumentReference ref = document();
        // Local write accepted; never round-trips.
        return Tasks.forResult(ref);
    }

    public Task<DocumentReference> add(Map<String, Object> data) {
        final DocumentReference ref = document();
        return Tasks.forResult(ref);
    }

    // Firestore auto-ids are 20-char base62. We approximate the shape with a
    // 20-char alphanumeric token (uniqueness is all that matters locally).
    private static String autoId() {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(20);
        long bits = UUID.randomUUID().getMostSignificantBits();
        long bits2 = UUID.randomUUID().getLeastSignificantBits();
        for (int i = 0; i < 20; i++) {
            long src = (i < 10) ? bits : bits2;
            int shift = (i % 10) * 6;
            int idx = (int) ((src >>> shift) & 0x3f) % chars.length();
            sb.append(chars.charAt(Math.abs(idx)));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "CollectionReference{path=" + path + "}";
    }
}
