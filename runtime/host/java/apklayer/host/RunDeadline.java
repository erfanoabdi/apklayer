package apklayer.host;

/**
 * {@code -Dapklayer.runms}: the run loop ends the app once that time is up, but only while the main thread comes back to it. An app whose main thread
 * hangs (in Application.onCreate, a synchronous binder call, a native pump) would run on: at runms plus the start-up allowance the threads are printed
 * (where it hung) and the JVM ends, so a timed run always ends.
 */
final class RunDeadline {
    private static final long STARTUP_MS = 30_000;  // launch, before the run loop starts counting runms

    private RunDeadline() {}

    static void start() {
        Long runMs = Long.getLong("apklayer.runms");
        if (runMs == null) return;
        Thread deadline = new Thread(() -> {
            try { Thread.sleep(runMs + STARTUP_MS); } catch (InterruptedException ended) { return; }
            System.out.println("[apklayer] runms " + runMs + " passed long ago and the app is still running: its threads, then the end");
            Thread.getAllStackTraces().forEach((t, stack) -> {
                if (t.isDaemon() && t != Thread.currentThread() && !t.getName().contains("main") && !t.getName().contains("Main")) return;
                System.out.println("[apklayer] thread " + t.getName() + " " + t.getState());
                int depth = t.getName().contains("Main") ? 150 : 25;  // the main thread's app frames lie under Robolectric's method-handle chains
                for (int i = 0; i < Math.min(stack.length, depth); i++) System.out.println("[apklayer]     at " + stack[i]);
            });
            System.out.flush();
            Runtime.getRuntime().halt(124);  // timeout(1)'s status
        }, "apklayer-runms");
        deadline.setDaemon(true);
        deadline.start();
    }
}
