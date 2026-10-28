package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowViewGroup;

/** WebView's own code (the provider's implementation behind it), not Robolectric's stand-in. */
@Implements(className = "android.webkit.WebView", callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealWebView extends ShadowViewGroup {
    /** The provider\'s own class loader (the sandbox\'s here), not the one the framework would derive from a package context. */
    @Implementation protected static ClassLoader getWebViewClassLoader() { return ShadowHostWebViewFactory.providerLoader(); }
}
