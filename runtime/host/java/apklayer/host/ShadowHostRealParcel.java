package apklayer.host;

import android.os.Binder;
import android.os.Parcel;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * The framework's own Parcel over the real native Parcel (what a binder transaction carries), not Robolectric's Java byte-buffer Parcel.
 * A request's interface-token header carries this process's Android uid as its work source (read by Binder.getCallingWorkSourceUid() after enforceInterface on the
 * other side): all JVMs run as one Linux uid and a oneway transaction has no calling pid, so that is how ShadowHostRealBinder.getCallingUid names the caller then.
 */
@Implements(value = Parcel.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealParcel {
    @RealObject private Parcel real;

    @Implementation public final void writeInterfaceToken(String interfaceName) {
        long token = Binder.setCallingWorkSourceUid(android.os.Process.myUid());
        try { Shadow.directlyOn(real, "android.os.Parcel", "writeInterfaceToken", ClassParameter.from(String.class, interfaceName)); }
        finally { Binder.restoreCallingWorkSource(token); }
    }
}
