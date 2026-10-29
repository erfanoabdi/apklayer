package apklayer.host;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.SQLiteMode;
import org.robolectric.annotation.ResourcesMode;
import org.robolectric.annotation.experimental.LazyApplication;
import org.robolectric.internal.AndroidSandbox;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.ShadowView;
import org.robolectric.simulator.FixedConfiguration;

/** APK environment setup and startup services. */
final class AppEnvironment {
    /** Robolectric simulator AppLoader: application state straight from the APK. */
    static void setUpApplication(AndroidSandbox sandbox, Path apk, boolean describing) {
        if (Thread.getDefaultUncaughtExceptionHandler() == null) Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> { System.out.println("FATAL EXCEPTION: " + thread.getName()); failure.printStackTrace(System.out); });  // like Android's RuntimeInit: an app's own handlers (ACRA, Gecko) still chain to it
        System.setProperty("java.vm.name", "Dalvik");  // libgdx, Netty ... pick their Android code path (native libraries through System.loadLibrary) by these names
        System.setProperty("java.runtime.name", "Android Runtime");
        if (java.security.Security.getProvider("AndroidKeyStoreBCWorkaround") == null) java.security.Security.addProvider(new HostBcWorkaroundProvider());
        HostCompat.install();  // behavior changes by the app's target SDK (PendingIntent mutability, ...)
        HostGeckoDiag.start();
        org.robolectric.shadows.ShadowAlarmManager.setAutoSchedule(true);  // an alarm fires its PendingIntent or listener when its time comes (Robolectric only records it otherwise)
        HostLibraryPath.install();  // before the app's own code runs: SoLoader finds the system libraries it looks up by name
        if (Boolean.getBoolean("apklayer.logcat")) org.robolectric.shadows.ShadowLog.stream = System.out; // android.util.Log output (-Dapklayer.logcat=true, APKLAYER_LOGCAT=1)
        AndroidManifest manifest = apk == null ? new AndroidManifest(null, null, null, "apklayer.hub")
                : new AndroidManifest(null, null, null, null, "", apk);
        Config.Builder builder = new Config.Builder()
                .setSdk(Integer.getInteger("apklayer.sdk", 36))
                .setQualifiers(System.getProperty("apklayer.qualifiers", "w480dp-h800dp"));
        // Install-time labels/icons need package resources, not the app's startup code.
        if (describing) builder.setApplication(Application.class);
        Config config = builder.build();
        FixedConfiguration configuration = FixedConfiguration.newBuilder()
                .put(ConscryptMode.Mode.class, ConscryptMode.Mode.OFF)
                .put(LooperMode.Mode.class, LooperMode.Mode.PAUSED)
                // Lazy: the package and its storage are set up first, so the data dirs can be
                // made persistent before Application.onCreate runs (persistDataDirs).
                .put(LazyApplication.LazyLoad.class, LazyApplication.LazyLoad.ON)
                .put(GraphicsMode.Mode.class, GraphicsMode.Mode.NATIVE)
                .put(SQLiteMode.Mode.class, SQLiteMode.Mode.NATIVE)
                .put(ResourcesMode.Mode.class, ResourcesMode.Mode.NATIVE)
                .put(Config.class, config)
                .build();
        sandbox.getTestEnvironment().setUpApplicationState("apklayer", configuration, manifest);
        HostClock.alignToMonotonic();  // after the clock's reset by the state setup
        HostKeyStore.install();  // apps open it in Application.onCreate (the application is lazy: not created yet); needs the sandbox's shadows
        NativeProcess.install();
        persistDataDirs();
        provideExternalStorage();
        provideNativeLibraryDir();
    }

    /** The app's ApplicationInfo names the directory of its extracted native libraries (SoLoader, ReLinker and others read it): where `apklayer install` put the arm64 ones. */
    private static void provideNativeLibraryDir() {
        String libdir = System.getProperty("apklayer.libdir");
        if (libdir == null) return;
        try {
            Object bound = org.robolectric.util.ReflectionHelpers.getField(RuntimeEnvironment.getActivityThread(), "mBoundApplication");
            android.content.pm.ApplicationInfo info = org.robolectric.util.ReflectionHelpers.getField(bound, "appInfo");
            info.nativeLibraryDir = libdir;
            info.primaryCpuAbi = "arm64-v8a";
        } catch (RuntimeException notBoundYet) { /* the context path (ShadowHostContextImpl.getApplicationInfo) covers it */ }
    }

    /**
     * Android always has a primary external volume; the sandbox's current-user environment has none
     * (apps index getExternalDirs()[0], e.g. Environment.isExternalStorageManager()).
     */
    private static void provideExternalStorage() {
        android.os.Environment.UserEnvironment userEnv = new android.os.Environment.UserEnvironment(0) {
            @Override
            public java.io.File[] getExternalDirs() {
                return new java.io.File[] {android.os.Environment.getExternalStorageDirectory()};
            }
        };
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Environment.class, "sCurrentUser", userEnv);
    }

    /**
     * PLAN D20: Robolectric creates the app's data dirs ({@code <package>-dataDir}, {@code
     * userDataDir}, {@code deviceDataDir}) in its per-run temp directory when the (lazy)
     * Application is created, and deletes them at exit. They are pre-created here as links into
     * the persistent per-app tree ({@code apklayer.data}); TempDirectory.createIfNotExists keeps
     * an existing entry, and the exit cleanup does not follow links.
     */
    private static void persistDataDirs() {
        String root = System.getProperty("apklayer.data");
        String pkg = System.getProperty("apklayer.package");
        if (root == null || pkg == null) return; // ephemeral (headless checks)
        Path temp = RuntimeEnvironment.getTempDirectory().getBasePath();
        String[][] links = {{pkg + "-dataDir", "app"}, {"userDataDir", "user"}, {"deviceDataDir", "device"}};
        try {
            for (String[] link : links) {
                Path dir = temp.resolve(link[0]);
                if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) continue;
                Files.createSymbolicLink(dir, Files.createDirectories(Path.of(root, link[1])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every Android device has the package "android" (the framework); androidx.media and others look it up. */
    static void installSystemPackage(Application app) {
        android.content.pm.PackageInfo info = new android.content.pm.PackageInfo();
        info.packageName = "android";
        android.content.pm.Signature[] platformCert = HostCertificate.signatures();
        info.signatures = platformCert;
        info.signingInfo = new android.content.pm.SigningInfo(new android.content.pm.SigningDetails(platformCert, 2, new android.util.ArraySet<>(), null));
        info.applicationInfo = new android.content.pm.ApplicationInfo();
        info.applicationInfo.packageName = "android";
        info.applicationInfo.flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM;
        org.robolectric.shadows.ShadowPackageManager pm = Shadow.extract(app.getPackageManager());
        pm.installPackage(info);
        // the app's own package is "signed" too (apps compare their own and callers' signing certificates)
        android.content.pm.PackageInfo own = pm.getInternalMutablePackageInfo(app.getPackageName());
        if (own != null) {
            if (own.signatures == null) own.signatures = platformCert;
            if (own.signingInfo == null) own.signingInfo = info.signingInfo;
        }
    }

    /**
     * Android creates every ContentProvider the manifest declares (highest initOrder first) before the first
     * activity; libraries like androidx.startup (WorkManager, emoji2, ProcessLifecycleOwner) initialise through them.
     */
    static void installProviders(Application app) {
        try {
            android.content.pm.ProviderInfo[] providers = app.getPackageManager()
                    .getPackageInfo(app.getPackageName(), android.content.pm.PackageManager.GET_PROVIDERS).providers;
            if (providers == null) return;
            java.util.Arrays.sort(providers, java.util.Comparator.comparingInt((android.content.pm.ProviderInfo p) -> p.initOrder).reversed());
            for (android.content.pm.ProviderInfo info : providers) {
                try {
                    Robolectric.buildContentProvider(Class.forName(info.name).asSubclass(android.content.ContentProvider.class)).create(info);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    System.out.println("[apklayer] provider " + info.name + " not started: " + e);
                }
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Settings.Secure.ANDROID_ID: a stable 64-bit id per app and desktop user (Jellyfin's SDK asserts it is there). */
    static void androidId(Application app) {
        android.content.ContentResolver resolver = app.getContentResolver();
        if (android.provider.Settings.Secure.getString(resolver, android.provider.Settings.Secure.ANDROID_ID) != null) return;
        String seed = app.getPackageName() + System.getProperty("user.name", "") + System.getProperty("user.home", "");
        android.provider.Settings.Secure.putString(resolver, android.provider.Settings.Secure.ANDROID_ID, String.format("%016x", seed.hashCode() * 0x9E3779B97F4A7C15L));
    }

    static void startRuntime(Application app) {
        if (Boolean.getBoolean("apklayer.logcat")) for (String name : new String[] {"alarm", "jobscheduler", "notification", "connectivity"}) System.out.println("[apklayer] service " + name + " = " + app.getSystemService(name));
        HostBuild.install();
        HostSystemConfigService.install();
        HostNetwork.install(app);
        HostBattery.install(app);
        // no doze on the desktop: an app is never held back by battery optimization
        org.robolectric.Shadows.shadowOf((android.os.PowerManager) app.getSystemService(android.content.Context.POWER_SERVICE)).setIgnoringBatteryOptimizations(app.getPackageName(), true);
        HostUsb.install(app);
        HostStorage.install(app);
        HostTrust.install();  // the default trust manager with the host-aware check Android's callers (Chromium, X509TrustManagerExtensions) need
        if (!"com.android.providers.media.module".equals(System.getProperty("apklayer.package"))) org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media", new HostMediaStub());  // replaced by the installed MediaProvider's remote provider when there is one
        HostLocation.install(app);
        HostSystemConfig.load().apply(app);  // features, privileged-permission allowlist and default grants of the build
        if (Boolean.getBoolean("apklayer.debugdata")) {
            SharedPreferences prefs = app.getSharedPreferences("apklayer_probe", 0);
            int launches = prefs.getInt("launches", 0) + 1;
            prefs.edit().putInt("launches", launches).commit();
            System.out.println("[apklayer] data: " + app.getDataDir() + " launches=" + launches);
        }

        // Simulator.start(): real draws and animations, a paused Choreographer driven by the loop.
        ShadowView.setUseRealViewAnimations(true);
        ShadowView.setUseRealDrawTraversals(true);
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(15));
        ShadowLog.setCaptureLogsEnabled(false);
        if (Boolean.getBoolean("apklayer.logcat")) ShadowLog.stream = System.out; // debug: the app's android.util.Log lines
        NativeMedia.ensureInitialized();
    }

    private AppEnvironment() {}
}
