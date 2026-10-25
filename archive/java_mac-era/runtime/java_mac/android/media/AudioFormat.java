package android.media;
public class AudioFormat implements android.os.Parcelable {
    public static final int CHANNEL_OUT_MONO        = 0x4;
    public static final int CHANNEL_OUT_STEREO      = 0xc;
    public static final int CHANNEL_IN_MONO         = 0x10;
    public static final int CHANNEL_IN_STEREO       = 0xc;
    public static final int ENCODING_PCM_8BIT       = 3;
    public static final int ENCODING_PCM_16BIT      = 2;
    public static final int ENCODING_PCM_FLOAT      = 4;
    public static final int ENCODING_INVALID        = 0;
    private final int encoding;
    private final int sampleRate;
    private final int channelMask;
    private final int channelIndexMask;
    public AudioFormat() { this(ENCODING_PCM_16BIT, 44100, CHANNEL_OUT_STEREO, 0); }
    private AudioFormat(int encoding, int sampleRate, int channelMask) {
        this(encoding, sampleRate, channelMask, 0);
    }
    private AudioFormat(int encoding, int sampleRate, int channelMask, int channelIndexMask) {
        this.encoding = encoding;
        this.sampleRate = sampleRate;
        this.channelMask = channelMask;
        this.channelIndexMask = channelIndexMask;
    }
    public int getEncoding()     { return encoding; }
    public int getSampleRate()   { return sampleRate; }
    public int getChannelMask()  { return channelMask; }
    /** AOSP: the INDEX mask, 0 when the format was built with a positional
     *  mask instead. Really tracked now -- it used to be hardcoded 0, so an app
     *  that built a format with setChannelIndexMask() and read it back was told
     *  its own setting had been dropped. */
    public int getChannelIndexMask() { return channelIndexMask; }
    /** AOSP counts the positional mask's bits, falling back to the index mask
     *  when only that one was set (AudioFormat.java, getChannelCount). */
    public int getChannelCount() {
        if (channelMask != 0) return Integer.bitCount(channelMask);
        return Integer.bitCount(channelIndexMask);
    }
    // AOSP getFrameSizeInBytes = bytesPerSample(encoding) * channelCount, with a
    // floor of 1 (mirrors AudioFormat.getBytesPerSample / getFrameSizeInBytes).
    public int getFrameSizeInBytes() {
        int bytesPerSample;
        switch (encoding) {
            case ENCODING_PCM_8BIT:  bytesPerSample = 1; break;
            case ENCODING_PCM_FLOAT: bytesPerSample = 4; break;
            case ENCODING_PCM_16BIT: bytesPerSample = 2; break;
            default:                 bytesPerSample = 1; break;
        }
        int ch = getChannelCount();
        return Math.max(1, bytesPerSample * (ch > 0 ? ch : 1));
    }
    // ---- Parcelable ----------------------------------------------------
    // AOSP: media/java/android/media/AudioFormat.java (android16-release),
    // whose writeToParcel emits a property-mask int followed by the three
    // properties that mask declares set. We track exactly encoding, sampleRate
    // and channelMask and always have all three, so the mask is constant and
    // the three ints are written unconditionally.
    //
    // AudioFormat crosses a Parcel constantly on a device: AudioAttributes
    // pairs, AudioDeviceInfo, MediaFormat conversions, and any app that stores
    // its chosen output format in onSaveInstanceState. Bundle.getParcelable in
    // this runtime returns null for a non-Parcelable value the app was allowed
    // to put (unchecked under VM-wide -Xverify:none), so the format came back
    // null and the app fell to a default sample rate with no error anywhere.
    private AudioFormat(android.os.Parcel source) {
        this.encoding    = source.readInt();
        this.sampleRate  = source.readInt();
        this.channelMask = source.readInt();
        this.channelIndexMask = source.readInt();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(encoding);
        dest.writeInt(sampleRate);
        dest.writeInt(channelMask);
        dest.writeInt(channelIndexMask);
    }

    public static final android.os.Parcelable.Creator<AudioFormat> CREATOR =
            new android.os.Parcelable.Creator<AudioFormat>() {
        @Override public AudioFormat createFromParcel(android.os.Parcel s) {
            return new AudioFormat(s);
        }
        @Override public AudioFormat[] newArray(int size) {
            return new AudioFormat[size];
        }
    };

    // AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
    // mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
    // app compiled against real Android that subclasses this fails to resolve
    // against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
    public static class Builder {
        private int encoding   = ENCODING_PCM_16BIT;
        private int sampleRate = 44100;
        private int channelMask = CHANNEL_OUT_STEREO;
        private int channelIndexMask = 0;
        public Builder() { }
        /** AOSP: "Constructs a new Builder from a given AudioFormat" -- a copy
         *  constructor apps use to tweak one field of a format they were handed
         *  (AudioFormat.java, Builder(AudioFormat)). */
        public Builder(AudioFormat af) {
            if (af != null) {
                encoding = af.encoding;
                sampleRate = af.sampleRate;
                channelMask = af.channelMask;
                channelIndexMask = af.channelIndexMask;
            }
        }
        public Builder setEncoding(int e)     { encoding = e; return this; }
        public Builder setSampleRate(int s)   { sampleRate = s; return this; }
        /** AOSP: setting the positional mask CLEARS any index mask, because the
         *  two are alternative descriptions of the same channel layout
         *  (AudioFormat.java, setChannelMask). */
        public Builder setChannelMask(int m)  {
            channelMask = m;
            channelIndexMask = 0;
            return this;
        }
        /** API 23. The index-based layout (bit N = channel N), used for
         *  multichannel content that has no positional name. AOSP clears the
         *  positional mask in the same way. */
        public Builder setChannelIndexMask(int m) {
            channelIndexMask = m;
            channelMask = 0;
            return this;
        }
        public AudioFormat build() {
            return new AudioFormat(encoding, sampleRate, channelMask, channelIndexMask);
        }
    }
}
