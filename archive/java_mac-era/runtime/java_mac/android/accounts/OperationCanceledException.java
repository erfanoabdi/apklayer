package android.accounts;
/** Thrown when an AccountManager operation is cancelled.
 *  Source: frameworks/base .../accounts/OperationCanceledException.java */
public class OperationCanceledException extends android.accounts.AccountsException {
    public OperationCanceledException() { super(); }
    public OperationCanceledException(String message) { super(message); }
    public OperationCanceledException(String message, Throwable cause) { super(message, cause); }
    public OperationCanceledException(Throwable cause) { super(cause); }
}
