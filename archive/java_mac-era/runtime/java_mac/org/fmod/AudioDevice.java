// org.fmod.AudioDevice -- RETIRED SHADOW (2026-09-27, MACAPK_RETIRE_ENGINE_SHADOWS,
// default ON). An app that bundles its own org.fmod.AudioDevice now gets ITS
// OWN class: AppClassLoader defines the app's copy for any framework class
// carrying the retired-shadow marker below, exactly as on Android, whose boot
// classpath has no org.fmod class. This shadow is reached only (a) under
// MACAPK_RETIRE_ENGINE_SHADOWS=0 or MACAPK_GLSV_PAUSE=0 (that knob clears the
// retirement too; harmless, it restores the pair that shipped), (b) by an app
// that bundles no org.fmod.AudioDevice, or (c) by a native-thread JNI
// FindClass under MACAPK_JNI_FINDCLASS_NOINIT=0, which still asks HotSpot's
// system loader (the framework jar) first. Removal condition: the live
// falsifier in docs/fmod-audiodevice-shadow-retired_2026-09-27.md passes on the
// installed stub (GD Lite: the "retired shadow -> app's own copy" classload
// line, NO "AudioDevice::init(channels=" line, chCfg=3, audio non-silent);
// then DELETE this file (that moves FRAMEWORK_ID, so batch it with another
// framework-shape change).
//
// Why it existed, and why that reason is gone. It was added in c20c9434
// (2026-05-16) to clamp the garbage libfmod handed init(IIII)Z (~1.8e9,
// ~1.8e9-80, ~1.8e9-128, -40): HotSpot reading libfmod's AAPCS64 va_list
// (__stack, __gr_top, __vr_top, __gr_offs) as an Apple char* at 8-byte stride,
// never a config query reading uninitialized memory (the old claim). The
// Bionic JNI variadic bridge (bionic_shim.cpp MACAPK_BIONIC_JNI_BRIDGE) now
// repacks it the way ART's va_arg does, and the native twin that forced
// 2/22050/256/4 is retired behind MACAPK_FMOD_OWN_JNI_ARGS
// (docs/fmod-jni-audio-args-retire_2026-09-27.md). With real args (2,
// 24000|44100, 512, 4) none of the clamps below fire. The "masks the
// inflate_state heap corruption" claim was refuted too: that was intra-thread
// in cocos's zlib 1.2.8 (docs/media-audio-camera-parity-audit_2026-08-30.md).
//
// One behavioural difference, and it is OURS: with no native track this
// shadow's getState() check returns false while the app's class returns true,
// because our AudioTrack.play() no-ops on an uninitialized track where AOSP's
// throws IllegalStateException and the app's catch returns false. The generic
// fix is an AOSP-faithful AudioTrack, not a shadow; it cannot occur while the
// AVAudioEngine backend is up. Body unchanged: header + marker keep every line
// number (stack traces, [audio-trace] "from" frames) identical under =0.
package org.fmod;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

@Deprecated(since = com.macapk.loader.AppClassLoader.RETIRED_SHADOW, forRemoval = true)
public final class AudioDevice {
    private static final String TAG = "fmod";
    private AudioTrack mTrack;

    public AudioDevice() {}

    public boolean init(int channels, int sampleRate,
                        int framesPerBuffer, int bufferCount) {
        // DIAGNOSTIC: log the raw values libfmod passes via JNI, BEFORE any
        // clamp below. These are FMOD's own output format (see the header
        // correction); this line is what the MACAPK_FMOD_OWN_JNI_ARGS
        // falsifier reads (debug/geh_fmod-jni-audio-args-patch_gate.sh --log).
        Log.i(TAG, "AudioDevice::init(channels=" + channels
                + ", sampleRate=" + sampleRate
                + ", framesPerBuffer=" + framesPerBuffer
                + ", bufferCount=" + bufferCount + ")");

        // Historical: libfmod used to hand us garbage here (sequential
        // ~1.8B values, really the misread va_list; see the header). Cap to
        // typical Android mixer-thread values. 1024 frames per buffer + 4
        // buffers covers the common 5.x-ms latency target with headroom.
        int safeFrames = framesPerBuffer;
        int safeBufs   = bufferCount;
        if (safeFrames <= 0 || safeFrames > 8192) safeFrames = 1024;
        if (safeBufs   <= 0 || safeBufs   > 16)   safeBufs   = 4;
        if (channels   <= 0 || channels   > 8)    channels   = 2;
        if (sampleRate <= 0 || sampleRate > 192000) sampleRate = 44100;

        int channelConfig = fetchChannelConfigFromCount(channels);
        int min = AudioTrack.getMinBufferSize(sampleRate, channelConfig,
                                              AudioFormat.ENCODING_PCM_16BIT);
        Log.i(TAG, "AudioDevice::init : Min buffer size: "
              + Math.max(min, 0) + " bytes");

        int want = safeFrames * safeBufs * channels * 2;
        int actual = (want > min) ? want : Math.max(min, 4096);
        Log.i(TAG, "AudioDevice::init : Actual buffer size: " + actual + " bytes");

        try {
            mTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                                     channelConfig,
                                     AudioFormat.ENCODING_PCM_16BIT,
                                     actual, AudioTrack.MODE_STREAM);
            if (mTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                mTrack = null;
                return false;
            }
            mTrack.play();
        } catch (Throwable t) {
            mTrack = null;
            return false;
        }
        return true;
    }

    public void close() {
        if (mTrack != null) {
            try { mTrack.stop(); } catch (Throwable ignored) {}
            try { mTrack.release(); } catch (Throwable ignored) {}
            mTrack = null;
        }
    }

    public void write(short[] data, int len) {
        if (mTrack != null && data != null && len > 0) {
            try { mTrack.write(data, 0, Math.min(len, data.length)); }
            catch (Throwable ignored) {}
        }
    }

    private int fetchChannelConfigFromCount(int channels) {
        switch (channels) {
            case 1: return AudioFormat.CHANNEL_OUT_MONO;
            case 2: return AudioFormat.CHANNEL_OUT_STEREO;
            default: return AudioFormat.CHANNEL_OUT_STEREO;
        }
    }
}
