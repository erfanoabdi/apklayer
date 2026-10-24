package com.google.firebase.storage;

import com.google.android.gms.tasks.Task;

// com.google.firebase.storage.CancellableTask<TResult> -- a gms Task that also
// supports cancellation and progress listeners. Real Storage tasks extend this
// through ControllableTask -> StorageTask. We keep the abstract surface so app
// code that declares a CancellableTask reference resolves correctly.
//
// Signatures mirror the published Firebase Storage Android reference.
public abstract class CancellableTask<TResult> extends Task<TResult> {

    public abstract boolean isInProgress();

    public abstract boolean cancel();

    public abstract CancellableTask<TResult> addOnProgressListener(
            OnProgressListener<? super TResult> listener);

    public abstract CancellableTask<TResult> addOnProgressListener(
            java.util.concurrent.Executor executor,
            OnProgressListener<? super TResult> listener);

    public abstract CancellableTask<TResult> addOnProgressListener(
            android.app.Activity activity,
            OnProgressListener<? super TResult> listener);
}
