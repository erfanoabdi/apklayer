package android.media.metrics;

/** Metrics session of the TranscodingSession kind; reports are accepted and dropped.
 *  See MediaMetricsManager for the rationale.
 *  Source: frameworks/base/media/java/android/media/metrics/TranscodingSession.java */
public final class TranscodingSession implements AutoCloseable {
    private final LogSessionId mLogSessionId = LogSessionId.LOG_SESSION_ID_NONE;
    TranscodingSession() { }
    public LogSessionId getSessionId() { return mLogSessionId; }
    @Override public void close() { }
}
