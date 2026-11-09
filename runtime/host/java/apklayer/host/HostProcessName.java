package apklayer.host;

import android.content.pm.PackageParser;
import org.robolectric.shadows.ShadowPackageParser;

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
    static void install(java.nio.file.Path apk) {
        String service = System.getProperty("apklayer.isolated");
        if (apk == null || service == null || !Boolean.getBoolean("apklayer.componenthost")) return;
        for (PackageParser.Service s : ShadowPackageParser.callParsePackage(apk).services) {  // the framework's parser resolves ":name" to "<package>:name"
            if (!service.equals(s.info.name)) continue;
            if (s.info.processName != null) set(s.info.processName);
            isolated = (s.info.flags & android.content.pm.ServiceInfo.FLAG_ISOLATED_PROCESS) != 0;
        }
    }

    /** Whether this component host runs an android:isolatedProcess service (Android gives such a process no content providers). */
    static boolean isolated;

    /** The android:process this window process was started for (`apklayer run` with APKLAYER_PROCESS: an activity of another process), null for the package's own. */
    static String windowProcess() {
        return Boolean.getBoolean("apklayer.componenthost") || System.getProperty("apklayer.isolated") != null ? null : System.getProperty("apklayer.processname");
    }

    /** The activity's own process ("<package>:jvm"), null when it runs in the package's process (the hub keys window processes so: HubDirectory.key). */
    static String of(android.content.pm.ActivityInfo info) {
        String process = info.processName;
        return process == null || process.equals(info.packageName) || (info.applicationInfo != null && process.equals(info.applicationInfo.processName)) ? null : process;
    }

    private static void set(String name) { System.setProperty("apklayer.processname", name); }  // ShadowActivityThread.currentProcessName answers it
}
