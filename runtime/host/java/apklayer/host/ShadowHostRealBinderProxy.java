package apklayer.host;

import org.robolectric.annotation.Implements;

/** BinderProxy (a handle to a binder in another JVM) over the real binder JNI: Robolectric answers an instrumented class's natives with default values unless told to call them. */
@Implements(className = "android.os.BinderProxy", callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealBinderProxy {
}
