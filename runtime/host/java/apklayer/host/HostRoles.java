package apklayer.host;

import android.app.Application;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

/** Roles as the installed apps imply them: an app that answers a plain http link while no other app does is the default browser, as it is on a device whose only browser it is (Fennec asks otherwise). */
final class HostRoles {
    private static boolean done;
    private HostRoles() {}

    private static java.nio.file.Path file() { return HostRuntimePermissions.file("roles"); }

    /** The roles the person made this package the holder of ("package TAB role" per line in the instance's data, beside permissions-granted). */
    private static java.util.List<String> held(String pkg) {
        java.util.List<String> roles = new java.util.ArrayList<>();
        try {
            for (String line : java.nio.file.Files.readAllLines(file())) if (line.startsWith(pkg + "\t")) roles.add(line.substring(pkg.length() + 1));
        } catch (java.io.IOException | RuntimeException none) { /* none given, or a sandboxed app that cannot read the file */ }
        return roles;
    }

    /** In the hub: the person made the package the role's holder. shortcut: never taken away (no UI to remove a holder), the browser role is not exclusive between apps. */
    static synchronized void record(String pkg, String role) {
        try {
            if (held(pkg).contains(role)) return;
            java.nio.file.Files.writeString(file(), pkg + "\t" + role + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException | RuntimeException failure) { System.out.println("[apklayer-hub] role not recorded: " + failure); }
    }

    static void install() {
        if (done) return;
        done = true;
        Application app = RuntimeEnvironment.getApplication();
        RoleManager roles = app.getSystemService(RoleManager.class);
        if (roles == null) return;
        Intent link = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com")).addCategory(Intent.CATEGORY_BROWSABLE);
        ResolveInfo answer = app.getPackageManager().resolveActivity(link, PackageManager.MATCH_DEFAULT_ONLY);
        for (String role : held(app.getPackageName())) Shadows.shadowOf(roles).addHeldRole(role);  // what the person gave it earlier (the hub's roles file)
        if (answer != null && answer.activityInfo != null && app.getPackageName().equals(answer.activityInfo.packageName)) Shadows.shadowOf(roles).addHeldRole(RoleManager.ROLE_BROWSER);
    }
}
