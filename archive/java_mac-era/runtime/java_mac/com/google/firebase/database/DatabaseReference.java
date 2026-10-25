package com.google.firebase.database;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.tasks.GmsTaskExecutors;

import java.util.Map;
import java.util.UUID;

// com.google.firebase.database.DatabaseReference -- a handle to a location in
// the Realtime Database tree, and the SDK's primary write surface. On our
// offline-only (de-Googled) shim:
//   - writes (setValue/updateChildren/removeValue/setPriority) resolve
//     SUCCESSFULLY but only locally; we acknowledge the write (matching the
//     SDK's offline "local write accepted" semantics) and do NOT round-trip
//     it to a backend. We deliberately do NOT persist it into a fake store
//     that a later read would echo back -- that would fabricate data; the
//     honest behavior is "write accepted, reads still empty".
//   - reads (inherited from Query) always deliver an EMPTY snapshot.
// This lets data-driven apps init, issue writes without exceptions, and read
// empty -- the graceful-degrade path -- instead of NoClassDefFound / hang.
//
// Signatures mirror the published Firebase RTDB Android reference.
public class DatabaseReference extends Query {

    // Completion callback for writes. Matches the published nested interface.
    public interface CompletionListener {
        void onComplete(DatabaseError error, DatabaseReference ref);
    }

    private final FirebaseDatabase database;
    private final String path;       // absolute path within the database, no leading slash
    private final boolean isRoot;

    DatabaseReference(FirebaseDatabase database, String path) {
        super(null);            // placeholder; selfRef wired below via reflection-free init
        // Query.selfRef is final and points back to THIS reference. We cannot
        // pass "this" before super(), so Query holds it as a settable-once
        // field initialized here.
        this.database = database;
        this.path = normalize(path);
        this.isRoot = this.path.isEmpty();
        setSelf(this);
    }

    private static String normalize(String p) {
        if (p == null) return "";
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    public FirebaseDatabase getDatabase() { return database; }

    // ---- navigation -------------------------------------------------------

    public DatabaseReference child(String pathString) {
        if (pathString == null) {
            throw new NullPointerException("Can't pass null for argument 'pathString'");
        }
        String childPath = path.isEmpty() ? normalize(pathString)
                                          : path + "/" + normalize(pathString);
        return new DatabaseReference(database, childPath);
    }

    public DatabaseReference getParent() {
        if (isRoot) return null;
        int slash = path.lastIndexOf('/');
        String parentPath = slash < 0 ? "" : path.substring(0, slash);
        return new DatabaseReference(database, parentPath);
    }

    public DatabaseReference getRoot() {
        return new DatabaseReference(database, "");
    }

    public String getKey() {
        if (isRoot) return null;
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    // push() mints a new child with a client-generated push id. Real Firebase
    // uses a time-ordered 20-char id; a UUID-derived unique key is sufficient
    // for an offline shim (the key is unique and stable for the returned ref).
    public DatabaseReference push() {
        String pushId = "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
        return child(pushId);
    }

    // ---- writes (succeed locally; never round-trip) -----------------------

    public Task<Void> setValue(Object value) {
        return Tasks.forResult(null);
    }

    public Task<Void> setValue(Object value, Object priority) {
        return Tasks.forResult(null);
    }

    public void setValue(Object value, CompletionListener listener) {
        completeOk(listener);
    }

    public void setValue(Object value, Object priority, CompletionListener listener) {
        completeOk(listener);
    }

    public Task<Void> setPriority(Object priority) {
        return Tasks.forResult(null);
    }

    public void setPriority(Object priority, CompletionListener listener) {
        completeOk(listener);
    }

    public Task<Void> updateChildren(Map<String, Object> update) {
        return Tasks.forResult(null);
    }

    public void updateChildren(Map<String, Object> update, CompletionListener listener) {
        completeOk(listener);
    }

    public Task<Void> removeValue() {
        return Tasks.forResult(null);
    }

    public void removeValue(CompletionListener listener) {
        completeOk(listener);
    }

    // ---- transactions -----------------------------------------------------

    // runTransaction runs the handler against the CURRENT data (empty on our
    // shim), then signals completion. We invoke doTransaction once with an
    // empty MutableData; whatever the handler returns (success/abort) we
    // report as a committed-but-empty result, mirroring an offline commit
    // that never reaches a backend.
    public void runTransaction(Transaction.Handler handler) {
        runTransaction(handler, true);
    }

    public void runTransaction(final Transaction.Handler handler,
                               boolean fireLocalEvents) {
        GmsTaskExecutors.defaultExecutor().execute(new Runnable() {
            @Override public void run() {
                try {
                    MutableData data = new MutableData();
                    Transaction.Result result;
                    try {
                        result = handler.doTransaction(data);
                    } catch (Throwable t) {
                        handler.onComplete(DatabaseError.fromException(t), false,
                                DataSnapshot.empty(DatabaseReference.this));
                        return;
                    }
                    boolean committed = result != null && result.isSuccess();
                    handler.onComplete(null, committed,
                            DataSnapshot.empty(DatabaseReference.this));
                } catch (Throwable ignored) {}
            }
        });
    }

    // ---- onDisconnect -----------------------------------------------------

    public OnDisconnect onDisconnect() {
        return new OnDisconnect();
    }

    @Override public String toString() {
        return "DatabaseReference(" + path + ")";
    }

    private void completeOk(final CompletionListener listener) {
        if (listener == null) return;
        GmsTaskExecutors.defaultExecutor().execute(new Runnable() {
            @Override public void run() {
                try { listener.onComplete(null, DatabaseReference.this); }
                catch (Throwable ignored) {}
            }
        });
    }
}
