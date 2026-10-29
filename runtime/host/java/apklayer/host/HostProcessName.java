package apklayer.host;

import android.app.Application;
import android.content.ComponentName;
import android.content.pm.ServiceInfo;
import org.robolectric.util.ReflectionHelpers;

/**
 * A service host of an own-package service in a process of its own (GeckoView's child processes) reports that process's name ("package:tab0"), as Android does: apps tell their main
 * process from the others by it (Fenix returns early from Application.onCreate in a secondary process, and its GeckoRuntime would otherwise start a second Gecko in the child).
 */
final class HostProcessName {
    private HostProcessName() {}

    /** True when this host is such a process (its name was set). */
    static boolean install(Application app) {
        String service = System.getProperty("apklayer.isolated");
        if (service == null || !Boolean.getBoolean("apklayer.componenthost")) return false;
        try {
            ServiceInfo info = app.getPackageManager().getServiceInfo(new ComponentName(app.getPackageName(), service), 0);
            if (info.processName == null || info.processName.equals(app.getPackageName())) return false;
            Object thread = ReflectionHelpers.callStaticMethod(Class.forName("android.app.ActivityThread"), "currentActivityThread");
            Object bound = ReflectionHelpers.getField(thread, "mBoundApplication");
            ReflectionHelpers.setField(bound, "processName", info.processName);
            System.out.println("[apklayer] process name " + info.processName + " (Application.getProcessName() = " + Application.getProcessName() + ")");
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException | ReflectiveOperationException | RuntimeException unknown) { System.out.println("[apklayer] process name not set: " + unknown); }
        return false;
    }
}
