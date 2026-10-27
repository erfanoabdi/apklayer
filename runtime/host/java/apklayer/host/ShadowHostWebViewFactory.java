package apklayer.host;

import java.io.File;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * The WebView provider is a Chromium APK (-Dapklayer.webviewprovider=&lt;installed package dir&gt;; its converted classes are on the class path, its native library
 * in {@code lib/}). The framework's WebViewFactory finds a provider through the update service and a package context, neither of which exist here: this hands it the
 * provider class directly, after loading the native library through bionic_translation, and the rest of WebViewFactory.getProvider runs as on a device.
 */
@Implements(className = "android.webkit.WebViewFactory", isInAndroidSdk = false)
public class ShadowHostWebViewFactory {
    private static final String FACTORY = "com.android.webview.chromium.WebViewChromiumFactoryProvider";

    @Implementation protected static Class<?> getProviderClass() {
        String dir = System.getProperty("apklayer.webviewprovider");
        File library = new File(dir, "lib/libwebviewchromium.so");
        NativeLoader.load(library.getPath());
        try { return Class.forName(FACTORY); }
        catch (ClassNotFoundException missing) { throw new IllegalStateException("The WebView provider's classes are not on the class path", missing); }
    }
}
