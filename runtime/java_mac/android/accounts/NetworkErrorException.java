package android.accounts;
/** Signals a network failure to the AccountManager framework. API 5.
 *  Source: frameworks/base/core/java/android/accounts/NetworkErrorException.java */
// AOSP: `extends AccountsException`. An app's `catch (AccountsException)` around
// an authenticator call silently misses this when it extends Exception directly.
// frameworks/base/core/java/android/accounts/NetworkErrorException.java
public class NetworkErrorException extends AccountsException {
    public NetworkErrorException() { super(); }
    public NetworkErrorException(String message) { super(message); }
    public NetworkErrorException(String message, Throwable cause) { super(message, cause); }
    public NetworkErrorException(Throwable cause) { super(cause); }
}
