package hello.system;

import android.content.Context;
import android.os.IBinder;
import java.util.List;

/** The hidden "package" and "user" services as apps reach them by reflection (App Manager's no-root mode, through a wrapper binder): ServiceManager.getService, Stub.asInterface, then the AIDL calls. */
final class HiddenServicesCheck implements Check {
    @Override public String run(Context context) throws Exception {
        Class<?> manager = Class.forName("android.os.ServiceManager");
        IBinder pmBinder = (IBinder) manager.getMethod("getService", String.class).invoke(null, "package");
        IBinder umBinder = (IBinder) manager.getMethod("getService", String.class).invoke(null, "user");
        Check.require(pmBinder != null && umBinder != null, "getService package " + pmBinder + " user " + umBinder);
        // as App Manager: through a wrapper binder of the app's own (no local interface), so every call is a transaction the service unparcels
        IBinder wrapped = (IBinder) java.lang.reflect.Proxy.newProxyInstance(IBinder.class.getClassLoader(), new Class<?>[] {IBinder.class}, (proxy, method, args) -> {
            if (method.getName().equals("queryLocalInterface")) return null;
            try { return method.invoke(pmBinder, args); } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        });
        Object pm = Class.forName("android.content.pm.IPackageManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, wrapped);
        Object slice = pm.getClass().getMethod("getInstalledPackages", long.class, int.class).invoke(pm, 0L, android.os.Process.myUserHandle().hashCode());
        List<?> packages = (List<?>) slice.getClass().getMethod("getList").invoke(slice);
        boolean self = false;
        for (Object info : packages) self |= context.getPackageName().equals(((android.content.pm.PackageInfo) info).packageName);
        Check.require(self, "getInstalledPackages: " + packages.size() + " packages, not " + context.getPackageName());
        Object info = pm.getClass().getMethod("getPackageInfo", String.class, long.class, int.class).invoke(pm, context.getPackageName(), 0L, 0);
        Check.require(info != null, "getPackageInfo of itself null");
        // install dates (App Manager listed 1970 for every app): itself and every other package report a real first install and update time
        long year2020 = 1577836800000L;
        Check.require(((android.content.pm.PackageInfo) info).firstInstallTime > year2020, "own firstInstallTime " + ((android.content.pm.PackageInfo) info).firstInstallTime);
        for (Object other : packages) {
            android.content.pm.PackageInfo p = (android.content.pm.PackageInfo) other;
            if (p.packageName.equals("android")) continue;  // the framework's own stand-in has no APK
            Check.require(p.firstInstallTime > year2020 && p.lastUpdateTime >= p.firstInstallTime, "install times of " + p.packageName + ": " + p.firstInstallTime + "/" + p.lastUpdateTime);
        }
        Object um = Class.forName("android.os.IUserManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, umBinder);
        List<?> users = (List<?>) um.getClass().getMethod("getUsers", boolean.class).invoke(um, true);
        List<?> profiles = (List<?>) um.getClass().getMethod("getProfiles", int.class, boolean.class).invoke(um, android.os.Process.myUserHandle().hashCode(), false);
        Check.require(users != null && !users.isEmpty() && profiles != null && !profiles.isEmpty(), "getUsers " + users + " getProfiles " + profiles);
        return packages.size() + " packages (itself among them), " + users.size() + " user(s), " + profiles.size() + " profile(s)";
    }
}
