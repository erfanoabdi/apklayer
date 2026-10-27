package apklayer.host;

import android.media.AudioTrack;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** Original framework AudioTrack and native buffers, clock and callbacks. */
@Implements(value = AudioTrack.class, callNativeMethodsByDefault = true)
public class ShadowHostNativeAudioTrack {
    @Implementation protected static int native_get_min_buff_size(int rate, int channels, int format) {
        NativeAudio.ensureInitialized();
        return ReflectionHelpers.callStaticMethod(AudioTrack.class,
                Shadow.directNativeMethodName(AudioTrack.class.getName(), "native_get_min_buff_size"),
                ClassParameter.from(int.class, rate), ClassParameter.from(int.class, channels),
                ClassParameter.from(int.class, format));
    }
    @Implementation protected static int native_get_output_sample_rate(int stream) {
        NativeAudio.ensureInitialized();
        return ReflectionHelpers.callStaticMethod(AudioTrack.class,
                Shadow.directNativeMethodName(AudioTrack.class.getName(), "native_get_output_sample_rate"),
                ClassParameter.from(int.class, stream));
    }
}
