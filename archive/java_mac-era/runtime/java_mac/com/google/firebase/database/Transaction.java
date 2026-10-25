package com.google.firebase.database;

// com.google.firebase.database.Transaction -- the namespace type holding the
// Handler interface and Result factory used by DatabaseReference.runTransaction.
// Signatures mirror the published Firebase RTDB Android reference.
public class Transaction {

    private Transaction() {}

    // The handler an app supplies to runTransaction.
    public interface Handler {
        Result doTransaction(MutableData currentData);
        void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData);
    }

    // The outcome a Handler returns to commit or abort the transaction.
    public static class Result {
        private final boolean success;
        private final MutableData data;

        private Result(boolean success, MutableData data) {
            this.success = success;
            this.data = data;
        }

        boolean isSuccess() { return success; }

        MutableData getNode() { return data; }
    }

    public static Result success(MutableData resultData) {
        return new Result(true, resultData);
    }

    public static Result abort() {
        return new Result(false, null);
    }
}
