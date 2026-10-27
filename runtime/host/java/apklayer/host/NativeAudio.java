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
        NativeMedia.ensureInitialized();
        if (initialized || initializing) return;
        initializing = true;
        try {
            AudioManager manager = (AudioManager) RuntimeEnvironment.systemContext
                    .getSystemService(Context.AUDIO_SERVICE);
            for (int stream = 0; stream < AudioSystem.getNumStreamTypes(); stream++) {
                int min = manager.getStreamMinVolume(stream), max = manager.getStreamMaxVolume(stream);
                if (min < 0 || max < min) continue; // Not exposed by the Java service.
                AudioAttributes attributes = new AudioAttributes.Builder().setInternalLegacyStreamType(stream).build();
                // Preserve the shared policy's index when another JVM already initialized this group.
                if (AudioSystem.getMaxVolumeIndexForAttributes(attributes) >= 0) continue;
                check(AudioSystem.initStreamVolume(stream, min, max));
                // Robolectric's manager says 0, which is silence; every stream sits at its maximum and the host's volume control is the volume
                check(AudioSystem.setStreamVolumeIndexAS(stream, max, false, AudioSystem.DEVICE_OUT_SPEAKER));
            }
            initialized = true;
        } finally { initializing = false; }
    }
    static void apply(AudioManager manager, int stream) {
        ensureInitialized();
        check(AudioSystem.setStreamVolumeIndexAS(stream, manager.getStreamVolume(stream),
                manager.isStreamMute(stream), AudioSystem.DEVICE_OUT_SPEAKER));
    }
    static void check(int status) {
        if (status != AudioSystem.SUCCESS) throw new IllegalStateException("Native audio policy status " + status);
    }
}
