package android.webkit;

import android.os.Handler;

// android.webkit.HttpAuthHandler (AOSP API-1).
// Passed to WebViewClient.onReceivedHttpAuthRequest for HTTP basic/digest auth.
// AOSP extends Handler; we mirror that. No web engine yet, so the
// proceed/cancel decision is recorded but not driven; a future WKWebView bridge
// resolves it against the URLAuthenticationChallenge completion handler.
// Ref: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/HttpAuthHandler.java
public class HttpAuthHandler extends Handler {

    public HttpAuthHandler() { super(); }

    // True only when a prior failed attempt's credentials are available to
    // retry. We never have stored credentials in the stub, so false.
    public boolean useHttpAuthUsernamePassword() { return false; }

    public void cancel() { }

    public void proceed(String username, String password) { }
}
