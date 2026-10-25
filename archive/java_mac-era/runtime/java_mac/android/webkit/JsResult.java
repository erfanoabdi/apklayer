package android.webkit;

// android.webkit.JsResult (AOSP API-1).
// Result handle for a JS dialog (alert/confirm) surfaced through
// WebChromeClient.onJsAlert/onJsConfirm. cancel()/confirm() are final in AOSP.
// Ref: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/JsResult.java
public class JsResult {

    // Internal listener the engine sets to learn the user's choice.
    public interface ResultReceiver {
        void onJsResultComplete(JsResult result);
    }

    private boolean result;
    private final ResultReceiver receiver;

    public JsResult() { this.receiver = null; }

    public JsResult(ResultReceiver receiver) { this.receiver = receiver; }

    public final void cancel() {
        result = false;
        if (receiver != null) receiver.onJsResultComplete(this);
    }

    public final void confirm() {
        result = true;
        if (receiver != null) receiver.onJsResultComplete(this);
    }

    public final boolean getResult() { return result; }
}
