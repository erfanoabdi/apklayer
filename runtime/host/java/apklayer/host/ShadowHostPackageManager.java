package apklayer.host;

import android.content.pm.PackageManager;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApplicationPackageManager;

/** Robolectric's package manager lacks signing-certificate checks (apps use them to trust callers, e.g. media browsers). */
@Implements(className = "android.app.ApplicationPackageManager")
public class ShadowHostPackageManager extends ShadowApplicationPackageManager {
    @org.robolectric.annotation.RealObject private android.app.ApplicationPackageManager real;

    /** As PackageManagerService: the package's signers and rotated-away keys, as getPackageInfo shows them (microG's spoofed one included), against a raw X.509 certificate or its SHA-256. */
    @Implementation
    protected boolean hasSigningCertificate(String packageName, byte[] certificate, int type) {
        android.content.pm.PackageInfo info;
        try {
            info = real.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        android.content.pm.Signature[] signers = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        if (signers == null || certificate == null) return false;
        if (info.signingInfo.hasPastSigningCertificates()) signers = info.signingInfo.getSigningCertificateHistory();  // SigningDetails.hasCertificate: past keys match too
        for (android.content.pm.Signature signer : signers) {
            byte[] mine = signer.toByteArray();
            if (type == PackageManager.CERT_INPUT_SHA256) mine = android.util.PackageUtils.computeSha256DigestBytes(mine);
            else if (type != PackageManager.CERT_INPUT_RAW_X509) return false;
            if (java.util.Arrays.equals(mine, certificate)) return true;
        }
        return false;
    }

    @Implementation
    protected boolean hasSigningCertificate(int uid, byte[] certificate, int type) {
        String[] packages = real.getPackagesForUid(uid);
        if (packages != null) for (String p : packages) if (hasSigningCertificate(p, certificate, type)) return true;
        return false;
    }

    /** Another installed app's uid (its registered package info carries it): a package not registered yet attaches this JVM to the hub first, as getPackageInfo does. */
    @Implementation
    public int getPackageUid(String packageName, int flags) throws PackageManager.NameNotFoundException {
        try { return super.getPackageUid(packageName, flags); }
        catch (PackageManager.NameNotFoundException unknown) { if (!registerInstalled(packageName)) throw unknown; return super.getPackageUid(packageName, flags); }
    }

    /** Whether the app may ask to install packages: it requests REQUEST_INSTALL_PACKAGES and the person allowed it as a source (HubInstaller), as AppOps answers on Android. */
    @Implementation(minSdk = android.os.Build.VERSION_CODES.O)
    protected boolean canRequestPackageInstalls() { return HubClient.connected() && HubClient.current().installSource(false); }

    /** As PackageManagerService: a declared feature at that version or a later one (Robolectric answers false for every version). */
    @Implementation
    protected boolean hasSystemFeature(String name, int version) {
        return HostSystemConfig.loadedFeatures.contains(name) && HostSystemConfig.loadedVersions.getOrDefault(name, 0) >= version;
    }

    /** The features the build declares, as a list (Robolectric answers null while its own list is empty; apps read .length). */
    @Implementation
    protected android.content.pm.FeatureInfo[] getSystemAvailableFeatures() {
        android.content.pm.FeatureInfo[] known = super.getSystemAvailableFeatures();
        java.util.List<android.content.pm.FeatureInfo> list = new java.util.ArrayList<>(known != null ? java.util.Arrays.asList(known)
                : HostSystemConfig.loadedFeatures.stream().sorted().map(name -> { android.content.pm.FeatureInfo info = new android.content.pm.FeatureInfo(); info.name = name; return info; }).toList());
        android.content.pm.FeatureInfo gles = new android.content.pm.FeatureInfo();  // as PackageManagerService: the unnamed feature carries the GLES version
        gles.reqGlEsVersion = ShadowHostActivityManager.glEsVersion();
        list.add(gles);
        return list.toArray(new android.content.pm.FeatureInfo[0]);
    }

    /** Apps read their own signing certificate in Application.onCreate (update checks, SoLoader...): every package has one from the first call. */
    private android.content.pm.PackageInfo signed(android.content.pm.PackageInfo info, long flags) {
        if (info == null) return null;
        installedPaths(info.applicationInfo);
        // the app's own data directories as Context.getApplicationInfo names them (/data/user/0/<pkg>): Termux exports PackageManager's dataDir to its shell
        if (info.applicationInfo != null && info.packageName != null && info.packageName.equals(System.getProperty("apklayer.package"))) {
            info.applicationInfo = new android.content.pm.ApplicationInfo(info.applicationInfo);
            AppEnvironment.stableDataDirs(info.applicationInfo);
            info.applicationInfo = AppEnvironment.androidApkPaths(info.applicationInfo);
        }
        if (info.splitNames == null && info.applicationInfo != null) info.splitNames = info.applicationInfo.splitNames;
        android.content.pm.SigningDetails details = HostCertificate.details(info.packageName);
        android.content.pm.Signature fake = fakeSignature(info.packageName);
        if (fake != null) details = new android.content.pm.SigningDetails(new android.content.pm.Signature[] {fake}, details.getSignatureSchemeVersion(), null, null);
        if ((flags & PackageManager.GET_SIGNATURES) != 0 && (info.signatures == null || info.signatures.length == 0 || fake != null)) info.signatures = HostCertificate.legacy(details);
        if ((flags & PackageManager.GET_SIGNING_CERTIFICATES) != 0 && (info.signingInfo == null || info.signingInfo.getApkContentsSigners() == null || fake != null))
        {
            android.content.pm.SigningInfo signing = new android.content.pm.SigningInfo();  // Robolectric's shadow of SigningInfo keeps the signers itself
            org.robolectric.Shadows.shadowOf(signing).setSignatures(details.getSignatures());
            org.robolectric.Shadows.shadowOf(signing).setPastSigningCertificates(details.getPastSigningCertificates());
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
    static void installedPaths(android.content.pm.ApplicationInfo app) {
        String apps = System.getProperty("apklayer.apps");
        if (app == null || apps == null) return;
        java.io.File dir = new java.io.File(apps, app.packageName);
        java.io.File apk = new java.io.File(dir, "base.apk");
        if (!apk.isFile()) return;
        if (app.splitNames == null) {  // the installed splits (split_<name>.apk), as PackageManagerService records them: LoadedApk adds their resources
            java.io.File[] splits = dir.listFiles((d, name) -> name.startsWith("split_") && name.endsWith(".apk"));
            if (splits != null && splits.length > 0) {
                java.util.Arrays.sort(splits);
                app.splitNames = java.util.Arrays.stream(splits).map(f -> f.getName().substring(6, f.getName().length() - 4)).toArray(String[]::new);
                app.splitSourceDirs = app.splitPublicSourceDirs = java.util.Arrays.stream(splits).map(java.io.File::getPath).toArray(String[]::new);
            }
        }
        if (app.sharedLibraryInfos == null && !app.packageName.equals(System.getProperty("apklayer.package"))) HostSharedLibraries.recordOther(app, apk);  // its own: HostSharedLibraries.record
        if (app.sourceDir != null) return;
        app.sourceDir = app.publicSourceDir = apk.getPath();
        if (app.nativeLibraryDir == null) app.nativeLibraryDir = new java.io.File(dir, "lib").getPath();
    }

    /** SHA-256 of the certificates in LineageOS's ComputerEngine: MICROG_REAL_SIGNATURE (microG's NOGAPPS key) and MICROG_FAKE_SIGNATURE (Google's, the only one that may be faked). */
    private static final String MICROG_REAL_SHA256 = "9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165";
    private static final String MICROG_FAKE_SHA256 = "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83";

    /**
     * Signature spoofing for microG, the rule of LineageOS (ComputerEngine.isMicrogSigned and generateFakeSignature): microG Services (com.google.android.gms) and
     * its Companion (com.android.vending) show the certificate their manifest names in the "fake-signature" meta-data, when their APK is signed with microG's own
     * key alone and that certificate is Google's. LineageOS also needs a debuggable build (ANDROID_DEBUGGABLE at compile time: our userdebug tree has it).
     */
    private android.content.pm.Signature fakeSignature(String packageName) {
        if (!"com.google.android.gms".equals(packageName) && !"com.android.vending".equals(packageName)) return null;
        android.content.pm.Signature[] real = HostCertificate.of(packageName);
        if (real.length != 1 || !MICROG_REAL_SHA256.equalsIgnoreCase(android.util.PackageUtils.computeSha256Digest(real[0].toByteArray()))) return null;
        try {
            android.content.pm.PackageInfo full = super.getPackageInfo(packageName, PackageManager.GET_META_DATA);
            android.os.Bundle metaData = full == null || full.applicationInfo == null ? null : full.applicationInfo.metaData;
            String hex = metaData == null ? null : metaData.getString("fake-signature");
            if (hex == null || hex.isEmpty()) return null;
            android.content.pm.Signature fake = new android.content.pm.Signature(hex);
            return MICROG_FAKE_SHA256.equalsIgnoreCase(android.util.PackageUtils.computeSha256Digest(fake.toByteArray())) ? fake : null;
        } catch (PackageManager.NameNotFoundException | RuntimeException none) { return null; }
    }

    @Implementation
    protected android.content.pm.PackageInfo getPackageInfo(String packageName, int flags) throws PackageManager.NameNotFoundException {
        try { return signed(super.getPackageInfo(packageName, flags), flags); }
        catch (PackageManager.NameNotFoundException unknown) { if (!registerInstalled(packageName)) throw unknown; return signed(super.getPackageInfo(packageName, flags), flags); }
    }

    /** The other installed apps register with this JVM when it first meets the hub, which can be late (Play Services clients ask for com.android.vending at start): an installed package not known yet attaches now, or registers alone when it was installed later. */
    private boolean registerInstalled(String packageName) {
        String apps = System.getProperty("apklayer.apps");
        if (apps == null || !new java.io.File(new java.io.File(apps, packageName), "info.properties").exists()) return false;
        if (!HubClient.connected()) { HubClient.attach(org.robolectric.RuntimeEnvironment.getApplication()); return true; }
        return HubClient.current().registerPackage(this, packageName);  // installed after this JVM attached
    }

    @Implementation(minSdk = android.os.Build.VERSION_CODES.TIRAMISU)
    protected android.content.pm.PackageInfo getPackageInfo(String packageName, PackageManager.PackageInfoFlags flags) throws PackageManager.NameNotFoundException {
        try { return signed(super.getPackageInfo(packageName, flags), flags.getValue()); }
        catch (PackageManager.NameNotFoundException unknown) { if (!registerInstalled(packageName)) throw unknown; return signed(super.getPackageInfo(packageName, flags), flags.getValue()); }
    }

    /** Another app's ApplicationInfo with its paths and shared libraries (getPackageInfo's, for apps that ask for the info alone). */
    @Implementation
    protected android.content.pm.ApplicationInfo getApplicationInfo(String packageName, int flags) throws PackageManager.NameNotFoundException {
        android.content.pm.ApplicationInfo info;
        try { info = super.getApplicationInfo(packageName, flags); }
        catch (PackageManager.NameNotFoundException unknown) { if (!registerInstalled(packageName)) throw unknown; info = super.getApplicationInfo(packageName, flags); }
        installedPaths(info);
        return info;
    }

    @Implementation(minSdk = android.os.Build.VERSION_CODES.TIRAMISU)
    protected android.content.pm.ApplicationInfo getApplicationInfo(String packageName, PackageManager.ApplicationInfoFlags flags) throws PackageManager.NameNotFoundException {
        return getApplicationInfo(packageName, (int) flags.getValue());
    }

    /** One user: the user's list is the installed one (Robolectric shadows only the plain call; the fake IPackageManager answered none to Settings' app list). */
    @Implementation(minSdk = android.os.Build.VERSION_CODES.TIRAMISU)
    protected java.util.List<android.content.pm.ApplicationInfo> getInstalledApplicationsAsUser(
            @org.robolectric.annotation.ClassName("android.content.pm.PackageManager$ApplicationInfoFlags") Object flags, int userId) {
        return getInstalledApplications(flags);
    }

    @Implementation
    protected java.util.List<android.content.pm.ApplicationInfo> getInstalledApplicationsAsUser(int flags, int userId) { return getInstalledApplications(flags); }

    /** The installed packages with their signatures as getPackageInfo has them (Robolectric's list skips it; F-Droid's update check refused a package without signers). */
    @Implementation
    @Override public java.util.List<android.content.pm.PackageInfo> getInstalledPackages(int flags) {
        java.util.List<android.content.pm.PackageInfo> all = new java.util.ArrayList<>(super.getInstalledPackages(flags));
        all.replaceAll(info -> signed(info, flags));
        return all;
    }

    @Implementation(minSdk = android.os.Build.VERSION_CODES.TIRAMISU)
    @Override protected java.util.List<android.content.pm.PackageInfo> getInstalledPackages(@org.robolectric.annotation.ClassName("android.content.pm.PackageManager$PackageInfoFlags") Object flags) {
        long value = ((PackageManager.PackageInfoFlags) flags).getValue();
        java.util.List<android.content.pm.PackageInfo> all = new java.util.ArrayList<>(super.getInstalledPackages(flags));
        all.replaceAll(info -> signed(info, value));
        return all;
    }

    /**
     * Another app's icon from its own resources, as ApplicationPackageManager loads it (Robolectric knows only icons a test stored and throws for the rest:
     * F-Droid's update of an installed app failed on it); the default icon when the package has none or its resources do not load.
     */
    @Implementation
    @Override protected android.graphics.drawable.Drawable getApplicationIcon(String packageName) throws PackageManager.NameNotFoundException {
        try { return super.getApplicationIcon(packageName); }
        catch (PackageManager.NameNotFoundException unknown) {
            android.content.pm.ApplicationInfo app = getPackageInfo(packageName, 0).applicationInfo;  // NameNotFound for a package that is not installed
            try {
                android.graphics.drawable.Drawable icon = app == null || app.icon == 0 ? null : real.getDrawable(packageName, app.icon, app);
                if (icon != null) return icon;
            } catch (RuntimeException unreadable) { System.out.println("[apklayer] icon of " + packageName + " not loaded: " + unreadable); }
            return getDefaultActivityIcon();
        }
    }

    /**
     * Another installed package's resources from its APK, as ApplicationPackageManager loads them (Robolectric hands out an empty table: labels
     * in Settings' app list and LineageParts' catalog that Settings reads failed with "No package ID 7f").
     */
    @Implementation
    @Override protected android.content.res.Resources getResourcesForApplication(android.content.pm.ApplicationInfo app) throws PackageManager.NameNotFoundException {
        if (!app.packageName.equals(org.robolectric.RuntimeEnvironment.getApplication().getPackageName())) {
            try {
                android.content.pm.ApplicationInfo source = app.sourceDir != null ? app : getPackageInfo(app.packageName, 0).applicationInfo;  // a listed one may carry no paths
                if (source == null || source.sourceDir == null || !new java.io.File(source.sourceDir).isFile()) return super.getResourcesForApplication(app);
                if (source.publicSourceDir == null) {  // the framework loads another uid's package from its public paths
                    source = new android.content.pm.ApplicationInfo(source);
                    source.publicSourceDir = source.sourceDir;
                    source.splitPublicSourceDirs = source.splitSourceDirs;
                }
                android.content.res.Resources own = org.robolectric.util.reflector.Reflector.reflector(PackageManagerDirect.class, real).getResourcesForApplication(source);
                if (own != null) return own;
            } catch (RuntimeException unreadable) { System.out.println("[apklayer] resources of " + app.packageName + " not loaded: " + unreadable); }
        }
        return super.getResourcesForApplication(app);
    }

    @Implementation
    @Override protected android.content.res.Resources getResourcesForApplication(String packageName) throws PackageManager.NameNotFoundException {
        if (packageName.equals(org.robolectric.RuntimeEnvironment.getApplication().getPackageName())) return super.getResourcesForApplication(packageName);
        android.content.pm.ApplicationInfo app = getPackageInfo(packageName, 0).applicationInfo;  // NameNotFound for a package that is not installed
        return app == null ? super.getResourcesForApplication(packageName) : getResourcesForApplication(app);
    }

    @org.robolectric.util.reflector.ForType(android.app.ApplicationPackageManager.class)
    interface PackageManagerDirect {
        @org.robolectric.util.reflector.Direct android.content.res.Resources getResourcesForApplication(android.content.pm.ApplicationInfo app) throws PackageManager.NameNotFoundException;
    }
}
