package android.media.metrics;

/** Common base for the media-metrics event types.
 *  Source: frameworks/base/media/java/android/media/metrics/Event.java */
public abstract class Event {
    long mTimeSinceCreatedMillis = -1;

    public long getTimeSinceCreatedMillis() { return mTimeSinceCreatedMillis; }

    public android.os.Bundle getMetricsBundle() { return new android.os.Bundle(); }
}
