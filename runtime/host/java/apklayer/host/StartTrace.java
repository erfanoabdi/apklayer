package apklayer.host;

/** -Dapklayer.perf=true: one line per start-up step on CLOCK_MONOTONIC (milliseconds), the clock the CLI and the shell print with APKLAYER_RUNTIME_DEBUG. */
public final class StartTrace {
    private static final boolean ON = Boolean.getBoolean("apklayer.perf");

    public static void mark(String step) {
        if (ON) System.out.println("[apklayer] t=" + System.nanoTime() / 1_000_000 + " " + step);
    }

    private StartTrace() {}
}
