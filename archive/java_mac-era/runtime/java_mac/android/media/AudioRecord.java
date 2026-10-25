// android.media.AudioRecord -- real PCM capture from the Mac's microphone.
// AOSP frameworks/base media/java/android/media/AudioRecord.java (android11).
//
// Backed by the macOS CoreAudio capture engine (runtime/src/audio_input.mm)
// through com.macapk.loader.AudioInputBridge. Each AudioRecord owns one capture
// stream: an AVAudioEngine input tap feeding an AVAudioConverter (sample-rate,
// channel-count and interleave conversion) and then a ring buffer that read()
// drains in the app's requested encoding.
//
// WHAT CHANGED AND WHY IT MATTERS. This class used to report STATE_INITIALIZED
// unconditionally and zero-fill every read(). An app therefore recorded happily,
// got a valid frame count back, and ended up with a file of pure silence and no
// error to point at. That is worse than an honest failure. The rule now:
//
//   getState() == STATE_INITIALIZED  IFF  a real capture stream is live.
//
// If the microphone is missing, or macOS denied access, the native open returns
// 0 and this object stays STATE_UNINITIALIZED -- so every read() returns
// ERROR_INVALID_OPERATION exactly as AOSP does for an uninitialized recorder
// (AudioRecord.java:1678), startRecording() throws IllegalStateException
// (:1560), and Builder.build() throws UnsupportedOperationException (:2044).
// Those are the same failures an Android app sees when RECORD_AUDIO was not
// granted, so an app's existing error path handles it.
package android.media;

public class AudioRecord implements AudioRecordingMonitor, AudioRouting, MicrophoneDirection {
    public static final int STATE_UNINITIALIZED = 0;
    public static final int STATE_INITIALIZED = 1;
    public static final int RECORDSTATE_STOPPED = 1;
    public static final int RECORDSTATE_RECORDING = 3;
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int ERROR_BAD_VALUE = -2;
    public static final int ERROR_INVALID_OPERATION = -3;
    public static final int ERROR_DEAD_OBJECT = -6;
    public static final int READ_BLOCKING = 0;
    public static final int READ_NON_BLOCKING = 1;

    // AOSP AudioFormat constants this class needs that our AudioFormat shadow
    // does not (yet) declare. Values pinned from AOSP AudioFormat.java
    // (android11-release): CHANNEL_IN_DEFAULT:492, CHANNEL_IN_FRONT:496,
    // CHANNEL_IN_BACK:497, CHANNEL_CONFIGURATION_MONO:343,
    // CHANNEL_CONFIGURATION_STEREO:346, CHANNEL_INVALID:349,
    // ENCODING_DEFAULT:228, SAMPLE_RATE_HZ_MIN:416, SAMPLE_RATE_HZ_MAX:422,
    // SAMPLE_RATE_UNSPECIFIED:427. They are literals here rather than added to
    // AudioFormat because that file belongs to another lane.
    private static final int CHANNEL_IN_DEFAULT = 1;
    private static final int CHANNEL_IN_FRONT = 0x10;
    private static final int CHANNEL_IN_BACK = 0x20;
    private static final int CHANNEL_IN_FRONT_BACK = CHANNEL_IN_FRONT | CHANNEL_IN_BACK;
    private static final int CHANNEL_CONFIGURATION_MONO = 2;
    private static final int CHANNEL_CONFIGURATION_STEREO = 3;
    private static final int CHANNEL_INVALID = 0;
    private static final int ENCODING_DEFAULT = 1;
    private static final int SAMPLE_RATE_HZ_MIN = 4000;
    private static final int SAMPLE_RATE_HZ_MAX = 192000;
    private static final int SAMPLE_RATE_UNSPECIFIED = 0;

    // AOSP MediaRecorder.getAudioSourceMax() -- the highest ordinary capture
    // preset (VOICE_PERFORMANCE). Inlined because our MediaRecorder shadow does
    // not declare the accessor.
    private static final int AUDIO_SOURCE_MAX = 10;

    private final long mHandle;          // 0 == no live capture stream
    private final int mSampleRate;
    private final int mChannelCount;
    private final int mAudioFormat;
    private final int mRecordSource;
    private final int mBufferSizeInBytes;
    private int mState = STATE_UNINITIALIZED;
    private int mRecordingState = RECORDSTATE_STOPPED;
    private final Object mRecordingStateLock = new Object();
    // Cumulative frames delivered to a caller via read() since the most
    // recent startRecording() -- backs getTimestamp()'s framePosition. AOSP:
    // "Calling startRecording() following a stop() will reset the frame
    // count to 0." Atomic because read() is not otherwise synchronized
    // against state transitions (matching this class's existing lock-free
    // read() path; see startRecording()/read() below).
    private final java.util.concurrent.atomic.AtomicLong mFramesDelivered =
            new java.util.concurrent.atomic.AtomicLong(0);

    public AudioRecord(int audioSource, int sampleRateInHz, int channelConfig,
                       int audioFormat, int bufferSizeInBytes) {
        // AOSP resolves the legacy channel config first (allowLegacyConfig =
        // true for this public constructor), then validates source/rate/format,
        // then the buffer size. Order matters: apps assert on which exception
        // they get for which bad argument.
        int channelMask = getChannelMaskFromLegacyConfig(channelConfig, true);
        mChannelCount = Integer.bitCount(channelMask);

        if ((audioSource < MediaRecorder.AudioSource.DEFAULT)
                || (audioSource > AUDIO_SOURCE_MAX)) {
            throw new IllegalArgumentException("Invalid audio source " + audioSource);
        }
        mRecordSource = audioSource;

        if ((sampleRateInHz < SAMPLE_RATE_HZ_MIN || sampleRateInHz > SAMPLE_RATE_HZ_MAX)
                && sampleRateInHz != SAMPLE_RATE_UNSPECIFIED) {
            throw new IllegalArgumentException(sampleRateInHz
                    + "Hz is not a supported sample rate.");
        }

        int encoding;
        switch (audioFormat) {
            case ENCODING_DEFAULT:
                encoding = AudioFormat.ENCODING_PCM_16BIT;
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
            case AudioFormat.ENCODING_PCM_16BIT:
            case AudioFormat.ENCODING_PCM_8BIT:
                encoding = audioFormat;
                break;
            default:
                throw new IllegalArgumentException("Unsupported sample encoding "
                        + audioFormat + ". Should be ENCODING_PCM_8BIT, "
                        + "ENCODING_PCM_16BIT, or ENCODING_PCM_FLOAT.");
        }
        mAudioFormat = encoding;

        int frameSizeInBytes = mChannelCount * bytesPerSample(mAudioFormat);
        if ((bufferSizeInBytes % frameSizeInBytes != 0) || (bufferSizeInBytes < 1)) {
            throw new IllegalArgumentException("Invalid audio buffer size "
                    + bufferSizeInBytes + " (frame size " + frameSizeInBytes + ")");
        }
        mBufferSizeInBytes = bufferSizeInBytes;

        // SAMPLE_RATE_UNSPECIFIED means "let the framework choose"; the backend
        // resamples from the device rate either way, so pick the Android
        // default rather than passing 0 down.
        mSampleRate = (sampleRateInHz == SAMPLE_RATE_UNSPECIFIED) ? 44100 : sampleRateInHz;

        mHandle = com.macapk.loader.AudioInputBridge.open(
                mSampleRate, mChannelCount, mAudioFormat,
                Math.max(1, bufferSizeInBytes / frameSizeInBytes));
        mState = (mHandle != 0) ? STATE_INITIALIZED : STATE_UNINITIALIZED;
        // v1.0.940: publish to the recording-activity registry. AOSP's
        // AudioService/RecordingActivityMonitor tracks live recorders so
        // AudioManager.getActiveRecordingConfigurations() and every registered
        // AudioRecordingCallback can see them. Without these four calls the
        // registry exists but nothing ever populates it, so the API would answer
        // an empty list forever -- honest, but permanently uninformative.
        if (mState == STATE_INITIALIZED) {
            AudioRecordingActivityMonitor.recorderCreated(this, mRecordSource, getFormat());
        }

        com.macapk.loader.AudioTrace.hit("AudioRecord.<init>",
            "src=" + audioSource + " sr=" + mSampleRate + " chCfg=" + channelConfig
            + " ch=" + mChannelCount + " fmt=" + mAudioFormat
            + " buf=" + bufferSizeInBytes + " -> h=" + mHandle
            + " state=" + mState);
    }

    AudioRecord(AudioAttributes attributes, AudioFormat format, int bufferSizeInBytes,
                int sessionId) {
        this(attributes, format, bufferSizeInBytes, sessionId,
                MediaRecorder.AudioSource.DEFAULT);
    }

    // The audio SOURCE the Builder asked for has to reach the instance: it is
    // what getAudioSource() reports, what the recording-activity registry
    // publishes, and what isPrivacySensitive()'s per-source default keys off.
    // The 4-arg form above kept it as DEFAULT unconditionally, so a
    // VOICE_RECOGNITION recorder identified itself as DEFAULT.
    AudioRecord(AudioAttributes attributes, AudioFormat format, int bufferSizeInBytes,
                int sessionId, int audioSource) {
        // The AudioAttributes/AudioFormat path rejects the deprecated
        // CHANNEL_CONFIGURATION_* values (allowLegacyConfig = false in AOSP).
        int mask = (format != null) ? format.getChannelMask() : AudioFormat.CHANNEL_IN_MONO;
        if (mask == CHANNEL_INVALID) mask = AudioFormat.CHANNEL_IN_MONO;
        int channelMask = getChannelMaskFromLegacyConfig(mask, false);
        mChannelCount = Integer.bitCount(channelMask);

        int encoding = (format != null) ? format.getEncoding()
                                        : AudioFormat.ENCODING_PCM_16BIT;
        if (encoding == AudioFormat.ENCODING_INVALID || encoding == ENCODING_DEFAULT) {
            encoding = AudioFormat.ENCODING_PCM_16BIT;
        }
        mAudioFormat = encoding;

        int sr = (format != null) ? format.getSampleRate() : 44100;
        mSampleRate = (sr <= 0) ? 44100 : sr;
        mRecordSource = audioSource;

        int frameSizeInBytes = mChannelCount * bytesPerSample(mAudioFormat);
        int bufBytes = (bufferSizeInBytes > 0) ? bufferSizeInBytes : frameSizeInBytes;
        mBufferSizeInBytes = bufBytes;

        mHandle = com.macapk.loader.AudioInputBridge.open(
                mSampleRate, mChannelCount, mAudioFormat,
                Math.max(1, bufBytes / frameSizeInBytes));
        mState = (mHandle != 0) ? STATE_INITIALIZED : STATE_UNINITIALIZED;
        // v1.0.940: publish to the recording-activity registry. AOSP's
        // AudioService/RecordingActivityMonitor tracks live recorders so
        // AudioManager.getActiveRecordingConfigurations() and every registered
        // AudioRecordingCallback can see them. Without these four calls the
        // registry exists but nothing ever populates it, so the API would answer
        // an empty list forever -- honest, but permanently uninformative.
        if (mState == STATE_INITIALIZED) {
            AudioRecordingActivityMonitor.recorderCreated(this, mRecordSource, getFormat());
        }

        com.macapk.loader.AudioTrace.hit("AudioRecord.<init>(builder)",
            "sr=" + mSampleRate + " ch=" + mChannelCount + " fmt=" + mAudioFormat
            + " buf=" + bufBytes + " session=" + sessionId
            + " -> h=" + mHandle + " state=" + mState);
    }

    // AOSP AudioRecord.getChannelMaskFromLegacyConfig. The deprecated
    // CHANNEL_CONFIGURATION_* aliases are accepted only on the legacy
    // constructor, which is why the flag exists.
    private static int getChannelMaskFromLegacyConfig(int inChannelConfig,
                                                      boolean allowLegacyConfig) {
        int mask;
        switch (inChannelConfig) {
            case CHANNEL_IN_DEFAULT:
            case AudioFormat.CHANNEL_IN_MONO:
            case CHANNEL_CONFIGURATION_MONO:
                mask = AudioFormat.CHANNEL_IN_MONO;
                break;
            case AudioFormat.CHANNEL_IN_STEREO:
            case CHANNEL_CONFIGURATION_STEREO:
                mask = AudioFormat.CHANNEL_IN_STEREO;
                break;
            case CHANNEL_IN_FRONT_BACK:
                mask = inChannelConfig;
                break;
            default:
                throw new IllegalArgumentException("Unsupported channel configuration.");
        }
        if (!allowLegacyConfig && ((inChannelConfig == CHANNEL_CONFIGURATION_MONO)
                || (inChannelConfig == CHANNEL_CONFIGURATION_STEREO))) {
            throw new IllegalArgumentException("Unsupported deprecated configuration.");
        }
        return mask;
    }

    // AOSP AudioFormat.getBytesPerSample.
    private static int bytesPerSample(int audioFormat) {
        switch (audioFormat) {
            case AudioFormat.ENCODING_PCM_8BIT:  return 1;
            case AudioFormat.ENCODING_PCM_FLOAT: return 4;
            case AudioFormat.ENCODING_PCM_16BIT:
            default:                             return 2;
        }
    }

    public int getState() { return mState; }
    public int getRecordingState() {
        synchronized (mRecordingStateLock) { return mRecordingState; }
    }
    public int getSampleRate() { return mSampleRate; }
    public int getChannelCount() { return mChannelCount; }
    public int getAudioFormat() { return mAudioFormat; }
    public int getAudioSource() { return mRecordSource; }
    public int getAudioSessionId() { return 0; }
    public int getBufferSizeInFrames() {
        int fs = mChannelCount * bytesPerSample(mAudioFormat);
        return fs > 0 ? Math.max(1, mBufferSizeInBytes / fs) : 1024;
    }

    // ---- Explicit routing (API 23+) ---------------------------------------
    // AOSP declares these on the android.media.AudioRouting interface, shared
    // with AudioTrack; this class does not formally `implements AudioRouting`
    // (neither does the AudioTrack sibling in this tree), it just provides
    // the same method shapes.

    /** AOSP: "Returns an AudioDeviceInfo identifying the current routing of
     *  this AudioRecord. Note: The query is only valid if the AudioRecord is
     *  currently recording. If it is not, getRoutedDevice() will return
     *  null." AOSP resolves a native device id against
     *  AudioManager.getDevicesStatic(GET_DEVICES_INPUTS); we have no native
     *  per-instance device id (audio_input.mm exposes one fixed capture
     *  route, not a native_getRoutedDeviceId()-style query), so the lookup
     *  below mirrors AOSP's STRUCTURE against AudioManager's real input-device
     *  list rather than inventing a device here. That list is honestly empty
     *  today (AudioManager.getDevices(GET_DEVICES_INPUTS) -- a file this lane
     *  does not own -- reports zero input devices by its own documented
     *  design), so this returns null even while recording; it starts
     *  reporting the real capture device the moment that list gains one,
     *  with no change needed at this call site. */
    public AudioDeviceInfo getRoutedDevice() {
        if (mRecordingState != RECORDSTATE_RECORDING) return null;
        AudioDeviceInfo[] inputs =
                new AudioManager().getDevices(AudioManager.GET_DEVICES_INPUTS);
        return (inputs != null && inputs.length > 0) ? inputs[0] : null;
    }

    /** AOSP: "@param deviceInfo ... If deviceInfo is null, default routing is
     *  restored. @return true if successful, false if the specified
     *  AudioDeviceInfo is non-null and does not correspond to a valid audio
     *  input device." AOSP validates deviceInfo.isSource() before accepting
     *  it; Mac.apk's only AudioDeviceInfo instance today is the built-in
     *  output speaker (isSource() == false, see AudioDeviceInfo.java, a file
     *  this lane does not own), so a non-null argument always fails that
     *  check here -- the same false a real device returns when handed an
     *  output-only descriptor. There is exactly one capture route on this
     *  backend (whatever CoreAudio's default input is; see
     *  runtime/src/audio_input.mm), so once a device passes validation there
     *  is nothing further to switch: accepting it, or a null "restore
     *  default", always succeeds. */
    public boolean setPreferredDevice(AudioDeviceInfo deviceInfo) {
        if (deviceInfo != null && !deviceInfo.isSource()) {
            return false;
        }
        mPreferredDevice = deviceInfo;
        return true;
    }

    private AudioDeviceInfo mPreferredDevice;

    /** AOSP: the device the app ASKED for, or null when none was requested. */
    public AudioDeviceInfo getPreferredDevice() { return mPreferredDevice; }

    /** AOSP: "Poll for an AudioTimestamp on demand ... Calling
     *  startRecording() following a stop() will reset the frame count to 0."
     *  @return SUCCESS if a timestamp is available, or
     *  ERROR_INVALID_OPERATION if a timestamp is not available. The
     *  IllegalArgumentException on a null timestamp / bad timebase is AOSP's
     *  own validation, thrown before any state check. A timestamp is only
     *  meaningful while frames are actually being delivered, so "available"
     *  here means "currently recording" -- outTimestamp is left untouched on
     *  the ERROR_INVALID_OPERATION path, matching AOSP (the native call is
     *  simply never made). framePosition is a running count of frames
     *  delivered to read() since the last startRecording(), which is exactly
     *  what AOSP's own javadoc promises and is real, live data on this
     *  backend (not a fabricated number) because every read() call already
     *  reports how many frames it drained. */
    public int getTimestamp(AudioTimestamp outTimestamp, int timebase) {
        if (outTimestamp == null ||
                (timebase != AudioTimestamp.TIMEBASE_BOOTTIME
                && timebase != AudioTimestamp.TIMEBASE_MONOTONIC)) {
            throw new IllegalArgumentException();
        }
        if (mRecordingState != RECORDSTATE_RECORDING) {
            return ERROR_INVALID_OPERATION;
        }
        outTimestamp.framePosition = mFramesDelivered.get();
        // Both timebases are CLOCK_MONOTONIC-derived on macOS (no separate
        // "boottime that excludes suspend" clock the way Linux/Android
        // distinguish them -- a Mac does not suspend a running JVM process),
        // so both read the same monotonic nanosecond clock.
        outTimestamp.nanoTime = System.nanoTime();
        return SUCCESS;
    }

    public void startRecording() throws IllegalStateException {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("startRecording() called on an "
                    + "uninitialized AudioRecord.");
        }
        synchronized (mRecordingStateLock) {
            com.macapk.loader.AudioInputBridge.nativeStart(mHandle);
            mRecordingState = RECORDSTATE_RECORDING;
            mFramesDelivered.set(0);
            AudioRecordingActivityMonitor.recorderStarted(this);
        }
        com.macapk.loader.AudioTrace.hit("AudioRecord.startRecording", "h=" + mHandle);
    }

    public void startRecording(MediaSyncEvent syncEvent) throws IllegalStateException {
        // We have no trigger-session plumbing, so the sync event degrades to an
        // immediate start -- which is what AOSP does for SYNC_EVENT_NONE.
        startRecording();
    }

    public void stop() throws IllegalStateException {
        if (mState != STATE_INITIALIZED) {
            throw new IllegalStateException("stop() called on an uninitialized AudioRecord.");
        }
        synchronized (mRecordingStateLock) {
            com.macapk.loader.AudioInputBridge.nativeStop(mHandle);
            mRecordingState = RECORDSTATE_STOPPED;
            AudioRecordingActivityMonitor.recorderStopped(this);
        }
    }

    public void release() {
        // Stop the position watcher first, or an app that churns through records
        // leaks one daemon thread per released instance.
        mPosStop = true;
        Thread w = mPosWatcher;
        if (w != null) w.interrupt();
        try {
            if (mRecordingState == RECORDSTATE_RECORDING) stop();
        } catch (IllegalStateException ignored) {
            // AOSP swallows this too: release() must be safe on any state.
        }
        if (mHandle != 0) {
            try {
                com.macapk.loader.AudioInputBridge.nativeRelease(mHandle);
            } catch (Throwable ignored) {
                // Backend already gone; nothing to reclaim on this side.
            }
        }
        mState = STATE_UNINITIALIZED;
        synchronized (mRecordingStateLock) { mRecordingState = RECORDSTATE_STOPPED; }
        AudioRecordingActivityMonitor.recorderReleased(this);
    }

    // ---- read() ----------------------------------------------------------
    // Error semantics are AOSP's, verbatim: an uninitialized recorder is
    // ERROR_INVALID_OPERATION (not a zero-filled buffer), a byte[]/short[] read
    // on a FLOAT-encoded recorder is likewise ERROR_INVALID_OPERATION, a bad
    // readMode or bad index/length is ERROR_BAD_VALUE, and the returned count
    // may legitimately be shorter than requested.

    public int read(byte[] audioData, int offsetInBytes, int sizeInBytes) {
        return read(audioData, offsetInBytes, sizeInBytes, READ_BLOCKING);
    }

    public int read(byte[] audioData, int offsetInBytes, int sizeInBytes, int readMode) {
        if (mState != STATE_INITIALIZED
                || mAudioFormat == AudioFormat.ENCODING_PCM_FLOAT) {
            return ERROR_INVALID_OPERATION;
        }
        if ((readMode != READ_BLOCKING) && (readMode != READ_NON_BLOCKING)) {
            return ERROR_BAD_VALUE;
        }
        if ((audioData == null) || (offsetInBytes < 0) || (sizeInBytes < 0)
                || (offsetInBytes + sizeInBytes < 0)   // integer overflow
                || (offsetInBytes + sizeInBytes > audioData.length)) {
            return ERROR_BAD_VALUE;
        }
        int got = com.macapk.loader.AudioInputBridge.nativeReadBytes(
                mHandle, audioData, offsetInBytes, sizeInBytes,
                readMode == READ_BLOCKING ? 1 : 0);
        if (got > 0) {
            mFramesDelivered.addAndGet(got / (mChannelCount * bytesPerSample(mAudioFormat)));
        }
        return got;
    }

    public int read(short[] audioData, int offsetInShorts, int sizeInShorts) {
        return read(audioData, offsetInShorts, sizeInShorts, READ_BLOCKING);
    }

    public int read(short[] audioData, int offsetInShorts, int sizeInShorts, int readMode) {
        if (mState != STATE_INITIALIZED
                || mAudioFormat == AudioFormat.ENCODING_PCM_FLOAT) {
            return ERROR_INVALID_OPERATION;
        }
        if ((readMode != READ_BLOCKING) && (readMode != READ_NON_BLOCKING)) {
            return ERROR_BAD_VALUE;
        }
        if ((audioData == null) || (offsetInShorts < 0) || (sizeInShorts < 0)
                || (offsetInShorts + sizeInShorts < 0)
                || (offsetInShorts + sizeInShorts > audioData.length)) {
            return ERROR_BAD_VALUE;
        }
        int got = com.macapk.loader.AudioInputBridge.nativeReadShorts(
                mHandle, audioData, offsetInShorts, sizeInShorts,
                readMode == READ_BLOCKING ? 1 : 0);
        if (got > 0) mFramesDelivered.addAndGet(got / mChannelCount);
        return got;
    }

    public int read(float[] audioData, int offsetInFloats, int sizeInFloats, int readMode) {
        if (mState == STATE_UNINITIALIZED) {
            return ERROR_INVALID_OPERATION;
        }
        if (mAudioFormat != AudioFormat.ENCODING_PCM_FLOAT) {
            // AOSP: "AudioRecord.read(float[] ...) requires format ENCODING_PCM_FLOAT"
            return ERROR_INVALID_OPERATION;
        }
        if ((readMode != READ_BLOCKING) && (readMode != READ_NON_BLOCKING)) {
            return ERROR_BAD_VALUE;
        }
        if ((audioData == null) || (offsetInFloats < 0) || (sizeInFloats < 0)
                || (offsetInFloats + sizeInFloats < 0)
                || (offsetInFloats + sizeInFloats > audioData.length)) {
            return ERROR_BAD_VALUE;
        }
        int got = com.macapk.loader.AudioInputBridge.nativeReadFloats(
                mHandle, audioData, offsetInFloats, sizeInFloats,
                readMode == READ_BLOCKING ? 1 : 0);
        if (got > 0) mFramesDelivered.addAndGet(got / mChannelCount);
        return got;
    }

    public int read(java.nio.ByteBuffer audioBuffer, int sizeInBytes) {
        return read(audioBuffer, sizeInBytes, READ_BLOCKING);
    }

    public int read(java.nio.ByteBuffer audioBuffer, int sizeInBytes, int readMode) {
        if (mState != STATE_INITIALIZED) {
            return ERROR_INVALID_OPERATION;
        }
        if ((readMode != READ_BLOCKING) && (readMode != READ_NON_BLOCKING)) {
            return ERROR_BAD_VALUE;
        }
        if ((audioBuffer == null) || (sizeInBytes < 0)) {
            return ERROR_BAD_VALUE;
        }
        int n = Math.min(sizeInBytes, audioBuffer.remaining());
        if (n <= 0) return 0;
        // Staged through a byte[] so heap and direct buffers behave alike. AOSP
        // reads straight into a direct buffer's address and returns 0 for a
        // heap buffer; staging is strictly more useful and costs one copy on a
        // path that is already doing format conversion.
        byte[] tmp = new byte[n];
        int got = com.macapk.loader.AudioInputBridge.nativeReadBytes(
                mHandle, tmp, 0, n, readMode == READ_BLOCKING ? 1 : 0);
        if (got <= 0) return got < 0 ? got : 0;
        mFramesDelivered.addAndGet(got / (mChannelCount * bytesPerSample(mAudioFormat)));
        // AOSP leaves position() unchanged, writing at position() onward.
        int pos = audioBuffer.position();
        audioBuffer.put(tmp, 0, got);
        audioBuffer.position(pos);
        return got;
    }

    // AOSP raises onMarkerReached / onPeriodicNotification as capture advances.
    // These setters DISCARDED the listener outright while
    // setNotificationMarkerPosition / setPositionNotificationPeriod both returned
    // SUCCESS -- so an app that sequences on capture progress (voice recorders,
    // speech endpointing, streaming voice) armed a notification, was told it
    // worked, and waited forever. Identical hole to the one AudioTrack had.
    //
    // mFramesDelivered is already maintained by every read path above, so the
    // notification only needed a watcher over it. One daemon thread per record
    // that actually arms a notification; nothing is started otherwise.
    private volatile OnRecordPositionUpdateListener mPosListener;
    private volatile android.os.Handler mPosHandler;
    private Thread mPosWatcher;
    private volatile boolean mPosStop;

    public void setRecordPositionUpdateListener(OnRecordPositionUpdateListener listener) {
        setRecordPositionUpdateListener(listener, null);
    }
    public void setRecordPositionUpdateListener(OnRecordPositionUpdateListener listener,
                                                android.os.Handler handler) {
        mPosListener = listener;
        mPosHandler = handler;
        if (listener != null) macapkStartPositionWatcher();
    }

    /** AOSP delivers on the handler's thread, never on an internal one. */
    private void macapkDeliver(Runnable r) {
        android.os.Handler h = mPosHandler;
        if (h != null) { try { h.post(r); return; } catch (Throwable ignored) { } }
        // Never let this watcher be the FIRST caller of getMainLooper(): that
        // would pin the main looper to this daemon thread (Looper.getMainLooper
        // notes the caller when none is registered), and every later main-thread
        // post would land on a thread nothing pumps.
        if (android.os.Looper.macapkHasMainThread()) {
            try { new android.os.Handler(android.os.Looper.getMainLooper()).post(r); return; }
            catch (Throwable ignored) { }
        }
        try { r.run(); } catch (Throwable ignored) { }
    }

    private synchronized void macapkStartPositionWatcher() {
        if (mPosWatcher != null) return;
        mPosWatcher = new Thread(new Runnable() { @Override public void run() {
            long lastPos = 0;
            boolean markerFired = false;
            int armedMarker = -1;
            long nextPeriod = -1;
            for (;;) {
                if (mPosStop) return;
                try { Thread.sleep(20L); } catch (InterruptedException e) { return; }
                if (mPosStop) return;
                final OnRecordPositionUpdateListener l = mPosListener;
                if (l == null) continue;
                long pos;
                try {
                    if (getRecordingState() != RECORDSTATE_RECORDING) continue;
                    pos = mFramesDelivered.get();
                } catch (Throwable t) { continue; }
                // startRecording() resets mFramesDelivered to 0, so a backwards
                // jump means a fresh session and the marker re-arms.
                if (pos < lastPos) { markerFired = false; nextPeriod = -1; }
                lastPos = pos;

                int marker = mMarkerPositionInFrames;
                // AOSP re-arms on every set, so a chained marker (the listener
                // setting the next one) keeps firing instead of stopping at one.
                if (marker != armedMarker) { armedMarker = marker; markerFired = false; }
                if (!markerFired && marker > 0 && pos >= marker) {
                    markerFired = true;
                    macapkDeliver(new Runnable() { @Override public void run() {
                        l.onMarkerReached(AudioRecord.this); } });
                }
                int period = mPositionNotificationPeriod;
                if (period > 0) {
                    if (nextPeriod < 0) nextPeriod = pos + period;
                    while (pos >= nextPeriod) {
                        nextPeriod += period;
                        macapkDeliver(new Runnable() { @Override public void run() {
                            l.onPeriodicNotification(AudioRecord.this); } });
                    }
                } else nextPeriod = -1;
            }
        } }, "macapk-audiorecord-pos");
        mPosWatcher.setDaemon(true);
        mPosWatcher.start();
    }
    public int setNotificationMarkerPosition(int markerInFrames) {
        if (mState != STATE_INITIALIZED) return ERROR_INVALID_OPERATION;
        mMarkerPositionInFrames = markerInFrames;
        return SUCCESS;
    }
    public int setPositionNotificationPeriod(int periodInFrames) {
        if (mState != STATE_INITIALIZED) return ERROR_INVALID_OPERATION;
        mPositionNotificationPeriod = periodInFrames;
        return SUCCESS;
    }

    // AOSP reports back the marker/period the app set (0 when none). Stored
    // rather than answered as 0 so a write/read pair agrees -- the only part of
    // the contract observable without the notification callbacks, which this
    // backend does not raise.
    private int mMarkerPositionInFrames;
    private int mPositionNotificationPeriod;

    public int getNotificationMarkerPosition() { return mMarkerPositionInFrames; }
    public int getPositionNotificationPeriod() { return mPositionNotificationPeriod; }

    public AudioFormat getFormat() {
        return new AudioFormat.Builder()
                .setSampleRate(mSampleRate)
                .setEncoding(mAudioFormat)
                .setChannelMask(mChannelCount == 2 ? AudioFormat.CHANNEL_IN_STEREO
                                                   : AudioFormat.CHANNEL_IN_MONO)
                .build();
    }

    // AOSP AudioRecord.getMinBufferSize: the channel switch and the
    // 0 -> ERROR_BAD_VALUE / -1 -> ERROR mapping are AOSP's; only the size
    // computation is ours, since there is no AudioSystem to ask.
    public static int getMinBufferSize(int sampleRateInHz, int channelConfig,
                                       int audioFormat) {
        int channelCount;
        switch (channelConfig) {
            case CHANNEL_IN_DEFAULT:
            case AudioFormat.CHANNEL_IN_MONO:
            case CHANNEL_CONFIGURATION_MONO:
                channelCount = 1;
                break;
            case AudioFormat.CHANNEL_IN_STEREO:
            case CHANNEL_CONFIGURATION_STEREO:
            case CHANNEL_IN_FRONT_BACK:
                channelCount = 2;
                break;
            case CHANNEL_INVALID:
            default:
                return ERROR_BAD_VALUE;
        }
        if (sampleRateInHz < SAMPLE_RATE_HZ_MIN || sampleRateInHz > SAMPLE_RATE_HZ_MAX) {
            return ERROR_BAD_VALUE;
        }
        int bps = bytesPerSample(audioFormat);
        int frameSize = channelCount * bps;
        // ~25 ms of audio, floored so a slow reader still has headroom, and
        // rounded UP to a whole frame because the constructor rejects a buffer
        // size that is not a frame multiple -- and apps pass this value
        // straight into it.
        int size = Math.max((sampleRateInHz / 40) * frameSize, 4096);
        int rem = size % frameSize;
        if (rem != 0) size += frameSize - rem;
        return size;
    }

    public interface OnRecordPositionUpdateListener {
        void onMarkerReached(AudioRecord recorder);
        void onPeriodicNotification(AudioRecord recorder);
    }

    // AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
    // mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
    // app compiled against real Android that subclasses this fails to resolve
    // against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
    public static class Builder {
        private AudioAttributes attrs;
        private AudioFormat format;
        private int bufferSizeInBytes;
        private int audioSource = MediaRecorder.AudioSource.DEFAULT;
        private AudioPlaybackCaptureConfiguration playbackCaptureConfig;
        public Builder() {}
        public Builder setAudioSource(int source) { this.audioSource = source; return this; }
        public Builder setAudioAttributes(AudioAttributes a) { this.attrs = a; return this; }
        public Builder setAudioFormat(AudioFormat f) { this.format = f; return this; }
        public Builder setBufferSizeInBytes(int bufferSizeInBytes) {
            this.bufferSizeInBytes = bufferSizeInBytes; return this;
        }

        private boolean privacySensitive;
        private boolean privacySensitiveExplicit;

        /** API 30. Marks the capture as privacy-sensitive, which on Android
         *  prevents another app sharing the input. Recorded so
         *  AudioRecord.isPrivacySensitive() reads back what was set instead of
         *  falling to the per-source default. Mac.apk runs one app per process,
         *  so there is no concurrent capture for the flag to exclude -- the
         *  guarantee holds trivially rather than being ignored. */
        public Builder setPrivacySensitive(boolean privacySensitive) {
            this.privacySensitive = privacySensitive;
            this.privacySensitiveExplicit = true;
            return this;
        }

        /** API 31. AOSP uses the Context only to attribute the capture to the
         *  right package/attribution tag for the microphone privacy indicator.
         *  There is one package in this process and no privacy indicator to
         *  attribute to, so the Context is accepted (non-null, as AOSP
         *  requires) and changes nothing observable. */
        public Builder setContext(android.content.Context context) {
            java.util.Objects.requireNonNull(context, "context cannot be null");
            return this;
        }

        /** AOSP: "Sets the AudioRecord to record audio played by other apps."
         *  @throws NullPointerException if config is null (AOSP's own
         *  Preconditions.checkNotNull). We do not additionally reproduce
         *  AOSP's "@throws IllegalStateException if called in conjunction
         *  with setAudioSource(int)" cross-check: that guard exists in AOSP
         *  to protect a real AudioPolicy mix registration from an
         *  inconsistent request, and build() below already refuses this
         *  whole feature outright (see its own comment), so the extra state
         *  tracking would guard a path that can never succeed anyway. */
        public Builder setAudioPlaybackCaptureConfig(AudioPlaybackCaptureConfiguration config) {
            java.util.Objects.requireNonNull(config,
                    "Illegal null AudioPlaybackCaptureConfiguration argument");
            this.playbackCaptureConfig = config;
            return this;
        }

        public AudioRecord build() throws UnsupportedOperationException {
            if (playbackCaptureConfig != null) {
                // AOSP routes this through AudioPolicy.createAudioRecordSink(),
                // which needs AudioFlinger's policy-mixer engine to intercept
                // another app's output -- infrastructure Mac.apk does not have
                // (Mac.apk runs one Android app per process straight into
                // CoreAudio; see AudioPlaybackCaptureConfiguration's class
                // comment). In practice this is unreachable: that class's own
                // Builder(MediaProjection) constructor already throws
                // IllegalArgumentException before a caller can ever obtain a
                // non-null config to pass here (MediaProjection.canProjectAudio()
                // is always false). If it is ever reached anyway -- e.g. a
                // config built by another means -- fail the same honest way
                // AOSP's own buildAudioPlaybackCaptureRecord() does when the
                // sink cannot be created, instead of silently falling through
                // to the plain-microphone path below and handing back an
                // AudioRecord that lies about what it is capturing.
                throw new UnsupportedOperationException("Cannot create AudioRecord");
            }
            AudioFormat f = format;
            if (f == null) {
                f = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build();
            }
            int bufBytes = bufferSizeInBytes;
            if (bufBytes == 0) {
                // AOSP: "use a single frame and let the native code figure out
                // the minimum buffer size".
                bufBytes = Math.max(1, f.getChannelCount())
                        * bytesPerSample(f.getEncoding());
            }
            AudioRecord record = new AudioRecord(attrs, f, bufBytes, 0, audioSource);
            if (privacySensitiveExplicit) {
                record.mPrivacySensitive = privacySensitive;
                record.mPrivacySensitiveExplicit = true;
            }
            if (record.getState() == STATE_UNINITIALIZED) {
                // AOSP AudioRecord.Builder.build(). This is the honest signal
                // that no microphone stream exists -- the same exception an
                // Android app gets when RECORD_AUDIO was not granted.
                throw new UnsupportedOperationException("Cannot create AudioRecord");
            }
            return record;
        }
    }

    // =====================================================================
    // API-gap close-out. Signatures from /tmp/aosp_api/android16.txt; behaviour
    // from frameworks/base/media/java/android/media/AudioRecord.java
    // (android16-release).
    // =====================================================================

    /** AOSP returns the CHANNEL_IN_* mask the recorder was created with.
     *  Derived from the channel count so it cannot disagree with
     *  getChannelCount(). */
    public int getChannelConfiguration() {
        switch (mChannelCount) {
            case 1: return AudioFormat.CHANNEL_IN_MONO;
            case 2: return AudioFormat.CHANNEL_IN_STEREO;
            default: return AudioFormat.CHANNEL_IN_MONO;
        }
    }

    // ---- routing listeners ------------------------------------------------
    // Capture routing never changes on this host (one CoreAudio input), so a
    // registered listener is retained and never fires -- the observable result
    // of a device with a fixed route.
    private final java.util.concurrent.CopyOnWriteArrayList<Object[]> mRoutingListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener listener,
            android.os.Handler handler) {
        if (listener == null) return;
        for (Object[] entry : mRoutingListeners) {
            if (entry[0] == listener) return;
        }
        mRoutingListeners.add(new Object[] { listener, handler });
    }

    public void removeOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener listener) {
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

    /** @deprecated in AOSP since API 24, superseded by
     *  AudioRouting.OnRoutingChangedListener. */
    @Deprecated
    public interface OnRoutingChangedListener extends AudioRouting.OnRoutingChangedListener {
        void onRoutingChanged(AudioRecord audioRecord);
        @Override default void onRoutingChanged(AudioRouting router) {
            if (router instanceof AudioRecord) onRoutingChanged((AudioRecord) router);
        }
    }

    // ---- AudioRecordingMonitor -------------------------------------------

    private final java.util.concurrent.CopyOnWriteArrayList<Object[]> mRecordingCallbacks =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** AOSP throws IllegalArgumentException on a null executor or callback.
     *  The capture path never changes underneath a running recorder on this
     *  backend (no HAL pre-processing swap, no system silencing), so a
     *  registered callback is retained and never invoked -- which is the same
     *  thing an app observes on a device whose capture path is stable. */
    public void registerAudioRecordingCallback(java.util.concurrent.Executor executor,
            AudioManager.AudioRecordingCallback cb) {
        if (executor == null) {
            throw new IllegalArgumentException("Illegal null Executor");
        }
        if (cb == null) {
            throw new IllegalArgumentException("Illegal null AudioRecordingCallback");
        }
        for (Object[] entry : mRecordingCallbacks) {
            if (entry[1] == cb) return;
        }
        mRecordingCallbacks.add(new Object[] { executor, cb });
    }

    public void unregisterAudioRecordingCallback(AudioManager.AudioRecordingCallback cb) {
        if (cb == null) {
            throw new IllegalArgumentException("Illegal null AudioRecordingCallback");
        }
        for (Object[] entry : mRecordingCallbacks) {
            if (entry[1] == cb) { mRecordingCallbacks.remove(entry); return; }
        }
    }

    /** @Nullable in AOSP: null when THIS recorder is not currently capturing.
     *  When it is, the configuration comes from the same registry
     *  AudioManager.getActiveRecordingConfigurations() reads, so the two can
     *  never disagree about what this recorder is doing. */
    public AudioRecordingConfiguration getActiveRecordingConfiguration() {
        if (mRecordingState != RECORDSTATE_RECORDING) return null;
        return AudioRecordingActivityMonitor.getConfigurationFor(this);
    }

    // ---- MicrophoneDirection (API 29) ------------------------------------
    // Steerable capture needs a microphone ARRAY the HAL can beam-form with.
    // CoreAudio hands this backend one default input stream with no such
    // control, and AOSP's own contract is that these return false on hardware
    // that cannot honour the preference -- so false, after AOSP's own argument
    // validation. Returning true would tell an app its beam was steered.

    public boolean setPreferredMicrophoneDirection(int direction) {
        if (direction < MIC_DIRECTION_UNSPECIFIED || direction > MIC_DIRECTION_EXTERNAL) {
            throw new IllegalArgumentException("Invalid direction " + direction);
        }
        return false;
    }

    public boolean setPreferredMicrophoneFieldDimension(float zoom) {
        if (zoom < -1.0f || zoom > 1.0f) {
            throw new IllegalArgumentException("Argument must fall between -1 & 1 (inclusive)");
        }
        return false;
    }

    /** AOSP: the microphones actively feeding THIS recorder, throwing
     *  IOException if the query fails. Empty while not recording (nothing is
     *  active), and the one real built-in microphone while recording -- the same
     *  device AudioManager.getMicrophones() describes. */
    public java.util.List<MicrophoneInfo> getActiveMicrophones() throws java.io.IOException {
        java.util.ArrayList<MicrophoneInfo> mics = new java.util.ArrayList<>(1);
        if (mRecordingState == RECORDSTATE_RECORDING) {
            mics.add(MicrophoneInfo.defaultBuiltIn(/* id */ 1, /* address */ ""));
        }
        return mics;
    }

    /**
     * API 30. Whether this capture is marked as privacy-sensitive -- which on
     * Android means "do not let a concurrent capture share this input". AOSP's
     * default depends on the source: true for VOICE_COMMUNICATION and
     * CAMCORDER, false otherwise, and the Builder can override it. The same
     * rule is applied here, so the answer is derived from the recorder's real
     * source rather than fixed.
     */
    public boolean isPrivacySensitive() {
        if (mPrivacySensitiveExplicit) return mPrivacySensitive;
        return mRecordSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION
                || mRecordSource == MediaRecorder.AudioSource.CAMCORDER;
    }

    boolean mPrivacySensitive;
    boolean mPrivacySensitiveExplicit;

    // ---- metrics / log session -------------------------------------------

    private android.media.metrics.LogSessionId mLogSessionId =
            android.media.metrics.LogSessionId.LOG_SESSION_ID_NONE;

    /** @NonNull in AOSP; LOG_SESSION_ID_NONE until the app sets one. */
    public android.media.metrics.LogSessionId getLogSessionId() { return mLogSessionId; }

    public void setLogSessionId(android.media.metrics.LogSessionId logSessionId) {
        mLogSessionId = java.util.Objects.requireNonNull(logSessionId,
                "logSessionId must not be null");
    }

    /**
     * AOSP's getMetrics() reports what the native recorder actually did. The
     * descriptive keys are filled in from this recorder's real configuration;
     * the private native counters are omitted rather than zero-filled, because a
     * zero counter reads as a measurement. AOSP returns null when there are no
     * metrics at all, so a caller already handles a thin bundle.
     */
    /** The metrics-bundle keys AOSP publishes for getMetrics(). */
    public static final class MetricsConstants {
        private MetricsConstants() { }
        public static final String CHANNELS = "android.media.audiorecord.channels";
        public static final String ENCODING = "android.media.audiorecord.encoding";
        /** @deprecated in AOSP; the platform stopped reporting it. */
        @Deprecated public static final String LATENCY = "android.media.audiorecord.latency";
        public static final String SAMPLERATE = "android.media.audiorecord.samplerate";
        public static final String SOURCE = "android.media.audiorecord.source";
    }

    public android.os.PersistableBundle getMetrics() {
        android.os.PersistableBundle bundle = new android.os.PersistableBundle();
        bundle.putString("android.media.audiorecord.samplerate", Integer.toString(mSampleRate));
        bundle.putString("android.media.audiorecord.channelmask",
                Integer.toString(getChannelConfiguration()));
        bundle.putString("android.media.audiorecord.encoding", Integer.toString(mAudioFormat));
        bundle.putString("android.media.audiorecord.source", Integer.toString(mRecordSource));
        return bundle;
    }

    /** AOSP declares finalize() so a dropped recorder releases its native
     *  capture stream instead of leaving the microphone open. release() is
     *  safe on any state. */
    @Override protected void finalize() throws Throwable {
        try { release(); } finally { super.finalize(); }
    }
}
