package hello.web;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** P5.1 sample: inline HTML, page callbacks, evaluateJavascript, a Java object for the page, link interception. */
public class Main extends Activity {
    public static class Bridge {
        @JavascriptInterface public String hello(String name) { return "hi " + name; }
        @JavascriptInterface public void log(String m) { System.out.println("[hello-webview] js says " + m); }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        WebView v = new WebView(this);
        v.getSettings().setJavaScriptEnabled(true);
        v.addJavascriptInterface(new Bridge(), "Android");
        v.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                System.out.println("[hello-webview] page started");
            }
            @Override public void onPageFinished(WebView view, String url) {
                System.out.println("[hello-webview] page finished");
                view.evaluateJavascript("6 * 7", r -> System.out.println("[hello-webview] 6*7 = " + r));
                view.evaluateJavascript("document.getElementById('l').click()", null);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                System.out.println("[hello-webview] link " + url);
                return true;
            }
        });
        setContentView(v);
        v.loadDataWithBaseURL("https://example.org/", "<html><body style='background:#2a6;font:40px sans-serif' onclick=\"document.body.style.background='#c22'\">"
                + "<h1>Hello WebKit</h1><a id=l href='https://example.org/next' onclick='event.stopPropagation()'>next</a>"
                + "<script>Android.log(Android.hello('web'))</script></body></html>", "text/html", "utf-8", null);
    }
}
