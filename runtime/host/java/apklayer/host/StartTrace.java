package apklayer.host;

/** -Dapklayer.perf=true: one line per start-up step on CLOCK_MONOTONIC (milliseconds), the clock the CLI and the shell print with APKLAYER_RUNTIME_DEBUG. */
public final class StartTrace {
    private static final boolean ON = Boolean.getBoolean("apklayer.perf");

    public static void mark(String step) {
        if (ON) System.out.println("[apklayer] t=" + System.nanoTime() / 1_000_000 + " " + step);
    }

    /**
     * The step that ends a start: the activity's first frame committed to the renderer. "activity resumed" alone compares badly across builds: the
     * first frame (the window's surface, EGL, the first draw: 4-5 s on a phone) runs before it when the frame is due at visible()'s idle, else after it.
     */
    static void firstFrame(android.app.Activity a) {
        if (ON) a.getWindow().getDecorView().getViewTreeObserver().registerFrameCommitCallback(() -> mark("first frame " + a.getClass().getName()));
    }

    private StartTrace() {}
}
