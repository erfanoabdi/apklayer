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
        AndroidManifest manifest = new AndroidManifest(null, null, null, null, "", apk);
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
                .put(Config.class, config)
                .build();
        sandbox.getTestEnvironment().setUpApplicationState("apklayer", configuration, manifest);
        persistDataDirs();
        provideExternalStorage();
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

    /** The desktop's connection: an unmetered Wi-Fi network (Robolectric defaults to a mobile one). */
    static void installNetwork(Application app) {
        org.robolectric.shadows.ShadowConnectivityManager cm = Shadow.extract(app.getSystemService(android.net.ConnectivityManager.class));
        cm.setActiveNetworkInfo(org.robolectric.shadows.ShadowNetworkInfo.newInstance(android.net.NetworkInfo.DetailedState.CONNECTED,
                android.net.ConnectivityManager.TYPE_WIFI, 0, true, android.net.NetworkInfo.State.CONNECTED));
    }

    /** Every Android device has the package "android" (the framework); androidx.media and others look it up. */
    static void installSystemPackage(Application app) {
        android.content.pm.PackageInfo info = new android.content.pm.PackageInfo();
        info.packageName = "android";
        android.content.pm.Signature[] platformCert = {new android.content.pm.Signature("3082")}; // some certificate
        info.signatures = platformCert;
        info.signingInfo = new android.content.pm.SigningInfo(new android.content.pm.SigningDetails(platformCert, 2, new android.util.ArraySet<>(), null));
        info.applicationInfo = new android.content.pm.ApplicationInfo();
        info.applicationInfo.packageName = "android";
        info.applicationInfo.flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM;
        org.robolectric.shadows.ShadowPackageManager pm = Shadow.extract(app.getPackageManager());
        pm.installPackage(info);
        // the app's own package is "signed" too (apps compare their own and callers' signing certificates)
        android.content.pm.PackageInfo own = pm.getInternalMutablePackageInfo(app.getPackageName());
        if (own != null && own.signingInfo == null) {
            own.signatures = platformCert;
            own.signingInfo = info.signingInfo;
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

    static void startRuntime(Application app) {
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
        // Legacy MediaPlayer still uses the host backend; native MediaCodec is independent.
        ShadowMediaPlayer.setMediaInfoProvider(ShadowHostMediaPlayer::mediaInfo);
        HostAudio.install();
        if (Boolean.getBoolean("apklayer.nativemedia")) {
            NativeMedia.ensureInitialized();
        } else {
            try {
                HostCodecs.install();
            } catch (Exception e) { // a newer framework may differ from the fallback builder
                System.err.println("[apklayer] MediaCodec decoders not registered on this framework: " + e.getMessage());
            }
        }

    }

    private AppEnvironment() {}
}
