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
 * As on Android, only an account type's authenticator (its package, or one signed with the same certificate) adds, removes and reads the secrets of its
 * accounts, and an app sees an account only when it is visible to it (resolveAccountVisibility below).
 */
final class HubAccounts extends IAccountManager.Stub {
    static final String SERVICE = "apklayer.accounts";

    private interface Call { void run(IAccountAuthenticator authenticator, IAccountAuthenticatorResponse response) throws RemoteException; }

    private final HubDirectory directory;
    private final HubServiceBindings bindings;
    private final HubPackages packages;
    private final HubAuthenticators authenticators;
    private final HubAccountStore store;

    HubAccounts(HubDirectory directory, HubServiceBindings bindings, HubPackages packages, HubAuthenticators authenticators, java.nio.file.Path file) {
        this.directory = directory;
        this.bindings = bindings;
        this.packages = packages;
        this.authenticators = authenticators;
        this.store = new HubAccountStore(file);
    }

    private HubDirectory.Peer caller() {
        HubDirectory.Peer peer = directory.peerByPid(Binder.getCallingPid());
        if (peer == null) throw new SecurityException("Unregistered calling process");
        return peer;
    }

    /** The package that authenticates the account type, or null. */
    private String authenticator(String type) {
        HubAuthenticators.Authenticator authenticator = type == null ? null : authenticators.all().get(type);
        return authenticator == null ? null : authenticator.service().getPackageName();
    }

    /** isAccountManagedByCaller: the authenticator's package, or one signed with the same certificate. */
    private boolean manages(String pkg, String type) {
        String authenticator = authenticator(type);
        return authenticator != null && (authenticator.equals(pkg) || HostCertificate.sameSigner(pkg, authenticator));
    }

    /** The secrets and the visibility of an account type belong to the packages that manage it. */
    private HubDirectory.Peer owner(String type) {
        HubDirectory.Peer peer = caller();
        if (!manages(peer.name(), type)) throw new SecurityException(peer.name() + " is not the authenticator of account type " + type);
        return peer;
    }

    /**
     * AccountManagerService.resolveAccountVisibility: the system and the authenticator's package always see an account; then what was set for the
     * package; else the legacy default: visible ("user managed") to a package signed like the authenticator, to an app targeting before O that holds
     * GET_ACCOUNTS and to holders of GET_ACCOUNTS_PRIVILEGED, not visible to others.
     * Limit: READ_CONTACTS does not open contacts-managing account types (accountTypeManagesContacts: no contacts sync adapter is modelled).
     */
    private int visibility(Account account, String pkg) {
        String authenticator = authenticator(account.type);
        if ("android".equals(pkg) || pkg.equals(authenticator)) return AccountManager.VISIBILITY_VISIBLE;
        Integer set = store.read(account, e -> e.visibility.get(pkg));
        if (set != null && set != AccountManager.VISIBILITY_UNDEFINED) return set;
        android.content.pm.PackageParser.Package parsed = packages.get(pkg);
        if (parsed == null) return AccountManager.VISIBILITY_NOT_VISIBLE;
        boolean legacy = (authenticator != null && HostCertificate.sameSigner(pkg, authenticator))
                || (parsed.applicationInfo.targetSdkVersion < android.os.Build.VERSION_CODES.O && packages.holds(pkg, android.Manifest.permission.GET_ACCOUNTS))
                || packages.holds(pkg, android.Manifest.permission.GET_ACCOUNTS_PRIVILEGED);
        Integer fallback = store.read(account, e -> e.visibility.get(legacy ? AccountManager.PACKAGE_NAME_KEY_LEGACY_VISIBLE : AccountManager.PACKAGE_NAME_KEY_LEGACY_NOT_VISIBLE));
        if (fallback != null && fallback != AccountManager.VISIBILITY_UNDEFINED) return fallback;
        return legacy ? AccountManager.VISIBILITY_USER_MANAGED_VISIBLE : AccountManager.VISIBILITY_USER_MANAGED_NOT_VISIBLE;
    }

    /** The accounts (of the type, or all) the package sees; with userManaged also those its user could pick for it (an authenticator asking for another package). */
    private Account[] visible(String type, String pkg, boolean userManaged) {
        return java.util.Arrays.stream(store.list(type)).filter(account -> {
            int v = visibility(account, pkg);
            return v == AccountManager.VISIBILITY_VISIBLE || v == AccountManager.VISIBILITY_USER_MANAGED_VISIBLE || (userManaged && v == AccountManager.VISIBILITY_USER_MANAGED_NOT_VISIBLE);
        }).toArray(Account[]::new);
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
        session(caller(), type, response, call, onResult);
    }
    private void session(HubDirectory.Peer caller, String type, IAccountManagerResponse response, Call call, java.util.function.Consumer<Bundle> onResult) {
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
    @Override public Account[] getAccountsForPackage(String packageName, int uid, String opPackageName) {
        throw new SecurityException("getAccountsForPackage() called from unauthorized " + caller().name());  // the system's: no app is
    }
    /** For another package: only an authenticator asks, for its own type (an account chooser it shows); all types: only what this app sees itself. */
    @Override public Account[] getAccountsByTypeForPackage(String type, String packageName, String opPackageName) {
        if (type == null) return visible(null, caller().name(), false);
        return manages(caller().name(), type) ? visible(type, packageName, true) : new Account[0];
    }
    @Override public Account[] getAccountsAsUser(String type, int userId, String opPackageName) { return visible(type, caller().name(), false); }
    @Override public boolean addAccountExplicitly(Account account, String password, Bundle extras, String opPackageName) {
        owner(account.type);
        boolean added = store.add(account, password, extras);
        if (added) { System.out.println("[apklayer-hub] account added: " + account.type + " by " + caller().name()); changed(null); }
        return added;
    }
    @Override public boolean addAccountExplicitlyWithVisibility(Account account, String password, Bundle extras, Map visibility, String opPackageName) {
        owner(account.type);
        if (!store.add(account, password, extras)) return false;
        if (visibility != null) store.update(account, e -> ((Map<?, ?>) visibility).forEach((pkg, v) -> e.visibility.put((String) pkg, (Integer) v)));
        System.out.println("[apklayer-hub] account added: " + account.type + " by " + caller().name());
        changed(null);
        return true;
    }
    @Override public boolean removeAccountExplicitly(Account account) {
        owner(account.type);
        boolean removed = store.remove(account);
        if (removed) changed(account);
        return removed;
    }
    /** AccountManagerService.RemoveAccountSession: the authenticator's getAccountRemovalAllowed decides; an allowed removal answers whether the store had it. */
    @Override public void removeAccountAsUser(IAccountManagerResponse response, Account account, boolean expectActivityLaunch, int userId) {
        owner(account.type);
        session(account.type, response, (a, r) -> a.getAccountRemovalAllowed(r, account), result -> {
            if (!result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT) || result.containsKey(AccountManager.KEY_INTENT)) return;
            boolean removed = store.remove(account);
            if (removed) changed(account);
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, removed);
        });
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

    // Visibility: set and read by the packages that manage the account type

    @Override public Map getPackagesAndVisibilityForAccount(Account account) {
        owner(account.type);
        Map<String, Integer> set = store.read(account, e -> new HashMap<>(e.visibility));
        return set == null ? new HashMap<String, Integer>() : set;
    }
    @Override public boolean setAccountVisibility(Account account, String packageName, int newVisibility) {
        owner(account.type);
        boolean set = store.update(account, e -> e.visibility.put(packageName, newVisibility));
        if (set) changed(null);
        return set;
    }
    @Override public int getAccountVisibility(Account account, String packageName) {
        owner(account.type);
        if (AccountManager.PACKAGE_NAME_KEY_LEGACY_VISIBLE.equals(packageName) || AccountManager.PACKAGE_NAME_KEY_LEGACY_NOT_VISIBLE.equals(packageName)) {
            Integer set = store.read(account, e -> e.visibility.get(packageName));
            return set != null ? set : packageName.equals(AccountManager.PACKAGE_NAME_KEY_LEGACY_VISIBLE) ? AccountManager.VISIBILITY_USER_MANAGED_VISIBLE : AccountManager.VISIBILITY_USER_MANAGED_NOT_VISIBLE;
        }
        return store.has(account) ? visibility(account, packageName) : AccountManager.VISIBILITY_NOT_VISIBLE;
    }
    @Override public Map getAccountsAndVisibilityForPackage(String packageName, String accountType) {
        if (accountType == null) throw new SecurityException("getAccountsAndVisibilityForPackage() for every type is the system's");
        owner(accountType);
        Map<Account, Integer> result = new HashMap<>();
        for (Account account : store.list(accountType)) result.put(account, visibility(account, packageName));
        return result;
    }
    @Override public boolean hasAccountAccess(Account account, String packageName, UserHandle userHandle) {
        throw new SecurityException("hasAccountAccess can be called only by the system, not " + caller().name());
    }
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

    /**
     * AccountManagerService.getAuthToken's grant check: an authenticator with custom tokens decides itself; the account's authenticator and an app that sees
     * the account get a token (shortcut: Android asks an app that sees it too unless its user granted that token type before; ask when an app relies on
     * the question); any other app's user is asked, as GrantCredentialsPermissionActivity does, in a desktop dialog of the hub (HubInstallDialog).
     * Allowed: the account becomes visible to the app (shortcut: Android keeps the grant per token type in its grants table, apart from visibility; keep it
     * apart when an app tells the two apart). Denied: ERROR_CODE_CANCELED, as that activity answers.
     */
    @Override public void getAuthToken(IAccountManagerResponse response, Account account, String authTokenType, boolean notifyOnAuthFailure, boolean expectActivityLaunch, Bundle options) {
        HubDirectory.Peer caller = caller();
        HubAuthenticators.Authenticator authenticator = account == null ? null : authenticators.all().get(account.type);
        boolean custom = authenticator != null && authenticator.description().customTokens;
        if (account != null && !custom && !java.util.Arrays.asList(visible(account.type, caller.name(), false)).contains(account)) {
            if (!store.has(account)) { error(response, AccountManager.ERROR_CODE_BAD_ARGUMENTS, "no account " + account.name + " of type " + account.type); return; }
            String label = HubInstaller.labelOf(caller.name(), caller.name());
            Thread ask = new Thread(() -> {  // the person's answer takes its time: never on a binder thread of the hub
                if (!HubInstallDialog.ask("Allow " + label + " to use your account?", label + " (" + caller.name() + ") asks for access to " + account.name + " (" + account.type + ").", "Allow", "Deny")) {
                    error(response, AccountManager.ERROR_CODE_CANCELED, "canceled");
                    return;
                }
                if (store.update(account, e -> e.visibility.put(caller.name(), AccountManager.VISIBILITY_USER_MANAGED_VISIBLE))) changed(null);
                token(caller, response, account, authTokenType, options, false);
            }, "hub-account-grant");
            ask.setDaemon(true);
            ask.start();
            return;
        }
        token(caller, response, account, authTokenType, options, custom);
    }
    private void token(HubDirectory.Peer caller, IAccountManagerResponse response, Account account, String authTokenType, Bundle options, boolean custom) {
        String cached = custom ? null : store.read(account, e -> e.tokens.get(authTokenType));
        if (cached != null) {
            Bundle result = accountResult(account);
            result.putString(AccountManager.KEY_AUTHTOKEN, cached);
            reply(response, result);
            return;
        }
        Bundle o = withCaller(options, caller);
        session(caller, account == null ? null : account.type, response, (a, r) -> a.getAuthToken(r, account, authTokenType, o), result -> {
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
    /** Limit: the required features are not checked with the authenticator (its hasFeatures per account): every visible account of the type is returned. */
    @Override public void getAccountsByFeatures(IAccountManagerResponse response, String accountType, String[] features, String opPackageName) {
        Bundle result = new Bundle();
        result.putParcelableArray(AccountManager.KEY_ACCOUNTS, visible(accountType, caller().name(), false));
        reply(response, result);
    }
    /** The one account of the type, or none (Android shows a chooser for several: the first is taken here). */
    @Override public void getAccountByTypeAndFeatures(IAccountManagerResponse response, String accountType, String[] features, String opPackageName) {
        Account[] accounts = visible(accountType, caller().name(), false);
        reply(response, accounts.length == 0 ? new Bundle() : accountResult(accounts[0]));
    }
}
