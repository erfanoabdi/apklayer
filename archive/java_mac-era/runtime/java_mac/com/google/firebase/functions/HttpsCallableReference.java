package com.google.firebase.functions;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import java.util.concurrent.TimeUnit;

// com.google.firebase.functions.HttpsCallableReference -- a handle to a single
// callable Cloud Function. Real Functions POSTs the argument to the function URL
// and resolves with its result. On Mac.apk there is no backend, so call(...)
// fails cleanly with FirebaseFunctionsException(Code.INTERNAL) -- we never
// fabricate a function result. Configuration (timeout) is recorded locally.
//
// Signatures mirror the published Firebase Functions Android reference.
public class HttpsCallableReference {

    private final FirebaseFunctions functions;
    private final String name; // callable name, or null when built from a URL
    private final java.net.URL url;
    private long timeoutMillis = 70_000L; // SDK default 70s

    HttpsCallableReference(FirebaseFunctions functions, String name) {
        this.functions = functions;
        this.name = name;
        this.url = null;
    }

    HttpsCallableReference(FirebaseFunctions functions, java.net.URL url) {
        this.functions = functions;
        this.name = null;
        this.url = url;
    }

    public Task<HttpsCallableResult> call() {
        return failedCall();
    }

    public Task<HttpsCallableResult> call(Object data) {
        return failedCall();
    }

    public void setTimeout(long timeout, TimeUnit units) {
        this.timeoutMillis = units.toMillis(timeout);
    }

    public long getTimeout() { return timeoutMillis; }

    public HttpsCallableReference withTimeout(long timeout, TimeUnit units) {
        setTimeout(timeout, units);
        return this;
    }

    // Build a gms Task already in the failed state via TaskCompletionSource (NOT
    // TaskImpl internals), carrying a faithful FirebaseFunctionsException.
    private Task<HttpsCallableResult> failedCall() {
        TaskCompletionSource<HttpsCallableResult> src =
                new TaskCompletionSource<HttpsCallableResult>();
        src.setException(new FirebaseFunctionsException(
                "INTERNAL", FirebaseFunctionsException.Code.INTERNAL, null));
        return src.getTask();
    }
}
