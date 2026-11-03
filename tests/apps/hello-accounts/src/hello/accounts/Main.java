package hello.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.os.Bundle;

/**
 * Another app's account (hello-system's "accounts" check adds it): this app lists it and gets a token from hello-system's authenticator, which the
 * instance's account service binds for it. Prints "[accounts] PASS|FAIL detail".
 */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            try {
                AccountManager am = AccountManager.get(this);
                Account[] accounts = am.getAccountsByType("hello.system.account");
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
