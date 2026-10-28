package apklayer.host;

import android.os.IBinder;
import android.os.Parcel;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * BinderProxy (a handle to a binder in another JVM) over the real binder JNI: Robolectric answers an instrumented class's natives with default values unless told to
 * call them. With -Dapklayer.logcat every transaction through a proxy is printed (code, sizes, how it ended).
 */
@Implements(className = "android.os.BinderProxy", callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealBinderProxy {
    @RealObject private IBinder real;

    @Implementation public boolean transact(int code, Parcel data, Parcel reply, int flags) {
        if (!Boolean.getBoolean("apklayer.logcat")) return direct(code, data, reply, flags);
        int sent = data == null ? -1 : data.dataSize();
        long started = System.nanoTime();
        try {
            boolean ok = direct(code, data, reply, flags);
            System.out.println("[apklayer] proxy transact " + code + " flags " + flags + " sent " + sent + " bytes -> " + ok + ", reply " + (reply == null ? -1 : reply.dataSize()) + " bytes, " + (System.nanoTime() - started) / 1000000 + " ms");
            return ok;
        } catch (RuntimeException failure) {
            System.out.println("[apklayer] proxy transact " + code + " failed: " + failure);
            throw failure;
        }
    }

    private boolean direct(int code, Parcel data, Parcel reply, int flags) {
        try {
            return ReflectionHelpers.callInstanceMethod(real, Shadow.directMethodName("android.os.BinderProxy", "transact"), ClassParameter.from(int.class, code),
                    ClassParameter.from(Parcel.class, data), ClassParameter.from(Parcel.class, reply), ClassParameter.from(int.class, flags));
        } catch (RuntimeException wrapped) {
            // ReflectionHelpers wraps the checked RemoteException (DeadObjectException) that BinderProxy.transact declares; callers catch it by type
            if (wrapped.getCause() instanceof android.os.RemoteException remote) throw ShadowHostRealBinderProxy.<RuntimeException>sneaky(remote);
            throw wrapped;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneaky(Throwable failure) throws T { throw (T) failure; }
}
