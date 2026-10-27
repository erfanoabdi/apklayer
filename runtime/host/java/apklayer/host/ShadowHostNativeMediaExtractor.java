package apklayer.host;

import android.media.MediaExtractor;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

/** apklayer patch: framework extractor API and real media JNI/binder implementation. */
@Implements(value = MediaExtractor.class, callNativeMethodsByDefault = true)
public class ShadowHostNativeMediaExtractor {
    @RealObject private MediaExtractor real;
    @Implementation protected static void __staticInitializer__() { /* deferred until registration */ }
    @Implementation protected void native_setup() {
        NativeMedia.ensureInitialized();
        ReflectionHelpers.callInstanceMethod(real,
                Shadow.directNativeMethodName(MediaExtractor.class.getName(), "native_setup"));
    }
}
