package android.media.metrics;

/** A playback error, as reported to the metrics pipeline.
 *  Source: frameworks/base/media/java/android/media/metrics/PlaybackErrorEvent.java */
public final class PlaybackErrorEvent extends Event implements android.os.Parcelable {

    public static final int ERROR_UNKNOWN = 0;
    public static final int ERROR_OTHER = 1;
    public static final int ERROR_RUNTIME = 2;
    public static final int ERROR_NETWORK_CONNECTION_FAILED = 3;
    public static final int ERROR_NETWORK_CONNECTION_TIMEOUT = 4;
    public static final int ERROR_IO = 5;
    public static final int ERROR_PARSING_CONTAINER_MALFORMED = 6;
    public static final int ERROR_PARSING_MANIFEST_MALFORMED = 7;
    public static final int ERROR_DECODER_INIT_FAILED = 10;
    public static final int ERROR_DECODING_FAILED = 12;
    public static final int ERROR_AUDIO_TRACK_INIT_FAILED = 14;
    public static final int ERROR_DRM_SCHEME_UNSUPPORTED = 20;
    public static final int ERROR_PLAYER_REMOTE = 25;

    private final String mExceptionStack;
    private final int mErrorCode;
    private final int mSubErrorCode;

    private PlaybackErrorEvent(Builder b) {
        mExceptionStack = b.mExceptionStack;
        mErrorCode = b.mErrorCode;
        mSubErrorCode = b.mSubErrorCode;
        mTimeSinceCreatedMillis = b.mTimeSinceCreatedMillis;
    }

    public int getErrorCode() { return mErrorCode; }
    public int getSubErrorCode() { return mSubErrorCode; }
    public String getExceptionStack() { return mExceptionStack; }

    // AOSP's metrics events are Parcelable because they cross a binder to the
    // system metrics collector. Nothing here crosses a process boundary (see
    // MediaMetricsManager), so the wire shape only has to round-trip with our
    // own CREATOR -- our field order, not AOSP's, per the
    // absent-fields-not-padded rule.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mExceptionStack);
        dest.writeInt(mErrorCode);
        dest.writeInt(mSubErrorCode);
        dest.writeLong(mTimeSinceCreatedMillis);
    }

    @Override public String toString() {
        return "PlaybackErrorEvent{ errorCode=" + mErrorCode
                + ", subErrorCode=" + mSubErrorCode + " }";
    }

    public static final android.os.Parcelable.Creator<PlaybackErrorEvent> CREATOR =
            new android.os.Parcelable.Creator<PlaybackErrorEvent>() {
        @Override public PlaybackErrorEvent createFromParcel(android.os.Parcel in) {
            Builder b = new Builder();
            // setException() derives the stack from a live exception; the
            // already-rendered string is restored directly instead.
            b.mExceptionStack = in.readString();
            return b.setErrorCode(in.readInt())
                    .setSubErrorCode(in.readInt())
                    .setTimeSinceCreatedMillis(in.readLong())
                    .build();
        }
        @Override public PlaybackErrorEvent[] newArray(int size) {
            return new PlaybackErrorEvent[size];
        }
    };

    public static final class Builder {
        private String mExceptionStack;
        private int mErrorCode = ERROR_UNKNOWN;
        private int mSubErrorCode;
        private long mTimeSinceCreatedMillis = -1;

        public Builder() { }

        public Builder setException(Exception e) {
            if (e != null) {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                mExceptionStack = sw.toString();
            }
            return this;
        }
        public Builder setErrorCode(int v) { mErrorCode = v; return this; }
        public Builder setSubErrorCode(int v) { mSubErrorCode = v; return this; }
        public Builder setTimeSinceCreatedMillis(long v) { mTimeSinceCreatedMillis = v; return this; }
        public Builder setMetricsBundle(android.os.Bundle b) { return this; }

        public PlaybackErrorEvent build() { return new PlaybackErrorEvent(this); }
    }
}
