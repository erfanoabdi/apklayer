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
}
