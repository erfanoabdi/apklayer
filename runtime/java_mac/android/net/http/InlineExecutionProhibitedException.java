package android.net.http;
public final class InlineExecutionProhibitedException extends RuntimeException {
    public InlineExecutionProhibitedException() { super("Inline execution is prohibited for this request"); }
}
