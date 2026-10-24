package android.webkit;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Message;

// android.webkit.WebViewClient (AOSP API-1+).
// The app overrides these to observe/intercept navigation. With no web engine
// yet NONE of these are invoked, but the full signature surface must exist so
// app subclasses that @Override the modern (API-21/23/24) overloads compile and
// resolve against our shadow. The default bodies mirror AOSP's defaults
// (return false / return null / no-op). Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebViewClient.java
public class WebViewClient {

    // ----- error-code constants (AOSP) -----
    public static final int ERROR_UNKNOWN = -1;
    public static final int ERROR_HOST_LOOKUP = -2;
    public static final int ERROR_UNSUPPORTED_AUTH_SCHEME = -3;
    public static final int ERROR_AUTHENTICATION = -4;
    public static final int ERROR_PROXY_AUTHENTICATION = -5;
    public static final int ERROR_CONNECT = -6;
    public static final int ERROR_IO = -7;
    public static final int ERROR_TIMEOUT = -8;
    public static final int ERROR_REDIRECT_LOOP = -9;
    public static final int ERROR_UNSUPPORTED_SCHEME = -10;
    public static final int ERROR_FAILED_SSL_HANDSHAKE = -11;
    public static final int ERROR_BAD_URL = -12;
    public static final int ERROR_FILE = -13;
    public static final int ERROR_FILE_NOT_FOUND = -14;
    public static final int ERROR_TOO_MANY_REQUESTS = -15;
    public static final int ERROR_UNSAFE_RESOURCE = -16;

    public static final int SAFE_BROWSING_THREAT_UNKNOWN = 0;
    public static final int SAFE_BROWSING_THREAT_MALWARE = 1;
    public static final int SAFE_BROWSING_THREAT_PHISHING = 2;
    public static final int SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE = 3;
    public static final int SAFE_BROWSING_THREAT_BILLING = 4;

    // ----- url loading -----
    @Deprecated
    public boolean shouldOverrideUrlLoading(WebView view, String url) { return false; }

    // API-24+ overload that modern OAuth deep-link interception relies on.
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // AOSP default delegates to the deprecated string overload for back-compat.
        return shouldOverrideUrlLoading(view, request == null || request.getUrl() == null
                ? null : request.getUrl().toString());
    }

    // ----- page lifecycle -----
    public void onPageStarted(WebView view, String url, Bitmap favicon) {}
    public void onPageFinished(WebView view, String url) {}
    public void onPageCommitVisible(WebView view, String url) {}
    public void onLoadResource(WebView view, String url) {}
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {}

    // ----- resource interception -----
    @Deprecated
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) { return null; }

    // API-21+ overload; hybrid apps serve bundled local assets here.
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return shouldInterceptRequest(view, request == null || request.getUrl() == null
                ? null : request.getUrl().toString());
    }

    // ----- errors -----
    @Deprecated
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {}

    // API-23+ overload. AOSP android14-release WebViewClient.java:337-343: the
    // default body forwards MAIN-FRAME errors to the deprecated 4-arg overload,
    // so an app that overrides only the old one still hears them (King's
    // CustomWebViewClient in Candy Crush). WebView.java's EV_RECEIVED_ERROR
    // dispatch calls ONLY this overload, so nothing is delivered twice.
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (request != null && request.isForMainFrame()) {
            onReceivedError(view, error.getErrorCode(), error.getDescription().toString(),
                    request.getUrl().toString());
        }
    }

    public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                    WebResourceResponse errorResponse) {}

    public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType,
                                  SafeBrowsingResponse callback) {
        if (callback != null) callback.backToSafety(true);
    }

    // ----- TLS / auth -----
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        // AOSP default cancels the load (secure default). We mirror that.
        if (handler != null) handler.cancel();
    }

    public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
        if (request != null) request.cancel();
    }

    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                          String host, String realm) {
        if (handler != null) handler.cancel();
    }

    @Deprecated
    public void onReceivedLoginRequest(WebView view, String realm, String account, String args) {}

    // ----- form / key events -----
    public void onFormResubmission(WebView view, Message dontResend, Message resend) {
        if (dontResend != null) dontResend.sendToTarget();
    }

    // @Deprecated in current AOSP ("This method is no longer called. When
    // the WebView encounters a redirect loop, it will cancel the load"),
    // but the member still exists on-device so an app subclass whose
    // override calls super.onTooManyRedirects(...) must still resolve
    // instead of NoSuchMethodError. Mirrors AOSP's own default body.
    @Deprecated
    public void onTooManyRedirects(WebView view, Message cancelMsg, Message continueMsg) {
        if (cancelMsg != null) cancelMsg.sendToTarget();
    }

    public boolean shouldOverrideKeyEvent(WebView view, android.view.KeyEvent event) { return false; }
    public void onUnhandledKeyEvent(WebView view, android.view.KeyEvent event) {}

    // @removed from current SDK stubs (so new code can't call/override it
    // against the latest compileSdk) but the member still physically exists
    // on-device, so an app built against an older SDK that overrides it
    // still resolves at the bytecode level. AOSP delegates the KeyEvent
    // case to onUnhandledKeyEvent (which we do too, since that path is
    // real); AOSP's non-KeyEvent path calls a private framework-internal
    // dispatch we have no engine behind, so that half stays a no-op rather
    // than fabricate a WebView-internal API we don't implement.
    public void onUnhandledInputEvent(WebView view, android.view.InputEvent event) {
        if (event instanceof android.view.KeyEvent) {
            onUnhandledKeyEvent(view, (android.view.KeyEvent) event);
        }
    }

    // ----- scale / render-process -----
    public void onScaleChanged(WebView view, float oldScale, float newScale) {}

    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        // AOSP default returns false -> the app is killed by the system. With no
        // real render process this is never invoked; returning false is faithful.
        return false;
    }
}
