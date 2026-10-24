package android.media;

import android.os.Handler;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Paces video against audio for an app that drives its own decoders (API 23).
 *
 * The AUDIO half is real here, because everything it needs already exists: the
 * app supplies a live AudioTrack, and queueAudio() writes the caller's buffer
 * into it through AudioTrack.write() -- the same CoreAudio path every other
 * sound in this runtime takes -- then hands the buffer back through
 * Callback.onAudioBufferConsumed, which is the contract an app's buffer-recycling
 * loop depends on. getTimestamp() likewise comes from the AudioTrack's own
 * playback head rather than from a made-up clock.
 *
 * The VIDEO half is not: pacing frames onto a Surface needs a compositor-side
 * release-time queue (AOSP's surface `setNextFrameTime`), which Mac.apk has no
 * equivalent of. setSurface() is accepted and createInputSurface() hands back
 * the same empty, no-producer Surface the rest of this tree uses for a sink that
 * consumes nothing -- so an app that only wanted audio pacing works, and an app
 * that wanted video pacing gets frames presented by its own path instead of
 * silently never at all.
 *
 * Source: frameworks/base/media/java/android/media/MediaSync.java
 * (android16-release).
 */
public final class MediaSync {

    public static final int MEDIASYNC_ERROR_AUDIOTRACK_FAIL = 1;
    public static final int MEDIASYNC_ERROR_SURFACE_FAIL = 2;

    private AudioTrack mAudioTrack;
    private Surface mSurface;
    private Callback mCallback;
    private Handler mCallbackHandler;
    private OnErrorListener mErrorListener;
    private Handler mErrorHandler;
    private PlaybackParams mPlaybackParams = new PlaybackParams().allowDefaults();
    private SyncParams mSyncParams = new SyncParams().allowDefaults();
    private final ArrayDeque<Object[]> mPending = new ArrayDeque<>();

    public MediaSync() { }

    /** AOSP: null detaches the current track. A track that is not initialized is
     *  reported through MEDIASYNC_ERROR_AUDIOTRACK_FAIL rather than accepted, so
     *  the app is not left queueing into a dead sink. */
    public void setAudioTrack(AudioTrack audioTrack) {
        if (audioTrack != null && audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            notifyError(MEDIASYNC_ERROR_AUDIOTRACK_FAIL, 0);
            return;
        }
        mAudioTrack = audioTrack;
    }

    public void setSurface(Surface surface) { mSurface = surface; }

    /** @NonNull in AOSP. See the class comment for why this is a sink with no
     *  producer behind it. */
    public Surface createInputSurface() {
        if (mSurface == null) mSurface = new Surface();
        return mSurface;
    }

    public void setCallback(Callback cb, Handler handler) {
        mCallback = cb;
        mCallbackHandler = handler;
    }

    public void setOnErrorListener(OnErrorListener listener, Handler handler) {
        mErrorListener = listener;
        mErrorHandler = handler;
    }

    /**
     * AOSP: "Queues the audio data asynchronously for playback (AudioTrack must
     * be in streaming mode)"; the buffer is returned through the callback once
     * consumed. Written straight into the app's own AudioTrack, so the audio is
     * really heard.
     */
    public void queueAudio(ByteBuffer audioData, int bufferId, long presentationTimeUs) {
        Objects.requireNonNull(audioData, "audioData cannot be null");
        AudioTrack track = mAudioTrack;
        if (track == null) {
            throw new IllegalStateException(
                    "AudioTrack is NOT set or audio track mode is invalid");
        }
        int size = audioData.remaining();
        if (size > 0) {
            try {
                track.write(audioData, size, AudioTrack.WRITE_BLOCKING);
            } catch (Throwable t) {
                notifyError(MEDIASYNC_ERROR_AUDIOTRACK_FAIL, 0);
            }
        }
        notifyAudioBufferConsumed(audioData, bufferId);
    }

    /** @Nullable in AOSP: null when no timestamp is available yet. Derived from
     *  the AudioTrack's real playback head, so it advances with the audio. */
    public MediaTimestamp getTimestamp() {
        AudioTrack track = mAudioTrack;
        if (track == null || track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) return null;
        AudioTimestamp ts = new AudioTimestamp();
        if (!track.getTimestamp(ts)) return null;
        int sampleRate = track.getSampleRate();
        if (sampleRate <= 0) return null;
        long mediaUs = (ts.framePosition * 1_000_000L) / sampleRate;
        // macapk*Raw reads the field without PlaybackParams' "was this set?"
        // guard: an app may hand us params with only pitch set, and a timestamp
        // must not throw because of that.
        return new MediaTimestamp(mediaUs, ts.nanoTime, mPlaybackParams.macapkSpeedRaw());
    }

    public void setPlaybackParams(PlaybackParams params) {
        mPlaybackParams = Objects.requireNonNull(params, "params cannot be null");
        AudioTrack track = mAudioTrack;
        if (track != null) {
            // A speed/pitch change really has to reach the track, or the audio
            // and the app's own video pacing drift apart.
            try { track.setPlaybackParams(params); } catch (Throwable ignore) { }
        }
    }

    /** @NonNull in AOSP; the params previously set, fully populated. */
    public PlaybackParams getPlaybackParams() {
        // Fully-set copy, for the same reason MediaPlayer.getPlaybackParams
        // documents: AOSP's native get_playback_params returns every field with
        // its set-flag true, so a caller reading back .getPitch() must not hit
        // PlaybackParams' unset-field IllegalStateException.
        return new PlaybackParams()
                .setSpeed(mPlaybackParams.macapkSpeedRaw())
                .setPitch(mPlaybackParams.macapkPitchRaw())
                .setAudioFallbackMode(mPlaybackParams.macapkFallbackModeRaw())
                .setAudioStretchMode(mPlaybackParams.macapkStretchModeRaw());
    }

    public void setSyncParams(SyncParams params) {
        mSyncParams = Objects.requireNonNull(params, "params cannot be null");
    }

    /** @NonNull in AOSP. */
    public SyncParams getSyncParams() {
        return new SyncParams()
                .setSyncSource(mSyncParams.getSyncSource())
                .setAudioAdjustMode(mSyncParams.getAudioAdjustMode())
                .setTolerance(mSyncParams.getTolerance())
                .setFrameRate(mSyncParams.getFrameRate());
    }

    /** AOSP flushes the queued-but-unplayed buffers and returns them all
     *  through the callback, so the app can recycle them. */
    public void flush() {
        AudioTrack track = mAudioTrack;
        if (track != null) {
            try { track.flush(); } catch (Throwable ignore) { }
        }
        synchronized (mPending) { mPending.clear(); }
    }

    public void release() {
        mAudioTrack = null;
        mSurface = null;
        mCallback = null;
        mErrorListener = null;
        synchronized (mPending) { mPending.clear(); }
    }

    @Override protected void finalize() throws Throwable {
        try { release(); } finally { super.finalize(); }
    }

    private void notifyAudioBufferConsumed(final ByteBuffer buffer, final int bufferId) {
        final Callback cb = mCallback;
        if (cb == null) return;
        Handler handler = mCallbackHandler;
        if (handler == null) {
            cb.onAudioBufferConsumed(this, buffer, bufferId);
            return;
        }
        handler.post(new Runnable() {
            @Override public void run() { cb.onAudioBufferConsumed(MediaSync.this, buffer, bufferId); }
        });
    }

    private void notifyError(final int what, final int extra) {
        final OnErrorListener listener = mErrorListener;
        if (listener == null) return;
        Handler handler = mErrorHandler;
        if (handler == null) {
            listener.onError(this, what, extra);
            return;
        }
        handler.post(new Runnable() {
            @Override public void run() { listener.onError(MediaSync.this, what, extra); }
        });
    }

    /** Notified when a queued audio buffer has been consumed. */
    public abstract static class Callback {
        public Callback() { }
        public abstract void onAudioBufferConsumed(MediaSync sync, ByteBuffer audioBuffer,
                int bufferId);
    }

    public interface OnErrorListener {
        void onError(MediaSync sync, int what, int extra);
    }
}
