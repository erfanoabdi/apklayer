// android.speech.RecognitionPart (API 34) -- one word/segment of a recognition
// result, with its raw and formatted text, timing and confidence. AOSP:
// frameworks/base/core/java/android/speech/RecognitionPart.java
package android.speech;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public final class RecognitionPart implements Parcelable {
    /** Confidence level is not known / not provided. */
    public static final int CONFIDENCE_LEVEL_UNKNOWN = 0;
    public static final int CONFIDENCE_LEVEL_LOW = 1;
    public static final int CONFIDENCE_LEVEL_MEDIUM_LOW = 2;
    public static final int CONFIDENCE_LEVEL_MEDIUM = 3;
    public static final int CONFIDENCE_LEVEL_MEDIUM_HIGH = 4;
    public static final int CONFIDENCE_LEVEL_HIGH = 5;

    private final String mRawText;
    private final String mFormattedText;
    private final long mTimestampMillis;
    private final int mConfidenceLevel;

    private RecognitionPart(String rawText, String formattedText, long timestampMillis,
            int confidenceLevel) {
        mRawText = rawText;
        mFormattedText = formattedText;
        mTimestampMillis = timestampMillis;
        mConfidenceLevel = confidenceLevel;
    }

    /** The unformatted text of this part; never null. */
    public String getRawText() { return mRawText; }

    /** The formatted text (capitalisation, punctuation), or null if the
     *  service did not provide formatting. */
    public String getFormattedText() { return mFormattedText; }

    /** Milliseconds from the start of the audio session to this part. */
    public long getTimestampMillis() { return mTimestampMillis; }

    /** One of the CONFIDENCE_LEVEL_* constants. */
    public int getConfidenceLevel() { return mConfidenceLevel; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mRawText);
        dest.writeString(mFormattedText);
        dest.writeLong(mTimestampMillis);
        dest.writeInt(mConfidenceLevel);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecognitionPart)) return false;
        RecognitionPart that = (RecognitionPart) o;
        return mTimestampMillis == that.mTimestampMillis
                && mConfidenceLevel == that.mConfidenceLevel
                && Objects.equals(mRawText, that.mRawText)
                && Objects.equals(mFormattedText, that.mFormattedText);
    }

    @Override public int hashCode() {
        return Objects.hash(mRawText, mFormattedText, mTimestampMillis, mConfidenceLevel);
    }

    @Override public String toString() {
        return "RecognitionPart { rawText = " + mRawText
                + ", formattedText = " + mFormattedText
                + ", timestampMillis = " + mTimestampMillis
                + ", confidenceLevel = " + mConfidenceLevel + " }";
    }

    public static final Parcelable.Creator<RecognitionPart> CREATOR =
            new Parcelable.Creator<RecognitionPart>() {
        @Override public RecognitionPart createFromParcel(Parcel in) {
            String raw = in.readString();
            String formatted = in.readString();
            long ts = in.readLong();
            int conf = in.readInt();
            return new RecognitionPart(raw == null ? "" : raw, formatted, ts, conf);
        }
        @Override public RecognitionPart[] newArray(int size) { return new RecognitionPart[size]; }
    };

    /**
     * AOSP's builder. Note there is NO no-arg constructor: rawText is
     * mandatory and is supplied up front, which is why setRawText also exists
     * (it replaces the value given to the constructor).
     */
    public static final class Builder {
        private String mRawText;
        private String mFormattedText;
        private long mTimestampMillis;
        private int mConfidenceLevel = CONFIDENCE_LEVEL_UNKNOWN;

        public Builder(String rawText) {
            mRawText = Objects.requireNonNull(rawText);
        }

        public Builder setRawText(String rawText) {
            mRawText = Objects.requireNonNull(rawText);
            return this;
        }

        public Builder setFormattedText(String formattedText) {
            mFormattedText = Objects.requireNonNull(formattedText);
            return this;
        }

        public Builder setTimestampMillis(long timestampMillis) {
            mTimestampMillis = timestampMillis;
            return this;
        }

        public Builder setConfidenceLevel(int confidenceLevel) {
            mConfidenceLevel = confidenceLevel;
            return this;
        }

        public RecognitionPart build() {
            return new RecognitionPart(mRawText, mFormattedText, mTimestampMillis,
                    mConfidenceLevel);
        }
    }
}
