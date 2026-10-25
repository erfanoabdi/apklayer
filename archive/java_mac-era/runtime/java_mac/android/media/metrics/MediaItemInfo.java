package android.media.metrics;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes one media item that took part in an editing operation, for the
 * media-metrics pipeline (API 35).
 *
 * Pure app-supplied DESCRIPTIVE data: the app (Media3 Transformer, typically)
 * fills one of these in and hands it to EditingSession. Nothing about it depends
 * on a platform capability, so it is implemented in full. Where the reports go
 * -- nowhere, on this host -- is EditingSession's business, and its class
 * comment says so.
 *
 * Source: frameworks/base/media/java/android/media/metrics/MediaItemInfo.java
 * (android16-release).
 */
public final class MediaItemInfo implements Parcelable {

    public static final int SOURCE_TYPE_UNSPECIFIED = 0;
    public static final int SOURCE_TYPE_GALLERY = 1;
    public static final int SOURCE_TYPE_CAMERA = 2;
    public static final int SOURCE_TYPE_EDITING_SESSION = 3;
    public static final int SOURCE_TYPE_LOCAL_FILE = 4;
    public static final int SOURCE_TYPE_REMOTE_FILE = 5;
    public static final int SOURCE_TYPE_REMOTE_LIVE_STREAM = 6;
    public static final int SOURCE_TYPE_GENERATED = 7;

    public static final long DATA_TYPE_IMAGE = 1L;
    public static final long DATA_TYPE_VIDEO = 1L << 1;
    public static final long DATA_TYPE_AUDIO = 1L << 2;
    public static final long DATA_TYPE_METADATA = 1L << 3;
    public static final long DATA_TYPE_DEPTH = 1L << 4;
    public static final long DATA_TYPE_GAIN_MAP = 1L << 5;
    public static final long DATA_TYPE_HIGH_FRAME_RATE = 1L << 6;
    public static final long DATA_TYPE_SPEED_SETTING_CUE_POINTS = 1L << 7;
    public static final long DATA_TYPE_GAPLESS = 1L << 8;
    public static final long DATA_TYPE_SPATIAL_AUDIO = 1L << 9;
    public static final long DATA_TYPE_HIGH_DYNAMIC_RANGE_VIDEO = 1L << 10;

    /** AOSP's "this field was not set" sentinel. */
    public static final int VALUE_UNSPECIFIED = -1;

    private final int mSourceType;
    private final long mDurationMillis;
    private final long mClipDurationMillis;
    private final String mContainerMimeType;
    private final List<String> mSampleMimeTypes;
    private final List<String> mCodecNames;
    private final long mDataTypes;
    private final int mVideoDataSpace;
    private final Size mVideoSize;
    private final float mVideoFrameRate;
    private final long mVideoSampleCount;
    private final int mAudioChannelCount;
    private final int mAudioSampleRateHz;
    private final long mAudioSampleCount;

    private MediaItemInfo(Builder b) {
        mSourceType = b.mSourceType;
        mDurationMillis = b.mDurationMillis;
        mClipDurationMillis = b.mClipDurationMillis;
        mContainerMimeType = b.mContainerMimeType;
        mSampleMimeTypes = Collections.unmodifiableList(new ArrayList<>(b.mSampleMimeTypes));
        mCodecNames = Collections.unmodifiableList(new ArrayList<>(b.mCodecNames));
        mDataTypes = b.mDataTypes;
        mVideoDataSpace = b.mVideoDataSpace;
        mVideoSize = b.mVideoSize;
        mVideoFrameRate = b.mVideoFrameRate;
        mVideoSampleCount = b.mVideoSampleCount;
        mAudioChannelCount = b.mAudioChannelCount;
        mAudioSampleRateHz = b.mAudioSampleRateHz;
        mAudioSampleCount = b.mAudioSampleCount;
    }

    public int getSourceType() { return mSourceType; }
    public long getDurationMillis() { return mDurationMillis; }
    public long getClipDurationMillis() { return mClipDurationMillis; }
    public String getContainerMimeType() { return mContainerMimeType; }
    public List<String> getSampleMimeTypes() { return mSampleMimeTypes; }
    public List<String> getCodecNames() { return mCodecNames; }
    public long getDataTypes() { return mDataTypes; }
    public int getVideoDataSpace() { return mVideoDataSpace; }
    public Size getVideoSize() { return mVideoSize; }
    public float getVideoFrameRate() { return mVideoFrameRate; }
    public long getVideoSampleCount() { return mVideoSampleCount; }
    public int getAudioChannelCount() { return mAudioChannelCount; }
    public int getAudioSampleRateHz() { return mAudioSampleRateHz; }
    public long getAudioSampleCount() { return mAudioSampleCount; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSourceType);
        dest.writeLong(mDurationMillis);
        dest.writeLong(mClipDurationMillis);
        dest.writeString(mContainerMimeType);
        dest.writeStringList(new ArrayList<>(mSampleMimeTypes));
        dest.writeStringList(new ArrayList<>(mCodecNames));
        dest.writeLong(mDataTypes);
        dest.writeInt(mVideoDataSpace);
        dest.writeInt(mVideoSize == null ? VALUE_UNSPECIFIED : mVideoSize.getWidth());
        dest.writeInt(mVideoSize == null ? VALUE_UNSPECIFIED : mVideoSize.getHeight());
        dest.writeFloat(mVideoFrameRate);
        dest.writeLong(mVideoSampleCount);
        dest.writeInt(mAudioChannelCount);
        dest.writeInt(mAudioSampleRateHz);
        dest.writeLong(mAudioSampleCount);
    }

    @Override public String toString() {
        return "MediaItemInfo{ sourceType=" + mSourceType + ", dataTypes=" + mDataTypes + " }";
    }

    public static final Parcelable.Creator<MediaItemInfo> CREATOR =
            new Parcelable.Creator<MediaItemInfo>() {
        @Override public MediaItemInfo createFromParcel(Parcel in) {
            Builder b = new Builder();
            b.setSourceType(in.readInt());
            b.setDurationMillis(in.readLong());
            b.setClipDurationMillis(in.readLong());
            String container = in.readString();
            if (container != null) b.setContainerMimeType(container);
            List<String> sampleMimes = new ArrayList<>();
            in.readStringList(sampleMimes);
            for (String m : sampleMimes) b.addSampleMimeType(m);
            List<String> codecs = new ArrayList<>();
            in.readStringList(codecs);
            for (String c : codecs) b.addCodecName(c);
            b.mDataTypes = in.readLong();
            b.setVideoDataSpace(in.readInt());
            int w = in.readInt();
            int h = in.readInt();
            if (w != VALUE_UNSPECIFIED || h != VALUE_UNSPECIFIED) {
                b.setVideoSize(new Size(w, h));
            }
            b.setVideoFrameRate(in.readFloat());
            b.setVideoSampleCount(in.readLong());
            b.setAudioChannelCount(in.readInt());
            b.setAudioSampleRateHz(in.readInt());
            b.setAudioSampleCount(in.readLong());
            return b.build();
        }
        @Override public MediaItemInfo[] newArray(int size) { return new MediaItemInfo[size]; }
    };

    /** AOSP defaults: every scalar VALUE_UNSPECIFIED, no data types, empty
     *  mime/codec lists (MediaItemInfo.java, Builder). */
    public static final class Builder {
        private int mSourceType = SOURCE_TYPE_UNSPECIFIED;
        private long mDurationMillis = VALUE_UNSPECIFIED;
        private long mClipDurationMillis = VALUE_UNSPECIFIED;
        private String mContainerMimeType;
        private final List<String> mSampleMimeTypes = new ArrayList<>();
        private final List<String> mCodecNames = new ArrayList<>();
        private long mDataTypes;
        private int mVideoDataSpace = VALUE_UNSPECIFIED;
        private Size mVideoSize;
        private float mVideoFrameRate = VALUE_UNSPECIFIED;
        private long mVideoSampleCount = VALUE_UNSPECIFIED;
        private int mAudioChannelCount = VALUE_UNSPECIFIED;
        private int mAudioSampleRateHz = VALUE_UNSPECIFIED;
        private long mAudioSampleCount = VALUE_UNSPECIFIED;

        public Builder() { }

        public Builder setSourceType(int sourceType) { mSourceType = sourceType; return this; }
        public Builder setDurationMillis(long durationMillis) {
            mDurationMillis = durationMillis; return this;
        }
        public Builder setClipDurationMillis(long clipDurationMillis) {
            mClipDurationMillis = clipDurationMillis; return this;
        }
        public Builder setContainerMimeType(String containerMimeType) {
            mContainerMimeType = Objects.requireNonNull(containerMimeType);
            return this;
        }
        public Builder addSampleMimeType(String sampleMimeType) {
            mSampleMimeTypes.add(Objects.requireNonNull(sampleMimeType));
            return this;
        }
        public Builder addCodecName(String codecName) {
            mCodecNames.add(Objects.requireNonNull(codecName));
            return this;
        }
        public Builder addDataType(long dataType) { mDataTypes |= dataType; return this; }
        public Builder setVideoDataSpace(int videoDataSpace) {
            mVideoDataSpace = videoDataSpace; return this;
        }
        public Builder setVideoSize(Size videoSize) {
            mVideoSize = Objects.requireNonNull(videoSize); return this;
        }
        public Builder setVideoFrameRate(float videoFrameRate) {
            mVideoFrameRate = videoFrameRate; return this;
        }
        public Builder setVideoSampleCount(long videoSampleCount) {
            mVideoSampleCount = videoSampleCount; return this;
        }
        public Builder setAudioChannelCount(int audioChannelCount) {
            mAudioChannelCount = audioChannelCount; return this;
        }
        public Builder setAudioSampleRateHz(int audioSampleRateHz) {
            mAudioSampleRateHz = audioSampleRateHz; return this;
        }
        public Builder setAudioSampleCount(long audioSampleCount) {
            mAudioSampleCount = audioSampleCount; return this;
        }
        public MediaItemInfo build() { return new MediaItemInfo(this); }
    }
}
