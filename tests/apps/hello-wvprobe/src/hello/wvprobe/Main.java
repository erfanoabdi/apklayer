package hello.wvprobe;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Creates a WebView of the provider and runs JavaScript in a page. */
public class Main extends Activity {
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
                    v.evaluateJavascript("1+1", value -> System.out.println("[wv] PASS js=" + value));
                }
            });
            setContentView(view);
            view.loadData("<html><body><h1>hello</h1></body></html>", "text/html", "utf-8");
        } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[wv] FAIL " + failure); }
    }
}
