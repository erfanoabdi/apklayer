package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowViewGroup;

/** WebView's own code (the provider's implementation behind it), not Robolectric's stand-in. */
@Implements(className = "android.webkit.WebView", callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealWebView extends ShadowViewGroup {
    /**
     * The provider's own class loader (the sandbox's here), not the one the framework would derive from a package context. As Android's
     * getFactory().getWebViewClassLoader(), the provider is created first: androidx.webkit loads Chromium's support-library glue through this
     * loader and asks WebViewChromiumFactoryProvider for its singleton, which only the provider's creation sets (Play services' sign-in page:
     * "WebViewChromiumFactoryProvider has not been set!" before any WebView existed).
     */
    @Implementation protected static ClassLoader getWebViewClassLoader() {
        try { ShadowHostWebViewFactory.getProvider(); }
        catch (ClassNotFoundException missing) { throw new IllegalStateException(missing); }
        return ShadowHostWebViewFactory.providerLoader();
    }
    /**
     * WebView.getCurrentWebViewPackage: the provider package, as the webviewupdate service answers it. There is no such service here, and the framework's
     * WebViewUpdateManager.getInstance then throws "WebView not supported by device", which Play services' AdServicesBackCompatModuleInitIntentOperation
     * does not catch: its crash handler ended the whole process, again and again (Play Store's binds to it timed out). Asks the package manager, not the
     * provider: no native library load for a version query.
     */
    @Implementation protected static android.content.pm.PackageInfo getCurrentWebViewPackage() {
        String dir = System.getProperty("apklayer.webviewprovider");
        if (dir == null) return null;
        try { return org.robolectric.RuntimeEnvironment.getApplication().getPackageManager().getPackageInfo(new java.io.File(dir).getName(), 0); }
        catch (android.content.pm.PackageManager.NameNotFoundException none) { return null; }
    }
}
