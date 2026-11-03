package hello.system;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.Context;
import android.os.Bundle;

/**
 * Accounts as the instance's account service keeps them: the app's own authenticator is listed among every installed one, an account it adds
 * outlives the process (a second run reports persisted=true) and a token comes from the authenticator, bound as a service, then from the cache.
 */
final class AccountsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        AccountManager am = AccountManager.get(context);
        StringBuilder types = new StringBuilder();
        boolean own = false;
        for (AuthenticatorDescription d : am.getAuthenticatorTypes()) { types.append(d.type).append(' '); own |= d.type.equals("hello.system.account") && d.packageName.equals(context.getPackageName()); }
        Check.require(own, "own authenticator not listed: " + types);
        Account account = new Account("check", "hello.system.account");
        boolean persisted = java.util.Arrays.asList(am.getAccountsByType(account.type)).contains(account);
        if (!persisted) {
            Bundle data = new Bundle();
            data.putString("key", "value");
            Check.require(am.addAccountExplicitly(account, "secret", data), "addAccountExplicitly refused");
        }
        Check.require(java.util.Arrays.asList(am.getAccounts()).contains(account), "added account not listed");
        // another app (unsigned, target 34) sees it only when the authenticator makes it visible; hello.accountshidden is never shown it
        if (persisted) Check.require(am.getAccountVisibility(account, "hello.accounts") == AccountManager.VISIBILITY_VISIBLE, "visibility not kept");
        Check.require(am.setAccountVisibility(account, "hello.accounts", AccountManager.VISIBILITY_VISIBLE), "setAccountVisibility refused");
        int hidden = am.getAccountVisibility(account, "hello.accountshidden");
        Check.require(hidden == AccountManager.VISIBILITY_USER_MANAGED_NOT_VISIBLE, "visibility to an app it was not shown: " + hidden);
        Check.require("value".equals(am.getUserData(account, "key")) && "secret".equals(am.getPassword(account)), "user data or password lost");
        am.invalidateAuthToken(account.type, am.peekAuthToken(account, "full"));
        String token = am.getAuthToken(account, "full", null, false, null, null).getResult().getString(AccountManager.KEY_AUTHTOKEN);
        Check.require(("token-full-" + android.os.Process.myUid()).equals(token), "token from the authenticator: " + token);
        Check.require(token.equals(am.peekAuthToken(account, "full")), "token not cached");
        return "persisted=" + persisted + " token=" + token + " types=" + types.toString().trim();
    }
}
