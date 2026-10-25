package android.webkit;

// android.webkit.ConsoleMessage (AOSP API-8).
// Handed to WebChromeClient.onConsoleMessage. Now stores the fields so getters
// return the real values (was returning constants). Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/ConsoleMessage.java
public class ConsoleMessage {

    public enum MessageLevel { TIP, LOG, WARNING, ERROR, DEBUG }

    private final String message;
    private final String sourceId;
    private final int lineNumber;
    private final MessageLevel level;

    public ConsoleMessage(String message, String sourceId, int lineNumber, MessageLevel level) {
        this.message = message;
        this.sourceId = sourceId;
        this.lineNumber = lineNumber;
        this.level = level;
    }

    public MessageLevel messageLevel() { return level; }

    public String message() { return message; }

    public String sourceId() { return sourceId; }

    public int lineNumber() { return lineNumber; }
}
