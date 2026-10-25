package android.media;

/**
 * One (format, sample-rate, channel-mask) capability set of an audio device.
 *
 * Source: frameworks/base/media/java/android/media/AudioProfile.java
 */
public class AudioProfile implements android.os.Parcelable {

    public static final int AUDIO_ENCAPSULATION_TYPE_NONE = 0;
    public static final int AUDIO_ENCAPSULATION_TYPE_IEC61937 = 1;

    private final int mFormat;
    private final int[] mSamplingRates;
    private final int[] mChannelMasks;
    private final int[] mChannelIndexMasks;
    private final int mEncapsulationType;

    AudioProfile(int format, int[] samplingRates, int[] channelMasks,
                 int[] channelIndexMasks, int encapsulationType) {
        mFormat = format;
        mSamplingRates = samplingRates != null ? samplingRates : new int[0];
        mChannelMasks = channelMasks != null ? channelMasks : new int[0];
        mChannelIndexMasks = channelIndexMasks != null ? channelIndexMasks : new int[0];
        mEncapsulationType = encapsulationType;
    }

    public int getFormat() { return mFormat; }

    public int[] getSampleRates() { return mSamplingRates.clone(); }

    public int[] getChannelMasks() { return mChannelMasks.clone(); }

    public int[] getChannelIndexMasks() { return mChannelIndexMasks.clone(); }

    public int getEncapsulationType() { return mEncapsulationType; }

    // AOSP field order: format, samplingRates, channelMasks,
    // channelIndexMasks, encapsulationType (AudioProfile.java L197-210).
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mFormat);
        dest.writeIntArray(mSamplingRates);
        dest.writeIntArray(mChannelMasks);
        dest.writeIntArray(mChannelIndexMasks);
        dest.writeInt(mEncapsulationType);
    }

    public static final android.os.Parcelable.Creator<AudioProfile> CREATOR =
            new android.os.Parcelable.Creator<AudioProfile>() {
        @Override public AudioProfile createFromParcel(android.os.Parcel in) {
            int format = in.readInt();
            int[] samplingRates = in.createIntArray();
            int[] channelMasks = in.createIntArray();
            int[] channelIndexMasks = in.createIntArray();
            int encapsulationType = in.readInt();
            return new AudioProfile(format, samplingRates, channelMasks,
                    channelIndexMasks, encapsulationType);
        }
        @Override public AudioProfile[] newArray(int size) { return new AudioProfile[size]; }
    };
}
