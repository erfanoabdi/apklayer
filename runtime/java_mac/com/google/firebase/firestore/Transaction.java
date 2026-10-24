package com.google.firebase.firestore;

import java.util.Map;

// com.google.firebase.firestore.Transaction -- the read/write handle passed to
// a Transaction.Function inside FirebaseFirestore.runTransaction(). On our
// offline-only shim, get() inside a transaction resolves to a NOT-EXISTS
// snapshot and set/update/delete are accepted locally (no round-trip). Every
// write method returns this for chaining, matching the published API.
public class Transaction {

    private final FirebaseFirestore firestore;

    Transaction(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    // com.google.firebase.firestore.Transaction.Function<TResult> -- the
    // user-supplied transaction body; may throw FirebaseFirestoreException.
    public interface Function<TResult> {
        TResult apply(Transaction transaction) throws FirebaseFirestoreException;
    }

    public Transaction set(DocumentReference documentRef, Object data) {
        return this;
    }

    public Transaction set(DocumentReference documentRef, Object data, SetOptions options) {
        return this;
    }

    public Transaction update(DocumentReference documentRef, Map<String, Object> data) {
        return this;
    }

    public Transaction update(DocumentReference documentRef, String field, Object value,
                              Object... moreFieldsAndValues) {
        return this;
    }

    public Transaction update(DocumentReference documentRef, FieldPath fieldPath, Object value,
                              Object... moreFieldsAndValues) {
        return this;
    }

    public Transaction delete(DocumentReference documentRef) {
        return this;
    }

    // Reads inside a transaction throw on real backend errors; here it simply
    // resolves NOT-EXISTS (no backend / no fabricated store).
    public DocumentSnapshot get(DocumentReference documentRef) throws FirebaseFirestoreException {
        return DocumentSnapshot.notExists(documentRef);
    }
}
