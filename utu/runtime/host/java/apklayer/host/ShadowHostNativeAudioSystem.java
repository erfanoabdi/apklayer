package apklayer.host;

import android.media.AudioSystem;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Original AudioSystem queries and controls through the real audio services. */
@Implements(value = AudioSystem.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostNativeAudioSystem {
    @Implementation protected static void __staticInitializer__() {
        // The JNI adapter completes the original initializer immediately after capability registration.
        NativeMedia.ensureInitialized();
    }
}
