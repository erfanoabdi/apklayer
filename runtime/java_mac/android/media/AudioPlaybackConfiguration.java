package android.media;
// android.media.AudioPlaybackConfiguration -- a snapshot of one active audio
// player's routing/attributes (API 26+). AOSP frameworks/base
// media/java/android/media/AudioPlaybackConfiguration.java. Mac.apk never
// surfaces live configurations (getActivePlaybackConfigurations returns empty),
// so this exists for the return type to resolve and for the few getters apps
// read off a configuration. Parcelable to match AOSP.
public final class AudioPlaybackConfiguration implements android.os.Parcelable {
    private final AudioAttributes mAttributes;
    public AudioPlaybackConfiguration() { this(new AudioAttributes()); }
    public AudioPlaybackConfiguration(AudioAttributes attrs) {
        mAttributes = attrs != null ? attrs : new AudioAttributes();
    }
    public AudioAttributes getAudioAttributes() { return mAttributes; }

    /** API 33. AOSP: "Return the audio device used for playback ... or null if
     *  the device is not available." There is one output on this host, and a
     *  configuration only exists while something is playing through it, so the
     *  built-in output IS the device -- and it is the same descriptor
     *  AudioManager.getDevices(GET_DEVICES_OUTPUTS) returns, so the two agree. */
    public AudioDeviceInfo getAudioDeviceInfo() { return AudioDeviceInfo.defaultOutput(); }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        mAttributes.writeToParcel(dest, flags);
    }
    public static final android.os.Parcelable.Creator<AudioPlaybackConfiguration> CREATOR =
        new android.os.Parcelable.Creator<AudioPlaybackConfiguration>() {
            @Override public AudioPlaybackConfiguration createFromParcel(android.os.Parcel in) {
                return new AudioPlaybackConfiguration(AudioAttributes.CREATOR.createFromParcel(in));
            }
            @Override public AudioPlaybackConfiguration[] newArray(int size) {
                return new AudioPlaybackConfiguration[size];
            }
        };
}
