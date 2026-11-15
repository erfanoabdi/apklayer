package apklayer.host;

import android.media.AudioRecord;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;
import org.robolectric.util.reflector.Reflector;

/** Original framework AudioRecord and its native capture path (AudioFlinger input thread over the audio HAL), not Robolectric's fake that returns silence at once. */
@Implements(value = AudioRecord.class, callNativeMethodsByDefault = true)
public class ShadowHostNativeAudioRecord {
    @RealObject private AudioRecord real;
    @Implementation protected static int native_get_min_buff_size(int rate, int channels, int format) {
        NativeAudio.ensureInitialized();
        return ReflectionHelpers.callStaticMethod(AudioRecord.class,
                Shadow.directNativeMethodName(AudioRecord.class.getName(), "native_get_min_buff_size"),
                ClassParameter.from(int.class, rate), ClassParameter.from(int.class, channels),
                ClassParameter.from(int.class, format));
    }
    // a recording process counts as active for the call mode's owner (ShadowHostPlayerBase.activity)
    @Implementation protected void startRecording() {
        Reflector.reflector(Direct_.class, real).startRecording();
        ShadowHostPlayerBase.activity(real, real.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING);
    }
    @Implementation protected void stop() {
        Reflector.reflector(Direct_.class, real).stop();
        ShadowHostPlayerBase.activity(real, false);
    }
    @Implementation protected void release() {
        Reflector.reflector(Direct_.class, real).release();
        ShadowHostPlayerBase.activity(real, false);
    }
    @ForType(AudioRecord.class)
    interface Direct_ {
        @Direct void startRecording();
        @Direct void stop();
        @Direct void release();
    }
}
