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
    @Implementation @Override protected void setStreamVolume(int stream, int index, int flags) {
        NativeAudio.ensureInitialized();
        super.setStreamVolume(stream, index, flags);
        NativeAudio.check(AudioSystem.setStreamVolumeIndexAS(stream, super.getStreamVolume(stream),
                real.isStreamMute(stream), AudioSystem.DEVICE_OUT_SPEAKER));
    }
    @Implementation @Override protected void adjustStreamVolume(int stream, int direction, int flags) {
        if (direction == AudioManager.ADJUST_RAISE || direction == AudioManager.ADJUST_LOWER) {
            setStreamVolume(stream, getStreamVolume(stream) + (direction == AudioManager.ADJUST_RAISE ? 1 : -1), flags);
        } else {
            if (direction == AudioManager.ADJUST_TOGGLE_MUTE)
                direction = real.isStreamMute(stream) ? AudioManager.ADJUST_UNMUTE : AudioManager.ADJUST_MUTE;
            super.adjustStreamVolume(stream, direction, flags);
            NativeAudio.apply(real, stream);
        }
    }
    @Implementation @Override protected int generateAudioSessionId() {
        NativeAudio.ensureInitialized();
        return AudioSystem.newAudioSessionId();
    }
}
