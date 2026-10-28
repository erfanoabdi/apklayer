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

    /** The other installed apps' Android uids (info.properties of every installed app): services of another app check a caller's package against its uid. */
    @Implementation
    public int getPackageUid(String packageName, int flags) throws PackageManager.NameNotFoundException {
        try { return super.getPackageUid(packageName, flags); }
        catch (PackageManager.NameNotFoundException unknown) {
            String apps = System.getProperty("apklayer.apps");
            if (apps != null) {
                java.io.File info = new java.io.File(new java.io.File(apps, packageName), "info.properties");
                try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.FileReader(info))) {
                    for (String line; (line = in.readLine()) != null; ) if (line.startsWith("uid=")) return Integer.parseInt(line.substring(4).trim());
                } catch (java.io.IOException | NumberFormatException none) { /* not installed */ }
            }
            throw unknown;
        }
    }


    /** Apps read their own signing certificate in Application.onCreate (update checks, SoLoader...): every package has one from the first call. */
    private static android.content.pm.PackageInfo signed(android.content.pm.PackageInfo info, long flags) {
        if (info == null) return null;
        if ((flags & PackageManager.GET_SIGNATURES) != 0 && info.signatures == null) info.signatures = HostCertificate.signatures();
        if ((flags & PackageManager.GET_SIGNING_CERTIFICATES) != 0 && info.signingInfo == null)
            info.signingInfo = new android.content.pm.SigningInfo(new android.content.pm.SigningDetails(HostCertificate.signatures(), 2, new android.util.ArraySet<>(), null));
        return info;
    }

    @Implementation
    protected android.content.pm.PackageInfo getPackageInfo(String packageName, int flags) throws PackageManager.NameNotFoundException {
        return signed(super.getPackageInfo(packageName, flags), flags);
    }

    @Implementation(minSdk = android.os.Build.VERSION_CODES.TIRAMISU)
    protected android.content.pm.PackageInfo getPackageInfo(String packageName, PackageManager.PackageInfoFlags flags) throws PackageManager.NameNotFoundException {
        return signed(super.getPackageInfo(packageName, flags), flags.getValue());
    }
}
