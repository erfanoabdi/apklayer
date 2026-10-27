package apklayer.host;

import org.robolectric.annotation.Implements;

/** WebView's own code (the provider's implementation behind it), not Robolectric's stand-in. */
@Implements(className = "android.webkit.WebView", callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealWebView {
}
