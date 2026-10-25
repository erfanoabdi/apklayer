package android.media.metrics;

/** A playback state transition.
 *  Source: frameworks/base/media/java/android/media/metrics/PlaybackStateEvent.java */
public final class PlaybackStateEvent extends Event implements android.os.Parcelable {

    public static final int STATE_NOT_STARTED = 0;
    public static final int STATE_JOINING_BACKGROUND = 1;
    public static final int STATE_JOINING_FOREGROUND = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED = 4;
    public static final int STATE_SEEKING = 5;
    public static final int STATE_BUFFERING = 6;
    public static final int STATE_ENDED = 11;
    public static final int STATE_STOPPED = 12;
    public static final int STATE_FAILED = 13;
    public static final int STATE_ABANDONED = 15;

    private final int mState;

    private PlaybackStateEvent(Builder b) {
        mState = b.mState;
        mTimeSinceCreatedMillis = b.mTimeSinceCreatedMillis;
    }

    public int getState() { return mState; }

    // AOSP's metrics events are Parcelable because they cross a binder to the
    // system metrics collector. Nothing here crosses a process boundary (see
    // MediaMetricsManager), so the wire shape only has to round-trip with our
    // own CREATOR -- our field order, not AOSP's, per the
    // absent-fields-not-padded rule.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mState);
        dest.writeLong(mTimeSinceCreatedMillis);
    }

    @Override public String toString() { return "PlaybackStateEvent{ state=" + mState + " }"; }

    public static final android.os.Parcelable.Creator<PlaybackStateEvent> CREATOR =
            new android.os.Parcelable.Creator<PlaybackStateEvent>() {
        @Override public PlaybackStateEvent createFromParcel(android.os.Parcel in) {
            return new Builder()
                    .setState(in.readInt())
                    .setTimeSinceCreatedMillis(in.readLong())
                    .build();
        }
        @Override public PlaybackStateEvent[] newArray(int size) {
            return new PlaybackStateEvent[size];
        }
    };

    public static final class Builder {
        private int mState = STATE_NOT_STARTED;
        private long mTimeSinceCreatedMillis = -1;

        public Builder() { }

        public Builder setState(int v) { mState = v; return this; }
        public Builder setTimeSinceCreatedMillis(long v) { mTimeSinceCreatedMillis = v; return this; }
        public Builder setMetricsBundle(android.os.Bundle b) { return this; }

        public PlaybackStateEvent build() { return new PlaybackStateEvent(this); }
    }
}
