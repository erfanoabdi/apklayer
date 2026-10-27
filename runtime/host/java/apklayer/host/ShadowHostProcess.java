package apklayer.host;

import android.os.Process;
import java.nio.file.Files;
import java.nio.file.Path;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowProcess;

/** Real pid and uid: framework code compares them with Binder.getCallingPid/Uid to tell a local call from a remote one. */
@Implements(value = Process.class, isInAndroidSdk = false)
public class ShadowHostProcess extends ShadowProcess {
    private static final int PID = (int) ProcessHandle.current().pid();
    private static final int UID = realUid();

    private static int realUid() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status")))
                if (line.startsWith("Uid:")) return Integer.parseInt(line.substring(4).trim().split("\\s+")[0]);
        } catch (Exception e) {
            // fall through
        }
        return 1000;
    }

    @Implementation protected static int myPid() { return PID; }
    @Implementation protected static int myUid() { return UID; }
}
