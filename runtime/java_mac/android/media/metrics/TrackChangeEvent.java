package android.media.metrics;

/** A track selection change (video/audio/text) during playback.
 *  Source: frameworks/base/media/java/android/media/metrics/TrackChangeEvent.java */
public final class TrackChangeEvent extends Event implements android.os.Parcelable {

    public static final int TRACK_STATE_OFF = 0;
    public static final int TRACK_STATE_ON = 1;

    public static final int TRACK_CHANGE_REASON_UNKNOWN = 0;
    public static final int TRACK_CHANGE_REASON_OTHER = 1;
    public static final int TRACK_CHANGE_REASON_INITIAL = 2;
    public static final int TRACK_CHANGE_REASON_MANUAL = 3;
    public static final int TRACK_CHANGE_REASON_ADAPTIVE = 4;

    public static final int TRACK_TYPE_AUDIO = 0;
    public static final int TRACK_TYPE_VIDEO = 1;
    public static final int TRACK_TYPE_TEXT = 2;

    private final int mState;
    private final int mReason;
    private final int mType;
    private final String mContainerMimeType;
    private final String mSampleMimeType;
    private final String mCodecName;
    private final int mBitrate;
    private final int mWidth;
    private final int mHeight;
    private final String mLanguage;
    // ⚠ These four were ACCEPTED by the Builder and then discarded, so an app
    // that set a sample rate and read it back was told it had never set one.
    // AOSP stores them and publishes a getter for each.
    private final String mLanguageRegion;
    private final float mVideoFrameRate;
    private final int mChannelCount;
    private final int mAudioSampleRate;

    private TrackChangeEvent(Builder b) {
        mState = b.mState; mReason = b.mReason; mType = b.mType;
        mContainerMimeType = b.mContainerMimeType; mSampleMimeType = b.mSampleMimeType;
        mCodecName = b.mCodecName; mBitrate = b.mBitrate;
        mWidth = b.mWidth; mHeight = b.mHeight; mLanguage = b.mLanguage;
        mLanguageRegion = b.mLanguageRegion; mVideoFrameRate = b.mVideoFrameRate;
        mChannelCount = b.mChannelCount; mAudioSampleRate = b.mAudioSampleRate;
        mTimeSinceCreatedMillis = b.mTimeSinceCreatedMillis;
    }

    public int getTrackState() { return mState; }
    public int getTrackChangeReason() { return mReason; }
    public int getTrackType() { return mType; }
    public String getContainerMimeType() { return mContainerMimeType; }
    public String getSampleMimeType() { return mSampleMimeType; }
    public String getCodecName() { return mCodecName; }
    public int getBitrate() { return mBitrate; }
    public int getWidth() { return mWidth; }
    public int getHeight() { return mHeight; }
    public String getLanguage() { return mLanguage; }
    /** @Nullable in AOSP: the region subtag ("US" in "en-US"). */
    public String getLanguageRegion() { return mLanguageRegion; }
    public float getVideoFrameRate() { return mVideoFrameRate; }
    public int getChannelCount() { return mChannelCount; }
    public int getAudioSampleRate() { return mAudioSampleRate; }

    // AOSP's metrics events are Parcelable because they cross a binder to the
    // system metrics collector. Nothing here crosses a process boundary (see
    // MediaMetricsManager), so the wire shape only has to round-trip with our
    // own CREATOR -- our field order, not AOSP's.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeInt(mState);
        dest.writeInt(mReason);
        dest.writeString(mContainerMimeType);
        dest.writeString(mSampleMimeType);
        dest.writeString(mCodecName);
        dest.writeInt(mBitrate);
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeString(mLanguage);
        dest.writeString(mLanguageRegion);
        dest.writeFloat(mVideoFrameRate);
        dest.writeInt(mChannelCount);
        dest.writeInt(mAudioSampleRate);
        dest.writeLong(mTimeSinceCreatedMillis);
    }

    @Override public String toString() {
        return "TrackChangeEvent{ type=" + mType + ", state=" + mState + " }";
    }

    public static final android.os.Parcelable.Creator<TrackChangeEvent> CREATOR =
            new android.os.Parcelable.Creator<TrackChangeEvent>() {
        @Override public TrackChangeEvent createFromParcel(android.os.Parcel in) {
            Builder b = new Builder(in.readInt());
            b.setTrackState(in.readInt());
            b.setTrackChangeReason(in.readInt());
            b.setContainerMimeType(in.readString());
            b.setSampleMimeType(in.readString());
            b.setCodecName(in.readString());
            b.setBitrate(in.readInt());
            b.setWidth(in.readInt());
            b.setHeight(in.readInt());
            b.setLanguage(in.readString());
            b.setLanguageRegion(in.readString());
            b.setVideoFrameRate(in.readFloat());
            b.setChannelCount(in.readInt());
            b.setAudioSampleRate(in.readInt());
            b.setTimeSinceCreatedMillis(in.readLong());
            return b.build();
        }
        @Override public TrackChangeEvent[] newArray(int size) {
            return new TrackChangeEvent[size];
        }
    };

    public static final class Builder {
        private final int mType;
        private int mState = TRACK_STATE_OFF;
        private int mReason = TRACK_CHANGE_REASON_UNKNOWN;
        private String mContainerMimeType, mSampleMimeType, mCodecName, mLanguage;
        private int mBitrate = -1, mWidth = -1, mHeight = -1;
        private String mLanguageRegion;
        private float mVideoFrameRate = -1;
        private int mChannelCount = -1, mAudioSampleRate = -1;
        private long mTimeSinceCreatedMillis = -1;

        public Builder(int type) { mType = type; }

        public Builder setTrackState(int v) { mState = v; return this; }
        public Builder setTrackChangeReason(int v) { mReason = v; return this; }
        public Builder setContainerMimeType(String v) { mContainerMimeType = v; return this; }
        public Builder setSampleMimeType(String v) { mSampleMimeType = v; return this; }
        public Builder setCodecName(String v) { mCodecName = v; return this; }
        public Builder setBitrate(int v) { mBitrate = v; return this; }
        public Builder setWidth(int v) { mWidth = v; return this; }
        public Builder setHeight(int v) { mHeight = v; return this; }
        public Builder setLanguage(String v) { mLanguage = v; return this; }
        public Builder setLanguageRegion(String v) { mLanguageRegion = v; return this; }
        public Builder setVideoFrameRate(float v) { mVideoFrameRate = v; return this; }
        public Builder setChannelCount(int v) { mChannelCount = v; return this; }
        public Builder setAudioSampleRate(int v) { mAudioSampleRate = v; return this; }
        public Builder setTimeSinceCreatedMillis(long v) { mTimeSinceCreatedMillis = v; return this; }
        public Builder setMetricsBundle(android.os.Bundle b) { return this; }

        public TrackChangeEvent build() { return new TrackChangeEvent(this); }
    }
}
