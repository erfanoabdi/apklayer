package com.google.firebase.storage;

// com.google.firebase.storage.ControllableTask<TResult> -- a CancellableTask
// that can additionally be paused/resumed. Real upload/download tasks extend
// this. On Mac.apk there is no live transfer to pause, so pause/resume return
// false (nothing to control), but the type and pause-listener surface exist so
// app code compiles and degrades cleanly.
//
// Signatures mirror the published Firebase Storage Android reference.
public abstract class ControllableTask<TResult> extends CancellableTask<TResult> {

    public abstract boolean pause();

    public abstract boolean resume();

    public abstract boolean isPaused();

    public abstract ControllableTask<TResult> addOnPausedListener(
            OnPausedListener<? super TResult> listener);

    public abstract ControllableTask<TResult> addOnPausedListener(
            java.util.concurrent.Executor executor,
            OnPausedListener<? super TResult> listener);

    public abstract ControllableTask<TResult> addOnPausedListener(
            android.app.Activity activity,
            OnPausedListener<? super TResult> listener);
}
