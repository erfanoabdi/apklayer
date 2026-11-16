package apklayer.host;

/**
 * -Dapklayer.traceuncaught=true: prints every exception that reaches the default uncaught-exception handler, before the app's own handler runs.
 * Play services' handler ends its process (Process.killProcess, exit 137) without a log line; this names the exception that caused it. An app sets
 * its handler whenever it likes, so a daemon thread wraps whichever one is current.
 */
final class UncaughtTrace {
    private UncaughtTrace() {}

    static void install() {
        if (!Boolean.getBoolean("apklayer.traceuncaught")) return;
        Thread watcher = new Thread(() -> {
            Thread.UncaughtExceptionHandler wrapped = null;
            while (true) {
                Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
                if (current != wrapped) {
                    Thread.UncaughtExceptionHandler app = current;
                    wrapped = (thread, failure) -> {
                        System.out.println("[apklayer] uncaught in " + thread.getName() + ":");
                        failure.printStackTrace(System.out);
                        System.out.flush();
                        if (app != null) app.uncaughtException(thread, failure);
                    };
                    Thread.setDefaultUncaughtExceptionHandler(wrapped);
                }
                try { Thread.sleep(20); } catch (InterruptedException stop) { return; }
            }
        }, "apklayer-traceuncaught");
        watcher.setDaemon(true);
        watcher.start();
    }
}
