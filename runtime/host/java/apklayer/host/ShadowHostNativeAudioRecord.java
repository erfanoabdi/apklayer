package apklayer.host;

import android.media.AudioRecord;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** Original framework AudioRecord and its native capture path (AudioFlinger input thread over the audio HAL), not Robolectric's fake that returns silence at once. */
@Implements(value = AudioRecord.class, callNativeMethodsByDefault = true)
public class ShadowHostNativeAudioRecord {
    @Implementation protected static int native_get_min_buff_size(int rate, int channels, int format) {
        NativeAudio.ensureInitialized();
        return ReflectionHelpers.callStaticMethod(AudioRecord.class,
                Shadow.directNativeMethodName(AudioRecord.class.getName(), "native_get_min_buff_size"),
                ClassParameter.from(int.class, rate), ClassParameter.from(int.class, channels),
                ClassParameter.from(int.class, format));
    }
}
