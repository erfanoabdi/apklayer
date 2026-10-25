package android.media.metrics;

/**
 * A completed playback's aggregate metrics.
 * Source: frameworks/base/media/java/android/media/metrics/PlaybackMetrics.java
 */
public final class PlaybackMetrics implements android.os.Parcelable {

    public static final int STREAM_SOURCE_UNKNOWN = 0;
    public static final int STREAM_SOURCE_NETWORK = 1;
    public static final int STREAM_SOURCE_DEVICE = 2;
    public static final int STREAM_SOURCE_MIXED = 3;

    public static final int STREAM_TYPE_UNKNOWN = 0;
    public static final int STREAM_TYPE_OTHER = 1;
    public static final int STREAM_TYPE_PROGRESSIVE = 2;
    public static final int STREAM_TYPE_DASH = 3;
    public static final int STREAM_TYPE_HLS = 4;
    public static final int STREAM_TYPE_SS = 5;

    public static final int PLAYBACK_TYPE_UNKNOWN = 0;
    public static final int PLAYBACK_TYPE_VOD = 1;
    public static final int PLAYBACK_TYPE_LIVE = 2;
    public static final int PLAYBACK_TYPE_OTHER = 3;

    public static final int DRM_TYPE_NONE = 0;
    public static final int DRM_TYPE_OTHER = 1;
    public static final int DRM_TYPE_PLAY_READY = 2;
    public static final int DRM_TYPE_WIDEVINE_L1 = 3;
    public static final int DRM_TYPE_WIDEVINE_L3 = 4;
    public static final int DRM_TYPE_WV_L3_FALLBACK = 5;
    public static final int DRM_TYPE_CLEARKEY = 6;

    public static final int CONTENT_TYPE_UNKNOWN = 0;
    public static final int CONTENT_TYPE_MAIN = 1;
    public static final int CONTENT_TYPE_AD = 2;
    public static final int CONTENT_TYPE_OTHER = 3;

    private final long mMediaDurationMillis;
    private final int mStreamSource;
    private final int mStreamType;
    private final int mPlaybackType;
    private final int mDrmType;
    private final int mContentType;
    private final String mPlayerName;
    private final String mPlayerVersion;
    private final int mVideoFramesPlayed;
    private final int mVideoFramesDropped;
    private final int mAudioUnderrunCount;
    private final long mNetworkBytesRead;
    private final long mLocalBytesRead;
    private final long mNetworkTransferDurationMillis;
    private final byte[] mDrmSessionId;
    private final long[] mExperimentIds;
    private final android.os.Bundle mMetricsBundle;

    private PlaybackMetrics(Builder b) {
        mMediaDurationMillis = b.mMediaDurationMillis;
        mStreamSource = b.mStreamSource;
        mStreamType = b.mStreamType;
        mPlaybackType = b.mPlaybackType;
        mDrmType = b.mDrmType;
        mContentType = b.mContentType;
        mPlayerName = b.mPlayerName;
        mPlayerVersion = b.mPlayerVersion;
        mVideoFramesPlayed = b.mVideoFramesPlayed;
        mVideoFramesDropped = b.mVideoFramesDropped;
        mAudioUnderrunCount = b.mAudioUnderrunCount;
        mNetworkBytesRead = b.mNetworkBytesRead;
        mLocalBytesRead = b.mLocalBytesRead;
        mNetworkTransferDurationMillis = b.mNetworkTransferDurationMillis;
        mDrmSessionId = b.mDrmSessionId;
        mExperimentIds = new long[b.mExperimentIds.size()];
        for (int i = 0; i < mExperimentIds.length; i++) {
            mExperimentIds[i] = b.mExperimentIds.get(i).longValue();
        }
        mMetricsBundle = b.mMetricsBundle;
    }

    public long getMediaDurationMillis() { return mMediaDurationMillis; }
    public int getStreamSource() { return mStreamSource; }
    public int getStreamType() { return mStreamType; }
    public int getPlaybackType() { return mPlaybackType; }
    public int getDrmType() { return mDrmType; }
    public int getContentType() { return mContentType; }
    public String getPlayerName() { return mPlayerName; }
    public String getPlayerVersion() { return mPlayerVersion; }
    public int getVideoFramesPlayed() { return mVideoFramesPlayed; }
    public int getVideoFramesDropped() { return mVideoFramesDropped; }
    public int getAudioUnderrunCount() { return mAudioUnderrunCount; }
    public long getNetworkBytesRead() { return mNetworkBytesRead; }
    public long getLocalBytesRead() { return mLocalBytesRead; }
    public long getNetworkTransferDurationMillis() { return mNetworkTransferDurationMillis; }

    /** @NonNull in AOSP: an empty array when the app set no session id. */
    public byte[] getDrmSessionId() { return mDrmSessionId; }

    /** @NonNull in AOSP: the A/B experiment ids the app tagged this playback
     *  with, empty when it tagged none. */
    public long[] getExperimentIds() { return mExperimentIds.clone(); }

    /** @NonNull in AOSP: the app's own extra metrics, empty when it set none. */
    public android.os.Bundle getMetricsBundle() {
        return mMetricsBundle == null ? new android.os.Bundle() : mMetricsBundle;
    }

    // AOSP's metrics types are Parcelable because they cross a binder to the
    // system metrics collector. Nothing here crosses a process boundary (see
    // MediaMetricsManager), so the wire shape only has to round-trip with our
    // own CREATOR -- our field order, not AOSP's.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeLong(mMediaDurationMillis);
        dest.writeInt(mStreamSource);
        dest.writeInt(mStreamType);
        dest.writeInt(mPlaybackType);
        dest.writeInt(mDrmType);
        dest.writeInt(mContentType);
        dest.writeString(mPlayerName);
        dest.writeString(mPlayerVersion);
        dest.writeInt(mVideoFramesPlayed);
        dest.writeInt(mVideoFramesDropped);
        dest.writeInt(mAudioUnderrunCount);
        dest.writeLong(mNetworkBytesRead);
        dest.writeLong(mLocalBytesRead);
        dest.writeLong(mNetworkTransferDurationMillis);
        dest.writeByteArray(mDrmSessionId);
        dest.writeLongArray(mExperimentIds);
        dest.writeBundle(mMetricsBundle);
    }

    @Override public String toString() {
        return "PlaybackMetrics{ streamType=" + mStreamType
                + ", playbackType=" + mPlaybackType + " }";
    }

    public static final android.os.Parcelable.Creator<PlaybackMetrics> CREATOR =
            new android.os.Parcelable.Creator<PlaybackMetrics>() {
        @Override public PlaybackMetrics createFromParcel(android.os.Parcel in) {
            Builder b = new Builder();
            b.setMediaDurationMillis(in.readLong());
            b.setStreamSource(in.readInt());
            b.setStreamType(in.readInt());
            b.setPlaybackType(in.readInt());
            b.setDrmType(in.readInt());
            b.setContentType(in.readInt());
            b.setPlayerName(in.readString());
            b.setPlayerVersion(in.readString());
            b.setVideoFramesPlayed(in.readInt());
            b.setVideoFramesDropped(in.readInt());
            b.setAudioUnderrunCount(in.readInt());
            b.setNetworkBytesRead(in.readLong());
            b.setLocalBytesRead(in.readLong());
            b.setNetworkTransferDurationMillis(in.readLong());
            byte[] sessionId = in.createByteArray();
            if (sessionId != null) b.setDrmSessionId(sessionId);
            long[] experiments = in.createLongArray();
            if (experiments != null) {
                for (long id : experiments) b.addExperimentId(id);
            }
            android.os.Bundle bundle = in.readBundle();
            if (bundle != null) b.setMetricsBundle(bundle);
            return b.build();
        }
        @Override public PlaybackMetrics[] newArray(int size) { return new PlaybackMetrics[size]; }
    };

    public static final class Builder {
        private long mMediaDurationMillis = -1;
        private int mStreamSource = STREAM_SOURCE_UNKNOWN;
        private int mStreamType = STREAM_TYPE_UNKNOWN;
        private int mPlaybackType = PLAYBACK_TYPE_UNKNOWN;
        private int mDrmType = DRM_TYPE_NONE;
        private int mContentType = CONTENT_TYPE_UNKNOWN;
        private String mPlayerName;
        private String mPlayerVersion;
        private int mVideoFramesPlayed = -1;
        private int mVideoFramesDropped = -1;
        private int mAudioUnderrunCount = -1;
        private long mNetworkBytesRead = -1;
        private long mLocalBytesRead = -1;
        private long mNetworkTransferDurationMillis = -1;
        private byte[] mDrmSessionId = new byte[0];
        private final java.util.ArrayList<Long> mExperimentIds = new java.util.ArrayList<>();
        private android.os.Bundle mMetricsBundle;

        public Builder() { }

        public Builder setMediaDurationMillis(long v) { mMediaDurationMillis = v; return this; }
        public Builder setStreamSource(int v) { mStreamSource = v; return this; }
        public Builder setStreamType(int v) { mStreamType = v; return this; }
        public Builder setPlaybackType(int v) { mPlaybackType = v; return this; }
        public Builder setDrmType(int v) { mDrmType = v; return this; }
        public Builder setContentType(int v) { mContentType = v; return this; }
        public Builder setPlayerName(String v) { mPlayerName = v; return this; }
        public Builder setPlayerVersion(String v) { mPlayerVersion = v; return this; }
        public Builder setVideoFramesPlayed(int v) { mVideoFramesPlayed = v; return this; }
        public Builder setVideoFramesDropped(int v) { mVideoFramesDropped = v; return this; }
        public Builder setAudioUnderrunCount(int v) { mAudioUnderrunCount = v; return this; }
        public Builder setNetworkBytesRead(long v) { mNetworkBytesRead = v; return this; }
        public Builder setLocalBytesRead(long v) { mLocalBytesRead = v; return this; }
        public Builder setNetworkTransferDurationMillis(long v) {
            mNetworkTransferDurationMillis = v; return this;
        }

        /** AOSP requires a non-null session id (PlaybackMetrics.java,
         *  Builder.setDrmSessionId). */
        public Builder setDrmSessionId(byte[] drmSessionId) {
            mDrmSessionId = java.util.Objects.requireNonNull(drmSessionId,
                    "drmSessionId must not be null");
            return this;
        }

        /** API 31. Tags this playback with one A/B experiment id; AOSP
         *  accumulates them. */
        public Builder addExperimentId(long experimentId) {
            mExperimentIds.add(Long.valueOf(experimentId));
            return this;
        }

        /** API 31. The app's own extra metrics, copied so a later mutation of
         *  the caller's Bundle cannot alter a built report. */
        public Builder setMetricsBundle(android.os.Bundle metricsBundle) {
            java.util.Objects.requireNonNull(metricsBundle, "metricsBundle must not be null");
            mMetricsBundle = new android.os.Bundle(metricsBundle);
            return this;
        }

        public PlaybackMetrics build() { return new PlaybackMetrics(this); }
    }
}
