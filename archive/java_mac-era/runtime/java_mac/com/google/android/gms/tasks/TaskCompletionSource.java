package com.google.android.gms.tasks;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// com.google.android.gms.tasks.TaskCompletionSource<T> -- produces a Task the
// caller completes later. Mirrors play-services exactly now (F2): it holds ONE
// TaskImpl created in the constructor; getTask() always returns that same
// instance (pending until set); setResult/setException transition it and fire
// queued listeners. This kills the three failure modes of the old SyncTask
// shadow (getTask-before-set returned a FAILED task; lost pre-set listeners;
// set-after-getTask built a new task) that stalled real apps' loading pipelines
// (Subway's Play-Games doBestEffortWrite path threw our ISE here).
public final class TaskCompletionSource<T> {
    private final TaskImpl<T> task = new TaskImpl<T>();

    public TaskCompletionSource() { TaskDiag.register(this); }

    // play-services added a CancellationToken overload in v17.1.0. The
    // parameter type is load-bearing: the JVM resolves constructors by EXACT
    // descriptor, so an app compiled against the real SDK emits
    // <init>(Lcom/google/android/gms/tasks/CancellationToken;)V and an
    // Object-typed declaration here misses it with NoSuchMethodError at link
    // time. Cancellation is wired for real -- when the token fires we cancel
    // the Task, which is what the real TaskCompletionSource(CancellationToken)
    // does (the token's whole purpose is to move the produced Task to the
    // canceled state without the producer having to observe it).
    public TaskCompletionSource(CancellationToken cancellationToken) {
        TaskDiag.register(this);
        if (cancellationToken != null) {
            try {
                cancellationToken.onCanceledRequested(new OnTokenCanceledListener() {
                    @Override public void onCanceled() { task.trySetCanceled(); }
                });
            } catch (Throwable ignored) {
                // A third-party CancellationToken subclass that throws from
                // registration must not take down construction of the source.
            }
        }
    }

    public Task<T> getTask() { return task; }

    public void setResult(T result) {
        if (!task.trySetResult(result))
            throw new IllegalStateException("Cannot set the result.");
    }
    public boolean trySetResult(T result) { return task.trySetResult(result); }

    public void setException(Exception e) {
        if (e == null) throw new NullPointerException("Exception must not be null");
        if (!task.trySetException(e))
            throw new IllegalStateException("Cannot set the exception.");
    }
    public boolean trySetException(Exception e) {
        if (e == null) throw new NullPointerException("Exception must not be null");
        return task.trySetException(e);
    }

    boolean isComplete() { return task.isComplete(); }

    // --- MACAPK_TASK_DIAG: pending-Task census ----------------------------
    // Converts every future "loading stalls silently on an async gate" into a
    // named component: records each TCS's creation stack and logs any that is
    // still incomplete after a grace period. Default-off; pure diagnostic.
    static final class TaskDiag {
        private static final boolean ON = System.getenv("MACAPK_TASK_DIAG") != null;
        private static final List<Object[]> REG = new ArrayList<Object[]>(); // (WeakRef<TCS>, StackTraceElement[], Long createNs)
        private static volatile boolean started = false;

        static void register(TaskCompletionSource<?> tcs) {
            if (!ON) return;
            synchronized (REG) {
                REG.add(new Object[]{ new WeakReference<TaskCompletionSource<?>>(tcs),
                                       new Throwable().getStackTrace(),
                                       Long.valueOf(System.nanoTime()) });
                ensureMonitor();
            }
        }

        private static void ensureMonitor() {
            if (started) return;
            started = true;
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    while (true) {
                        try { Thread.sleep(10_000); } catch (InterruptedException ie) { return; }
                        scan();
                    }
                }
            }, "MacApkTaskDiag");
            t.setDaemon(true);
            t.start();
        }

        private static void scan() {
            long now = System.nanoTime();
            synchronized (REG) {
                for (Iterator<Object[]> it = REG.iterator(); it.hasNext(); ) {
                    Object[] e = it.next();
                    @SuppressWarnings("unchecked")
                    WeakReference<TaskCompletionSource<?>> ref =
                        (WeakReference<TaskCompletionSource<?>>) e[0];
                    TaskCompletionSource<?> tcs = ref.get();
                    if (tcs == null || tcs.isComplete()) { it.remove(); continue; }
                    long ageMs = (now - ((Long) e[2]).longValue()) / 1_000_000L;
                    if (ageMs < 30_000L) continue;
                    StackTraceElement[] st = (StackTraceElement[]) e[1];
                    StringBuilder sb = new StringBuilder();
                    sb.append("[task-diag] TaskCompletionSource pending ")
                      .append(ageMs / 1000).append("s (never completed) -- created at:\n");
                    int n = 0;
                    for (StackTraceElement f : st) {
                        String cn = f.getClassName();
                        if (cn.startsWith("com.google.android.gms.tasks.TaskCompletionSource")) continue;
                        sb.append("    at ").append(f).append('\n');
                        if (++n >= 12) break;
                    }
                    System.err.print(sb);
                    System.err.flush();
                    it.remove();   // report once
                }
            }
        }
    }
}
