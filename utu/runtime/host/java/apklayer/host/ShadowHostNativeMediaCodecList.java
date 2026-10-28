package apklayer.host;

import android.media.MediaCodecList;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

/** apklayer patch: codec capabilities come from the real media library and HAL. */
@Implements(value = MediaCodecList.class, callNativeMethodsByDefault = true)
public class ShadowHostNativeMediaCodecList {
    @Implementation protected static void __staticInitializer__() { /* deferred until registration */ }
    @Implementation protected static int native_getCodecCount() {
        NativeMedia.ensureInitialized();
        return ReflectionHelpers.callStaticMethod(MediaCodecList.class,
                Shadow.directNativeMethodName(MediaCodecList.class.getName(), "native_getCodecCount"));
    }
}
