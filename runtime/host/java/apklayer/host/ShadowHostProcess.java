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

    /** SIGKILL, as on Android: this process ends at once (the hub sees it die: its windows go, the activities it showed are finished), or another
     *  process of this app (Android lets an app kill only its own uid's processes; the hub knows them). Robolectric's only records the pid. */
    @Implementation protected static void killProcess(int pid) {
        if (HubHost.hub) return;  // never the hub's JVM (system_server)
        if (pid == PID) {
            // the restart idiom (an activity start, then the kill: ApklayerExit's rule): the started activity, in this process, is the restart
            String last = System.getProperty("apklayer.laststart");
            if (last != null && System.nanoTime() / 1_000_000 - Long.parseLong(last) < 5000) { System.out.println("[apklayer] killProcess(myPid) after an activity start: the restart is the started activity"); return; }
            // as ActivityManagerService after the death: the activity below the top one comes back in a new process (FCL's launcher after the game)
            android.content.Intent below = ActivityTask.belowTop();
            if (below != null && HubClient.connected()) {
                try { HubProtocol.call(HubClient.current().binder(), HubProtocol.DESCRIPTOR, HubProtocol.RESTART_ON_DEATH, request -> request.writeTypedObject(below, 0), reply -> null); }
                catch (RuntimeException hubGone) { /* nothing comes back then */ }
            }
            System.out.println("[apklayer] Process.killProcess(myPid): this process ends" + (below != null ? ", " + below.getComponent() + " comes back in a new one" : "")); System.out.flush();
            NativeLoader.endWithoutNativeDestructors();
            Runtime.getRuntime().halt(128 + 9);
        }
        if (ShadowHostActivityManager.ownProcesses().stream().anyMatch(p -> p.pid == pid)) ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
    }
}
