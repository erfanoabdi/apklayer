package com.google.android.play.core.tasks;

// com.google.android.play.core.tasks.OnSuccessListener<TResult> -- Play Core's
// own success callback, distinct from the gms.tasks equivalent.
public interface OnSuccessListener<TResult> {
    void onSuccess(TResult result);
}
