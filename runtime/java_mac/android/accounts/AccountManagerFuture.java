package android.accounts;

import java.util.concurrent.TimeUnit;

/**
 * The future returned by AccountManager async operations.
 * Source: frameworks/base/core/java/android/accounts/AccountManagerFuture.java
 */
public interface AccountManagerFuture<V> {
    boolean cancel(boolean mayInterruptIfRunning);
    boolean isCancelled();
    boolean isDone();
    V getResult() throws android.accounts.OperationCanceledException,
            java.io.IOException, AuthenticatorException;
    V getResult(long timeout, TimeUnit unit) throws android.accounts.OperationCanceledException,
            java.io.IOException, AuthenticatorException;
}
