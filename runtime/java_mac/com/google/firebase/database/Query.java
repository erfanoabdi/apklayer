package com.google.firebase.database;

import com.google.android.gms.tasks.GmsTaskExecutors;

// com.google.firebase.database.Query -- the base type for DatabaseReference,
// exposing the filter/order/limit builders plus the listener-attach methods.
// On our offline-only (de-Googled) shim the query never round-trips to a
// backend, so:
//   - every order/filter/limit builder returns a Query rooted at the same
//     location (the ordering is irrelevant when results are always empty)
//   - addListenerForSingleValueEvent fires onDataChange ONCE with an empty
//     snapshot (the honest "no data" result, never fabricated)
//   - addValueEventListener / addChildEventListener fire the initial empty
//     snapshot once and are then retained but never call back again (no
//     backend to push updates); removeEventListener forgets them
// This lets data-driven apps init, attach listeners, and receive a real empty
// snapshot instead of NoClassDefFound / hang.
//
// Signatures mirror the published Firebase RTDB Android reference.
public class Query {

    // selfRef is the DatabaseReference this query targets. DatabaseReference
    // cannot pass "this" to super() before its own ctor body runs, so this is
    // wired via setSelf() from the subclass ctor (set once, never reassigned
    // afterward). Not final for that reason.
    DatabaseReference selfRef;

    Query(DatabaseReference selfRef) {
        this.selfRef = selfRef;
    }

    // Package-private one-time wiring of the back-reference; see ctor note.
    final void setSelf(DatabaseReference ref) {
        this.selfRef = ref;
    }

    // ---- ordering (no-ops: empty results have no order) -------------------

    public Query orderByChild(String path) { return this; }
    public Query orderByKey() { return this; }
    public Query orderByValue() { return this; }
    public Query orderByPriority() { return this; }

    // ---- filtering (no-ops) ----------------------------------------------

    public Query startAt(String value) { return this; }
    public Query startAt(double value) { return this; }
    public Query startAt(boolean value) { return this; }
    public Query startAt(String value, String key) { return this; }
    public Query startAt(double value, String key) { return this; }
    public Query startAt(boolean value, String key) { return this; }

    public Query startAfter(String value) { return this; }
    public Query startAfter(double value) { return this; }
    public Query startAfter(boolean value) { return this; }
    public Query startAfter(String value, String key) { return this; }
    public Query startAfter(double value, String key) { return this; }
    public Query startAfter(boolean value, String key) { return this; }

    public Query endAt(String value) { return this; }
    public Query endAt(double value) { return this; }
    public Query endAt(boolean value) { return this; }
    public Query endAt(String value, String key) { return this; }
    public Query endAt(double value, String key) { return this; }
    public Query endAt(boolean value, String key) { return this; }

    public Query endBefore(String value) { return this; }
    public Query endBefore(double value) { return this; }
    public Query endBefore(boolean value) { return this; }
    public Query endBefore(String value, String key) { return this; }
    public Query endBefore(double value, String key) { return this; }
    public Query endBefore(boolean value, String key) { return this; }

    public Query equalTo(String value) { return this; }
    public Query equalTo(double value) { return this; }
    public Query equalTo(boolean value) { return this; }
    public Query equalTo(String value, String key) { return this; }
    public Query equalTo(double value, String key) { return this; }
    public Query equalTo(boolean value, String key) { return this; }

    public Query limitToFirst(int limit) { return this; }
    public Query limitToLast(int limit) { return this; }

    public DatabaseReference getRef() { return selfRef; }

    // ---- listeners --------------------------------------------------------

    public ValueEventListener addValueEventListener(final ValueEventListener listener) {
        // Fire the initial empty snapshot, then retain (no backend pushes).
        fireEmptyOnce(listener);
        return listener;
    }

    public void addListenerForSingleValueEvent(final ValueEventListener listener) {
        fireEmptyOnce(listener);
    }

    public ChildEventListener addChildEventListener(ChildEventListener listener) {
        // No children to enumerate; nothing fires (matches an empty location:
        // the SDK does not call onChildAdded for a location with no children,
        // and there is no completion callback for child listeners).
        return listener;
    }

    public void removeEventListener(ValueEventListener listener) {}
    public void removeEventListener(ChildEventListener listener) {}

    public void keepSynced(boolean keepSynced) {}

    private void fireEmptyOnce(final ValueEventListener listener) {
        final DataSnapshot snap = DataSnapshot.empty(selfRef);
        // Dispatch asynchronously, like the real SDK delivers callbacks off
        // the calling stack, so apps that attach a listener inside onCreate
        // and read view state in onDataChange behave consistently.
        GmsTaskExecutors.defaultExecutor().execute(new Runnable() {
            @Override public void run() {
                try { listener.onDataChange(snap); } catch (Throwable ignored) {}
            }
        });
    }
}
