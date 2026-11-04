package hello.system;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/** The authenticator of "hello.system.account" (AccountsCheck): issues "token-<type>-<caller uid>" for any account of its type. */
public final class Authenticator extends Service {
    private static final class Impl extends AbstractAccountAuthenticator {
        Impl(Context context) { super(context); }
        @Override public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) {
            Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            result.putString(AccountManager.KEY_AUTHTOKEN, "token-" + authTokenType + "-" + options.getInt(AccountManager.KEY_CALLER_UID, -1));
            return result;
        }
        @Override public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] features, Bundle options) { return new Bundle(); }
        @Override public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) { return null; }
        @Override public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) { return null; }
        @Override public String getAuthTokenLabel(String authTokenType) { return authTokenType; }
        @Override public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) { return null; }
        @Override public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) { return null; }
        @Override public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response, Account account) {  // AccountRemovalCheck: "kept" stays
            Bundle result = new Bundle();
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, !account.name.equals("kept"));
            return result;
        }
    }

    private Impl impl;

    @Override public IBinder onBind(Intent intent) {
        if (impl == null) impl = new Impl(this);
        return impl.getIBinder();
    }
}
