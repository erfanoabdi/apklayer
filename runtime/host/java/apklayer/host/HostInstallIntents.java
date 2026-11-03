package apklayer.host;

import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.util.List;
import org.robolectric.RuntimeEnvironment;

/**
 * The package installer's activities on a phone (PackageInstaller's InstallStart and UninstallerActivity, Settings' "install unknown apps"):
 * ACTION_INSTALL_PACKAGE and ACTION_VIEW of an APK, ACTION_DELETE and ACTION_UNINSTALL_PACKAGE, ACTION_MANAGE_UNKNOWN_APP_SOURCES. They go to
 * the instance hub's installer, which asks the person (HubInstaller). The APK is opened here, with the app's own access to its URI.
 * shortcut: startActivityForResult gets no result (EXTRA_RETURN_RESULT); the installed package's broadcast tells the app.
 */
final class HostInstallIntents {
    private static final String APK = "application/vnd.android.package-archive";

    private HostInstallIntents() {}

    /** True when the intent was one of these and went to the hub. */
    static boolean handle(Intent intent) {
        String action = intent.getAction();
        if (action == null || !HubClient.connected()) return false;
        Uri data = intent.getData();
        boolean install = Intent.ACTION_INSTALL_PACKAGE.equals(action) || (Intent.ACTION_VIEW.equals(action) && data != null
                && (APK.equals(intent.getType()) || APK.equals(RuntimeEnvironment.getApplication().getContentResolver().getType(data))));
        if (install && data != null) {
            try (ParcelFileDescriptor apk = RuntimeEnvironment.getApplication().getContentResolver().openFileDescriptor(data, "r")) {
                HubClient.current().commitInstall(0, null, null, List.of("base.apk"), List.of(apk), null, true);
            } catch (java.io.IOException | RuntimeException failure) { System.out.println("[apklayer] cannot install " + data + ": " + failure); }
            return true;
        }
        if ((Intent.ACTION_DELETE.equals(action) || Intent.ACTION_UNINSTALL_PACKAGE.equals(action)) && data != null && "package".equals(data.getScheme())) {
            HubClient.current().uninstall(data.getSchemeSpecificPart(), null, true);
            return true;
        }
        if (android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES.equals(action)) {
            HubClient.current().installSource(true);
            return true;
        }
        return false;
    }
}
