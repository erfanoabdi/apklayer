package com.google.firebase.firestore;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

// com.google.firebase.firestore.FirebaseFirestore -- the Firestore client entry
// point. On our de-Googled (offline-only) shim there is no backend: reads
// resolve EMPTY / NOT-EXISTS, writes are accepted locally without round-tripping,
// and network toggles / transactions / batches complete cleanly. This is the
// honest offline behavior the SDK exhibits with no connectivity and no cached
// remote data -- it lets data-driven apps init and degrade gracefully instead
// of NoClassDefFound / hang. We NEVER fabricate remote documents.
//
// Instances are cached per (FirebaseApp, databaseId) like the real SDK so that
// repeated getInstance() calls return the same client.
public class FirebaseFirestore {

    // The default Firestore database id, matching the published constant value.
    public static final String DEFAULT_DATABASE_ID = "(default)";

    private static final Map<String, FirebaseFirestore> INSTANCES =
        new HashMap<String, FirebaseFirestore>();

    private final FirebaseApp app;
    private final String databaseId;
    private FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder().build();

    private FirebaseFirestore(FirebaseApp app, String databaseId) {
        this.app = app;
        this.databaseId = databaseId;
    }

    // ---- getInstance overloads -------------------------------------------

    public static FirebaseFirestore getInstance() {
        return getInstance(FirebaseApp.getInstance(), DEFAULT_DATABASE_ID);
    }

    public static FirebaseFirestore getInstance(FirebaseApp app) {
        return getInstance(app, DEFAULT_DATABASE_ID);
    }

    public static FirebaseFirestore getInstance(String database) {
        return getInstance(FirebaseApp.getInstance(), database);
    }

    public static FirebaseFirestore getInstance(FirebaseApp app, String database) {
        if (app == null) {
            throw new NullPointerException("Provided FirebaseApp must not be null.");
        }
        if (database == null) {
            throw new NullPointerException("Provided database name must not be null.");
        }
        String key = app.getName() + "|" + database;
        synchronized (INSTANCES) {
            FirebaseFirestore instance = INSTANCES.get(key);
            if (instance == null) {
                instance = new FirebaseFirestore(app, database);
                INSTANCES.put(key, instance);
            }
            return instance;
        }
    }

    public FirebaseApp getApp() { return app; }

    // ---- path accessors ---------------------------------------------------

    public CollectionReference collection(String collectionPath) {
        if (collectionPath == null) {
            throw new NullPointerException("Provided collection path must not be null.");
        }
        return new CollectionReference(this, collectionPath);
    }

    public DocumentReference document(String documentPath) {
        if (documentPath == null) {
            throw new NullPointerException("Provided document path must not be null.");
        }
        return new DocumentReference(this, documentPath);
    }

    // A collection-group query across every collection with the given id. On
    // the offline shim it resolves empty like any other query.
    public Query collectionGroup(String collectionId) {
        if (collectionId == null) {
            throw new NullPointerException("Provided collection ID must not be null.");
        }
        return new Query(this, collectionId);
    }

    // ---- transactions / batches ------------------------------------------

    public <TResult> Task<TResult> runTransaction(Transaction.Function<TResult> updateFunction) {
        return runTransactionInternal(updateFunction);
    }

    // The TransactionOptions overload (max-attempts/executor); options are
    // accepted but have no effect (a local transaction never retries).
    public <TResult> Task<TResult> runTransaction(TransactionOptions options,
                                                  Transaction.Function<TResult> updateFunction) {
        return runTransactionInternal(updateFunction);
    }

    private <TResult> Task<TResult> runTransactionInternal(
            Transaction.Function<TResult> updateFunction) {
        try {
            // Run the body once against a local Transaction (reads not-exists,
            // writes accepted). No backend means no contention -> no retry.
            TResult result = updateFunction.apply(new Transaction(this));
            return Tasks.forResult(result);
        } catch (FirebaseFirestoreException e) {
            return Tasks.forException(e);
        } catch (Exception e) {
            return Tasks.forException(e);
        }
    }

    public Task<Void> runBatch(WriteBatch.Function batchFunction) {
        try {
            WriteBatch batch = batch();
            batchFunction.apply(batch);
            return batch.commit();
        } catch (Exception e) {
            return Tasks.forException(e);
        }
    }

    public WriteBatch batch() {
        return new WriteBatch(this);
    }

    // ---- settings ---------------------------------------------------------

    public void setFirestoreSettings(FirebaseFirestoreSettings settings) {
        if (settings == null) {
            throw new NullPointerException("Provided settings must not be null.");
        }
        this.settings = settings;
    }

    public FirebaseFirestoreSettings getFirestoreSettings() {
        return settings;
    }

    // ---- lifecycle / network (all clean no-ops offline) -------------------

    public Task<Void> clearPersistence() {
        // No persisted store to clear -> succeed cleanly.
        return Tasks.forResult(null);
    }

    public Task<Void> enableNetwork() {
        // No backend to reach -> accepted, no observable effect.
        return Tasks.forResult(null);
    }

    public Task<Void> disableNetwork() {
        return Tasks.forResult(null);
    }

    public Task<Void> terminate() {
        return Tasks.forResult(null);
    }

    public Task<Void> waitForPendingWrites() {
        // No pending writes outstanding -> already settled.
        return Tasks.forResult(null);
    }

    public Task<Void> disableNetwork(Executor executor) {
        return Tasks.forResult(null);
    }

    public ListenerRegistration addSnapshotsInSyncListener(Runnable runnable) {
        if (runnable != null) runnable.run();
        return Query.NoopRegistration.INSTANCE;
    }

    public ListenerRegistration addSnapshotsInSyncListener(Executor executor, Runnable runnable) {
        if (runnable != null) runnable.run();
        return Query.NoopRegistration.INSTANCE;
    }

    @Override
    public String toString() {
        return "FirebaseFirestore{app=" + app.getName() + ", database=" + databaseId + "}";
    }
}
