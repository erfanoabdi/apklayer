package hello.web;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** P5.1 sample: loads inline HTML, and when the page has finished evaluates JS and logs the answer. */
public class Main extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        WebView v = new WebView(this);
        v.getSettings().setJavaScriptEnabled(true);
        v.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                System.out.println("[hello-webview] page finished");
                view.evaluateJavascript("6 * 7", r -> System.out.println("[hello-webview] 6*7 = " + r));
            }
        });
        setContentView(v);
        v.loadDataWithBaseURL("https://example.org/", "<html><body style='background:#2a6;font:40px sans-serif'><h1>Hello WebKit</h1></body></html>", "text/html", "utf-8", null);
    }
}
