package android.accounts;
/** Callback for AccountManager async operations.
 *  Source: frameworks/base .../accounts/AccountManagerCallback.java */
public interface AccountManagerCallback<V> {
    void run(AccountManagerFuture<V> future);
}
