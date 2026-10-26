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
    /** {script, ValueCallback or null} in call order. */
    final Queue<Object[]> evals = new ConcurrentLinkedQueue<>();

    @Implementation
    @Override
    protected void evaluateJavascript(String script, ValueCallback<String> callback) {
        super.evaluateJavascript(script, callback);
        evals.add(new Object[] {script, callback});
    }
}
