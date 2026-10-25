package android.accounts;
/** Thrown when an AccountManager authenticator fails.
 *  Source: frameworks/base .../accounts/AuthenticatorException.java */
public class AuthenticatorException extends android.accounts.AccountsException {
    public AuthenticatorException() { super(); }
    public AuthenticatorException(String message) { super(message); }
    public AuthenticatorException(String message, Throwable cause) { super(message, cause); }
    public AuthenticatorException(Throwable cause) { super(cause); }
}
