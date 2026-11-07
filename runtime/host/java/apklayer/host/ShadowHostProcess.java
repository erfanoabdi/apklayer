package apklayer.host;

import android.os.Process;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowProcess;

/** Real pid and uid: framework code compares them with Binder.getCallingPid/Uid to tell a local call from a remote one. */
@Implements(value = Process.class, isInAndroidSdk = false)
public class ShadowHostProcess extends ShadowProcess {
    private static final int PID = (int) ProcessHandle.current().pid();
    // the desktop user's Linux uid is apklayer's own (native services, the hub, a spare not yet given its app): Android's system uid, as libbinder's
    // patch 0007 says. Taken raw it names another user from 100000 up (Sailfish OS's defaultuser is 100000: user 1) or an app (Ubuntu Touch's 32011).
    private static final int OWN_UID = Process.SYSTEM_UID;

    static int ownUid() { return OWN_UID; }

    @Implementation protected static int myPid() { return PID; }
    // the package's Android app uid (bin/apklayer), the system uid without one; read per call: a spare learns it late (AppSpare)
    @Implementation protected static int myUid() { return Integer.getInteger("apklayer.uid", OWN_UID); }
}
