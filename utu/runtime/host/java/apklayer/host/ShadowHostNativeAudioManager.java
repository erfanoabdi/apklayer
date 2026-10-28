package apklayer.host;

import android.media.AudioManager;
import android.media.AudioSystem;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowAudioManager;

/** Java service bookkeeping with real policy volume and global session allocation. */
@Implements(AudioManager.class)
public class ShadowHostNativeAudioManager extends ShadowAudioManager {
    @RealObject private AudioManager real;
    @Implementation @Override protected int getStreamVolume(int stream) {
        if (NativeAudio.isInitializing()) return super.getStreamVolume(stream);
        NativeAudio.ensureInitialized();
        int index = AudioSystem.getStreamVolumeIndex(stream, AudioSystem.DEVICE_OUT_SPEAKER);
        return index >= 0 ? index : super.getStreamVolume(stream);
    }
    // The Android volume stays at its maximum for every stream: the host's volume control (the PipeWire sink) is the one volume of the
    // machine, so an app's own volume keys, sliders and mutes change nothing here (the request is accepted and ignored).
    @Implementation @Override protected void setStreamVolume(int stream, int index, int flags) {
        NativeAudio.ensureInitialized();
    }
    @Implementation @Override protected void adjustStreamVolume(int stream, int direction, int flags) {
        NativeAudio.ensureInitialized();
    }
    @Implementation @Override protected int generateAudioSessionId() {
        NativeAudio.ensureInitialized();
        return AudioSystem.newAudioSessionId();
    }
}
