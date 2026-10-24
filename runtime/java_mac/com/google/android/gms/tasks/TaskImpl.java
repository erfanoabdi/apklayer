package com.google.android.gms.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

// Real stateful com.google.android.gms.tasks.Task<T> implementation (F2).
//
// The previous shadow (SyncTask) was "born complete" and could not represent
// the pending->complete transition AT ALL: TaskCompletionSource.getTask()
// before setResult() returned a task already FAILED with
// "TaskCompletionSource was not completed", listeners attached before
// completion were dropped forever, and setResult() after getTask() built a NEW
// task the holder never saw. Every correct play-services usage (create a TCS ->
// hand its Task to a caller -> complete it later from a callback) was broken
// three ways. Real Subway Surfers hit this on the Play-Games doBestEffortWrite
// path (GoogleApiManager -> TaskCompletionSource.getTask() -> our ISE), which
// stalled its kotlinx loading pipeline at 0%.
//
// This class mirrors play-services' observable contract: a Task starts
// INCOMPLETE; exactly one of setResult/setException/setCanceled transitions it
// once; listeners attached while pending are queued and fired (on their
// executor) exactly once at completion; listeners attached after completion
// fire immediately on their executor; getResult() on an incomplete task throws
// IllegalStateException; on a failed task it throws RuntimeExecutionException.
// No-executor listeners dispatch via GmsTaskExecutors.defaultExecutor() (see
// that class for the play-services deviation note). Thread-safe.
public class TaskImpl<T> extends Task<T> {

    private final Object lock = new Object();
    private boolean complete = false;
    private boolean canceled = false;
    private T result;
    private Exception exception;
    private final List<Object[]> pending = new ArrayList<Object[]>(); // (Executor, Runnable)
    private final CountDownLatch latch = new CountDownLatch(1);

    public TaskImpl() {}

    // --- state queries -----------------------------------------------------
    @Override public boolean isComplete()   { synchronized (lock) { return complete; } }
    @Override public boolean isCanceled()   { synchronized (lock) { return canceled; } }
    @Override public boolean isSuccessful() {
        synchronized (lock) { return complete && !canceled && exception == null; }
    }
    @Override public Exception getException() { synchronized (lock) { return exception; } }

    @Override public T getResult() {
        synchronized (lock) {
            if (!complete) throw new IllegalStateException("Task is not yet complete");
            if (canceled)  throw new CancellationException("Task is already canceled.");
            if (exception != null) throw new RuntimeExecutionException(exception);
            return result;
        }
    }
    @Override public <X extends Throwable> T getResult(Class<X> exceptionType) throws X {
        synchronized (lock) {
            if (!complete) throw new IllegalStateException("Task is not yet complete");
            if (canceled)  throw new CancellationException("Task is already canceled.");
            if (exception != null && exceptionType.isInstance(exception))
                throw exceptionType.cast(exception);
            if (exception != null) throw new RuntimeExecutionException(exception);
            return result;
        }
    }

    // --- completion (package-visible; used by TaskCompletionSource + Tasks) -
    boolean trySetResult(T r) {
        synchronized (lock) {
            if (complete) return false;
            complete = true; result = r;
        }
        finish();
        return true;
    }
    boolean trySetException(Exception e) {
        synchronized (lock) {
            if (complete) return false;
            complete = true;
            exception = (e != null) ? e : new NullPointerException("null exception");
        }
        finish();
        return true;
    }
    boolean trySetCanceled() {
        synchronized (lock) {
            if (complete) return false;
            complete = true; canceled = true;
        }
        finish();
        return true;
    }

    private void finish() {
        List<Object[]> toRun;
        synchronized (lock) {
            toRun = new ArrayList<Object[]>(pending);
            pending.clear();
        }
        for (Object[] le : toRun) dispatch((Executor) le[0], (Runnable) le[1]);
        latch.countDown();
    }

    private static void dispatch(Executor exec, Runnable r) {
        try { exec.execute(r); }
        catch (RuntimeException ex) {
            // An executor that rejects (shutdown) must not break completion;
            // run inline so the listener still fires.
            r.run();
        }
    }

    // Schedule a per-listener dispatch runnable: run now if complete, else queue.
    private void schedule(Executor exec, Runnable r) {
        Executor e = (exec != null) ? exec : GmsTaskExecutors.defaultExecutor();
        synchronized (lock) {
            if (!complete) { pending.add(new Object[]{ e, r }); return; }
        }
        dispatch(e, r);
    }

    // Snapshot accessors used inside dispatch runnables (state is immutable
    // once complete, so reading without the lock there is safe).
    private boolean snapSuccessful() { return complete && !canceled && exception == null; }
    private boolean snapFailed()     { return complete && !canceled && exception != null; }

    // --- listeners ---------------------------------------------------------
    @Override public Task<T> addOnSuccessListener(final OnSuccessListener<? super T> l) {
        return addOnSuccessListener((Executor) null, l);
    }
    @Override public Task<T> addOnSuccessListener(Executor executor,
                                                  final OnSuccessListener<? super T> l) {
        if (l != null) schedule(executor, new Runnable() {
            @Override public void run() { if (snapSuccessful()) l.onSuccess(result); }
        });
        return this;
    }
    @Override public Task<T> addOnFailureListener(final OnFailureListener l) {
        return addOnFailureListener((Executor) null, l);
    }
    @Override public Task<T> addOnFailureListener(Executor executor, final OnFailureListener l) {
        if (l != null) schedule(executor, new Runnable() {
            @Override public void run() { if (snapFailed()) l.onFailure(exception); }
        });
        return this;
    }
    @Override public Task<T> addOnCompleteListener(final OnCompleteListener<T> l) {
        return addOnCompleteListener((Executor) null, l);
    }
    @Override public Task<T> addOnCompleteListener(Executor executor, final OnCompleteListener<T> l) {
        if (l != null) schedule(executor, new Runnable() {
            @Override public void run() { l.onComplete(TaskImpl.this); }
        });
        return this;
    }
    @Override public Task<T> addOnCanceledListener(final OnCanceledListener l) {
        return addOnCanceledListener((Executor) null, l);
    }
    @Override public Task<T> addOnCanceledListener(Executor executor, final OnCanceledListener l) {
        if (l != null) schedule(executor, new Runnable() {
            @Override public void run() { synchronized (lock) { if (!canceled) return; } l.onCanceled(); }
        });
        return this;
    }

    // --- blocking await (used by Tasks.await) ------------------------------
    void awaitLatch() throws InterruptedException { latch.await(); }
    boolean awaitLatch(long timeoutMillis) throws InterruptedException {
        return latch.await(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
