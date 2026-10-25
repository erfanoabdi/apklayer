// android.media.PlaybackParams -- speed/pitch/fallback parameters for playback.
// AOSP frameworks/base media/java/android/media/PlaybackParams.java. A typed bag
// passed to MediaPlayer.setPlaybackParams / AudioTrack.setPlaybackParams.
//
// THE mSet BITMASK IS LOAD-BEARING, not bookkeeping. AOSP's JNI
// set_playback_params does a READ-MODIFY-WRITE against the track's current
// rate, gated per field on these flags (frameworks/base core/jni/
// android_media_AudioTrack.cpp:826-870, "to simulate partially set params, we
// do a read-modify-write"). Without the mask that behaviour is not expressible:
// an app that sets only speed would have its pitch silently reset to the
// default instead of left alone. The mask is also why AOSP's getters THROW
// IllegalStateException for a field nobody set -- a contract our previous
// version could not honour, because it had no way to tell "1.0 because the app
// asked for 1.0" from "1.0 because nobody said".
//
// Parcel order matches AOSP exactly (mSet, fallback, stretch, pitch, speed) --
// the previous version wrote four values and no mask, so a parcel round-trip
// between the two shapes would desynchronise.
package android.media;

public final class PlaybackParams implements android.os.Parcelable {
    public static final int AUDIO_FALLBACK_MODE_DEFAULT = 0;
    public static final int AUDIO_FALLBACK_MODE_MUTE = 1;
    public static final int AUDIO_FALLBACK_MODE_FAIL = 2;
    public static final int AUDIO_STRETCH_MODE_DEFAULT = 0;
    public static final int AUDIO_STRETCH_MODE_VOICE = 1;

    // Flags indicating which params were actually set (AOSP's own names/values).
    private static final int SET_SPEED               = 1 << 0;
    private static final int SET_PITCH               = 1 << 1;
    private static final int SET_AUDIO_FALLBACK_MODE = 1 << 2;
    private static final int SET_AUDIO_STRETCH_MODE  = 1 << 3;
    private int mSet = 0;

    private float mSpeed = 1.0f;
    private float mPitch = 1.0f;
    private int mAudioFallbackMode = AUDIO_FALLBACK_MODE_DEFAULT;
    private int mAudioStretchMode = AUDIO_STRETCH_MODE_DEFAULT;

    public PlaybackParams() {}
    private PlaybackParams(android.os.Parcel in) {
        mSet = in.readInt();
        mAudioFallbackMode = in.readInt();
        mAudioStretchMode = in.readInt();
        mPitch = in.readFloat();
        if (mPitch < 0.f) {
            mPitch = 0.f;      // AOSP clamps on the read side, not the write side
        }
        mSpeed = in.readFloat();
    }

    public PlaybackParams setSpeed(float speed) {
        mSpeed = speed;
        mSet |= SET_SPEED;
        return this;
    }
    /** @throws IllegalStateException if speed is not set (AOSP). */
    public float getSpeed() {
        if ((mSet & SET_SPEED) == 0) {
            throw new IllegalStateException("speed not set");
        }
        return mSpeed;
    }
    /** @throws IllegalArgumentException if the pitch is negative (AOSP). */
    public PlaybackParams setPitch(float pitch) {
        if (pitch < 0.f) {
            throw new IllegalArgumentException("pitch must not be negative");
        }
        mPitch = pitch;
        mSet |= SET_PITCH;
        return this;
    }
    /** @throws IllegalStateException if pitch is not set (AOSP). */
    public float getPitch() {
        if ((mSet & SET_PITCH) == 0) {
            throw new IllegalStateException("pitch not set");
        }
        return mPitch;
    }
    public PlaybackParams setAudioFallbackMode(int audioFallbackMode) {
        mAudioFallbackMode = audioFallbackMode;
        mSet |= SET_AUDIO_FALLBACK_MODE;
        return this;
    }
    /** @throws IllegalStateException if the audio fallback mode is not set. */
    public int getAudioFallbackMode() {
        if ((mSet & SET_AUDIO_FALLBACK_MODE) == 0) {
            throw new IllegalStateException("audio fallback mode not set");
        }
        return mAudioFallbackMode;
    }
    public PlaybackParams setAudioStretchMode(int audioStretchMode) {
        mAudioStretchMode = audioStretchMode;
        mSet |= SET_AUDIO_STRETCH_MODE;
        return this;
    }
    /** @throws IllegalStateException if the audio stretch mode is not set. */
    public int getAudioStretchMode() {
        if ((mSet & SET_AUDIO_STRETCH_MODE) == 0) {
            throw new IllegalStateException("audio stretch mode not set");
        }
        return mAudioStretchMode;
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mSet);
        dest.writeInt(mAudioFallbackMode);
        dest.writeInt(mAudioStretchMode);
        dest.writeFloat(mPitch);
        dest.writeFloat(mSpeed);
    }
    public static final android.os.Parcelable.Creator<PlaybackParams> CREATOR =
        new android.os.Parcelable.Creator<PlaybackParams>() {
            @Override public PlaybackParams createFromParcel(android.os.Parcel in) {
                return new PlaybackParams(in);
            }
            @Override public PlaybackParams[] newArray(int size) {
                return new PlaybackParams[size];
            }
        };

    // AOSP: "Allows defaults to be returned for properties not set."
    // Previously `return this` with no effect, which made it look harmless
    // while leaving every getter unable to distinguish set from unset.
    public PlaybackParams allowDefaults() {
        mSet |= SET_AUDIO_FALLBACK_MODE | SET_AUDIO_STRETCH_MODE | SET_PITCH | SET_SPEED;
        return this;
    }

    // ---- Mac.apk internals (package-private, NOT part of the Android API) ----
    // AudioTrack.setPlaybackParams needs to ask "did the caller set this
    // field?" and to read the raw value without the throwing getter, in order
    // to reproduce AOSP's per-field read-modify-write. AOSP's JNI reads the
    // private fields directly through gPlaybackParamsFields; these accessors
    // are the same access with a name.
    boolean macapkHasSpeed()  { return (mSet & SET_SPEED) != 0; }
    boolean macapkHasPitch()  { return (mSet & SET_PITCH) != 0; }
    boolean macapkHasFallbackMode() { return (mSet & SET_AUDIO_FALLBACK_MODE) != 0; }
    boolean macapkHasStretchMode()  { return (mSet & SET_AUDIO_STRETCH_MODE) != 0; }
    float macapkSpeedRaw() { return mSpeed; }
    float macapkPitchRaw() { return mPitch; }
    int   macapkFallbackModeRaw() { return mAudioFallbackMode; }
    int   macapkStretchModeRaw()  { return mAudioStretchMode; }
}
