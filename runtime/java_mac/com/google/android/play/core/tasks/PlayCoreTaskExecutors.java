package com.google.android.play.core.tasks;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;

// Internal (package-private) executor helpers for the Play Core Task impl. Not
// part of the published Play Core API surface -- apps never reference this. We
// post listener callbacks to the main Looper, matching the behaviour Play Core's
// own internal main-thread executor provides so listeners observe a consistent
// thread.
final class PlayCoreTaskExecutors {
    private PlayCoreTaskExecutors() {}

    static final Executor MAIN_THREAD = new Executor() {
        private final Handler handler = new Handler(Looper.getMainLooper());
        @Override public void execute(Runnable command) {
            if (Looper.myLooper() == Looper.getMainLooper()) command.run();
            else handler.post(command);
        }
    };

    // Direct/synchronous executor used as a fallback when no Looper is set up.
    static final Executor DIRECT = new Executor() {
        @Override public void execute(Runnable command) { command.run(); }
    };
}
