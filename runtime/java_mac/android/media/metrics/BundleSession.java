package android.media.metrics;

/** Metrics session of the BundleSession kind; reports are accepted and dropped.
 *  See MediaMetricsManager for the rationale.
 *  Source: frameworks/base/media/java/android/media/metrics/BundleSession.java */
public final class BundleSession implements AutoCloseable {
    private final LogSessionId mLogSessionId = LogSessionId.LOG_SESSION_ID_NONE;
    BundleSession() { }
    public LogSessionId getSessionId() { return mLogSessionId; }
    /** API 33. AOSP forwards the bundle to the system metrics collector. There
     *  is no collector here (see MediaMetricsManager), so the report is accepted
     *  and dropped -- which is deliberate rather than lazy: a throwing
     *  implementation would break the PLAYBACK that is reporting into it. AOSP
     *  requires a non-null bundle, and so does this. */
    public void reportBundleMetrics(android.os.PersistableBundle metrics) {
        java.util.Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @Override public void close() { }
}
