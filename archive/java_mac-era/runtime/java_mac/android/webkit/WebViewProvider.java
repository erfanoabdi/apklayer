package android.webkit;
/** The private SPI a WebView delegates to (the backend implementation). Mac.apk
 *  backs WebView with a WKWebView bridge rather than this AOSP SPI, so this is a
 *  type-only handle. Source: frameworks/base/core/java/android/webkit/WebViewProvider.java */
public interface WebViewProvider {
    default void setWebViewClient(android.webkit.WebViewClient client) { }
    default android.view.View getView() { return null; }
}
