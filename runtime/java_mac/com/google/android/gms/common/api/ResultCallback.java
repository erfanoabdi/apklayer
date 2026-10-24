package com.google.android.gms.common.api;

// com.google.android.gms.common.api.ResultCallback -- receives the result of an
// asynchronous (legacy) GMS PendingResult. Single abstract method so apps can
// pass a lambda.
public interface ResultCallback<R extends Result> {
    void onResult(R result);
}
