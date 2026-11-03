package apklayer.host;

import android.app.ActivityManager;
import android.app.Application;
import android.app.IActivityManager;
import android.os.Process;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowActivityManager;

/** Robolectric's activity manager, with the calls that cross processes (receivers, broadcasts) going to the instance hub once the app is connected to it. */
@Implements(value = ActivityManager.class, isInAndroidSdk = false)
public class ShadowHostActivityManager extends ShadowActivityManager {
    @Implementation protected static IActivityManager getService() {
        return HubActivityManager.service(ShadowActivityManager.getService());
    }

    /** This process under its own name (Robolectric names it after the package): apps tell their main process from a secondary one (android:process) by it. */
    @Implementation
    @Override
    protected List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() {
        List<ActivityManager.RunningAppProcessInfo> processes = super.getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo p : processes) if (p.pid == Process.myPid()) p.processName = Application.getProcessName();
        return processes;
    }
}
