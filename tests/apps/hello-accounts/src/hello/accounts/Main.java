package hello.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.os.Bundle;

/**
 * Another app's account (hello-system's "accounts" check adds it and makes it visible to hello.accounts): this app lists it and gets a token from
 * hello-system's authenticator, which the instance's account service binds for it. Built again as hello.accountshidden (tests/apps/hello-accounts-hidden),
 * to which the account was not made visible: it lists none and gets no token. Prints "[accounts] PASS|FAIL detail".
 */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            try {
                AccountManager am = AccountManager.get(this);
                Account[] accounts = am.getAccountsByType("hello.system.account");
                if (!getPackageName().equals("hello.accounts")) {
                    if (accounts.length != 0 || java.util.Arrays.stream(am.getAccounts()).anyMatch(a -> a.type.equals("hello.system.account")))
                        throw new AssertionError("hello-system's account is visible to " + getPackageName());
                    // its user is asked (the hub's dialog; tests/vm/accounts.sh answers -Dhello.grant): denied, no token; allowed, a token and the account
                    Account hidden = new Account("check", "hello.system.account");
                    if (Boolean.getBoolean("hello.grant")) {
                        String token = am.getAuthToken(hidden, "cross", null, false, null, null).getResult().getString(AccountManager.KEY_AUTHTOKEN);
                        if (!("token-cross-" + android.os.Process.myUid()).equals(token)) throw new AssertionError("token after the grant " + token);
                        if (am.getAccountsByType("hello.system.account").length != 1) throw new AssertionError("the granted account is not listed");
                        System.out.println("[accounts] PASS granted: " + getPackageName() + " was allowed the account it did not see, token " + token);
                        return;
                    }
                    try {
                        am.getAuthToken(hidden, "cross", null, false, null, null).getResult();
                        throw new AssertionError("a token of an account this app does not see");
                    } catch (android.accounts.OperationCanceledException denied) { /* its user said no */ }
                    System.out.println("[accounts] PASS hidden: " + getPackageName() + " sees no account of hello.system.account and gets no token when its user denies it");
                    return;
                }
                if (accounts.length == 0) throw new AssertionError("hello-system's account is not listed");
                String token = am.getAuthToken(accounts[0], "cross", null, false, null, null).getResult().getString(AccountManager.KEY_AUTHTOKEN);
                if (!("token-cross-" + android.os.Process.myUid()).equals(token)) throw new AssertionError("token " + token);
                System.out.println("[accounts] PASS " + accounts.length + " account(s) of hello.system.account, token " + token);
            } catch (Throwable failure) {
                failure.printStackTrace(System.out);
                System.out.println("[accounts] FAIL " + failure);
            }
        }, "accounts-check").start();
    }
}
