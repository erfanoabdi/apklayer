package android.webkit;

// android.webkit.WebResourceError (AOSP API-23).
// Carries the error code + description handed to the API-23
// onReceivedError(WebView, WebResourceRequest, WebResourceError) overload.
// Abstract in AOSP; the engine subclasses it. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebResourceError.java
public abstract class WebResourceError {

    public abstract int getErrorCode();

    public abstract CharSequence getDescription();
}
