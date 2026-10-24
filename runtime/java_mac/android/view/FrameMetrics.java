// android.view.FrameMetrics -- per-frame timing data (API 24+) delivered to a
// Window.OnFrameMetricsAvailableListener. Apps use it for jank/perf telemetry.
//
// Why Mac.apk needs it (v1.0.406): Reddit's IntroductionActivity (welcome/login
// screen) wires a Window.OnFrameMetricsAvailableListener in onCreate (its
// RedditAppPerformanceAnalytics). The listener's callback is typed
// (Window, FrameMetrics, int), so this class must exist or the listener
// interface fails to load (NoClassDefFoundError) and onCreate aborts before the
// Compose login UI sets up. Mac.apk doesn't produce real frame timings, so the
// listener is never invoked; this class exists for type resolution and returns
// zeros if anything ever reads it.
package android.view;

public final class FrameMetrics {
    public static final int UNKNOWN_DELAY_DURATION      = 0;
    public static final int INPUT_HANDLING_DURATION     = 1;
    public static final int ANIMATION_DURATION          = 2;
    public static final int LAYOUT_MEASURE_DURATION     = 3;
    public static final int DRAW_DURATION               = 4;
    public static final int SYNC_DURATION               = 5;
    public static final int COMMAND_ISSUE_DURATION      = 6;
    public static final int SWAP_BUFFERS_DURATION       = 7;
    public static final int TOTAL_DURATION              = 8;
    public static final int FIRST_DRAW_FRAME            = 9;
    public static final int INTENDED_VSYNC_TIMESTAMP    = 10;
    public static final int VSYNC_TIMESTAMP             = 11;
    public static final int GPU_DURATION                = 12;
    public static final int DEADLINE                    = 13;

    public FrameMetrics() {}
    public FrameMetrics(FrameMetrics other) {}

    /** AOSP returns the requested metric in nanoseconds (or a boolean-as-long
     *  for FIRST_DRAW_FRAME). We have no real timings, so report 0. */
    public long getMetric(int id) { return 0L; }
}
