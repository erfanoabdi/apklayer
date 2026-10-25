package com.google.android.play.core.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

// Internal concrete implementation of the Play Core Task. NOT part of the public
// Play Core surface (apps only see the abstract Task) -- this is our backing
// holder. Supports the born-complete case (forResult/forException) and the
// deferred case (set later) so AppUpdate/SplitInstall/Review can return a task
// and complete it on a callback. Listener dispatch goes through the supplied
// executor (default main thread) to match real Play Core thread semantics.
public final class PlayCoreTaskImpl<TResult> extends Task<TResult> {
    private final Object lock = new Object();
    private boolean complete;
    private TResult result;
    private Exception exception;

    private final List<Runnable> pending = new ArrayList<Runnable>();

    public PlayCoreTaskImpl() {}

    public boolean trySetResult(TResult value) {
        synchronized (lock) {
            if (complete) return false;
            complete = true;
            result = value;
        }
        flush();
        return true;
    }

    public boolean trySetException(Exception e) {
        if (e == null) throw new NullPointerException("Exception must not be null");
        synchronized (lock) {
            if (complete) return false;
            complete = true;
            exception = e;
        }
        flush();
        return true;
    }

    public void setResult(TResult value) {
        if (!trySetResult(value)) throw new IllegalStateException("Cannot set the result.");
    }

    public void setException(Exception e) {
        if (!trySetException(e)) throw new IllegalStateException("Cannot set the exception.");
    }

    @Override public boolean isComplete() { synchronized (lock) { return complete; } }
    @Override public boolean isSuccessful() {
        synchronized (lock) { return complete && exception == null; }
    }
    @Override public TResult getResult() {
        synchronized (lock) {
            if (!complete) throw new IllegalStateException("Task is not yet complete");
            if (exception != null) throw new RuntimeExecutionException(exception);
            return result;
        }
    }
    @Override public Exception getException() { synchronized (lock) { return exception; } }

    @Override
    public Task<TResult> addOnSuccessListener(OnSuccessListener<? super TResult> listener) {
        return addOnSuccessListener(PlayCoreTaskExecutors.MAIN_THREAD, listener);
    }

    @Override
    public Task<TResult> addOnSuccessListener(final Executor executor,
                                              final OnSuccessListener<? super TResult> listener) {
        enqueue(executor, new Runnable() {
            @Override public void run() {
                if (isSuccessful()) listener.onSuccess(getRawResult());
            }
        });
        return this;
    }

    @Override
    public Task<TResult> addOnFailureListener(OnFailureListener listener) {
        return addOnFailureListener(PlayCoreTaskExecutors.MAIN_THREAD, listener);
    }

    @Override
    public Task<TResult> addOnFailureListener(final Executor executor,
                                              final OnFailureListener listener) {
        enqueue(executor, new Runnable() {
            @Override public void run() {
                Exception e = getException();
                if (e != null) listener.onFailure(e);
            }
        });
        return this;
    }

    @Override
    public Task<TResult> addOnCompleteListener(final Executor executor,
                                               final OnCompleteListener<TResult> listener) {
        final Task<TResult> self = this;
        enqueue(executor, new Runnable() {
            @Override public void run() { listener.onComplete(self); }
        });
        return this;
    }

    private TResult getRawResult() { synchronized (lock) { return result; } }

    private void enqueue(final Executor executor, final Runnable callback) {
        boolean fireNow;
        synchronized (lock) {
            fireNow = complete;
            if (!fireNow) {
                pending.add(new Runnable() {
                    @Override public void run() { executor.execute(callback); }
                });
            }
        }
        if (fireNow) executor.execute(callback);
    }

    private void flush() {
        List<Runnable> toRun;
        synchronized (lock) {
            if (pending.isEmpty()) return;
            toRun = new ArrayList<Runnable>(pending);
            pending.clear();
        }
        for (Runnable r : toRun) r.run();
    }
}
