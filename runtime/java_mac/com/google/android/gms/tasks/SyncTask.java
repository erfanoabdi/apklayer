package com.google.android.gms.tasks;

import java.util.concurrent.Executor;

// Internal synchronous Task implementation. All listeners fire
// immediately on registration -- the task is born complete, so
// "addOn..." is really just "call the listener". This is exactly the
// behavior the user-facing apps need from us: they call .addOnSuccess
// Listener(l) and expect their callback to run.
//
// We are NOT exposing this class as part of the public Firebase
// surface -- the bundled APK code never holds a SyncTask reference,
// only the abstract Task<T> the factory methods return. Kept package-
// private-ish (public for cross-package access from Tasks/Task
// continuations but not part of the API contract).
public final class SyncTask<T> extends Task<T> {
    private final T result;
    private final Exception exception;
    private final boolean canceled;

    public SyncTask(T result, Exception exception, boolean canceled) {
        this.result = result;
        this.exception = exception;
        this.canceled = canceled;
    }

    @Override public boolean isComplete()   { return true; }
    @Override public boolean isCanceled()   { return canceled; }
    @Override public boolean isSuccessful() { return !canceled && exception == null; }

    @Override public T getResult() {
        if (canceled) {
            throw new java.util.concurrent.CancellationException("Task is cancelled");
        }
        if (exception != null) {
            // Mirror Firebase: getResult on a failed Task wraps the
            // failure in a RuntimeException (RuntimeExecutionException
            // in real SDKs). Apps that defensively catch RuntimeException
            // (most do) see the same behavior.
            throw new RuntimeException(exception);
        }
        return result;
    }

    @Override public <X extends Throwable> T getResult(Class<X> exceptionType) throws X {
        if (canceled) {
            throw new java.util.concurrent.CancellationException("Task is cancelled");
        }
        if (exception != null && exceptionType.isInstance(exception)) {
            throw exceptionType.cast(exception);
        }
        if (exception != null) {
            throw new RuntimeException(exception);
        }
        return result;
    }

    @Override public Exception getException() { return exception; }

    @Override public Task<T> addOnSuccessListener(OnSuccessListener<? super T> listener) {
        if (isSuccessful() && listener != null) listener.onSuccess(result);
        return this;
    }
    @Override public Task<T> addOnSuccessListener(Executor executor,
                                                   OnSuccessListener<? super T> listener) {
        return addOnSuccessListener(listener);
    }

    @Override public Task<T> addOnFailureListener(OnFailureListener listener) {
        if (exception != null && listener != null) listener.onFailure(exception);
        return this;
    }
    @Override public Task<T> addOnFailureListener(Executor executor,
                                                   OnFailureListener listener) {
        return addOnFailureListener(listener);
    }

    @Override public Task<T> addOnCompleteListener(OnCompleteListener<T> listener) {
        if (listener != null) listener.onComplete(this);
        return this;
    }
    @Override public Task<T> addOnCompleteListener(Executor executor,
                                                    OnCompleteListener<T> listener) {
        return addOnCompleteListener(listener);
    }

    @Override public Task<T> addOnCanceledListener(OnCanceledListener listener) {
        if (canceled && listener != null) listener.onCanceled();
        return this;
    }
    @Override public Task<T> addOnCanceledListener(Executor executor,
                                                    OnCanceledListener listener) {
        return addOnCanceledListener(listener);
    }
}
