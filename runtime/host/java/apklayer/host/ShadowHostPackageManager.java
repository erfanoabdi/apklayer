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
        installedPaths(info.applicationInfo);
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

    /** An app asks who installed it (F-Droid, StreetComplete in Application.onCreate): nobody, as for a sideloaded APK. */
    @Implementation(minSdk = android.os.Build.VERSION_CODES.R)
    protected android.content.pm.InstallSourceInfo getInstallSourceInfo(String packageName) throws PackageManager.NameNotFoundException {
        try { return (android.content.pm.InstallSourceInfo) super.getInstallSourceInfo(packageName); }
        catch (PackageManager.NameNotFoundException unknown) {
            org.robolectric.Shadows.shadowOf(real).setInstallSourceInfo(packageName, null, null);
            return (android.content.pm.InstallSourceInfo) super.getInstallSourceInfo(packageName);
        }
    }

    /** Where an installed app's APK and libraries are (Robolectric leaves them null, Chromium's crash reporter builds file paths from them in Application.onCreate). */
    private static void installedPaths(android.content.pm.ApplicationInfo app) {
        String apps = System.getProperty("apklayer.apps");
        if (app == null || apps == null || app.sourceDir != null) return;
        java.io.File dir = new java.io.File(apps, app.packageName);
        java.io.File apk = new java.io.File(dir, "base.apk");
        if (!apk.isFile()) return;
        app.sourceDir = app.publicSourceDir = apk.getPath();
        if (app.nativeLibraryDir == null) app.nativeLibraryDir = new java.io.File(dir, "lib").getPath();
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
        try { return signed(super.getPackageInfo(packageName, flags), flags); }
        catch (PackageManager.NameNotFoundException unknown) { if (!registerInstalled(packageName)) throw unknown; return signed(super.getPackageInfo(packageName, flags), flags); }
    }

    /** The other installed apps register with this JVM when it first meets the hub, which can be late (Play Services clients ask for com.android.vending at start): an installed package not known yet attaches now. */
    private boolean registerInstalled(String packageName) {
        String apps = System.getProperty("apklayer.apps");
        if (apps == null || HubClient.connected() || !new java.io.File(new java.io.File(apps, packageName), "info.properties").exists()) return false;
        HubClient.attach(org.robolectric.RuntimeEnvironment.getApplication());
        return true;
    }

    @Implementation(minSdk = android.os.Build.VERSION_CODES.TIRAMISU)
    protected android.content.pm.PackageInfo getPackageInfo(String packageName, PackageManager.PackageInfoFlags flags) throws PackageManager.NameNotFoundException {
        try { return signed(super.getPackageInfo(packageName, flags), flags.getValue()); }
        catch (PackageManager.NameNotFoundException unknown) { if (!registerInstalled(packageName)) throw unknown; return signed(super.getPackageInfo(packageName, flags), flags.getValue()); }
    }
}
