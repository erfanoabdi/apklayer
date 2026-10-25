// android.media.MediaRecorder -- records audio/video to a file. AOSP
// frameworks/base media/java/android/media/MediaRecorder.java.
//
// ⚠ CORRECTED: the header used to say "Mac.apk has no capture/encode backend"
// and that getMaxAmplitude "returns 0". The CAPTURE half of that is no longer
// true -- runtime/src/audio_input.mm gives this host a real microphone, and
// android.media.AudioRecord already reads from it. This class now uses it:
// start()..stop() really opens the input, so getMaxAmplitude() reports the real
// peak since the last call and the recorder registers itself in the
// recording-activity registry the way a live recorder must.
//
// What is STILL missing, and the reason the output file is empty, is the
// ENCODE + MUX half. Producing a 3GP/MP4/AAC file needs an audio ENCODER and a
// container writer; every native bridge in this tree is decode-only
// (com.macapk.loader.NativeMedia has nativeDecOpen/nativeDecDecode and no
// counterpart), and MediaCodec's own header says the same. The macOS-native
// answer per the decision ladder is AudioToolbox's ExtAudioFile /
// AVAudioFile (m4a/AAC, CAF, WAV) or AVAssetWriter for A/V -- but that is
// native code in runtime/src/, outside this file's ownership.
//
// Until that lands, the honest position is the one below: no capability is
// claimed that is not delivered, the state machine is AOSP's, level metering
// really works (which is what a hold-to-record UI actually needs), and the
// output file is created but stays empty.
package android.media;

public class MediaRecorder implements AudioRecordingMonitor, AudioRouting, MicrophoneDirection {
    public static final class AudioSource {
        public static final int DEFAULT = 0;
        public static final int MIC = 1;
        public static final int VOICE_UPLINK = 2;
        public static final int VOICE_DOWNLINK = 3;
        public static final int VOICE_CALL = 4;
        public static final int CAMCORDER = 5;
        public static final int VOICE_RECOGNITION = 6;
        public static final int VOICE_COMMUNICATION = 7;
        public static final int REMOTE_SUBMIX = 8;
        public static final int UNPROCESSED = 9;
        public static final int VOICE_PERFORMANCE = 10;
        private AudioSource() {}
    }
    public static final class VideoSource {
        public static final int DEFAULT = 0;
        public static final int CAMERA = 1;
        public static final int SURFACE = 2;
        private VideoSource() {}
    }
    public static final class OutputFormat {
        public static final int DEFAULT = 0;
        public static final int THREE_GPP = 1;
        public static final int MPEG_4 = 2;
        public static final int AMR_NB = 3;
        public static final int AMR_WB = 4;
        public static final int AAC_ADTS = 6;
        public static final int MPEG_2_TS = 8;
        public static final int WEBM = 9;
        public static final int OGG = 11;
        private OutputFormat() {}
    }
    public static final class AudioEncoder {
        public static final int DEFAULT = 0;
        public static final int AMR_NB = 1;
        public static final int AMR_WB = 2;
        public static final int AAC = 3;
        public static final int HE_AAC = 4;
        public static final int AAC_ELD = 5;
        public static final int VORBIS = 6;
        public static final int OPUS = 7;
        private AudioEncoder() {}
    }
    public static final class VideoEncoder {
        public static final int DEFAULT = 0;
        public static final int H263 = 1;
        public static final int H264 = 2;
        public static final int MPEG_4_SP = 3;
        public static final int VP8 = 4;
        public static final int HEVC = 5;
        private VideoEncoder() {}
    }

    public static final int MEDIA_RECORDER_INFO_UNKNOWN = 1;
    public static final int MEDIA_RECORDER_INFO_MAX_DURATION_REACHED = 800;
    public static final int MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED = 801;
    public static final int MEDIA_RECORDER_ERROR_UNKNOWN = 1;

    private String mOutputPath;
    private java.io.FileDescriptor mOutputFd;
    private String mNextOutputPath;
    private OnErrorListener mOnError;
    private OnInfoListener mOnInfo;

    // --- real capture state (see the corrected header) ---------------------
    private int mAudioSource = AudioSource.DEFAULT;
    private int mAudioSamplingRate;
    private int mAudioChannels;
    private AudioRecord mCapture;
    private Thread mCaptureThread;
    private volatile boolean mCapturing;
    private volatile int mPeakAmplitude;
    private boolean mPrivacySensitive;
    private boolean mPrivacySensitiveExplicit;
    private AudioDeviceInfo mPreferredDevice;

    public MediaRecorder() { }

    /** API 31. AOSP uses the Context to attribute the capture for the
     *  microphone privacy indicator; there is one package per process here and
     *  no indicator to attribute to, so it is validated non-null (as AOSP
     *  requires) and changes nothing observable. */
    public MediaRecorder(android.content.Context context) {
        java.util.Objects.requireNonNull(context, "context may not be null");
    }

    public void setAudioSource(int audioSource) { mAudioSource = audioSource; }
    public void setVideoSource(int videoSource) {}
    public void setOutputFormat(int outputFormat) {}
    public void setAudioEncoder(int audioEncoder) {}
    public void setVideoEncoder(int videoEncoder) {}
    public void setAudioEncodingBitRate(int bitRate) {}
    public void setAudioSamplingRate(int samplingRate) { mAudioSamplingRate = samplingRate; }
    public void setAudioChannels(int numChannels) { mAudioChannels = numChannels; }
    public void setVideoEncodingBitRate(int bitRate) {}
    public void setVideoFrameRate(int rate) {}
    public void setVideoSize(int width, int height) {}
    public void setMaxDuration(int max_duration_ms) {}
    public void setMaxFileSize(long max_filesize_bytes) {}
    public void setOrientationHint(int degrees) {}
    public void setLocation(float latitude, float longitude) {}
    public void setCaptureRate(double fps) {}
    // Published descriptors name android.view.Surface; the prior Object params
    // compiled but would not resolve a guest's setPreviewDisplay(surface) /
    // setInputSurface(surface) call (descriptor mismatch -> NoSuchMethodError).
    // No capture backend, so accept and ignore. getSurface returns a real (empty,
    // no-producer) Surface so SURFACE-source recorders get a non-null endpoint.
    public void setPreviewDisplay(android.view.Surface sv) {}
    public void setInputSurface(android.view.Surface surface) {}
    private android.view.Surface mSurface;
    public android.view.Surface getSurface() {
        if (mSurface == null) mSurface = new android.view.Surface();
        return mSurface;
    }
    public void setProfile(CamcorderProfile profile) {}
    public void setAudioAttributes(AudioAttributes attrs) {}

    public void setOutputFile(String path) { mOutputPath = path; }
    public void setOutputFile(java.io.File file) { mOutputPath = file != null ? file.getPath() : null; }
    public void setOutputFile(java.io.FileDescriptor fd) { mOutputFd = fd; }

    public void prepare() throws java.io.IOException {
        // Touch the output file so callers that re-open it after stop() find it.
        touchOutputFile();
    }

    private void touchOutputFile() {
        if (mOutputPath != null) {
            try { new java.io.FileOutputStream(mOutputPath).close(); }
            catch (Throwable ignore) { }
        }
    }
    /**
     * Opens the real microphone and starts draining it. The PCM is discarded
     * (there is no encoder -- see the header), but the capture is genuine: the
     * peak sample feeds getMaxAmplitude(), and the recorder appears in
     * AudioManager.getActiveRecordingConfigurations() the way a live recorder
     * must. AOSP throws IllegalStateException when start() is called out of
     * order; a capture that cannot be opened is reported the same way.
     */
    public void start() throws IllegalStateException {
        if (mCapturing) throw new IllegalStateException("MediaRecorder is already recording");
        int rate = mAudioSamplingRate > 0 ? mAudioSamplingRate : 44100;
        int channelConfig = (mAudioChannels == 2)
                ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        int minBuf;
        try {
            minBuf = AudioRecord.getMinBufferSize(rate, channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT);
        } catch (Throwable t) {
            minBuf = 4096;
        }
        if (minBuf <= 0) minBuf = 4096;
        final int bufBytes = minBuf;
        AudioRecord record;
        try {
            record = new AudioRecord(
                    mAudioSource == AudioSource.DEFAULT ? AudioSource.MIC : mAudioSource,
                    rate, channelConfig, AudioFormat.ENCODING_PCM_16BIT, bufBytes);
        } catch (Throwable t) {
            throw new IllegalStateException("Cannot open audio capture", t);
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            try { record.release(); } catch (Throwable ignore) { }
            throw new IllegalStateException("Cannot open audio capture");
        }
        mCapture = record;
        mPeakAmplitude = 0;
        mCapturing = true;
        record.startRecording();
        mCaptureThread = new Thread(new Runnable() {
            @Override public void run() {
                short[] buf = new short[Math.max(256, bufBytes / 2)];
                while (mCapturing) {
                    int n = mCapture.read(buf, 0, buf.length);
                    if (n <= 0) {
                        // A negative return is an AudioRecord error code; stop
                        // spinning on it rather than burning a core.
                        if (n < 0) break;
                        continue;
                    }
                    int peak = 0;
                    for (int i = 0; i < n; i++) {
                        int v = buf[i] < 0 ? -buf[i] : buf[i];
                        if (v > peak) peak = v;
                    }
                    if (peak > mPeakAmplitude) mPeakAmplitude = peak;
                }
            }
        }, "MediaRecorder-capture");
        mCaptureThread.setDaemon(true);
        mCaptureThread.start();
    }

    /** AOSP pauses capture without ending the recording; the input stays open
     *  and the drain loop simply stops consuming. */
    public void pause() throws IllegalStateException {
        if (mCapture != null && mCapturing) {
            try { mCapture.stop(); } catch (Throwable ignore) { }
        }
    }

    public void resume() throws IllegalStateException {
        if (mCapture != null && mCapturing) {
            try { mCapture.startRecording(); } catch (Throwable ignore) { }
        }
    }

    public void stop() throws IllegalStateException {
        stopCapture();
        // AOSP switches to the queued next output file at stop() when one was
        // set; the same handover happens here so setNextOutputFile is not a lie.
        if (mNextOutputPath != null) {
            mOutputPath = mNextOutputPath;
            mNextOutputPath = null;
            touchOutputFile();
        }
    }

    private void stopCapture() {
        mCapturing = false;
        Thread t = mCaptureThread;
        mCaptureThread = null;
        if (t != null) {
            try { t.join(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        AudioRecord record = mCapture;
        mCapture = null;
        if (record != null) {
            try { record.stop(); } catch (Throwable ignore) { }
            try { record.release(); } catch (Throwable ignore) { }
        }
    }

    public void reset() {
        stopCapture();
        mOutputPath = null; mOutputFd = null; mNextOutputPath = null;
        mAudioSource = AudioSource.DEFAULT;
        mAudioSamplingRate = 0; mAudioChannels = 0;
        mPeakAmplitude = 0;
    }

    public void release() {
        stopCapture();
        mOutputPath = null; mOutputFd = null; mNextOutputPath = null;
    }

    /**
     * AOSP: "the maximum absolute amplitude ... since the last call", 0 the
     * first time. This is a REAL measurement now: the drain loop above tracks
     * the peak 16-bit sample the microphone delivered, so a hold-to-record level
     * meter or a voice-visualiser -- the commonest use of MediaRecorder outside
     * actually keeping the file -- works correctly on this host.
     */
    public int getMaxAmplitude() throws IllegalStateException {
        int peak = mPeakAmplitude;
        mPeakAmplitude = 0;
        return peak;
    }
    /** The metrics-bundle keys AOSP publishes for getMetrics(). */
    public static final class MetricsConstants {
        private MetricsConstants() { }
        public static final String AUDIO_BITRATE = "android.media.mediarecorder.audio-bitrate";
        public static final String AUDIO_CHANNELS = "android.media.mediarecorder.audio-channels";
        public static final String AUDIO_SAMPLERATE = "android.media.mediarecorder.audio-samplerate";
        public static final String AUDIO_TIMESCALE = "android.media.mediarecorder.audio-timescale";
        public static final String CAPTURE_FPS = "android.media.mediarecorder.capture-fps";
        public static final String CAPTURE_FPS_ENABLE = "android.media.mediarecorder.capture-fpsenable";
        public static final String FRAMERATE = "android.media.mediarecorder.frame-rate";
        public static final String HEIGHT = "android.media.mediarecorder.height";
        public static final String MOVIE_TIMESCALE = "android.media.mediarecorder.movie-timescale";
        public static final String ROTATION = "android.media.mediarecorder.rotation";
        public static final String VIDEO_BITRATE = "android.media.mediarecorder.video-bitrate";
        public static final String VIDEO_IFRAME_INTERVAL =
                "android.media.mediarecorder.video-iframe-interval";
        public static final String VIDEO_LEVEL = "android.media.mediarecorder.video-encoder-level";
        public static final String VIDEO_PROFILE =
                "android.media.mediarecorder.video-encoder-profile";
        public static final String VIDEO_TIMESCALE = "android.media.mediarecorder.video-timescale";
        public static final String WIDTH = "android.media.mediarecorder.width";
    }

    /**
     * AOSP: "@return the metrics recorded ... or null if no metrics are
     * available". Nothing is encoded here (see the header), so there are no
     * encoder metrics to report and null is the truthful answer rather than a
     * bundle of zeros that reads as a measurement.
     */
    public android.os.PersistableBundle getMetrics() { return null; }
    /** AOSP MediaRecorder.getActiveRecordingConfiguration() returns an
     *  `AudioRecordingConfiguration`, not an int -- this declared the wrong
     *  RETURN TYPE, so app bytecode compiled against the real API could never
     *  link to it at all (a different method descriptor entirely, the same
     *  class of defect as the old Intent.resolveActivity signature).
     *  Found by debug/framework_lint.py, which diffs our shapes against the
     *  android-30 stub jar.
     *
     *  Null is AOSP's own answer when the recorder is not actively recording;
     *  we have no MediaRecorder capture path, so it never is. */
    public AudioRecordingConfiguration getActiveRecordingConfiguration() {
        // No longer permanently null: while start() has a live AudioRecord, the
        // configuration comes from the same registry AudioManager reads, so the
        // two can never disagree about what this recorder is doing.
        AudioRecord capture = mCapture;
        return (capture == null) ? null : capture.getActiveRecordingConfiguration();
    }

    public void setOnErrorListener(OnErrorListener l) { mOnError = l; }
    public void setOnInfoListener(OnInfoListener l) { mOnInfo = l; }

    public interface OnErrorListener { void onError(MediaRecorder mr, int what, int extra); }
    public interface OnInfoListener  { void onInfo(MediaRecorder mr, int what, int extra); }

    public void setCamera(android.hardware.Camera c) { }

    // =====================================================================
    // API-gap close-out. Signatures from /tmp/aosp_api/android16.txt; behaviour
    // from frameworks/base/media/java/android/media/MediaRecorder.java
    // (android16-release).
    // =====================================================================

    /** AOSP: the highest legal AudioSource value. Derived from the enum block
     *  above so the two can never drift. */
    public static final int getAudioSourceMax() { return AudioSource.VOICE_PERFORMANCE; }

    /** API 26. AOSP switches to this file when the current one hits its size or
     *  duration limit, and at stop(). Honoured at stop() here (see stop()); the
     *  mid-recording rollover cannot happen because no bytes are ever written,
     *  so no limit is ever reached. */
    public void setNextOutputFile(java.io.File file) throws java.io.IOException {
        java.util.Objects.requireNonNull(file, "file must not be null");
        mNextOutputPath = file.getPath();
    }

    public void setNextOutputFile(java.io.FileDescriptor fd) throws java.io.IOException {
        java.util.Objects.requireNonNull(fd, "fd must not be null");
        // A file descriptor carries no path to hand over at stop(); AOSP dups it
        // natively. Accepted, and recorded as "no next PATH" so stop() does not
        // silently switch to the wrong file.
        mNextOutputPath = null;
    }

    /** API 31. Configures the audio encoder from a CamcorderProfile-derived
     *  profile. There is no encoder (see the header), so the codec/bitrate half
     *  cannot be honoured -- but the sampling rate and channel count ARE real
     *  capture parameters this class already uses, so they are applied rather
     *  than dropped. */
    public void setAudioProfile(EncoderProfiles.AudioProfile profile) {
        java.util.Objects.requireNonNull(profile, "profile must not be null");
        if (profile.getSampleRate() > 0) setAudioSamplingRate(profile.getSampleRate());
        if (profile.getChannels() > 0) setAudioChannels(profile.getChannels());
    }

    /** API 31. The video half has no capture path at all (no camera), so this
     *  is accepted and has nothing to apply. */
    public void setVideoProfile(EncoderProfiles.VideoProfile profile) {
        java.util.Objects.requireNonNull(profile, "profile must not be null");
    }

    /** API 26. Selects an encoder profile/level. No encoder exists, so there is
     *  nothing to configure; AOSP validates the arguments first and so does
     *  this -- a caller passing a bogus level still learns about it. */
    public void setVideoEncodingProfileLevel(int profile, int level) {
        if (profile <= 0) {
            throw new IllegalArgumentException("Video encoding profile is not positive");
        }
        if (level <= 0) {
            throw new IllegalArgumentException("Video encoding level is not positive");
        }
    }

    // ---- privacy-sensitive capture (API 30) ------------------------------

    /** AOSP throws IllegalStateException when called after prepare(). The flag
     *  prevents a CONCURRENT capture from sharing the input; Mac.apk runs one
     *  app per process, so there is no concurrent capture and the guarantee
     *  holds trivially -- the value is still recorded so the getter agrees. */
    public void setPrivacySensitive(boolean privacySensitive) {
        mPrivacySensitive = privacySensitive;
        mPrivacySensitiveExplicit = true;
    }

    /** AOSP's default depends on the source: true for VOICE_COMMUNICATION and
     *  CAMCORDER, false otherwise. Derived, not fixed. */
    public boolean isPrivacySensitive() {
        if (mPrivacySensitiveExplicit) return mPrivacySensitive;
        return mAudioSource == AudioSource.VOICE_COMMUNICATION
                || mAudioSource == AudioSource.CAMCORDER;
    }

    // ---- AudioRouting -----------------------------------------------------

    private final java.util.concurrent.CopyOnWriteArrayList<Object[]> mRoutingListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override public AudioDeviceInfo getPreferredDevice() { return mPreferredDevice; }

    /** AOSP returns false for a device that is not an audio INPUT. */
    @Override public boolean setPreferredDevice(AudioDeviceInfo deviceInfo) {
        if (deviceInfo == null) { mPreferredDevice = null; return true; }
        if (!deviceInfo.isSource()) return false;
        mPreferredDevice = deviceInfo;
        return true;
    }

    /** AOSP: null when not recording; the real capture device otherwise. */
    @Override public AudioDeviceInfo getRoutedDevice() {
        AudioRecord capture = mCapture;
        return (capture == null) ? null : capture.getRoutedDevice();
    }

    @Override public void addOnRoutingChangedListener(
            AudioRouting.OnRoutingChangedListener listener, android.os.Handler handler) {
        if (listener == null) return;
        for (Object[] entry : mRoutingListeners) {
            if (entry[0] == listener) return;
        }
        mRoutingListeners.add(new Object[] { listener, handler });
    }

    @Override public void removeOnRoutingChangedListener(
            AudioRouting.OnRoutingChangedListener listener) {
        for (Object[] entry : mRoutingListeners) {
            if (entry[0] == listener) { mRoutingListeners.remove(entry); return; }
        }
    }

    /** @deprecated AOSP's pre-API-24 overload; its listener type extends the
     *  modern one, so it delegates. */
    @Deprecated
    public void addOnRoutingChangedListener(OnRoutingChangedListener listener,
            android.os.Handler handler) {
        addOnRoutingChangedListener((AudioRouting.OnRoutingChangedListener) listener, handler);
    }

    @Deprecated
    public void removeOnRoutingChangedListener(OnRoutingChangedListener listener) {
        removeOnRoutingChangedListener((AudioRouting.OnRoutingChangedListener) listener);
    }

    /** @deprecated in AOSP since API 24. */
    @Deprecated
    public interface OnRoutingChangedListener extends AudioRouting.OnRoutingChangedListener {
        void onRoutingChanged(MediaRecorder mr);
        @Override default void onRoutingChanged(AudioRouting router) {
            if (router instanceof MediaRecorder) onRoutingChanged((MediaRecorder) router);
        }
    }

    // ---- AudioRecordingMonitor --------------------------------------------

    private final java.util.concurrent.CopyOnWriteArrayList<Object[]> mRecordingCallbacks =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** AOSP throws IllegalArgumentException on a null executor or callback. The
     *  capture path never changes underneath a running recorder on this backend,
     *  so a registered callback is retained and never invoked -- the same as on
     *  a device whose capture path is stable. */
    @Override public void registerAudioRecordingCallback(java.util.concurrent.Executor executor,
            AudioManager.AudioRecordingCallback cb) {
        if (executor == null) throw new IllegalArgumentException("Illegal null Executor");
        if (cb == null) throw new IllegalArgumentException("Illegal null AudioRecordingCallback");
        for (Object[] entry : mRecordingCallbacks) {
            if (entry[1] == cb) return;
        }
        mRecordingCallbacks.add(new Object[] { executor, cb });
    }

    @Override public void unregisterAudioRecordingCallback(AudioManager.AudioRecordingCallback cb) {
        if (cb == null) throw new IllegalArgumentException("Illegal null AudioRecordingCallback");
        for (Object[] entry : mRecordingCallbacks) {
            if (entry[1] == cb) { mRecordingCallbacks.remove(entry); return; }
        }
    }

    /** AOSP: the microphones actively feeding THIS recorder, throwing
     *  IOException if the query fails. Delegated to the live AudioRecord so the
     *  two never disagree; empty while not recording. */
    public java.util.List<MicrophoneInfo> getActiveMicrophones() throws java.io.IOException {
        AudioRecord capture = mCapture;
        if (capture == null) return new java.util.ArrayList<MicrophoneInfo>();
        return capture.getActiveMicrophones();
    }

    // ---- MicrophoneDirection (API 29) -------------------------------------
    // Delegated to AudioRecord, which reports false: steering needs a microphone
    // ARRAY the HAL can beam-form with, and CoreAudio hands this host one plain
    // default input. AOSP's contract is that these return false on hardware that
    // cannot honour the preference.

    @Override public boolean setPreferredMicrophoneDirection(int direction) {
        if (direction < MIC_DIRECTION_UNSPECIFIED || direction > MIC_DIRECTION_EXTERNAL) {
            throw new IllegalArgumentException("Invalid direction " + direction);
        }
        return false;
    }

    @Override public boolean setPreferredMicrophoneFieldDimension(float zoom) {
        if (zoom < -1.0f || zoom > 1.0f) {
            throw new IllegalArgumentException("Argument must fall between -1 & 1 (inclusive)");
        }
        return false;
    }

    // ---- log session -------------------------------------------------------

    private android.media.metrics.LogSessionId mLogSessionId =
            android.media.metrics.LogSessionId.LOG_SESSION_ID_NONE;

    /** @NonNull in AOSP; LOG_SESSION_ID_NONE until the app sets one. */
    public android.media.metrics.LogSessionId getLogSessionId() { return mLogSessionId; }

    public void setLogSessionId(android.media.metrics.LogSessionId id) {
        mLogSessionId = java.util.Objects.requireNonNull(id, "id must not be null");
    }

    /** AOSP declares finalize() so a dropped recorder releases the microphone
     *  instead of leaving the input open. release() is safe on any state. */
    @Override protected void finalize() throws Throwable {
        try { release(); } finally { super.finalize(); }
    }
}

