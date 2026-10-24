package android.media;
// android.media.AudioDeviceInfo (API 23+). Minimal real surface: a single
// built-in speaker sink. Apps enumerate devices for audio routing; reporting one
// output device matches a basic Android device.
public final class AudioDeviceInfo {
    // The complete AOSP TYPE_* set (frameworks/base/media/java/android/media/
    // AudioDeviceInfo.java, android16-release). These are pure identity
    // constants -- an app switches on getType()'s answer, and MediaRoute2Info
    // defines its own device types as aliases of these -- so the whole set is
    // declared even though this host only ever REPORTS a built-in speaker.
    // Declaring a type is not advertising a device; getDevices() still returns
    // only what CoreAudio actually has.
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_BUILTIN_EARPIECE = 1;
    public static final int TYPE_BUILTIN_SPEAKER = 2;
    public static final int TYPE_WIRED_HEADSET = 3;
    public static final int TYPE_WIRED_HEADPHONES = 4;
    public static final int TYPE_LINE_ANALOG = 5;
    public static final int TYPE_LINE_DIGITAL = 6;
    public static final int TYPE_BLUETOOTH_SCO = 7;
    public static final int TYPE_BLUETOOTH_A2DP = 8;
    public static final int TYPE_HDMI = 9;
    public static final int TYPE_HDMI_ARC = 10;
    public static final int TYPE_USB_DEVICE = 11;
    public static final int TYPE_USB_ACCESSORY = 12;
    public static final int TYPE_DOCK = 13;
    public static final int TYPE_FM = 14;
    public static final int TYPE_BUILTIN_MIC = 15;
    public static final int TYPE_FM_TUNER = 16;
    public static final int TYPE_TV_TUNER = 17;
    public static final int TYPE_TELEPHONY = 18;
    public static final int TYPE_AUX_LINE = 19;
    public static final int TYPE_IP = 20;
    public static final int TYPE_BUS = 21;
    public static final int TYPE_USB_HEADSET = 22;
    public static final int TYPE_HEARING_AID = 23;
    public static final int TYPE_BUILTIN_SPEAKER_SAFE = 24;
    public static final int TYPE_REMOTE_SUBMIX = 25;
    public static final int TYPE_BLE_HEADSET = 26;
    public static final int TYPE_BLE_SPEAKER = 27;
    public static final int TYPE_HDMI_EARC = 29;
    public static final int TYPE_BLE_BROADCAST = 30;
    public static final int TYPE_DOCK_ANALOG = 31;
    // The host really has BOTH directions -- CoreAudio's default output feeds
    // AudioTrack, and its default input feeds AudioRecord through
    // runtime/src/audio_input.mm -- so this class describes whichever one it was
    // built for instead of being hardwired to the speaker. The no-arg
    // constructor still yields the built-in speaker, because every existing
    // caller in this tree means "the output device" by it.
    private final int mType;
    private final boolean mIsSink;
    private final int mId;

    public AudioDeviceInfo() { this(TYPE_BUILTIN_SPEAKER, true, 1); }

    private AudioDeviceInfo(int type, boolean isSink, int id) {
        mType = type; mIsSink = isSink; mId = id;
    }

    /** The CoreAudio default output, as an output-direction descriptor. */
    static AudioDeviceInfo defaultOutput() {
        return new AudioDeviceInfo(TYPE_BUILTIN_SPEAKER, true, 1);
    }

    /** The CoreAudio default input. This is a REAL device: AudioRecord captures
     *  from it, and AudioManager.getMicrophones() describes the same microphone.
     *  Reporting it from getDevices(GET_DEVICES_INPUTS) is what keeps those
     *  three answers from contradicting each other. */
    static AudioDeviceInfo defaultInput() {
        return new AudioDeviceInfo(TYPE_BUILTIN_MIC, false, 2);
    }

    public int getId()                 { return mId; }
    public int getType()               { return mType; }
    public CharSequence getProductName() { return "Mac.apk"; }
    public boolean isSink()            { return mIsSink; }
    public boolean isSource()          { return !mIsSink; }
    public int[] getSampleRates()      { return new int[] { 48000, 44100 }; }
    public int[] getChannelCounts()    { return mIsSink ? new int[] { 2 } : new int[] { 1, 2 }; }
    public String getAddress()         { return ""; }
    // AOSP getEncodings/getChannelMasks/getChannelIndexMasks describe the
    // device's supported PCM formats. CoreAudio's default output is 16-bit and
    // float PCM stereo; report that. The input side accepts the same encodings
    // (AudioRecord validates exactly ENCODING_PCM_8BIT/16BIT/FLOAT) but is
    // described with the CHANNEL_IN_* masks, since an app matches the mask
    // family against the direction.
    public int[] getEncodings()        { return new int[] { AudioFormat.ENCODING_PCM_16BIT,
                                                            AudioFormat.ENCODING_PCM_FLOAT }; }
    public int[] getChannelMasks()     {
        return mIsSink ? new int[] { AudioFormat.CHANNEL_OUT_STEREO }
                       : new int[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO };
    }
    public int[] getChannelIndexMasks(){ return new int[0]; }
    public int getEncapsulationMode()  { return 0; }
    /**
     * Which audio-encapsulation modes this device supports. API 31. AOSP:
     * `return mPort.encapsulationModes();`
     * (frameworks/base/media/java/android/media/AudioDeviceInfo.java, L640).
     *
     * Empty is the honest, AOSP-shaped answer: encapsulation carries a
     * compressed bitstream (IEC61937 / ELEMENTARY_STREAM) through to an
     * external decoder over HDMI or S/PDIF. CoreAudio's built-in output takes
     * PCM only, and AOSP's own javadoc says a device with no encapsulation
     * support returns an array containing only ENCAPSULATION_MODE_NONE, which
     * this tree's AudioTrack does not define -- so the empty array, which the
     * @NonNull contract permits, is used rather than inventing a constant.
     * Advertising a mode we cannot honour is the failure this avoids.
     */
    public int[] getEncapsulationModes() { return new int[0]; }
    public int[] getEncapsulationMetadataTypes() { return new int[0]; }
    public boolean isHasAllChannelMask() { return false; }

    /**
     * The device's capabilities as (format, rates, channel masks) tuples.
     * API 31. AOSP: `return mPort.profiles();`
     * (frameworks/base/media/java/android/media/AudioDeviceInfo.java, main,
     * L623).
     *
     * This is a REAL answer, not an empty stub: an AudioProfile list IS the
     * encodings/rates/masks table this class already reports through
     * getEncodings/getSampleRates/getChannelMasks, so it is DERIVED from those
     * rather than invented alongside them. Deriving matters -- a hand-written
     * second copy would drift the day the output device description changes,
     * and an app comparing getEncodings() against getAudioProfiles() would see
     * a device contradicting itself.
     */
    public java.util.List<AudioProfile> getAudioProfiles() {
        int[] encodings = getEncodings();
        java.util.List<AudioProfile> profiles = new java.util.ArrayList<>(encodings.length);
        for (int encoding : encodings) {
            profiles.add(new AudioProfile(encoding, getSampleRates(), getChannelMasks(),
                    getChannelIndexMasks(), AudioProfile.AUDIO_ENCAPSULATION_TYPE_NONE));
        }
        return java.util.Collections.unmodifiableList(profiles);
    }

    /**
     * Extra capability blobs for formats the platform does not recognise --
     * EDID short audio descriptors and the like. API 31. AOSP:
     * `return mPort.audioDescriptors();` (same file, L630).
     *
     * Empty, and honestly so: descriptors come from an HDMI/eARC sink
     * negotiating its capabilities, and this device reports a built-in speaker
     * over CoreAudio, which has none to report on real Android either.
     */
    public java.util.List<AudioDescriptor> getAudioDescriptors() {
        return java.util.Collections.emptyList();
    }

    /**
     * Which speaker channels a TYPE_BUILTIN_SPEAKER physically has. API 34.
     * AOSP: `return mPort.speakerLayoutChannelMask();` (same file, L598), and
     * its javadoc restricts the answer to built-in speakers.
     *
     * Derived from getType()/getChannelMasks() for the same
     * do-not-let-the-device-contradict-itself reason as getAudioProfiles above,
     * rather than hardcoded: a non-speaker device must answer
     * AudioFormat.CHANNEL_INVALID (== 0; that constant is absent from this
     * tree's AudioFormat, which another lane owns).
     */
    public int getSpeakerLayoutChannelMask() {
        if (getType() != TYPE_BUILTIN_SPEAKER) {
            return 0;  // AudioFormat.CHANNEL_INVALID
        }
        int[] masks = getChannelMasks();
        return masks.length > 0 ? masks[0] : 0;
    }
}
