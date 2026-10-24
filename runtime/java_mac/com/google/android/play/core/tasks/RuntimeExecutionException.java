package com.google.android.play.core.tasks;

// com.google.android.play.core.tasks.RuntimeExecutionException -- thrown by
// Play Core's Task.getResult() variants when the underlying task failed (wraps
// the cause). Mirrors the gms.tasks RuntimeExecutionException but in Play Core's
// own package.
public class RuntimeExecutionException extends RuntimeException {
    public RuntimeExecutionException(Throwable cause) {
        super(cause);
    }
}
