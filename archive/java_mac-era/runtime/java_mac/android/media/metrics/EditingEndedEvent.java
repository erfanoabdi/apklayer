package android.media.metrics;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The metrics report an editing/transcoding session emits when it finishes
 * (API 35). Pure app-supplied data; see MediaItemInfo and EditingSession.
 *
 * Source: frameworks/base/media/java/android/media/metrics/EditingEndedEvent.java
 * (android16-release).
 */
public final class EditingEndedEvent extends Event implements Parcelable {

    public static final int FINAL_STATE_SUCCEEDED = 1;
    public static final int FINAL_STATE_CANCELED = 2;
    public static final int FINAL_STATE_ERROR = 3;

    public static final int ERROR_CODE_NONE = 1;
    public static final int ERROR_CODE_FAILED_RUNTIME_CHECK = 2;
    public static final int ERROR_CODE_IO_UNSPECIFIED = 3;
    public static final int ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 4;
    public static final int ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 5;
    public static final int ERROR_CODE_IO_BAD_HTTP_STATUS = 6;
    public static final int ERROR_CODE_IO_FILE_NOT_FOUND = 7;
    public static final int ERROR_CODE_IO_NO_PERMISSION = 8;
    public static final int ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED = 9;
    public static final int ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 10;
    public static final int ERROR_CODE_DECODER_INIT_FAILED = 11;
    public static final int ERROR_CODE_DECODING_FAILED = 12;
    public static final int ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 13;
    public static final int ERROR_CODE_ENCODER_INIT_FAILED = 14;
    public static final int ERROR_CODE_ENCODING_FAILED = 15;
    public static final int ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED = 16;
    public static final int ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED = 17;
    public static final int ERROR_CODE_AUDIO_PROCESSING_FAILED = 18;
    public static final int ERROR_CODE_MUXING_FAILED = 19;

    public static final long OPERATION_TYPE_VIDEO_TRANSCODE = 1L;
    public static final long OPERATION_TYPE_AUDIO_TRANSCODE = 1L << 1;
    public static final long OPERATION_TYPE_VIDEO_EDIT = 1L << 2;
    public static final long OPERATION_TYPE_AUDIO_EDIT = 1L << 3;
    public static final long OPERATION_TYPE_VIDEO_TRANSMUX = 1L << 4;
    public static final long OPERATION_TYPE_AUDIO_TRANSMUX = 1L << 5;
    public static final long OPERATION_TYPE_PAUSED = 1L << 6;
    public static final long OPERATION_TYPE_RESUMED = 1L << 7;

    public static final int PROGRESS_PERCENT_UNKNOWN = -1;
    public static final int TIME_SINCE_CREATED_UNKNOWN = -1;

    private final int mFinalState;
    private final float mFinalProgressPercent;
    private final int mErrorCode;
    private final long mOperationTypes;
    private final String mExporterName;
    private final String mMuxerName;
    private final List<MediaItemInfo> mInputMediaItemInfos;
    private final MediaItemInfo mOutputMediaItemInfo;
    private final Bundle mMetricsBundle;

    private EditingEndedEvent(Builder b) {
        mFinalState = b.mFinalState;
        mFinalProgressPercent = b.mFinalProgressPercent;
        mErrorCode = b.mErrorCode;
        mOperationTypes = b.mOperationTypes;
        mExporterName = b.mExporterName;
        mMuxerName = b.mMuxerName;
        mInputMediaItemInfos =
                Collections.unmodifiableList(new ArrayList<>(b.mInputMediaItemInfos));
        mOutputMediaItemInfo = b.mOutputMediaItemInfo;
        mMetricsBundle = b.mMetricsBundle;
        mTimeSinceCreatedMillis = b.mTimeSinceCreatedMillis;
    }

    public int getFinalState() { return mFinalState; }
    public float getFinalProgressPercent() { return mFinalProgressPercent; }
    public int getErrorCode() { return mErrorCode; }
    public long getOperationTypes() { return mOperationTypes; }
    public String getExporterName() { return mExporterName; }
    public String getMuxerName() { return mMuxerName; }
    public List<MediaItemInfo> getInputMediaItemInfos() { return mInputMediaItemInfos; }
    public MediaItemInfo getOutputMediaItemInfo() { return mOutputMediaItemInfo; }

    @Override public Bundle getMetricsBundle() {
        return mMetricsBundle == null ? new Bundle() : mMetricsBundle;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFinalState);
        dest.writeFloat(mFinalProgressPercent);
        dest.writeInt(mErrorCode);
        dest.writeLong(mOperationTypes);
        dest.writeString(mExporterName);
        dest.writeString(mMuxerName);
        dest.writeTypedList(mInputMediaItemInfos);
        dest.writeTypedObject(mOutputMediaItemInfo, flags);
        dest.writeBundle(mMetricsBundle);
        dest.writeLong(mTimeSinceCreatedMillis);
    }

    @Override public String toString() {
        return "EditingEndedEvent{ finalState=" + mFinalState + ", errorCode=" + mErrorCode + " }";
    }

    public static final Parcelable.Creator<EditingEndedEvent> CREATOR =
            new Parcelable.Creator<EditingEndedEvent>() {
        @Override public EditingEndedEvent createFromParcel(Parcel in) {
            Builder b = new Builder(in.readInt());
            b.setFinalProgressPercent(in.readFloat());
            b.setErrorCode(in.readInt());
            b.mOperationTypes = in.readLong();
            String exporter = in.readString();
            if (exporter != null) b.setExporterName(exporter);
            String muxer = in.readString();
            if (muxer != null) b.setMuxerName(muxer);
            List<MediaItemInfo> inputs = in.createTypedArrayList(MediaItemInfo.CREATOR);
            if (inputs != null) {
                for (MediaItemInfo info : inputs) b.addInputMediaItemInfo(info);
            }
            MediaItemInfo output = in.readTypedObject(MediaItemInfo.CREATOR);
            if (output != null) b.setOutputMediaItemInfo(output);
            Bundle bundle = in.readBundle();
            if (bundle != null) b.setMetricsBundle(bundle);
            b.setTimeSinceCreatedMillis(in.readLong());
            return b.build();
        }
        @Override public EditingEndedEvent[] newArray(int size) {
            return new EditingEndedEvent[size];
        }
    };

    /** AOSP defaults: PROGRESS_PERCENT_UNKNOWN, ERROR_CODE_NONE, no operation
     *  types, TIME_SINCE_CREATED_UNKNOWN (EditingEndedEvent.java, Builder). */
    public static final class Builder {
        private final int mFinalState;
        private float mFinalProgressPercent = PROGRESS_PERCENT_UNKNOWN;
        private int mErrorCode = ERROR_CODE_NONE;
        private long mOperationTypes;
        private String mExporterName;
        private String mMuxerName;
        private final List<MediaItemInfo> mInputMediaItemInfos = new ArrayList<>();
        private MediaItemInfo mOutputMediaItemInfo;
        private Bundle mMetricsBundle;
        private long mTimeSinceCreatedMillis = TIME_SINCE_CREATED_UNKNOWN;

        public Builder(int finalState) { mFinalState = finalState; }

        /** AOSP requires 0..100 or PROGRESS_PERCENT_UNKNOWN. */
        public Builder setFinalProgressPercent(float finalProgressPercent) {
            if (finalProgressPercent != PROGRESS_PERCENT_UNKNOWN
                    && (finalProgressPercent < 0 || finalProgressPercent > 100)) {
                throw new IllegalArgumentException(
                        "finalProgressPercent must be between 0 and 100");
            }
            mFinalProgressPercent = finalProgressPercent;
            return this;
        }
        public Builder setErrorCode(int errorCode) { mErrorCode = errorCode; return this; }
        public Builder addOperationType(long operationType) {
            mOperationTypes |= operationType; return this;
        }
        public Builder setExporterName(String exporterName) {
            mExporterName = Objects.requireNonNull(exporterName); return this;
        }
        public Builder setMuxerName(String muxerName) {
            mMuxerName = Objects.requireNonNull(muxerName); return this;
        }
        public Builder addInputMediaItemInfo(MediaItemInfo info) {
            mInputMediaItemInfos.add(Objects.requireNonNull(info)); return this;
        }
        public Builder setOutputMediaItemInfo(MediaItemInfo info) {
            mOutputMediaItemInfo = Objects.requireNonNull(info); return this;
        }
        public Builder setMetricsBundle(Bundle metricsBundle) {
            mMetricsBundle = Objects.requireNonNull(metricsBundle); return this;
        }
        public Builder setTimeSinceCreatedMillis(long timeSinceCreatedMillis) {
            mTimeSinceCreatedMillis = timeSinceCreatedMillis; return this;
        }
        public EditingEndedEvent build() { return new EditingEndedEvent(this); }
    }
}
