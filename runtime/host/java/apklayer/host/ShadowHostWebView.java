package apklayer.host;

import android.webkit.ValueCallback;
import android.webkit.WebView;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowWebView;

/**
 * Robolectric's WebView shadow remembers only the last evaluateJavascript call; apps issue several in a row.
 * This subclass keeps Robolectric's behaviour and also queues every call for the engine (HostWebViews).
 */
@Implements(WebView.class)
public class ShadowHostWebView extends ShadowWebView {
    /** Commands for the engine ("back", "forward", "reload"), drained by HostWebViews. */
    final Queue<String> commands = new ConcurrentLinkedQueue<>();
    /** The engine's own state, set by HostWebViews: it follows links and redirects the app never sees. */
    volatile boolean canBack, canForward;
    volatile String currentUrl, currentTitle;

    @Implementation protected boolean canGoBack() { return canBack; }
    @Implementation protected boolean canGoForward() { return canForward; }
    @Implementation protected void goBack() { commands.add("back"); }
    @Implementation protected void goForward() { commands.add("forward"); }
    @Implementation protected void reload() { commands.add("reload"); }
    @Implementation protected String getUrl() { return currentUrl != null ? currentUrl : super.getLastLoadedUrl(); }
    @Implementation protected String getTitle() { return currentTitle; }

    /** {script, ValueCallback or null} in call order. */
    final Queue<Object[]> evals = new ConcurrentLinkedQueue<>();

    @Implementation
    @Override
    protected void evaluateJavascript(String script, ValueCallback<String> callback) {
        super.evaluateJavascript(script, callback);
        evals.add(new Object[] {script, callback});
    }
}
