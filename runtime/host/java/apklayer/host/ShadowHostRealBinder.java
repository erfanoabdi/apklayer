package apklayer.host;

import android.os.Binder;
import android.os.Process;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * The framework's own Binder (transact, linkToDeath, calling identity) over the real binder JNI, not Robolectric's in-process stand-in. Every process has the desktop
 * user's Linux uid, so the calling uid is the app uid of the calling process: this one's for a local call, the hub's record for another app's pid, the real uid
 * (what native services run as) for a pid the hub does not know.
 */
@Implements(value = Binder.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealBinder {
    private static final Map<Integer, Integer> APP_UIDS = new ConcurrentHashMap<>();

    @Implementation protected static int getCallingUid() {
        int pid = Binder.getCallingPid();
        if (pid == Process.myPid()) return Process.myUid();
        Integer known = APP_UIDS.get(pid);
        if (known != null) return known;
        int uid = HubClient.connected() ? HubClient.current().uidOf(pid) : -1;
        if (uid < 0) return ShadowHostProcess.realUidValue();
        APP_UIDS.put(pid, uid);
        return uid;
    }
}
