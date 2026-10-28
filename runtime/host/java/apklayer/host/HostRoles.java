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

    static void install() {
        if (done) return;
        done = true;
        Application app = RuntimeEnvironment.getApplication();
        RoleManager roles = app.getSystemService(RoleManager.class);
        if (roles == null) return;
        Intent link = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com")).addCategory(Intent.CATEGORY_BROWSABLE);
        ResolveInfo answer = app.getPackageManager().resolveActivity(link, PackageManager.MATCH_DEFAULT_ONLY);
        if (answer != null && answer.activityInfo != null && app.getPackageName().equals(answer.activityInfo.packageName)) Shadows.shadowOf(roles).addHeldRole(RoleManager.ROLE_BROWSER);
    }
}
