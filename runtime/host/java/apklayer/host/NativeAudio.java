package apklayer.host;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioSystem;
import org.robolectric.RuntimeEnvironment;

/** Forward Java volume state to the real policy, which normally receives it from AudioService. */
final class NativeAudio {
    private static boolean initialized, initializing;
    private NativeAudio() {}
    static synchronized boolean isInitializing() { return initializing; }
    static synchronized void ensureInitialized() {
        if (initialized || initializing) return;
        initializing = true;
        try { initialized = initVolumes(); } finally { initializing = false; }
    }
    /** The stream volume ranges and indexes AudioService sets at boot: the hub calls it for every audioserver that registers (apps that play
     * natively, AAudio/OpenSL as Gecko, never reach a Java AudioTrack, and an uninitialised range mixes to silence: gain NaN), an app JVM before its
     * first Java audio call. A range another process set stays, with its index. */
    static boolean initVolumes() {
        NativeMedia.ensureInitialized();
        AudioManager manager = (AudioManager) RuntimeEnvironment.systemContext.getSystemService(Context.AUDIO_SERVICE);
        for (int stream = 0; stream < AudioSystem.getNumStreamTypes(); stream++) {
            int min = manager.getStreamMinVolume(stream), max = manager.getStreamMaxVolume(stream);
            if (min < 0 || max < min) continue; // Not exposed by the Java service.
            AudioAttributes attributes = new AudioAttributes.Builder().setInternalLegacyStreamType(stream).build();
            if (AudioSystem.getMaxVolumeIndexForAttributes(attributes) >= 0) continue;
            if (!ok(AudioSystem.initStreamVolume(stream, min, max))) return false;  // tried again on the next call, as AudioService retries
            // Robolectric's manager says 0, which is silence; every stream sits at its maximum and the host's volume control is the volume
            if (!ok(AudioSystem.setStreamVolumeIndexAS(stream, max, false, AudioSystem.DEVICE_OUT_SPEAKER))) return false;
        }
        return true;
    }
    static void apply(AudioManager manager, int stream) {
        ensureInitialized();
        ok(AudioSystem.setStreamVolumeIndexAS(stream, manager.getStreamVolume(stream),
                manager.isStreamMute(stream), AudioSystem.DEVICE_OUT_SPEAKER));
    }
    private static boolean reported;
    /** A policy without outputs (no PipeWire on the machine) refuses volumes: AudioService logs that and the app goes on (CPU-Z's ads SDK died on the exception). */
    private static boolean ok(int status) {
        if (status == AudioSystem.SUCCESS) return true;
        if (!reported) { reported = true; System.out.println("[apklayer] audio policy refuses stream volumes (status " + status + "): no audio output?"); }
        return false;
    }
}
