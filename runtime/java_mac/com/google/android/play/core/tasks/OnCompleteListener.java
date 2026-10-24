package com.google.android.play.core.tasks;

// com.google.android.play.core.tasks.OnCompleteListener<TResult> -- fires once
// the task is complete regardless of success/failure; the listener inspects the
// passed Task for the outcome.
public interface OnCompleteListener<TResult> {
    void onComplete(Task<TResult> task);
}
