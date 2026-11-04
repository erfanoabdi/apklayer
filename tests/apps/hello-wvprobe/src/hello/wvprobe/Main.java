package hello.wvprobe;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Creates a WebView of the provider, runs JavaScript in a page and reads the cookie the page set through CookieManager. */
public class Main extends Activity {
    private static final String BASE = "https://probe.example/";
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            WebView view = new WebView(this);
            System.out.println("[wv] created " + view.getClass().getName() + " userAgent=" + view.getSettings().getUserAgentString());
            view.getSettings().setJavaScriptEnabled(true);
            view.setWebViewClient(new WebViewClient() {
                @Override public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail detail) {
                    System.out.println("[wv] render process gone: didCrash=" + detail.didCrash() + " priorityAtExit=" + detail.rendererPriorityAtExit());
                    return true;
                }
                @Override public void onPageFinished(WebView v, String url) {
                    System.out.println("[wv] page finished " + url);
                    // a cookie the page sets is the app's CookieManager's (the provider's store, as on a device: sign-in flows read it there)
                    v.evaluateJavascript("document.cookie = 'probe=1; path=/'; 1+1", value -> {
                        String cookie = android.webkit.CookieManager.getInstance().getCookie(BASE);
                        if (cookie == null || !cookie.contains("probe=1")) { System.out.println("[wv] FAIL cookie set by the page not in CookieManager: " + cookie); return; }
                        System.out.println("[wv] PASS js=" + value + " cookie=" + cookie);
                    });
                }
            });
            setContentView(view);
            if (System.getProperty("hello.wvinput") != null) { // a text field (tests/vm/webview-input.py): its value as typed, long press and keys
                view.setWebChromeClient(new android.webkit.WebChromeClient() {
                    @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m) { System.out.println("[wv] " + m.message()); return true; }
                });
                view.loadDataWithBaseURL(BASE, "<html><body style='margin:0'><input id=f value='word' style='font-size:40px;width:100%;height:100vh'>"
                        + "<script>f.oninput = () => console.log('value=' + f.value); f.onfocus = () => console.log('focused'); document.onselectionchange = () => console.log('selection=' + f.selectionStart + ',' + f.selectionEnd)</script></body></html>",
                        "text/html", "utf-8", null);
                return;
            }
            view.loadDataWithBaseURL(BASE, "<html><body><h1>hello</h1></body></html>", "text/html", "utf-8", null);
        } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[wv] FAIL " + failure); }
    }
    // a long press's selection toolbar (Chromium's floating ActionMode)
    @Override public void onActionModeStarted(android.view.ActionMode mode) { System.out.println("[wv] action mode started"); }
    @Override public void onActionModeFinished(android.view.ActionMode mode) { System.out.println("[wv] action mode finished"); }
}
