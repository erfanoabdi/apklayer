package android.media;
// android.media.AudioAttributes -- describes how/why audio is played so the
// platform can route and duck it. Mirrors AOSP frameworks/base
// media/java/android/media/AudioAttributes.java: the prior stub built but
// dropped every value, so getUsage/getContentType returned nothing. We store
// the configured fields and expose the standard getters + Parcelable so apps
// that introspect an AudioAttributes (ExoPlayer, libGDX) read back what they
// set. Routing is a no-op (one CoreAudio output), which matches the field
// contract -- the attributes do not change the single output device.
public final class AudioAttributes implements android.os.Parcelable {
    public static final int CONTENT_TYPE_UNKNOWN     = 0;
    public static final int CONTENT_TYPE_SPEECH      = 1;
    public static final int CONTENT_TYPE_MUSIC       = 2;
    public static final int CONTENT_TYPE_MOVIE       = 3;
    public static final int CONTENT_TYPE_SONIFICATION = 4;

    public static final int USAGE_UNKNOWN            = 0;
    public static final int USAGE_MEDIA              = 1;
    public static final int USAGE_VOICE_COMMUNICATION = 2;
    public static final int USAGE_VOICE_COMMUNICATION_SIGNALLING = 3;
    public static final int USAGE_ALARM              = 4;
    public static final int USAGE_NOTIFICATION       = 5;
    public static final int USAGE_NOTIFICATION_RINGTONE = 6;
    public static final int USAGE_NOTIFICATION_COMMUNICATION_REQUEST = 7;
    public static final int USAGE_NOTIFICATION_COMMUNICATION_INSTANT = 8;
    public static final int USAGE_NOTIFICATION_COMMUNICATION_DELAYED = 9;
    public static final int USAGE_NOTIFICATION_EVENT = 10;
    public static final int USAGE_ASSISTANCE_ACCESSIBILITY = 11;
    public static final int USAGE_ASSISTANCE_NAVIGATION_GUIDANCE = 12;
    public static final int USAGE_ASSISTANCE_SONIFICATION = 13;
    public static final int USAGE_GAME               = 14;
    public static final int USAGE_ASSISTANT          = 16;

    public static final int FLAG_AUDIBILITY_ENFORCED = 0x1;
    public static final int FLAG_HW_AV_SYNC          = 0x10;
    public static final int FLAG_LOW_LATENCY         = 0x100;

    public static final int ALLOW_CAPTURE_BY_ALL     = 1;
    public static final int ALLOW_CAPTURE_BY_SYSTEM  = 2;
    public static final int ALLOW_CAPTURE_BY_NONE    = 3;

    // Spatialization behaviour, API 32. AOSP frameworks/base
    // media/java/android/media/AudioAttributes.java (main) L715, L723.
    public static final int SPATIALIZATION_BEHAVIOR_AUTO  = 0;
    public static final int SPATIALIZATION_BEHAVIOR_NEVER = 1;

    private final int mUsage;
    private final int mContentType;
    private final int mFlags;
    private final int mLegacyStreamType;
    // AOSP stores this as a bit (FLAG_MUTE_HAPTIC, @hide) folded into mFlags,
    // masked back out of the public getFlags() (which returns mFlags &
    // FLAG_ALL_PUBLIC). Our getFlags() below returns mFlags unfiltered, so
    // folding the bit in would leak a hidden flag through a public getter;
    // kept as its own field instead. AOSP default: muted=true (Builder's
    // mMuteHapticChannels starts true) unless the app opts out.
    private final boolean mMuteHapticChannels;
    // AOSP encodes these three in HIDDEN mFlags bits (FLAG_NO_SYSTEM_CAPTURE,
    // FLAG_NO_MEDIA_PROJECTION, FLAG_NEVER_SPATIALIZE, FLAG_CONTENT_SPATIALIZED)
    // and masks them back out of the public getFlags(). This file's getFlags()
    // returns mFlags unfiltered (see the note on mMuteHapticChannels above), so
    // folding them in would leak hidden flags through a public getter -- they
    // get their own fields for exactly the reason mMuteHapticChannels does.
    // The OBSERVABLE contract is identical either way: the getters below return
    // what the Builder was told, which is what an app that sets and re-reads
    // (ExoPlayer does) depends on.
    private final int mCapturePolicy;
    private final int mSpatializationBehavior;
    private final boolean mIsContentSpatialized;

    public AudioAttributes() {
        this(USAGE_UNKNOWN, CONTENT_TYPE_UNKNOWN, 0, -1, true,
             ALLOW_CAPTURE_BY_ALL, SPATIALIZATION_BEHAVIOR_AUTO, false);
    }
    private AudioAttributes(int usage, int contentType, int flags, int legacyStream,
            boolean muteHapticChannels, int capturePolicy, int spatializationBehavior,
            boolean isContentSpatialized) {
        mUsage = usage;
        mContentType = contentType;
        mFlags = flags;
        mLegacyStreamType = legacyStream;
        mMuteHapticChannels = muteHapticChannels;
        mCapturePolicy = capturePolicy;
        mSpatializationBehavior = spatializationBehavior;
        mIsContentSpatialized = isContentSpatialized;
    }

    public int getUsage()       { return mUsage; }
    public int getContentType() { return mContentType; }
    public int getFlags()       { return mFlags; }
    // AOSP: whether haptic channels embedded in the audio stream are muted.
    public boolean areHapticChannelsMuted() { return mMuteHapticChannels; }

    /**
     * Who may capture audio played with these attributes. API 29. AOSP
     * (AudioAttributes.java L740-750) decodes it from the hidden flags and
     * falls through to ALLOW_CAPTURE_BY_ALL, which is the default here too.
     *
     * A REAL round-trip of what Builder.setAllowedCapturePolicy was given --
     * previously that setter silently DROPPED its argument, so an app that set
     * ALLOW_CAPTURE_BY_NONE and read it back was told ALL. This does not make
     * Mac.apk enforce the policy (nothing here captures playback), but
     * misreporting an app's own stated intent is a different and worse failure
     * than not enforcing it: an app that checks its own attributes before
     * starting a sensitive stream would take the wrong branch.
     */
    public int getAllowedCapturePolicy() { return mCapturePolicy; }

    /**
     * Whether this content may be spatialized. API 32. AOSP L729-732 derives it
     * from the hidden FLAG_NEVER_SPATIALIZE; default AUTO.
     */
    public int getSpatializationBehavior() { return mSpatializationBehavior; }

    /**
     * Whether the content is ALREADY binaural/transaural, so the platform must
     * not process it again. API 32. AOSP L697-699. Default false.
     */
    public boolean isContentSpatialized() { return mIsContentSpatialized; }
    // AOSP getVolumeControlStream maps the attributes back to a legacy stream;
    // we keep whatever setLegacyStreamType stored, else STREAM_MUSIC.
    public int getVolumeControlStream() {
        return mLegacyStreamType >= 0 ? mLegacyStreamType : AudioManager.STREAM_MUSIC;
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mUsage);
        dest.writeInt(mContentType);
        dest.writeInt(mFlags);
        dest.writeInt(mLegacyStreamType);
        dest.writeInt(mMuteHapticChannels ? 1 : 0);
        dest.writeInt(mCapturePolicy);
        dest.writeInt(mSpatializationBehavior);
        dest.writeInt(mIsContentSpatialized ? 1 : 0);
    }
    public static final android.os.Parcelable.Creator<AudioAttributes> CREATOR =
        new android.os.Parcelable.Creator<AudioAttributes>() {
            @Override public AudioAttributes createFromParcel(android.os.Parcel in) {
                // Read order must match writeToParcel exactly; the three new
                // fields are appended so an AudioAttributes written by this
                // build reads back identical.
                return new AudioAttributes(in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                    in.readInt() != 0, in.readInt(), in.readInt(), in.readInt() != 0);
            }
            @Override public AudioAttributes[] newArray(int size) {
                return new AudioAttributes[size];
            }
        };

    @Override public boolean equals(Object o) {
        if (!(o instanceof AudioAttributes)) return false;
        AudioAttributes a = (AudioAttributes) o;
        // The three new fields participate: AOSP folds their flags into mFlags,
        // which its equals() already compares, so leaving them out here would
        // make two attributes with different capture policies compare equal --
        // a divergence an app using them as map keys would see.
        return a.mUsage == mUsage && a.mContentType == mContentType
            && a.mFlags == mFlags && a.mLegacyStreamType == mLegacyStreamType
            && a.mMuteHapticChannels == mMuteHapticChannels
            && a.mCapturePolicy == mCapturePolicy
            && a.mSpatializationBehavior == mSpatializationBehavior
            && a.mIsContentSpatialized == mIsContentSpatialized;
    }
    @Override public int hashCode() {
        int h = (((mUsage * 31 + mContentType) * 31 + mFlags) * 31 + mLegacyStreamType) * 31
            + (mMuteHapticChannels ? 1 : 0);
        h = h * 31 + mCapturePolicy;
        h = h * 31 + mSpatializationBehavior;
        h = h * 31 + (mIsContentSpatialized ? 1 : 0);
        return h;
    }

    public static class Builder {
        private int usage = USAGE_UNKNOWN;
        private int contentType = CONTENT_TYPE_UNKNOWN;
        private int flags = 0;
        private int legacyStream = -1;
        private boolean muteHapticChannels = true;
        // AOSP defaults: ALLOW_CAPTURE_BY_ALL (no capture flags set),
        // SPATIALIZATION_BEHAVIOR_AUTO, and not-already-spatialized.
        private int capturePolicy = ALLOW_CAPTURE_BY_ALL;
        private int spatializationBehavior = SPATIALIZATION_BEHAVIOR_AUTO;
        private boolean isContentSpatialized = false;
        public Builder() {}
        public Builder(AudioAttributes aa) {
            if (aa != null) {
                usage = aa.mUsage; contentType = aa.mContentType;
                flags = aa.mFlags; legacyStream = aa.mLegacyStreamType;
                muteHapticChannels = aa.mMuteHapticChannels;
                capturePolicy = aa.mCapturePolicy;
                spatializationBehavior = aa.mSpatializationBehavior;
                isContentSpatialized = aa.mIsContentSpatialized;
            }
        }
        public Builder setUsage(int u) { usage = u; return this; }
        public Builder setContentType(int t) { contentType = t; return this; }
        public Builder setFlags(int f) { flags |= f; return this; }
        public Builder setLegacyStreamType(int streamType) { legacyStream = streamType; return this; }
        /** AOSP rejects anything outside the three ALLOW_CAPTURE_BY_* values
         *  with IllegalArgumentException rather than storing it, so a typo
         *  fails at the setter instead of much later. */
        public Builder setAllowedCapturePolicy(int capturePolicy) {
            if (capturePolicy != ALLOW_CAPTURE_BY_ALL
                    && capturePolicy != ALLOW_CAPTURE_BY_SYSTEM
                    && capturePolicy != ALLOW_CAPTURE_BY_NONE) {
                throw new IllegalArgumentException("Invalid allowed capture policy "
                        + capturePolicy);
            }
            this.capturePolicy = capturePolicy;
            return this;
        }
        public Builder setSpatializationBehavior(int sb) {
            if (sb != SPATIALIZATION_BEHAVIOR_AUTO && sb != SPATIALIZATION_BEHAVIOR_NEVER) {
                throw new IllegalArgumentException("Invalid spatialization behavior " + sb);
            }
            spatializationBehavior = sb;
            return this;
        }
        public Builder setIsContentSpatialized(boolean spatialized) {
            isContentSpatialized = spatialized;
            return this;
        }
        // AOSP default true (haptic channels muted unless the app opts out).
        public Builder setHapticChannelsMuted(boolean muted) { muteHapticChannels = muted; return this; }
        public AudioAttributes build() {
            return new AudioAttributes(usage, contentType, flags, legacyStream, muteHapticChannels,
                capturePolicy, spatializationBehavior, isContentSpatialized);
        }
    }
}
