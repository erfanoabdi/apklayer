package apklayer.host;

import org.robolectric.annotation.Implements;

/** ParcelFileDescriptor's own code: descriptors cross processes in a real Parcel (dup'ed by the binder driver), Robolectric's stand-in only knows in-process ones. */
@Implements(className = "android.os.ParcelFileDescriptor", callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealParcelFileDescriptor {
}
