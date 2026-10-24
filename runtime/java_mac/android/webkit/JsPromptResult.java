package android.webkit;

// android.webkit.JsPromptResult (AOSP API-1).
// Result handle for a JS prompt() dialog surfaced through
// WebChromeClient.onJsPrompt; carries the typed-in string. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/JsPromptResult.java
public class JsPromptResult extends JsResult {

    private String stringResult;

    public JsPromptResult() { super(); }

    public JsPromptResult(ResultReceiver receiver) { super(receiver); }

    public void confirm(String result) {
        this.stringResult = result;
        // Reuse the base confirm() to fire the receiver and set the boolean.
        confirm();
    }

    public String getStringResult() { return stringResult; }
}
