package android.media.metrics;

/**
 * Entry point for the platform media-metrics pipeline (API 31+).
 *
 * On a device this ships playback telemetry to the system statsd collector.
 * Mac.apk has no such collector, and there is no user-visible behavior to
 * reproduce -- so the sessions returned here ACCEPT every report and drop it.
 * That is the right call rather than throwing: ExoPlayer/Media3 create a
 * playback session and report into it on every buffer, error and track change,
 * so a throwing implementation would break playback itself in order to service
 * telemetry that nothing consumes. AOSP's own contract already allows a
 * no-collection device (LogSessionId.LOG_SESSION_ID_NONE exists for exactly
 * that), and the sessions here report that id.
 *
 * Source: frameworks/base/media/java/android/media/metrics/MediaMetricsManager.java
 */
public class MediaMetricsManager {

    public MediaMetricsManager() { }

    public PlaybackSession createPlaybackSession() { return new PlaybackSession(); }

    public RecordingSession createRecordingSession() { return new RecordingSession(); }

    public TranscodingSession createTranscodingSession() { return new TranscodingSession(); }

    public EditingSession createEditingSession() { return new EditingSession(); }

    public BundleSession createBundleSession() { return new BundleSession(); }

    /** API 31. AOSP tells the collector it may drop the state it accumulated
     *  for this session id. No collector holds any state here (see the class
     *  comment), so there is nothing to release -- but the call must exist and
     *  must not throw, because a player calls it on teardown. AOSP requires a
     *  non-null id. */
    public void releaseSessionId(String sessionId) {
        java.util.Objects.requireNonNull(sessionId, "sessionId must not be null");
    }
}
