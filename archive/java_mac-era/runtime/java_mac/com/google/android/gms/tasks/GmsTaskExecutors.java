package com.google.android.gms.tasks;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

// Default dispatch executor for our stateful gms.tasks implementation
// (TaskImpl). Real play-services-tasks fires a no-executor listener on the
// process MAIN thread (its main Looper). On Mac.apk the Unity app's main
// thread runs ANGLE's native render/poll loop, and whether Android main-Looper
// Handler posts actually drain there is build/runtime dependent (tracked as
// planner-unknown U3). A listener that never runs is exactly the failure class
// F2 exists to fix, so we do NOT post the default to the main Looper on hope.
//
// Instead the default executor is a dedicated daemon single-thread executor
// that always drains and preserves play-services' "listeners run async, in
// order, off the completing thread" observable contract. DEVIATION FROM
// play-services: dispatch is on this worker thread, not the main thread. REVERT
// CONDITION: once the boot-time main-Looper-drain probe (MACAPK_TASK_DIAG)
// confirms Android main Handler posts drain in Unity apps, the default can move
// to the main thread to match play-services exactly. Explicit-Executor listener
// overloads always use the caller's executor and are unaffected.
public final class GmsTaskExecutors {
    private GmsTaskExecutors() {}

    private static final ExecutorService DEFAULT = Executors.newSingleThreadExecutor(
        new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "GmsTaskDispatcher");
                t.setDaemon(true);
                return t;
            }
        });

    /** The default listener-dispatch executor (a draining daemon thread). */
    public static Executor defaultExecutor() { return DEFAULT; }

    /** Runs the command on the calling thread (used for await's internal latch
     *  signalling, never for user listeners). */
    public static final Executor DIRECT = new Executor() {
        @Override public void execute(Runnable command) { command.run(); }
    };
}
