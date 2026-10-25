package com.google.firebase.storage;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import java.util.concurrent.Executor;

// com.google.firebase.storage.StorageTask<TResult> -- the concrete base for
// UploadTask / FileDownloadTask / StreamDownloadTask. Real Storage uses an
// elaborate internal state machine driving an HTTP transfer. On Mac.apk there
// is no backend, so every StorageTask completes by FAILING cleanly with a
// StorageException(ERROR_UNKNOWN) -- we never fabricate a successful transfer
// or any transferred bytes.
//
// Bridging note (per the bucket spec): we do NOT touch package-private TaskImpl
// internals. StorageTask owns its own gms TaskCompletionSource; getTask() of
// that source is the real, completable gms Task whose abstract methods we
// delegate to. Subclasses call fail() in their constructor to drive the source
// to the failed terminal state, mirroring "init then degrade".
//
// Signatures mirror the published Firebase Storage Android reference.
public abstract class StorageTask<TResult> extends ControllableTask<TResult> {

    private final TaskCompletionSource<TResult> source = new TaskCompletionSource<TResult>();
    private final Task<TResult> inner = source.getTask();

    protected StorageTask() {}

    // Drive the underlying gms Task to a clean failure. Idempotent (trySet).
    protected void fail(StorageException e) {
        source.trySetException(e == null ? StorageException.fromException(null) : e);
    }

    // Drive the underlying gms Task to success with the given snapshot. Used by
    // operations that legitimately succeed locally (none currently, but kept so
    // subclasses can complete without reaching into internals).
    protected void succeed(TResult result) {
        source.trySetResult(result);
    }

    // ---- gms.tasks.Task<TResult> delegation ------------------------------

    @Override public boolean isComplete() { return inner.isComplete(); }
    @Override public boolean isSuccessful() { return inner.isSuccessful(); }
    @Override public boolean isCanceled() { return inner.isCanceled(); }
    @Override public TResult getResult() { return inner.getResult(); }
    @Override public <X extends Throwable> TResult getResult(Class<X> exceptionType) throws X {
        return inner.getResult(exceptionType);
    }
    @Override public Exception getException() { return inner.getException(); }

    @Override public Task<TResult> addOnSuccessListener(OnSuccessListener<? super TResult> l) {
        inner.addOnSuccessListener(l); return this;
    }
    @Override public Task<TResult> addOnSuccessListener(Executor e, OnSuccessListener<? super TResult> l) {
        inner.addOnSuccessListener(e, l); return this;
    }
    @Override public Task<TResult> addOnFailureListener(OnFailureListener l) {
        inner.addOnFailureListener(l); return this;
    }
    @Override public Task<TResult> addOnFailureListener(Executor e, OnFailureListener l) {
        inner.addOnFailureListener(e, l); return this;
    }
    @Override public Task<TResult> addOnCompleteListener(OnCompleteListener<TResult> l) {
        inner.addOnCompleteListener(l); return this;
    }
    @Override public Task<TResult> addOnCompleteListener(Executor e, OnCompleteListener<TResult> l) {
        inner.addOnCompleteListener(e, l); return this;
    }
    @Override public Task<TResult> addOnCanceledListener(OnCanceledListener l) {
        inner.addOnCanceledListener(l); return this;
    }
    @Override public Task<TResult> addOnCanceledListener(Executor e, OnCanceledListener l) {
        inner.addOnCanceledListener(e, l); return this;
    }

    // ---- CancellableTask --------------------------------------------------

    @Override public boolean isInProgress() { return false; }

    @Override public boolean cancel() {
        // Nothing live to cancel; the task has already failed terminally.
        return false;
    }

    @Override public CancellableTask<TResult> addOnProgressListener(
            OnProgressListener<? super TResult> listener) {
        // No progress is ever emitted (no transfer); accept and drop.
        return this;
    }
    @Override public CancellableTask<TResult> addOnProgressListener(
            Executor executor, OnProgressListener<? super TResult> listener) {
        return this;
    }
    @Override public CancellableTask<TResult> addOnProgressListener(
            android.app.Activity activity, OnProgressListener<? super TResult> listener) {
        return this;
    }

    // ---- ControllableTask -------------------------------------------------

    @Override public boolean pause() { return false; }
    @Override public boolean resume() { return false; }
    @Override public boolean isPaused() { return false; }

    @Override public ControllableTask<TResult> addOnPausedListener(
            OnPausedListener<? super TResult> listener) {
        return this;
    }
    @Override public ControllableTask<TResult> addOnPausedListener(
            Executor executor, OnPausedListener<? super TResult> listener) {
        return this;
    }
    @Override public ControllableTask<TResult> addOnPausedListener(
            android.app.Activity activity, OnPausedListener<? super TResult> listener) {
        return this;
    }
}
