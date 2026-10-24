// NativeAudio -- JNI bridge to the macOS CoreAudio backend
// (runtime/src/audio_backend.mm). Mirrors the NativeSqlite pattern: the
// native methods are bound at boot via RegisterNatives
// (native_methods.cpp -> audio::RegisterNativeAudioMethods), because the
// JVM does not auto-resolve Java_* symbols from the main executable on
// macOS.
//
// android.media.AudioTrack (and later SoundPool / MediaPlayer) call into
// here. A handle (long) identifies one output stream; 0 means "no backend"
// (the caller then behaves like the old silent stub).
package com.macapk.loader;

public final class NativeAudio {
    private NativeAudio() {}

    // Create an output stream. encoding is android.media.AudioFormat.ENCODING_*
    // (PCM_16BIT=2 / PCM_8BIT=3 / PCM_FLOAT=4); bufferFrames is the app's
    // requested buffer size expressed in frames. Returns a handle (>0) or 0.
    public static native long nativeCreate(int sampleRate, int channels,
                                           int encoding, int bufferFrames);

    // Streaming writes. Return the number of source elements (bytes/shorts/
    // floats) actually consumed. `blocking` mirrors AudioTrack's WRITE_BLOCKING
    // (true) vs WRITE_NON_BLOCKING (false): non-blocking queues whatever fits
    // right now and returns immediately, matching AOSP's documented contract
    // ("the write will return immediately after queuing as much audio data
    // for playback as possible without blocking" -- AudioTrack.java). See
    // audio_backend.mm's write_floats for why this matters: a caller with a
    // single-threaded pump loop (media3's AudioTrackAudioOutput is exactly
    // this) that explicitly asks for non-blocking must never be made to
    // block anyway, or its own position-refresh loop stalls with it.
    public static native int nativeWriteBytes(long h, byte[] data, int offset, int size, boolean blocking);
    public static native int nativeWriteShorts(long h, short[] data, int offset, int size, boolean blocking);
    public static native int nativeWriteFloats(long h, float[] data, int offset, int size, boolean blocking);

    public static native void nativePlay(long h);
    public static native void nativePause(long h);
    public static native void nativeStop(long h);
    public static native void nativeFlush(long h);
    public static native void nativeRelease(long h);
    public static native void nativeSetVolume(long h, float left, float right);
    public static native int  nativeGetPlayState(long h);

    // AudioTrack.getPlaybackHeadPosition()/getTimestamp() (audio_backend.mm:
    // Stream::played_frames / last_render_nanos). nativeGetPlaybackHeadPosition
    // returns the full unwrapped 64-bit frame count -- callers apply AOSP's
    // documented 32-bit wrap themselves via an int cast. nativeGetTimestamp
    // fills out[0]=frames (unwrapped), out[1]=hostTimeNanos (System.nanoTime()
    // timebase) and returns false (out left untouched) when no real frame has
    // been rendered since the last reset, mirroring AOSP's
    // native_get_timestamp() ERROR_WOULD_BLOCK case.
    public static native long    nativeGetPlaybackHeadPosition(long h);
    public static native boolean nativeGetTimestamp(long h, long[] out);

    // AudioTrack.setPlaybackRate / getPlaybackRate / setPlaybackParams /
    // getPlaybackParams (audio_backend.mm -- see the big comment above
    // macapk_audio_stream_set_playback_rate for how the three app-visible
    // knobs split across a resampler and a real time stretcher).
    //
    // nativeSetPlaybackRate and nativeSetPlaybackParams return AOSP's own
    // status codes -- AudioTrack.SUCCESS(0) / ERROR_BAD_VALUE(-2) /
    // ERROR_INVALID_OPERATION(-3) -- so the Java layer can hand
    // setPlaybackRate's result straight back to the app and turn
    // setPlaybackParams' BAD_VALUE into AOSP's
    // IllegalArgumentException("arguments out of range").
    //
    // nativeGetPlaybackRate answers the CURRENT playback rate, which is a
    // different number from AudioTrack.getSampleRate(): AOSP's Java
    // mSampleRate is written only by native_setup and audioParamCheck and
    // keeps reporting the construction rate, while getPlaybackRate() reads
    // the native track (frameworks/base .../AudioTrack.java:887,1855,2096).
    //
    // nativeGetPlaybackParams fills out[0]=speed, out[1]=pitch. It needs an
    // array because JNI has no float-pair return; the caller allocates.
    public static native int  nativeSetPlaybackRate(long h, int sampleRateInHz);
    public static native int  nativeGetPlaybackRate(long h);
    public static native int  nativeSetPlaybackParams(long h, float speed, float pitch);
    public static native void nativeGetPlaybackParams(long h, float[] out);

    // ---- SoundPool ----
    // Decode the encoded clip bytes (Ogg Vorbis / WAV / MP3 / M4A / ...) and
    // cache it; returns a soundID (>0) or 0 on failure.
    public static native int  nativeSpLoad(byte[] data, int len);
    // Play a loaded clip; returns a streamID (>0) or 0. loop: 0=once, -1=forever,
    // n=repeat n times. rate: playback pitch+speed, 1.0 = original (SoundPool
    // documents 0.5..2.0; the backend resamples via a fractional cursor).
    public static native int  nativeSpPlay(int soundID, float left, float right, int loop, float rate);
    public static native void nativeSpStop(int streamID);
    public static native void nativeSpPause(int streamID);
    public static native void nativeSpResume(int streamID);
    public static native void nativeSpSetVolume(int streamID, float left, float right);
    public static native void nativeSpSetLoop(int streamID, int loop);
    // Live pitch/speed modulation of a playing stream (SoundPool.setRate).
    // Engine-RPM loops (cocos2d-x setEffectRate) drive this per frame.
    public static native void nativeSpSetRate(int streamID, float rate);
    public static native boolean nativeSpUnload(int soundID);
    public static native void nativeSpAutoPause();
    public static native void nativeSpAutoResume();

    // ---- MediaPlayer ----
    public static native long nativeMpCreate();
    // Decode the encoded source bytes (WAV/MP3/M4A/Ogg/...) and bind them as
    // this player's clip; returns false on decode failure.
    public static native boolean nativeMpSetSource(long h, byte[] data, int len);
    public static native void nativeMpStart(long h);
    public static native void nativeMpPause(long h);
    public static native void nativeMpStop(long h);
    public static native void nativeMpSeek(long h, int ms);
    public static native void nativeMpSetLooping(long h, boolean loop);
    public static native void nativeMpSetVolume(long h, float left, float right);
    public static native int  nativeMpDuration(long h);
    public static native int  nativeMpPosition(long h);
    public static native boolean nativeMpIsPlaying(long h);
    public static native void nativeMpRelease(long h);

    // The track's frameCount (the app's request raised to the platform
    // minimum) and the fill threshold under it: AudioTrack
    // getBufferCapacityInFrames / setBufferSizeInFrames (audio_backend.mm
    // macapk_audio_stream_buffer_capacity / _set_buffer_size).
    public static native int nativeGetBufferCapacity(long h);
    public static native int nativeSetBufferSize(long h, int frames);
    // AudioTrack.getUnderrunCount: starved render cycles, one per episode.
    public static native int nativeGetUnderrunCount(long h);
}
