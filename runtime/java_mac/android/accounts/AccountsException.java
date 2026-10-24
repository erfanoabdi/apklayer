package android.accounts;
/** Base for AccountManager exceptions.
 *  Source: frameworks/base .../accounts/AccountsException.java */
public class AccountsException extends Exception {
    public AccountsException() { super(); }
    public AccountsException(String message) { super(message); }
    public AccountsException(String message, Throwable cause) { super(message, cause); }
    public AccountsException(Throwable cause) { super(cause); }
}
