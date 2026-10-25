package android.media.metrics;

/** Metrics session of the EditingSession kind; reports are accepted and dropped.
 *  See MediaMetricsManager for the rationale.
 *  Source: frameworks/base/media/java/android/media/metrics/EditingSession.java */
public final class EditingSession implements AutoCloseable {
    private final LogSessionId mLogSessionId = LogSessionId.LOG_SESSION_ID_NONE;
    EditingSession() { }
    public LogSessionId getSessionId() { return mLogSessionId; }
    /** API 35. Same contract as BundleSession.reportBundleMetrics: accepted and
     *  dropped, because there is no collector, and throwing would break the
     *  editing operation that is reporting into it. */
    public void reportEditingEndedEvent(EditingEndedEvent event) {
        java.util.Objects.requireNonNull(event, "event must not be null");
    }

    @Override public void close() { }
}
