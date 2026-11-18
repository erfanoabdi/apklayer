package hello.system;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.content.Context;
import java.util.concurrent.TimeUnit;

final class AccountNullBindingCheck implements Check {
    @Override public String run(Context context) throws Exception {
        AccountManager accounts = AccountManager.get(context);
        String type = "hello.system.nullbinding";
        Check.require(java.util.Arrays.stream(accounts.getAuthenticatorTypes()).anyMatch(auth -> type.equals(auth.type)),
                "null-binding authenticator not listed");
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                accounts.addAccount(type, null, null, null, null, null, null).getResult(10, TimeUnit.SECONDS);
                throw new AssertionError("null authenticator binding returned success");
            } catch (AuthenticatorException expected) {
                Check.require(("authenticator unavailable for " + type).equals(expected.getMessage()),
                        "unexpected authenticator error: " + expected.getMessage());
            }
        }
        return "both null bindings returned an authenticator error";
    }
}
