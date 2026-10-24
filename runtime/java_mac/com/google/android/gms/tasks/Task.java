package com.google.android.gms.tasks;

import java.util.concurrent.Executor;

// com.google.android.gms.tasks.Task<T> -- the abstract base class used
// by every async-returning Firebase / GMS API. Real Firebase ships a
// thread-pool-backed implementation; we ship a no-op synchronous one
// (see SyncTask below). Apps don't care which they got -- they just
// call addOnSuccessListener / getResult / etc. and we want those to
// return immediately rather than block on Google's servers.
//
// Architectural note: this class is intentionally NOT named with a
// "Stub" suffix. Bundled APK code is compiled against
// "com.google.android.gms.tasks.Task" and the bytecode resolves that
// exact name through Mac.apk's parent-first AppClassLoader. Our
// class file IS the Task the APK sees; renaming it would defeat the
// shadow.
public abstract class Task<T> {
    public abstract boolean isComplete();
    public abstract boolean isSuccessful();
    public abstract boolean isCanceled();
    public abstract T getResult();
    public abstract <X extends Throwable> T getResult(Class<X> exceptionType) throws X;
    public abstract Exception getException();

    public abstract Task<T> addOnSuccessListener(OnSuccessListener<? super T> listener);
    public abstract Task<T> addOnSuccessListener(Executor executor,
                                                  OnSuccessListener<? super T> listener);
    public abstract Task<T> addOnFailureListener(OnFailureListener listener);
    public abstract Task<T> addOnFailureListener(Executor executor,
                                                  OnFailureListener listener);
    public abstract Task<T> addOnCompleteListener(OnCompleteListener<T> listener);
    public abstract Task<T> addOnCompleteListener(Executor executor,
                                                   OnCompleteListener<T> listener);
    public abstract Task<T> addOnCanceledListener(OnCanceledListener listener);
    public abstract Task<T> addOnCanceledListener(Executor executor,
                                                   OnCanceledListener listener);

    // Activity-scoped overloads. Real Firebase auto-removes the
    // listener when the Activity is destroyed (so a Task that
    // completes after the user has navigated away doesn't fire into
    // a stale view). Our stub completes synchronously at
    // construction, so the Activity reference is just ignored -- the
    // listener fires before this method returns and there's nothing
    // to auto-remove later. Keep the overload so apps compile.
    public Task<T> addOnSuccessListener(android.app.Activity activity,
                                         OnSuccessListener<? super T> listener) {
        return addOnSuccessListener(listener);
    }
    public Task<T> addOnFailureListener(android.app.Activity activity,
                                         OnFailureListener listener) {
        return addOnFailureListener(listener);
    }
    public Task<T> addOnCompleteListener(android.app.Activity activity,
                                          OnCompleteListener<T> listener) {
        return addOnCompleteListener(listener);
    }
    public Task<T> addOnCanceledListener(android.app.Activity activity,
                                          OnCanceledListener listener) {
        return addOnCanceledListener(listener);
    }

    // continueWith / continueWithTask / onSuccessTask are DEFERRED: the
    // continuation runs when THIS task completes (immediately if already
    // complete), on the given/default executor, and the returned task tracks
    // its outcome. Running them synchronously (the old shadow) was only correct
    // for born-complete tasks; with real pending tasks (F2) that would invoke
    // the continuation against an incomplete source.
    public <R> Task<R> continueWith(Continuation<T, R> continuation) {
        return continueWith(GmsTaskExecutors.defaultExecutor(), continuation);
    }
    public <R> Task<R> continueWith(Executor executor, final Continuation<T, R> continuation) {
        final TaskImpl<R> out = new TaskImpl<R>();
        addOnCompleteListener(executor, new OnCompleteListener<T>() {
            @Override public void onComplete(Task<T> t) {
                try { out.trySetResult(continuation.then(t)); }
                catch (Exception e) { out.trySetException(e); }
            }
        });
        return out;
    }

    public <R> Task<R> continueWithTask(Continuation<T, Task<R>> continuation) {
        return continueWithTask(GmsTaskExecutors.defaultExecutor(), continuation);
    }
    public <R> Task<R> continueWithTask(Executor executor,
                                         final Continuation<T, Task<R>> continuation) {
        final TaskImpl<R> out = new TaskImpl<R>();
        addOnCompleteListener(executor, new OnCompleteListener<T>() {
            @Override public void onComplete(Task<T> t) {
                try {
                    Task<R> next = continuation.then(t);
                    if (next == null) out.trySetResult(null);
                    else chain(next, out);
                } catch (Exception e) { out.trySetException(e); }
            }
        });
        return out;
    }

    public <R> Task<R> onSuccessTask(SuccessContinuation<T, R> continuation) {
        return onSuccessTask(GmsTaskExecutors.defaultExecutor(), continuation);
    }
    public <R> Task<R> onSuccessTask(Executor executor,
                                      final SuccessContinuation<T, R> continuation) {
        final TaskImpl<R> out = new TaskImpl<R>();
        addOnCompleteListener(executor, new OnCompleteListener<T>() {
            @Override public void onComplete(Task<T> t) {
                if (t.isCanceled())      { out.trySetCanceled(); return; }
                if (!t.isSuccessful())   { out.trySetException(t.getException()); return; }
                try {
                    Task<R> next = continuation.then(t.getResult());
                    if (next == null) out.trySetResult(null);
                    else chain(next, out);
                } catch (Exception e) { out.trySetException(e); }
            }
        });
        return out;
    }

    // Forward a source task's eventual outcome into a deferred output task.
    private static <R> void chain(Task<R> src, final TaskImpl<R> out) {
        src.addOnCompleteListener(new OnCompleteListener<R>() {
            @Override public void onComplete(Task<R> t) {
                if (t.isCanceled())     out.trySetCanceled();
                else if (t.isSuccessful()) out.trySetResult(t.getResult());
                else                    out.trySetException(t.getException());
            }
        });
    }
}
