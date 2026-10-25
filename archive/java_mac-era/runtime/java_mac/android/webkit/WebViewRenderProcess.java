package android.webkit;

// android.webkit.WebViewRenderProcess (AOSP API-29).
// Handle to the renderer process backing a WebView. Abstract in AOSP. We have
// no separate render process, so terminate() reports failure. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebViewRenderProcess.java
public abstract class WebViewRenderProcess {

    public abstract boolean terminate();
}
