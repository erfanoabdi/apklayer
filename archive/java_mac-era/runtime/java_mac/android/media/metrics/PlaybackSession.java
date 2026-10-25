package android.media.metrics;

/**
 * A playback-metrics session. Reports are accepted and dropped; see
 * MediaMetricsManager for why that is the correct behavior here.
 *
 * Source: frameworks/base/media/java/android/media/metrics/PlaybackSession.java
 */
public final class PlaybackSession implements AutoCloseable {

    private final LogSessionId mLogSessionId = LogSessionId.LOG_SESSION_ID_NONE;

    PlaybackSession() { }

    public LogSessionId getSessionId() { return mLogSessionId; }

    public void reportPlaybackMetrics(PlaybackMetrics metrics) { }

    public void reportPlaybackErrorEvent(PlaybackErrorEvent event) { }

    public void reportNetworkEvent(NetworkEvent event) { }

    public void reportPlaybackStateEvent(PlaybackStateEvent event) { }

    public void reportTrackChangeEvent(TrackChangeEvent event) { }

    @Override public void close() { }
}
