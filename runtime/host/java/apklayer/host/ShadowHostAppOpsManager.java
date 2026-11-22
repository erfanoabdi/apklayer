package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAppOpsManager;

/**
 * An app's isolated process (uid 99000 and up) names its owner's package in every provider call (ContentProvider.Transport.call asks
 * AppOpsManager.checkPackage(callingUid, callingPackage)); Robolectric's check compared the package's own uid with 99000 and threw
 * "Package .. belongs to 10029", so a provider that checks the caller (SettingsProvider) ended the process at its first Settings read.
 * AppOpsService resolves an isolated uid to its owner as PackageManagerService's mIsolatedOwners says (the hub knows it).
 */
@Implements(className = "android.app.AppOpsManager", isInAndroidSdk = false)
public class ShadowHostAppOpsManager extends ShadowAppOpsManager {
    @Implementation
    protected void checkPackage(int uid, String packageName) {
        if (android.os.Process.isIsolated(uid) && HubClient.connected() && packageName != null && packageName.equals(HubClient.current().isolatedOwner(uid))) return;
        // a process naming its own package is that package's (an installed Play Store failed the uid comparison after its reinstall and ended in Application.onCreate)
        if (packageName != null && packageName.equals(System.getProperty("apklayer.package"))) return;
        super.checkPackage(uid, packageName);
    }
}
