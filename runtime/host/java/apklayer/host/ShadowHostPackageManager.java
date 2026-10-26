package apklayer.host;

import android.content.pm.PackageManager;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApplicationPackageManager;

/** Robolectric's package manager lacks signing-certificate checks (apps use them to trust callers, e.g. media browsers). */
@Implements(className = "android.app.ApplicationPackageManager")
public class ShadowHostPackageManager extends ShadowApplicationPackageManager {
    @org.robolectric.annotation.RealObject private android.app.ApplicationPackageManager real;

    /** Every installed package carries the same placeholder certificate (AppHost.installSystemPackage): trust it. */
    @Implementation
    protected boolean hasSigningCertificate(String packageName, byte[] certificate, int type) {
        try {
            return real.getPackageInfo(packageName, 0) != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
