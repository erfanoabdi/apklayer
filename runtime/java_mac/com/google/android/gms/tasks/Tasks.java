package com.google.android.gms.tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

// com.google.android.gms.tasks.Tasks -- static factory + combinator methods,
// now backed by the real stateful TaskImpl (F2). forResult/forException/
// forCanceled return an already-completed task; await() truly blocks until the
// task completes (so a Task handed across threads and completed later works);
// whenAll*/call defer and complete when their sources do.
public final class Tasks {
    private Tasks() {}

    public static <T> Task<T> forResult(T result) {
        TaskImpl<T> t = new TaskImpl<T>(); t.trySetResult(result); return t;
    }
    public static <T> Task<T> forException(Exception e) {
        TaskImpl<T> t = new TaskImpl<T>(); t.trySetException(e); return t;
    }
    public static <T> Task<T> forCanceled() {
        TaskImpl<T> t = new TaskImpl<T>(); t.trySetCanceled(); return t;
    }

    public static <T> Task<T> call(Callable<T> callable) {
        return call(GmsTaskExecutors.defaultExecutor(), callable);
    }
    public static <T> Task<T> call(Executor executor, final Callable<T> callable) {
        final TaskImpl<T> t = new TaskImpl<T>();
        executor.execute(new Runnable() {
            @Override public void run() {
                try { t.trySetResult(callable.call()); }
                catch (Exception e) { t.trySetException(e); }
            }
        });
        return t;
    }

    // --- await: truly blocks until completion ------------------------------
    public static <T> T await(Task<T> task)
            throws ExecutionException, InterruptedException {
        if (task == null) throw new NullPointerException("task");
        if (!task.isComplete()) {
            if (task instanceof TaskImpl) ((TaskImpl<?>) task).awaitLatch();
            else while (!task.isComplete()) Thread.sleep(5); // foreign Task impl
        }
        return unwrap(task);
    }
    public static <T> T await(Task<T> task, long timeout, TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException {
        if (task == null) throw new NullPointerException("task");
        if (!task.isComplete()) {
            if (task instanceof TaskImpl) {
                if (!((TaskImpl<?>) task).awaitLatch(unit.toMillis(timeout)))
                    throw new TimeoutException("Timed out waiting for Task");
            } else {
                long deadline = System.nanoTime() + unit.toNanos(timeout);
                while (!task.isComplete()) {
                    if (System.nanoTime() > deadline) throw new TimeoutException("Timed out waiting for Task");
                    Thread.sleep(5);
                }
            }
        }
        return unwrap(task);
    }
    private static <T> T unwrap(Task<T> task) throws ExecutionException {
        if (task.isCanceled()) throw new CancellationException("Task is already canceled");
        if (task.isSuccessful()) return task.getResult();
        throw new ExecutionException(task.getException());
    }

    // --- combinators: defer until all sources complete ---------------------
    public static Task<Void> whenAll(Collection<? extends Task<?>> tasks) {
        return whenAllImpl(tasks);
    }
    public static Task<Void> whenAll(Task<?>... tasks) {
        return whenAllImpl(tasks == null ? null : java.util.Arrays.asList(tasks));
    }
    private static Task<Void> whenAllImpl(Collection<? extends Task<?>> tasks) {
        if (tasks == null || tasks.isEmpty()) return forResult(null);
        final TaskImpl<Void> out = new TaskImpl<Void>();
        final AtomicInteger remaining = new AtomicInteger(tasks.size());
        final Object[] firstFailure = new Object[1];
        OnCompleteListener<Object> oc = new OnCompleteListener<Object>() {
            @Override public void onComplete(Task<Object> t) {
                if (!t.isSuccessful() && !t.isCanceled() && firstFailure[0] == null)
                    firstFailure[0] = t.getException();
                if (remaining.decrementAndGet() == 0) {
                    if (firstFailure[0] != null) out.trySetException((Exception) firstFailure[0]);
                    else out.trySetResult(null);
                }
            }
        };
        for (Task<?> t : tasks) attachComplete(t, oc);
        return out;
    }

    public static <T> Task<List<T>> whenAllSuccess(Collection<? extends Task<?>> tasks) {
        return whenAllSuccessImpl(tasks);
    }
    public static <T> Task<List<T>> whenAllSuccess(Task<?>... tasks) {
        return whenAllSuccessImpl(tasks == null ? null : java.util.Arrays.asList(tasks));
    }
    private static <T> Task<List<T>> whenAllSuccessImpl(Collection<? extends Task<?>> tasks) {
        final List<? extends Task<?>> list =
            (tasks == null) ? new ArrayList<Task<?>>() : new ArrayList<Task<?>>(tasks);
        final TaskImpl<List<T>> out = new TaskImpl<List<T>>();
        if (list.isEmpty()) { out.trySetResult(new ArrayList<T>()); return out; }
        Task<Void> all = whenAllImpl(list);
        all.addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override public void onComplete(Task<Void> t) {
                if (!t.isSuccessful()) { out.trySetException(t.getException()); return; }
                List<T> results = new ArrayList<T>();
                for (Task<?> s : list) {
                    @SuppressWarnings("unchecked") T r = (T) s.getResult();
                    results.add(r);
                }
                out.trySetResult(results);
            }
        });
        return out;
    }

    public static Task<List<Task<?>>> whenAllComplete(Collection<? extends Task<?>> tasks) {
        return whenAllCompleteImpl(tasks);
    }
    public static Task<List<Task<?>>> whenAllComplete(Task<?>... tasks) {
        return whenAllCompleteImpl(tasks == null ? null : java.util.Arrays.asList(tasks));
    }
    private static Task<List<Task<?>>> whenAllCompleteImpl(Collection<? extends Task<?>> tasks) {
        final List<Task<?>> list = new ArrayList<Task<?>>();
        if (tasks != null) for (Task<?> t : tasks) list.add(t);
        final TaskImpl<List<Task<?>>> out = new TaskImpl<List<Task<?>>>();
        if (list.isEmpty()) { out.trySetResult(list); return out; }
        Task<Void> all = whenAllImpl(list); // completes when all complete (success or fail)
        all.addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override public void onComplete(Task<Void> t) { out.trySetResult(list); }
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void attachComplete(Task<?> t, OnCompleteListener<Object> oc) {
        ((Task<Object>) t).addOnCompleteListener(oc);
    }
}
