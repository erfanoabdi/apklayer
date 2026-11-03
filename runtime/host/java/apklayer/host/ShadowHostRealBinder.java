package apklayer.host;

import android.os.Binder;
import android.os.Parcel;
import android.os.Process;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * The framework's own Binder (transact, linkToDeath, calling identity) over the real binder JNI, not Robolectric's in-process stand-in. Every process has the desktop
 * user's Linux uid, so the calling uid is the app uid of the calling process: this one's for a local call, the hub's record for another app's pid, the uid in
 * the request header for a oneway call (no pid), the real uid (what native services run as) for a pid the hub does not know.
 */
@Implements(value = Binder.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealBinder {
    private static final ThreadLocal<Boolean> RESOLVING = new ThreadLocal<>();
    private static final ThreadLocal<Integer> HEADER_UID = new ThreadLocal<>();
    private static final Map<Integer, Integer> APP_UIDS = new ConcurrentHashMap<>();
    private static final int SYST = 0x53595354, VNDR = 0x564e4452;  // libbinder's interface-token header words (Parcel.cpp kHeader)

    @RealObject private Binder real;

    @Implementation protected static int getCallingUid() {
        int pid = Binder.getCallingPid();
        if (pid == Process.myPid()) return Process.myUid();
        if (HubClient.connected() && pid == HubClient.current().hubPid()) return android.os.Process.SYSTEM_UID;  // the hub JVM stands for system_server
        if (pid == 0) {  // oneway: the kernel gives no calling pid; the sender's uid came in the request header (ShadowHostRealParcel)
            Integer sender = HEADER_UID.get();
            // no header (a raw parcel, native code): nobody, not the desktop user's uid, which can be 1000, the system's
            return sender != null ? sender : Process.NOBODY_UID;
        }
        Integer known = APP_UIDS.get(pid);
        if (known != null) return known;
        if (Boolean.TRUE.equals(RESOLVING.get())) return ShadowHostProcess.realUidValue();  // the hub call below went through a proxy transaction listener (MediaProvider's) that asks the calling uid again
        RESOLVING.set(true);
        int uid;
        try { uid = HubClient.connected() ? HubClient.current().uidOf(pid) : -1; }
        finally { RESOLVING.remove(); }
        if (uid < 0) return ShadowHostProcess.realUidValue();
        APP_UIDS.put(pid, uid);
        return uid;
    }

    /**
     * Every incoming transaction (from the JNI): a oneway request's sender is read from its header here, as the kernel's sender uid is known before any code
     * runs; ActivityThread's ApplicationThread and Binder's own accounting ask getCallingUid before the stub's enforceInterface reads the work source.
     */
    @Implementation protected boolean execTransact(int code, long dataObj, long replyObj, int flags) {
        Integer previous = HEADER_UID.get();
        HEADER_UID.set(Binder.getCallingPid() == 0 ? headerUid(dataObj) : null);
        try {
            return Shadow.directlyOn(real, "android.os.Binder", "execTransact", ClassParameter.from(int.class, code), ClassParameter.from(long.class, dataObj),
                    ClassParameter.from(long.class, replyObj), ClassParameter.from(int.class, flags));
        } finally { HEADER_UID.set(previous); }
    }

    /** The work-source uid of an interface-token header (strict-mode policy, work source, header word, descriptor), or null without one. */
    private static Integer headerUid(long dataObj) {
        Parcel data = ReflectionHelpers.callStaticMethod(Parcel.class, "obtain", ClassParameter.from(long.class, dataObj));
        int at = data.dataPosition();
        try {
            if (data.dataAvail() < 12) return null;
            data.readInt();
            int uid = data.readInt(), header = data.readInt();
            return (header == SYST || header == VNDR) && uid >= 0 ? uid : null;
        } finally { data.setDataPosition(at); data.recycle(); }
    }
}
