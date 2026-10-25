package com.google.android.play.core.tasks;

// com.google.android.play.core.tasks.Tasks -- static factory for born-complete
// Play Core tasks. Mirrors the small published surface (Play Core's Tasks is
// minimal vs the gms one). Our managers use these to return already-resolved
// "no update available" / "billing unavailable"-style results cleanly.
public final class Tasks {
    private Tasks() {}

    public static <TResult> Task<TResult> forResult(TResult result) {
        PlayCoreTaskImpl<TResult> t = new PlayCoreTaskImpl<TResult>();
        t.trySetResult(result);
        return t;
    }

    public static <TResult> Task<TResult> forException(Exception e) {
        PlayCoreTaskImpl<TResult> t = new PlayCoreTaskImpl<TResult>();
        t.trySetException(e);
        return t;
    }
}
