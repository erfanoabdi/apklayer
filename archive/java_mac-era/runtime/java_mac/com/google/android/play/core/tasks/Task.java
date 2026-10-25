package com.google.android.play.core.tasks;

import android.app.Activity;
import java.util.concurrent.Executor;

// com.google.android.play.core.tasks.Task<TResult> -- Play Core ships its OWN
// async-result type, predating and DISTINCT from com.google.android.gms.tasks.Task.
// The older Play Core artifacts (AppUpdate / SplitInstall / Review) return THIS
// type from their async APIs, not the gms one, so a shadow that returned the gms
// Task would give the app a NoSuchMethodError when it called e.g.
// task.addOnSuccessListener(...) expecting this package's listener interface.
//
// We mirror the published Play Core Task abstract surface exactly. Our concrete
// implementation is PlayCoreTaskImpl (a small synchronous/deferred holder); apps
// only ever see this abstract type.
public abstract class Task<TResult> {
    public abstract boolean isComplete();
    public abstract boolean isSuccessful();
    public abstract TResult getResult();
    public abstract Exception getException();

    public abstract Task<TResult> addOnSuccessListener(OnSuccessListener<? super TResult> listener);
    public abstract Task<TResult> addOnSuccessListener(Executor executor,
                                                       OnSuccessListener<? super TResult> listener);
    public abstract Task<TResult> addOnFailureListener(OnFailureListener listener);
    public abstract Task<TResult> addOnFailureListener(Executor executor,
                                                       OnFailureListener listener);

    // Play Core added OnCompleteListener overloads in later artifact versions.
    // The default impls forward to the success/failure listener path so that a
    // born-complete task still fires the complete callback exactly once.
    public Task<TResult> addOnCompleteListener(OnCompleteListener<TResult> listener) {
        return addOnCompleteListener(PlayCoreTaskExecutors.MAIN_THREAD, listener);
    }
    public Task<TResult> addOnCompleteListener(Executor executor,
                                               final OnCompleteListener<TResult> listener) {
        // Default behaviour: when this task completes, deliver it to the
        // listener. Subclasses (PlayCoreTaskImpl) override this for the deferred
        // case; the abstract default covers any foreign subclass.
        final Task<TResult> self = this;
        addOnSuccessListener(executor, new OnSuccessListener<TResult>() {
            @Override public void onSuccess(TResult result) { listener.onComplete(self); }
        });
        addOnFailureListener(executor, new OnFailureListener() {
            @Override public void onFailure(Exception e) { listener.onComplete(self); }
        });
        return this;
    }

    // Activity-scoped overloads (auto-remove on Activity destroy on real Android;
    // our tasks complete promptly so we just ignore the Activity reference).
    public Task<TResult> addOnSuccessListener(Activity activity,
                                              OnSuccessListener<? super TResult> listener) {
        return addOnSuccessListener(listener);
    }
    public Task<TResult> addOnFailureListener(Activity activity, OnFailureListener listener) {
        return addOnFailureListener(listener);
    }
    public Task<TResult> addOnCompleteListener(Activity activity,
                                               OnCompleteListener<TResult> listener) {
        return addOnCompleteListener(listener);
    }
}
