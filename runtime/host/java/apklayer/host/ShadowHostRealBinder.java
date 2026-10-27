package apklayer.host;

import android.os.Binder;
import org.robolectric.annotation.Implements;

/** The framework's own Binder (transact, linkToDeath, calling identity) over the real binder JNI, not Robolectric's in-process stand-in. */
@Implements(value = Binder.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealBinder {
}
