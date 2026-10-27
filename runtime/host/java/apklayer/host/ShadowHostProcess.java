package apklayer.host;

import android.os.Process;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowProcess;

/** Real pid and uid: framework code compares them with Binder.getCallingPid/Uid to tell a local call from a remote one. */
@Implements(value = Process.class, isInAndroidSdk = false)
public class ShadowHostProcess extends ShadowProcess {
    private static final int PID = (int) ProcessHandle.current().pid();
    private static final int UID = realUid();

    private static int realUid() {
        return Math.toIntExact(new com.sun.security.auth.module.UnixSystem().getUid());
    }

    @Implementation protected static int myPid() { return PID; }
    @Implementation protected static int myUid() { return UID; }
}
