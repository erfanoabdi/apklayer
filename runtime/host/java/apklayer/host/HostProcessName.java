package apklayer.host;

import android.content.ComponentName;
import android.content.pm.ServiceInfo;
import org.robolectric.RuntimeEnvironment;

/**
 * The name of the process (Application.getProcessName): a service host of an own-package service in a process of its own (GeckoView's child processes) reports that process's name ("package:tab0"), as Android does: apps tell their main
 * process from the others by it (Application.getProcessName, ActivityManager.getRunningAppProcesses: Fenix returns early from Application.onCreate in a secondary process).
 */
final class HostProcessName {
    private HostProcessName() {}

    /**
     * Names this process before its Application exists (Fenix asks in attachBaseContext and keeps the answer): its own-package service's process name
     * in a component host; every other process keeps the package's name (ShadowActivityThread.currentProcessName falls back to it).
     */
    static void install() {
        String service = System.getProperty("apklayer.isolated"), pkg = System.getProperty("apklayer.package");
        if (service == null || pkg == null || !Boolean.getBoolean("apklayer.componenthost")) return;
        try {
            ServiceInfo info = RuntimeEnvironment.systemContext.getPackageManager().getServiceInfo(new ComponentName(pkg, service), 0);
            if (info.processName != null) set(info.processName);
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unknown) { System.out.println("[apklayer] process name not set: " + unknown); }
    }

    private static void set(String name) { System.setProperty("apklayer.processname", name); }  // ShadowActivityThread.currentProcessName answers it
}
