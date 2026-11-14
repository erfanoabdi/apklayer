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
        showWebView(this, false);
    }

    private void showWebView(android.content.Context context, boolean changedConfiguration) {
        try {
            WebView view = new WebView(context);
            System.out.println("[wv] created " + view.getClass().getName() + " userAgent=" + view.getSettings().getUserAgentString());
            view.getSettings().setJavaScriptEnabled(true);
            view.setWebViewClient(new WebViewClient() {
                private boolean completed;
                @Override public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail detail) {
                    System.out.println("[wv] render process gone: didCrash=" + detail.didCrash() + " priorityAtExit=" + detail.rendererPriorityAtExit());
                    return true;
                }
                @Override public void onPageFinished(WebView v, String url) {
                    if (completed) return;
                    completed = true;
                    System.out.println("[wv] page finished " + url);
                    // a cookie the page sets is the app's CookieManager's (the provider's store, as on a device: sign-in flows read it there)
                    v.evaluateJavascript("document.cookie = 'probe=1; path=/'; 1+1", value -> {
                        String cookie = android.webkit.CookieManager.getInstance().getCookie(BASE);
                        if (cookie == null || !cookie.contains("probe=1")) { System.out.println("[wv] FAIL cookie set by the page not in CookieManager: " + cookie); return; }
                        if (!changedConfiguration && System.getProperty("hello.wvinput") == null) {
                            // A new ResourcesImpl must retain the registered provider assets.
                            // Exercise this through another real WebView, without depending on
                            // resource IDs/names from a particular Chromium release.
                            android.content.res.Configuration config = new android.content.res.Configuration(getResources().getConfiguration());
                            config.orientation = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                    ? android.content.res.Configuration.ORIENTATION_PORTRAIT : android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                            android.view.ContextThemeWrapper changed = new android.view.ContextThemeWrapper(Main.this, android.R.style.Theme_Material_Light_NoActionBar);
                            changed.applyOverrideConfiguration(config);
                            System.out.println("[wv] checking changed configuration resources");
                            showWebView(changed, true);
                            v.destroy();
                        } else {
                            System.out.println("[wv] PASS js=" + value + " cookie=" + cookie
                                    + (changedConfiguration ? " changed-configuration" : ""));
                        }
                    });
                }
            });
            setContentView(view);
            if (System.getProperty("hello.wvinput") != null) { // a text field (tests/vm/webview-input.py): its value as typed, long press and keys
                view.setWebChromeClient(new android.webkit.WebChromeClient() {
                    @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m) { System.out.println("[wv] " + m.message()); return true; }
                });
                view.loadDataWithBaseURL(BASE, "<html><body style='margin:0'><input id=f value='" + (System.getProperty("hello.wvsymbols") != null ? "" : "word") + "' style='font-size:40px;width:100%;height:100vh'>"
                        + "<script>f.onkeydown = e => console.log('key=' + e.key + ' code=' + e.code); f.oninput = () => console.log('value=' + f.value); f.onfocus = () => console.log('focused'); document.onselectionchange = () => console.log('selection=' + f.selectionStart + ',' + f.selectionEnd)</script></body></html>",
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
