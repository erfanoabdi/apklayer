package apklayer.host;

import android.media.MediaCodec;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** apklayer patch: real Java MediaCodec and device JNI; defer only native library setup. */
@Implements(value = MediaCodec.class, callNativeMethodsByDefault = true)
public class ShadowHostNativeMediaCodec {
    @RealObject private MediaCodec real;
    @Implementation protected static void __staticInitializer__() { /* registered by runtime shim */ }
    @Implementation protected void native_setup(String name, boolean type, boolean encoder, int pid, int uid) {
        NativeMedia.ensureInitialized();
        ReflectionHelpers.callInstanceMethod(real,
                Shadow.directNativeMethodName(MediaCodec.class.getName(), "native_setup"),
                ClassParameter.from(String.class, name), ClassParameter.from(boolean.class, type),
                ClassParameter.from(boolean.class, encoder), ClassParameter.from(int.class, pid),
                ClassParameter.from(int.class, uid));
    }
}
