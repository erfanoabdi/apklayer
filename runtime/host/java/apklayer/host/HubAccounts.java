package apklayer.host;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.accounts.IAccountAuthenticator;
import android.accounts.IAccountAuthenticatorResponse;
import android.accounts.IAccountManager;
import android.accounts.IAccountManagerResponse;
import android.app.IBinderSession;
import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import java.util.HashMap;
import java.util.Map;

/**
 * The instance's account service, what AccountManagerService is: one store of accounts (HubAccountStore) every app's AccountManager reaches through
 * the hub ("account" in each app, ShadowHostServiceManager), and the authenticators bound as services through the hub to add accounts and issue tokens.
 * As on Android, only an account type's authenticator package adds, removes and reads the secrets of its accounts.
 * shortcut: every account is visible to every app (Android O's per-package visibility is not modelled); model it when an app must not see another's accounts.
 */
final class HubAccounts extends IAccountManager.Stub {
    static final String SERVICE = "apklayer.accounts";

    private interface Call { void run(IAccountAuthenticator authenticator, IAccountAuthenticatorResponse response) throws RemoteException; }

    private final HubDirectory directory;
    private final HubServiceBindings bindings;
    private final HubAuthenticators authenticators;
    private final HubAccountStore store;

    HubAccounts(HubDirectory directory, HubServiceBindings bindings, HubAuthenticators authenticators, java.nio.file.Path file) {
        this.directory = directory;
        this.bindings = bindings;
        this.authenticators = authenticators;
        this.store = new HubAccountStore(file);
    }

    private HubDirectory.Peer caller() {
        HubDirectory.Peer peer = directory.peerByPid(Binder.getCallingPid());
        if (peer == null) throw new SecurityException("Unregistered calling process");
        return peer;
    }

    /** checkCallingUidAgainstAuthenticator: the secrets and the list of an account type belong to the package that authenticates it. */
    private HubDirectory.Peer owner(String type) {
        HubDirectory.Peer peer = caller();
        HubAuthenticators.Authenticator authenticator = type == null ? null : authenticators.all().get(type);
        if (authenticator == null || !authenticator.service().getPackageName().equals(peer.name()))
            throw new SecurityException(peer.name() + " is not the authenticator of account type " + type);
        return peer;
    }

    private void changed(Account removed) {
        directory.sendSystem(new Intent(AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION).addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND), false);
        directory.sendSystem(new Intent(AccountManager.ACTION_VISIBLE_ACCOUNTS_CHANGED), false);
        if (removed != null) directory.sendSystem(new Intent(AccountManager.ACTION_ACCOUNT_REMOVED)
                .putExtra(AccountManager.KEY_ACCOUNT_NAME, removed.name).putExtra(AccountManager.KEY_ACCOUNT_TYPE, removed.type), false);
    }

    private static Bundle accountResult(Account account) {
        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        return result;
    }

    private static void reply(IAccountManagerResponse response, Bundle result) {
        try { if (response != null) response.onResult(result); } catch (RemoteException gone) { /* the client died */ }
    }

    private static void error(IAccountManagerResponse response, int code, String message) {
        System.out.println("[apklayer-hub] accounts: " + message);
        try { if (response != null) response.onError(code, message); } catch (RemoteException gone) { /* the client died */ }
    }

    /** The caller's identity in the options an authenticator gets (AccountManagerService adds the same keys). */
    private static Bundle withCaller(Bundle options, HubDirectory.Peer caller) {
        Bundle o = options == null ? new Bundle() : new Bundle(options);
        o.putInt(AccountManager.KEY_CALLER_UID, caller.appUid());
        o.putInt(AccountManager.KEY_CALLER_PID, caller.pid());
        o.putString(AccountManager.KEY_ANDROID_PACKAGE_NAME, caller.name());
        return o;
    }

    /** AccountManagerService.Session: binds the type's authenticator through the hub, runs one call, hands its answer to the client and unbinds. */
    private void session(String type, IAccountManagerResponse response, Call call, java.util.function.Consumer<Bundle> onResult) {
        HubDirectory.Peer caller = caller();
        HubAuthenticators.Authenticator authenticator = type == null ? null : authenticators.all().get(type);
        if (authenticator == null) { error(response, AccountManager.ERROR_CODE_BAD_ARGUMENTS, "no authenticator for account type " + type); return; }
        IServiceConnection.Stub connection = new IServiceConnection.Stub() {
            private boolean called;
            @Override public void connected(ComponentName name, IBinder service, IBinderSession session, boolean dead) {
                synchronized (this) { if (service == null || called) return; called = true; }
                try {
                    call.run(IAccountAuthenticator.Stub.asInterface(service), new IAccountAuthenticatorResponse.Stub() {
                        @Override public void onResult(Bundle result) {
                            if (result != null && onResult != null) onResult.accept(result);
                            reply(response, result);
                            done();
                        }
                        @Override public void onRequestContinued() { /* the authenticator answers later */ }
                        @Override public void onError(int code, String message) { error(response, code, message); done(); }
                    });
                } catch (RemoteException | RuntimeException failure) {
                    error(response, AccountManager.ERROR_CODE_REMOTE_EXCEPTION, "authenticator of " + type + ": " + failure);
                    done();
                }
            }
            private void done() { bindings.unbind(caller, asBinder()); }
        };
        Intent intent = new Intent(AccountManager.ACTION_AUTHENTICATOR_INTENT).setComponent(authenticator.service());
        if (!bindings.bind(caller, intent, connection, Context.BIND_AUTO_CREATE, null))
            error(response, AccountManager.ERROR_CODE_REMOTE_EXCEPTION, "cannot bind the authenticator " + authenticator.service());
    }

    // The store

    @Override public String getPassword(Account account) { owner(account.type); return store.read(account, e -> e.password); }
    @Override public String getUserData(Account account, String key) { owner(account.type); return store.read(account, e -> e.data.get(key)); }
    @Override public AuthenticatorDescription[] getAuthenticatorTypes(int userId) {
        return authenticators.all().values().stream().map(HubAuthenticators.Authenticator::description).toArray(AuthenticatorDescription[]::new);
    }
    @Override public Account[] getAccountsForPackage(String packageName, int uid, String opPackageName) { return store.list(null); }
    @Override public Account[] getAccountsByTypeForPackage(String type, String packageName, String opPackageName) { return store.list(type); }
    @Override public Account[] getAccountsAsUser(String type, int userId, String opPackageName) { return store.list(type); }
    @Override public boolean addAccountExplicitly(Account account, String password, Bundle extras, String opPackageName) {
        owner(account.type);
        boolean added = store.add(account, password, extras);
        if (added) { System.out.println("[apklayer-hub] account added: " + account.type + " by " + caller().name()); changed(null); }
        return added;
    }
    @Override public boolean addAccountExplicitlyWithVisibility(Account account, String password, Bundle extras, Map visibility, String opPackageName) {
        return addAccountExplicitly(account, password, extras, opPackageName);
    }
    @Override public boolean removeAccountExplicitly(Account account) {
        owner(account.type);
        boolean removed = store.remove(account);
        if (removed) changed(account);
        return removed;
    }
    /** shortcut: removed without asking the authenticator's getAccountRemovalAllowed (every authenticator here allows it); ask it when one refuses. */
    @Override public void removeAccountAsUser(IAccountManagerResponse response, Account account, boolean expectActivityLaunch, int userId) {
        owner(account.type);
        boolean removed = store.remove(account);
        if (removed) changed(account);
        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, removed);
        reply(response, result);
    }
    @Override public void invalidateAuthToken(String accountType, String authToken) { store.invalidate(accountType, authToken); }
    @Override public String peekAuthToken(Account account, String authTokenType) { owner(account.type); return store.read(account, e -> e.tokens.get(authTokenType)); }
    @Override public void setAuthToken(Account account, String authTokenType, String authToken) { owner(account.type); store.update(account, e -> e.tokens.put(authTokenType, authToken)); }
    @Override public void setPassword(Account account, String password) { owner(account.type); store.update(account, e -> { e.password = password; e.tokens.clear(); }); }
    @Override public void clearPassword(Account account) { setPassword(account, null); }
    @Override public void setUserData(Account account, String key, String value) {
        owner(account.type);
        store.update(account, e -> { if (value == null) e.data.remove(key); else e.data.put(key, value); });
    }
    @Override public boolean accountAuthenticated(Account account) { owner(account.type); return store.has(account); }
    @Override public void renameAccount(IAccountManagerResponse response, Account account, String newName) {
        owner(account.type);
        Account renamed = store.rename(account, newName);
        if (renamed == null) { error(response, AccountManager.ERROR_CODE_BAD_ARGUMENTS, "cannot rename " + account.type + " account"); return; }
        changed(null);
        reply(response, accountResult(renamed));
    }
    @Override public String getPreviousName(Account account) { return null; }
    @Override public boolean someUserHasAccount(Account account) { return store.has(account); }

    // Visibility: every account is visible to every app (see the class comment)

    @Override public Map getPackagesAndVisibilityForAccount(Account account) { return new HashMap<String, Integer>(); }
    @Override public boolean setAccountVisibility(Account account, String packageName, int newVisibility) { return store.has(account); }
    @Override public int getAccountVisibility(Account account, String packageName) { return AccountManager.VISIBILITY_VISIBLE; }
    @Override public Map getAccountsAndVisibilityForPackage(String packageName, String accountType) {
        Map<Account, Integer> visible = new HashMap<>();
        for (Account account : store.list(accountType)) visible.put(account, AccountManager.VISIBILITY_VISIBLE);
        return visible;
    }
    @Override public boolean hasAccountAccess(Account account, String packageName, UserHandle userHandle) { return store.has(account); }
    @Override public IntentSender createRequestAccountAccessIntentSenderAsUser(Account account, String packageName, UserHandle userHandle) { return null; }
    @Override public void updateAppPermission(Account account, String authTokenType, int uid, boolean value) { /* every app may use every token type */ }
    @Override public void registerAccountListener(String[] accountTypes, String opPackageName) { /* the change broadcasts go to every app */ }
    @Override public void unregisterAccountListener(String[] accountTypes, String opPackageName) { /* as above */ }
    @Override public void onAccountAccessed(String token) { /* no access records */ }
    @Override public void copyAccountToUser(IAccountManagerResponse response, Account account, int userFrom, int userTo) {
        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);  // one user
        reply(response, result);
    }
    @Override public void addSharedAccountsFromParentUser(int parentUserId, int userId, String opPackageName) { /* one user */ }

    // Through the authenticator

    @Override public void getAuthToken(IAccountManagerResponse response, Account account, String authTokenType, boolean notifyOnAuthFailure, boolean expectActivityLaunch, Bundle options) {
        HubAuthenticators.Authenticator authenticator = account == null ? null : authenticators.all().get(account.type);
        boolean custom = authenticator != null && authenticator.description().customTokens;
        String cached = custom ? null : store.read(account, e -> e.tokens.get(authTokenType));
        if (cached != null) {
            Bundle result = accountResult(account);
            result.putString(AccountManager.KEY_AUTHTOKEN, cached);
            reply(response, result);
            return;
        }
        Bundle o = withCaller(options, caller());
        session(account == null ? null : account.type, response, (a, r) -> a.getAuthToken(r, account, authTokenType, o), result -> {
            String token = result.getString(AccountManager.KEY_AUTHTOKEN);
            if (!custom && token != null) store.update(account, e -> e.tokens.put(authTokenType, token));  // AccountManagerService.saveAuthTokenToDatabase
        });
    }
    @Override public void addAccount(IAccountManagerResponse response, String accountType, String authTokenType, String[] requiredFeatures, boolean expectActivityLaunch, Bundle options) {
        Bundle o = withCaller(options, caller());
        session(accountType, response, (a, r) -> a.addAccount(r, accountType, authTokenType, requiredFeatures, o), null);
    }
    @Override public void addAccountAsUser(IAccountManagerResponse response, String accountType, String authTokenType, String[] requiredFeatures, boolean expectActivityLaunch, Bundle options, int userId) {
        addAccount(response, accountType, authTokenType, requiredFeatures, expectActivityLaunch, options);
    }
    @Override public void updateCredentials(IAccountManagerResponse response, Account account, String authTokenType, boolean expectActivityLaunch, Bundle options) {
        session(account.type, response, (a, r) -> a.updateCredentials(r, account, authTokenType, options), null);
    }
    @Override public void editProperties(IAccountManagerResponse response, String accountType, boolean expectActivityLaunch) {
        session(accountType, response, (a, r) -> a.editProperties(r, accountType), null);
    }
    @Override public void confirmCredentialsAsUser(IAccountManagerResponse response, Account account, Bundle options, boolean expectActivityLaunch, int userId) {
        session(account.type, response, (a, r) -> a.confirmCredentials(r, account, options), null);
    }
    @Override public void getAuthTokenLabel(IAccountManagerResponse response, String accountType, String authTokenType) {
        session(accountType, response, (a, r) -> a.getAuthTokenLabel(r, authTokenType), null);
    }
    @Override public void hasFeatures(IAccountManagerResponse response, Account account, String[] features, int userId, String opPackageName) {
        session(account.type, response, (a, r) -> a.hasFeatures(r, account, features), null);
    }
    @Override public void startAddAccountSession(IAccountManagerResponse response, String accountType, String authTokenType, String[] requiredFeatures, boolean expectActivityLaunch, Bundle options) {
        Bundle o = withCaller(options, caller());
        session(accountType, response, (a, r) -> a.startAddAccountSession(r, accountType, authTokenType, requiredFeatures, o), null);
    }
    @Override public void startUpdateCredentialsSession(IAccountManagerResponse response, Account account, String authTokenType, boolean expectActivityLaunch, Bundle options) {
        session(account.type, response, (a, r) -> a.startUpdateCredentialsSession(r, account, authTokenType, options), null);
    }
    @Override public void finishSessionAsUser(IAccountManagerResponse response, Bundle sessionBundle, boolean expectActivityLaunch, Bundle appInfo, int userId) {
        String type = sessionBundle == null ? null : sessionBundle.getString(AccountManager.KEY_ACCOUNT_TYPE);
        session(type, response, (a, r) -> a.finishSession(r, type, sessionBundle), null);
    }
    @Override public void isCredentialsUpdateSuggested(IAccountManagerResponse response, Account account, String statusToken) {
        session(account.type, response, (a, r) -> a.isCredentialsUpdateSuggested(r, account, statusToken), null);
    }
    /** shortcut: the required features are not checked with the authenticator (hasFeatures per account); ask it when an app filters by features. */
    @Override public void getAccountsByFeatures(IAccountManagerResponse response, String accountType, String[] features, String opPackageName) {
        Bundle result = new Bundle();
        result.putParcelableArray(AccountManager.KEY_ACCOUNTS, store.list(accountType));
        reply(response, result);
    }
    /** The one account of the type, or none (Android shows a chooser for several: the first is taken here). */
    @Override public void getAccountByTypeAndFeatures(IAccountManagerResponse response, String accountType, String[] features, String opPackageName) {
        Account[] accounts = store.list(accountType);
        reply(response, accounts.length == 0 ? new Bundle() : accountResult(accounts[0]));
    }
}
