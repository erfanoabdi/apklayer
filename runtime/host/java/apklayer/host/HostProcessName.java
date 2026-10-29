package apklayer.host;

import android.app.Application;
import android.content.ComponentName;
import android.content.pm.ServiceInfo;
import org.robolectric.util.ReflectionHelpers;

/**
 * The name of the process (Application.getProcessName): a service host of an own-package service in a process of its own (GeckoView's child processes) reports that process's name ("package:tab0"), as Android does: apps tell their main
 * process from the others by it (Fenix returns early from Application.onCreate in a secondary process, and its GeckoRuntime would otherwise start a second Gecko in the child).
 */
final class HostProcessName {
    private HostProcessName() {}

    /** True when this host is such a process (its name was set). Every other process is named after its package, as the system names a default process (Application.getProcessName() was null: MediaProvider's static initialiser reads it). */
    static boolean install(Application app) {
        boolean secondary = secondary(app);
        if (!secondary) set(app.getPackageName());
        return secondary;
    }

    private static boolean secondary(Application app) {
        String service = System.getProperty("apklayer.isolated");
        if (service == null || !Boolean.getBoolean("apklayer.componenthost")) return false;
        try {
            ServiceInfo info = app.getPackageManager().getServiceInfo(new ComponentName(app.getPackageName(), service), 0);
            if (info.processName == null || info.processName.equals(app.getPackageName())) return false;
            set(info.processName);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unknown) { System.out.println("[apklayer] process name not set: " + unknown); }
        return false;
    }

    private static void set(String name) {
        try {
            Object thread = ReflectionHelpers.callStaticMethod(Class.forName("android.app.ActivityThread"), "currentActivityThread");
            Object bound = ReflectionHelpers.getField(thread, "mBoundApplication");
            ReflectionHelpers.setField(bound, "processName", name);
        } catch (ReflectiveOperationException | RuntimeException unknown) { System.out.println("[apklayer] process name not set: " + unknown); }
    }
}
