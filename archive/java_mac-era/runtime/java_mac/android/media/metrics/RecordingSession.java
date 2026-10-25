package android.media.metrics;

/** Metrics session of the RecordingSession kind; reports are accepted and dropped.
 *  See MediaMetricsManager for the rationale.
 *  Source: frameworks/base/media/java/android/media/metrics/RecordingSession.java */
public final class RecordingSession implements AutoCloseable {
    private final LogSessionId mLogSessionId = LogSessionId.LOG_SESSION_ID_NONE;
    RecordingSession() { }
    public LogSessionId getSessionId() { return mLogSessionId; }
    @Override public void close() { }
}
