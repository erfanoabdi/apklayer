package android.webkit;

import android.os.Handler;

// android.webkit.SslErrorHandler (AOSP API-1).
// Passed to WebViewClient.onReceivedSslError so the app can proceed() or
// cancel() a TLS error. AOSP extends Handler; we mirror that so apps that
// (rarely) treat it as a Handler still type-check. With no web engine yet the
// decisions are recorded but not acted on; a future WKWebView bridge wires
// proceed/cancel into WKNavigationDelegate's challenge completion handler.
// Ref: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/SslErrorHandler.java
public class SslErrorHandler extends Handler {

    public SslErrorHandler() { super(); }

    public void proceed() { }

    public void cancel() { }
}
