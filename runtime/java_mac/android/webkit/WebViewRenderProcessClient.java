package android.webkit;

// android.webkit.WebViewRenderProcessClient (AOSP API-29).
// Observes renderer responsiveness. The app subclasses it; no engine drives it.
// Ref: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebViewRenderProcessClient.java
public abstract class WebViewRenderProcessClient {

    public abstract void onRenderProcessUnresponsive(WebView view, WebViewRenderProcess renderer);

    public abstract void onRenderProcessResponsive(WebView view, WebViewRenderProcess renderer);
}
