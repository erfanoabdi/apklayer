// android.media.Ringtone -- plays a single ringtone/notification/alarm sound.
// AOSP frameworks/base media/java/android/media/Ringtone.java.
//
// Mac.apk backs playback with MediaPlayer when the ringtone Uri resolves to a
// decodable file; otherwise play()/stop() are no-ops. The state surface
// (isPlaying, set/getAudioAttributes, getTitle) is faithful so settings/alarm
// UIs that preview a ringtone run without crashing.
package android.media;

public class Ringtone {
    private final android.content.Context mContext;
    private android.net.Uri mUri;
    private AudioAttributes mAttributes =
        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();
    private boolean mLooping = false;
    private float mVolume = 1.0f;
    private MediaPlayer mPlayer;

    // Package-private: instances are obtained via RingtoneManager.getRingtone.
    Ringtone(android.content.Context context, boolean allowRemote) {
        mContext = context;
    }

    void setUri(android.net.Uri uri) { mUri = uri; }

    public void setStreamType(int streamType) {
        mAttributes = new AudioAttributes.Builder()
            .setLegacyStreamType(streamType).build();
    }
    public int getStreamType() { return mAttributes.getVolumeControlStream(); }
    public void setAudioAttributes(AudioAttributes attributes) {
        if (attributes != null) mAttributes = attributes;
    }
    public AudioAttributes getAudioAttributes() { return mAttributes; }
    public void setLooping(boolean looping) { mLooping = looping; }
    public boolean isLooping() { return mLooping; }
    public void setVolume(float volume) { mVolume = volume; }

    /**
     * API 29. The "haptic generator" derives a vibration waveform from the
     * ringtone's audio and plays it on the device vibrator. It is an audiofx
     * effect (AudioEffect.EFFECT_TYPE_HAPTIC_GENERATOR) attached to the
     * playback session, and this tree's audiofx layer has no effect engine
     * behind it (see AudioEffect's class comment) -- and a Mac has no vibrator
     * to drive either.
     *
     * So setHapticGeneratorEnabled returns FALSE ("could not be enabled"),
     * which is AOSP's own answer on a device where the effect cannot be
     * created, and isHapticGeneratorEnabled stays false to match. Recording
     * "enabled" here would make the two disagree and tell an app a haptic
     * track is playing when nothing is.
     */
    public boolean setHapticGeneratorEnabled(boolean enabled) { return false; }

    public boolean isHapticGeneratorEnabled() { return false; }

    /** AOSP declares finalize() so a dropped Ringtone stops playing instead of
     *  leaking its player. stop() is safe to call when nothing is playing. */
    @Override protected void finalize() throws Throwable {
        try { stop(); } finally { super.finalize(); }
    }
    public float getVolume() { return mVolume; }

    public String getTitle(android.content.Context context) {
        if (mUri == null) return "";
        String last = mUri.getLastPathSegment();
        return last != null ? last : mUri.toString();
    }

    public void play() {
        stop();
        if (mUri == null) return;
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(mContext, mUri);
            mp.setLooping(mLooping);
            mp.setVolume(mVolume, mVolume);
            mp.prepare();
            mp.start();
            mPlayer = mp;
        } catch (Throwable ignore) { mPlayer = null; }
    }
    public boolean isPlaying() {
        MediaPlayer mp = mPlayer;
        return mp != null && mp.isPlaying();
    }
    public void stop() {
        MediaPlayer mp = mPlayer;
        mPlayer = null;
        if (mp != null) {
            try { mp.stop(); } catch (Throwable ignore) {}
            try { mp.release(); } catch (Throwable ignore) {}
        }
    }
}
