package android.media.metrics;

/**
 * Opaque id tying media-metrics events to one playback session.
 *
 * Source: frameworks/base/media/java/android/media/metrics/LogSessionId.java
 */
public final class LogSessionId {

    /** AOSP's sentinel meaning "metrics are not being collected". */
    public static final LogSessionId LOG_SESSION_ID_NONE = new LogSessionId("");

    private final String mSessionId;

    public LogSessionId(String id) {
        if (id == null) throw new NullPointerException("id can't be null");
        mSessionId = id;
    }

    public String getStringId() { return mSessionId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogSessionId)) return false;
        return mSessionId.equals(((LogSessionId) o).mSessionId);
    }

    @Override public int hashCode() { return mSessionId.hashCode(); }

    @Override public String toString() { return mSessionId; }
}
