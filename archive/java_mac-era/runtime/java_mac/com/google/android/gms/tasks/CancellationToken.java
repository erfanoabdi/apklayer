package com.google.android.gms.tasks;

// com.google.android.gms.tasks.CancellationToken -- propagates a cancellation
// signal into an asynchronous Task. Created by a CancellationTokenSource and
// passed to cancellable Task-returning APIs (common in play-services-location
// and auth Task chains). This is the abstract public base; the concrete
// implementation lives inside CancellationTokenSource.
public abstract class CancellationToken {
    /** @return true once the owning CancellationTokenSource has been canceled. */
    public abstract boolean isCancellationRequested();

    /** Registers a listener fired when cancellation is requested. Fires
     *  immediately if cancellation already happened. Returns this for chaining. */
    public abstract CancellationToken onCanceledRequested(OnTokenCanceledListener listener);
}
