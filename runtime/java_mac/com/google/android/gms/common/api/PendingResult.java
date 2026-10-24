package com.google.android.gms.common.api;

import java.util.concurrent.TimeUnit;

// com.google.android.gms.common.api.PendingResult<R> -- the legacy (pre-Task)
// async result handle returned by GMS Api.* calls made through a
// GoogleApiClient. Apps either block on await() or register a
// ResultCallback. This is the public abstract surface; concrete results are
// produced by GoogleApiClient-backed calls (which, on Mac.apk, fail cleanly
// with SERVICE_MISSING -- see GoogleApiClient).
public abstract class PendingResult<R extends Result> {

    public abstract R await();
    public abstract R await(long time, TimeUnit unit);
    public abstract void cancel();
    public abstract boolean isCanceled();
    public abstract void setResultCallback(ResultCallback<? super R> callback);
    public abstract void setResultCallback(ResultCallback<? super R> callback,
                                           long time, TimeUnit unit);
}
