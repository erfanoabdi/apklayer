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
    private android.content.pm.PackageInfo signed(android.content.pm.PackageInfo info, long flags) {
        if (info == null) return null;
        android.content.pm.Signature[] signatures = HostCertificate.signatures();
        android.content.pm.Signature fake = fakeSignature(info.packageName);
        if (fake != null) signatures = new android.content.pm.Signature[] {fake};
        if ((flags & PackageManager.GET_SIGNATURES) != 0 && (info.signatures == null || info.signatures.length == 0 || fake != null)) info.signatures = signatures;
        if ((flags & PackageManager.GET_SIGNING_CERTIFICATES) != 0 && (info.signingInfo == null || info.signingInfo.getApkContentsSigners() == null || fake != null))
        {
            android.content.pm.SigningInfo signing = new android.content.pm.SigningInfo();  // Robolectric's shadow of SigningInfo keeps the signers itself
            org.robolectric.Shadows.shadowOf(signing).setSignatures(signatures);
            info.signingInfo = signing;
        }
        return info;
    }

    /**
     * Signature spoofing for microG, the policy of the LineageOS patches (ComputerEngine.generateFakeSignature): only microG Services (com.google.android.gms) and its
     * Companion (com.android.vending) are spoofed, with the certificate their manifest names in the "fake-signature" meta-data (Google's, which Google apps and Play
     * Services clients check). Signatures of installed apps are not verified here, so the meta-data is the only condition.
     */
    private android.content.pm.Signature fakeSignature(String packageName) {
        if (!"com.google.android.gms".equals(packageName) && !"com.android.vending".equals(packageName)) return null;
        try {
            android.content.pm.PackageInfo full = super.getPackageInfo(packageName, PackageManager.GET_META_DATA);
            android.os.Bundle metaData = full == null || full.applicationInfo == null ? null : full.applicationInfo.metaData;
            String hex = metaData == null ? null : metaData.getString("fake-signature");
            return hex == null || hex.isEmpty() ? null : new android.content.pm.Signature(hex);
        } catch (PackageManager.NameNotFoundException | RuntimeException none) { return null; }
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
