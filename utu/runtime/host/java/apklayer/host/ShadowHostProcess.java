package apklayer.host;

import android.os.Process;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowProcess;

/** Real pid and uid: framework code compares them with Binder.getCallingPid/Uid to tell a local call from a remote one. */
@Implements(value = Process.class, isInAndroidSdk = false)
public class ShadowHostProcess extends ShadowProcess {
    private static final int PID = (int) ProcessHandle.current().pid();
    private static final int REAL_UID = realUid();
    private static final int UID = Integer.getInteger("apklayer.uid", REAL_UID); // the package's Android app uid (bin/apklayer), the desktop user's uid without one

    static int realUidValue() { return REAL_UID; }

    private static int realUid() {
        return Math.toIntExact(new com.sun.security.auth.module.UnixSystem().getUid());
    }

    @Implementation protected static int myPid() { return PID; }
    @Implementation protected static int myUid() { return UID; }
}
