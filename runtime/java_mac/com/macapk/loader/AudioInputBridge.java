// AudioInputBridge -- JNI bridge to the macOS microphone capture backend
// (runtime/src/audio_input.mm). Mirrors the NativeAudio pattern exactly: the
// native methods are bound at boot via RegisterNatives (native_methods.cpp ->
// audioin::RegisterAudioInputMethods), because the JVM does not auto-resolve
// Java_* symbols from the main executable on macOS.
//
// android.media.AudioRecord calls into here. A handle (long) identifies one
// capture stream; 0 means "no microphone" -- and unlike the output path, the
// caller must NOT degrade to a silent-but-successful stub when it gets 0. A
// recorder that reports STATE_INITIALIZED and then hands back zero-filled
// buffers gives an app a valid-looking frame count and nothing to point at,
// which is the exact dishonesty this bridge exists to remove. AudioRecord
// reports STATE_UNINITIALIZED instead.
package com.macapk.loader;

public final class AudioInputBridge {
    private AudioInputBridge() {}

    // macOS TCC microphone-authorization state; mirrors AVAuthorizationStatus
    // and the MACAPK_MIC_PERM_* enum in runtime/include/macapk/audio_input.h.
    public static final int PERM_UNKNOWN    = 0;   // not determined (will prompt)
    public static final int PERM_GRANTED    = 1;
    public static final int PERM_DENIED     = 2;
    public static final int PERM_RESTRICTED = 3;

    // Whether the native bridge is bound. A framework-only context (a unit
    // test, or a runtime built without the backend) sees false rather than an
    // UnsatisfiedLinkError escaping into guest code.
    //
    // Computed LAZILY on first use, never in a static initializer. HotSpot's
    // JNI FindClass initializes the class it returns (jni_FindClass passes
    // init=true), so a `static final boolean AVAILABLE = probe()` would run
    // from inside RegisterAudioInputMethods' own FindClass call -- i.e. BEFORE
    // its RegisterNatives had bound anything -- and latch false for the life of
    // the process. -1 unknown / 0 no / 1 yes.
    private static volatile int sAvailable = -1;

    public static boolean available() {
        int a = sAvailable;
        if (a < 0) {
            try {
                nativePermission();
                a = 1;
            } catch (Throwable t) {
                a = 0;
            }
            sAvailable = a;
        }
        return a == 1;
    }

    // Pure query: never prompts, never opens the device.
    public static native int nativePermission();

    // Open a capture stream. encoding is android.media.AudioFormat.ENCODING_*
    // (PCM_16BIT=2 / PCM_8BIT=3 / PCM_FLOAT=4); bufferFrames is the app's
    // requested buffer expressed in frames. Returns a handle (> 0) only when
    // capture is genuinely live, else 0. May block briefly the very first time
    // while the user answers the system microphone prompt (bounded -- see
    // MACAPK_MIC_PERMISSION_TIMEOUT_MS).
    public static native long nativeOpen(int sampleRate, int channels,
                                         int encoding, int bufferFrames);

    public static native void nativeStart(long h);
    public static native void nativeStop(long h);
    public static native void nativeRelease(long h);

    // Drain captured audio in the stream's encoding. blocking != 0 waits for
    // the full request (bounded); 0 returns what is buffered. Returns element
    // counts (bytes / shorts / floats), never negative.
    public static native int nativeReadBytes(long h, byte[] data, int offset,
                                             int size, int blocking);
    public static native int nativeReadShorts(long h, short[] data, int offset,
                                              int size, int blocking);
    public static native int nativeReadFloats(long h, float[] data, int offset,
                                              int size, int blocking);

    // Frames captured but not yet read (diagnostic / pacing).
    public static native int nativeBufferedFrames(long h);

    // Nominal rate of the default input device, or 0 if none. Diagnostic: it
    // says how much resampling sits between the device and the app.
    public static native int nativeHwSampleRate();

    // Convenience wrappers that keep the "backend missing" case out of every
    // call site in AudioRecord.
    public static int permission() {
        if (!available()) return PERM_DENIED;
        try {
            return nativePermission();
        } catch (Throwable t) {
            return PERM_DENIED;
        }
    }

    public static long open(int sampleRate, int channels, int encoding,
                            int bufferFrames) {
        if (!available()) return 0L;
        try {
            return nativeOpen(sampleRate, channels, encoding, bufferFrames);
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static int hwSampleRate() {
        if (!available()) return 0;
        try {
            return nativeHwSampleRate();
        } catch (Throwable t) {
            return 0;
        }
    }
}
