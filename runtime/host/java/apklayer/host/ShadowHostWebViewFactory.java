package apklayer.host;

import java.io.File;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * The WebView provider is a Chromium APK (-Dapklayer.webviewprovider=&lt;installed package dir&gt;; its converted classes get a class loader of their own, its native library
 * in {@code lib/}). The framework's WebViewFactory finds a provider through the update service and a package context, neither of which exist here: this hands it the
 * provider class directly, after loading the native library through bionic_translation, and the rest of WebViewFactory.getProvider runs as on a device.
 */
@Implements(className = "android.webkit.WebViewFactory", isInAndroidSdk = false)
public class ShadowHostWebViewFactory {
    private static final String FACTORY = "com.android.webview.chromium.WebViewChromiumFactoryProvider";

    /** What WebViewFactory.getWebViewContextAndSetProvider records about the chosen provider: its PackageInfo (read from the APK), with the paths of the installed copy. */
    private static void providerPackage(String dir) {
        android.content.pm.PackageManager pm = org.robolectric.RuntimeEnvironment.getApplication().getPackageManager();
        String apk = new File(dir, "base.apk").getPath();
        android.content.pm.PackageInfo info = pm.getPackageArchiveInfo(apk, android.content.pm.PackageManager.GET_META_DATA | android.content.pm.PackageManager.GET_SERVICES);
        if (info == null) throw new IllegalStateException("Cannot read the WebView provider's manifest: " + apk);
        info.applicationInfo.sourceDir = info.applicationInfo.publicSourceDir = apk;  // before the package is installed: the package manager's copy is what Chromium asks for its paths
        info.applicationInfo.nativeLibraryDir = new File(dir, "lib").getPath();
        info.applicationInfo.dataDir = java.nio.file.Path.of(System.getProperty("apklayer.storage")).resolveSibling("app-data").resolve(info.packageName).toString();  // per instance (cli/layout)
        org.robolectric.shadows.ShadowPackageManager shadow = org.robolectric.Shadows.shadowOf(pm); // the provider is an installed package here too: its renderer services resolve
        shadow.installPackage(info);
        org.robolectric.util.ReflectionHelpers.setStaticField(android.webkit.WebViewFactory.class, "sPackageInfo", info);
        System.out.println("[apklayer] WebView provider " + info.packageName + " " + info.versionName + " (" + info.getLongVersionCode() + ")");
    }

    private static Class<?> providerClass;

    /** The loader of the provider's classes: what androidx.webkit loads Chromium's support-library glue through (it must see the framework's WebViewFactoryProvider). */
    static ClassLoader providerLoader() { return getProviderClass().getClassLoader(); }

    /** Known from the start, not from the package feature the host sets once the Application exists (apps call WebView statics in Application.onCreate). */
    @Implementation protected static boolean isWebViewSupported() { return System.getProperty("apklayer.webviewprovider") != null; }

    /** A class loader of its own, as WebViewFactory makes one on Android: Chromium, jni_zero and its bundled libraries never meet the app's copies (microG and Meet bundle Cronet). */
    private static AppClassLoader providerLoader(String dir) {
        AppClassLoader loader = new AppClassLoader("webview", new java.nio.file.Path[] {new File(dir, "classes.jar").toPath(), new File(dir, "base.apk").toPath()},
                ShadowHostWebViewFactory.class.getClassLoader());
        try {  // framework code naming a provider class (its services) through the sandbox loader: after the app's own, the provider's
            java.lang.reflect.Field hook = Class.forName("org.robolectric.internal.bytecode.ApklayerAppHook").getField("app");
            @SuppressWarnings("unchecked") java.util.function.Function<String, Class<?>> app = (java.util.function.Function<String, Class<?>>) hook.get(null);
            hook.set(null, (java.util.function.Function<String, Class<?>>) name -> { Class<?> c = app == null ? null : app.apply(name); return c != null ? c : loader.apply(name); });
        } catch (ReflectiveOperationException noOverlay) { throw new IllegalStateException("runtime overlay without ApklayerAppHook", noOverlay); }
        return loader;
    }

    /**
     * Chromium reads its switch file (/data/local/tmp/webview-command-line, tools/host-setup/webview.sh: the seccomp sandbox off) only on an eng or userdebug
     * build, while the provider initialises (CommandLineUtil.initCommandLine). The device is a release build (HostBuild): debuggable for that call only.
     * shortcut: another thread reading Build.TYPE during the provider's creation sees userdebug; feed the switches to org.chromium.base.CommandLine
     * directly if one does.
     */
    @Implementation protected static android.webkit.WebViewFactoryProvider getProvider() throws ClassNotFoundException {
        String type = android.os.Build.TYPE;
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class, "TYPE", "userdebug");
        try { return org.robolectric.shadow.api.Shadow.directlyOn(Class.forName("android.webkit.WebViewFactory"), "getProvider"); }
        finally { org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class, "TYPE", type); }
    }

    @Implementation protected static synchronized Class<?> getProviderClass() {
        if (providerClass != null) return providerClass;
        String dir = System.getProperty("apklayer.webviewprovider");
        File library = new File(dir, "lib/libwebviewchromium.so");
        // the provider package itself (its renderer services in a component host) already runs in its app loader
        ClassLoader loader = new File(dir).getName().equals(System.getProperty("apklayer.package")) && AppClassLoader.app != null ? AppClassLoader.app : providerLoader(dir);
        NativeLoader.load(library.getPath(), loader);
        providerPackage(dir);
        try { return providerClass = Class.forName(FACTORY, true, loader); }
        catch (ClassNotFoundException missing) { throw new IllegalStateException("The WebView provider's classes are missing from " + dir, missing); }
    }
}
