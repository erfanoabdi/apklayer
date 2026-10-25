package com.google.android.gms.tasks;

// com.google.android.gms.tasks.RuntimeExecutionException -- thrown by
// Task.getResult() when the task failed (real play-services wraps the
// underlying cause in this unchecked type). Apps that defensively catch
// RuntimeException (the common pattern) still catch it, since it extends
// RuntimeException; apps that catch this exact type also work.
public class RuntimeExecutionException extends RuntimeException {
    public RuntimeExecutionException(Throwable cause) { super(cause); }
}
