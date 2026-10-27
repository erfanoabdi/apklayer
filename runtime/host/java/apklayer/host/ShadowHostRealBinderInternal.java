package apklayer.host;

import org.robolectric.annotation.Implements;

/** BinderInternal's natives (death-notification bookkeeping, proxy counts) over the real binder JNI. */
@Implements(className = "com.android.internal.os.BinderInternal", callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealBinderInternal {
}
