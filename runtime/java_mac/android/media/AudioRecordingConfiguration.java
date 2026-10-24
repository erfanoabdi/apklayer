package android.media;
// android.media.AudioRecordingConfiguration -- a snapshot of one active capture
// client (API 24+). AOSP frameworks/base
// media/java/android/media/AudioRecordingConfiguration.java. Parcelable to
// match AOSP.
//
// Mac.apk DOES have a microphone source now (runtime/src/audio_input.mm backs
// android.media.AudioRecord with the Mac's real input device), so the old
// "there is no mic, this only exists for the return type to resolve" note here
// no longer holds. What is still missing is the REGISTRY: nothing tracks live
// AudioRecord instances, so AudioManager.getActiveRecordingConfigurations()
// keeps returning an empty list. That is a separate lane -- AudioManager.java
// is not this file's owner -- and the constructor below is the seam it needs:
// it carries the real client source and format instead of fabricating one.
public final class AudioRecordingConfiguration implements android.os.Parcelable {
    private final int mClientAudioSource;
    private final AudioFormat mClientFormat;
    private final int mClientSessionId;

    public AudioRecordingConfiguration() { this(MediaRecorder.AudioSource.MIC); }

    public AudioRecordingConfiguration(int clientAudioSource) {
        this(clientAudioSource, null);
    }

    // Preferred constructor once a registry exists: pass the AudioRecord's own
    // getFormat() so getClientFormat() reports what is actually being captured
    // rather than a default-constructed guess.
    public AudioRecordingConfiguration(int clientAudioSource, AudioFormat clientFormat) {
        // AudioRecord.getAudioSessionId() is hardcoded 0 today (no session-id
        // allocator exists), so 0 is the only value that can ever agree with
        // it -- see the 3-arg constructor below for the real seam.
        this(clientAudioSource, clientFormat, 0);
    }

    // AOSP's real constructor order is (uid, session, ...); this tree has no
    // uid concept for a single-app-per-process host, so session is threaded
    // through alone. Same seam pattern as the clientFormat parameter above:
    // AudioRecordingActivityMonitor.recorderCreated (android.media, not this
    // file's owner) still calls the 2-arg constructor today, so this overload
    // is dormant until that call site is updated to pass a real session id --
    // at which point getClientAudioSessionId() below becomes live with no
    // further edits here.
    public AudioRecordingConfiguration(int clientAudioSource, AudioFormat clientFormat,
            int clientSessionId) {
        mClientAudioSource = clientAudioSource;
        mClientFormat = clientFormat;
        mClientSessionId = clientSessionId;
    }

    public int getClientAudioSource() { return mClientAudioSource; }

    /** AOSP: "Returns the audio source being used for the recording." On a real
     *  device this is the source AFTER the audio policy has had its say, which
     *  can differ from the client's request; there is no policy layer here, so
     *  the two are the same value -- and reporting the client's source is the
     *  truthful answer for what is actually being captured. */
    public int getAudioSource() { return mClientAudioSource; }

    /**
     * AOSP: getEffects() is the effect chain the PLATFORM applied to this
     * capture, getClientEffects() the chain the CLIENT requested. Both are
     * @NonNull lists.
     *
     * Empty on both counts, and honestly so: android.media.audiofx here is a
     * present-but-inert layer with no effect engine behind it (see
     * AudioEffect's class comment), and nothing inserts an effect into the
     * capture path. Reporting an effect that is not running would tell an app
     * its echo canceller was active when the microphone signal is untouched --
     * exactly the kind of claim that fails later and harder than an honest
     * absence.
     */
    public java.util.List<android.media.audiofx.AudioEffect.Descriptor> getEffects() {
        return java.util.Collections.emptyList();
    }

    public java.util.List<android.media.audiofx.AudioEffect.Descriptor> getClientEffects() {
        return java.util.Collections.emptyList();
    }
    public AudioFormat getClientFormat() {
        return mClientFormat != null ? mClientFormat : new AudioFormat();
    }
    public AudioFormat getFormat() { return getClientFormat(); }
    public boolean isClientSilenced() { return false; }

    /** AOSP: "Returns the session number of the recording, see
     *  AudioRecord#getAudioSessionId()." Backed by mClientSessionId, which
     *  is 0 until a real session-id allocator exists -- matching
     *  AudioRecord.getAudioSessionId()'s own current, equally-hardcoded 0,
     *  so the two never disagree for a caller that cross-checks them. */
    public int getClientAudioSessionId() { return mClientSessionId; }

    /** AOSP: "Returns information about the audio input device used for this
     *  recording. @return the audio recording device or null if this
     *  information cannot be retrieved." AOSP resolves this from a native
     *  patch-handle lookup; this tree has no patch registry at all yet (see
     *  the class comment -- that registry is a separate lane, in
     *  AudioManager.java, not this file), so the honest answer today is
     *  always the documented null fallback, exactly as AOSP itself returns
     *  when "recording end[ed] already" and no patch can be found. */
    public AudioDeviceInfo getAudioDevice() { return null; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mClientAudioSource);
    }
    public static final android.os.Parcelable.Creator<AudioRecordingConfiguration> CREATOR =
        new android.os.Parcelable.Creator<AudioRecordingConfiguration>() {
            @Override public AudioRecordingConfiguration createFromParcel(android.os.Parcel in) {
                return new AudioRecordingConfiguration(in.readInt());
            }
            @Override public AudioRecordingConfiguration[] newArray(int size) {
                return new AudioRecordingConfiguration[size];
            }
        };
}
