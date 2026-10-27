package apklayer.host;

import android.os.Parcel;
import org.robolectric.annotation.Implements;

/** The framework's own Parcel over the real native Parcel (what a binder transaction carries), not Robolectric's Java byte-buffer Parcel. */
@Implements(value = Parcel.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealParcel {
}
