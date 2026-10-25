package com.google.android.gms.tasks;

import java.util.concurrent.Executor;

// com.google.android.gms.tasks.TaskExecutors -- the canonical play-services
// class holding the standard listener-dispatch executors. Apps reference
// TaskExecutors.MAIN_THREAD directly (it is the documented public field used to
// route Task callbacks back onto the UI thread), so the shipped class MUST carry
// this exact name; the project's earlier GmsTaskExecutors helper kept the
// internal default-dispatch worker but did NOT satisfy a bytecode link to
// "com.google.android.gms.tasks.TaskExecutors.MAIN_THREAD".
//
// MAIN_THREAD: real play-services posts the Runnable onto the application main
// Looper. On Mac.apk we route through android.os.Handler bound to the main
// Looper so the callback runs on the UI thread exactly as apps expect, matching
// play-services' observable contract. Construction-order safety: the Handler is
// created lazily on first execute() so class init does not depend on the main
// Looper already existing.
public final class TaskExecutors {
    private TaskExecutors() {}

    /** Executor that runs the command on the application main (UI) thread. */
    public static final Executor MAIN_THREAD = new Executor() {
        private volatile android.os.Handler handler;
        @Override public void execute(Runnable command) {
            if (command == null) return;
            android.os.Handler h = handler;
            if (h == null) {
                synchronized (this) {
                    if (handler == null) {
                        android.os.Looper main = android.os.Looper.getMainLooper();
                        handler = (main != null) ? new android.os.Handler(main)
                                                 : new android.os.Handler();
                    }
                    h = handler;
                }
            }
            h.post(command);
        }
    };
}
