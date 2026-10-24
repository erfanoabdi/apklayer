package android.accounts;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
/** The SPI an app implements to add an account type (a sync/login provider).
 *  On real Android the AccountManagerService binds to the app's authenticator
 *  service and drives these. Mac.apk has no AccountManagerService, so the
 *  authenticator is constructed but never bound; getIBinder returns a real
 *  transport Binder so the app's service.onBind can hand it back. API 5.
 *  Source: frameworks/base/core/java/android/accounts/AbstractAccountAuthenticator.java */
public abstract class AbstractAccountAuthenticator {
    private final Context mContext;
    private final android.os.Binder mTransport = new android.os.Binder();
    public AbstractAccountAuthenticator(Context context) {
        mContext = context;
        synchronized (BY_TRANSPORT) {
            BY_TRANSPORT.put(mTransport, new java.lang.ref.WeakReference<>(this));
        }
    }
    public final IBinder getIBinder() { return mTransport; }

    // AOSP's Transport is an IAccountAuthenticator.Stub the AccountManagerService calls across
    // processes. Here the app and its "service" share one process, so AccountManager maps the
    // binder the app's authenticator Service returned from onBind back to the authenticator
    // and calls it directly -- the same methods, minus the parcel round trip.
    // The value is a WeakReference because the authenticator holds its own transport: a strong
    // value would pin the key and the map would never let a dead authenticator go.
    private static final java.util.Map<IBinder,
            java.lang.ref.WeakReference<AbstractAccountAuthenticator>> BY_TRANSPORT =
            new java.util.WeakHashMap<>();

    static AbstractAccountAuthenticator forBinder(IBinder b) {
        if (b == null) return null;
        synchronized (BY_TRANSPORT) {
            java.lang.ref.WeakReference<AbstractAccountAuthenticator> r = BY_TRANSPORT.get(b);
            return r == null ? null : r.get();
        }
    }

    public Bundle addAccountFromCredentials(AccountAuthenticatorResponse response,
            Account account, Bundle accountCredentials) throws NetworkErrorException { return null; }
    public abstract Bundle editProperties(AccountAuthenticatorResponse response, String accountType);
    public abstract Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
            String authTokenType, String[] requiredFeatures, Bundle options)
            throws NetworkErrorException;
    public abstract Bundle confirmCredentials(AccountAuthenticatorResponse response,
            Account account, Bundle options) throws NetworkErrorException;
    public abstract Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException;
    public abstract String getAuthTokenLabel(String authTokenType);
    public abstract Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException;
    public abstract Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
            String[] features) throws NetworkErrorException;
    /** AOSP's default (AbstractAccountAuthenticator.java, android14-release): removal is
     *  allowed -- a Bundle carrying KEY_BOOLEAN_RESULT=true. Null would mean "the answer comes
     *  later through the response", which is not what an authenticator that does not override
     *  this says. */
    public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response, Account account)
            throws NetworkErrorException {
        final Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        return result;
    }

    // === Session-based add/update (API 26) ==================================
    // These five have real AOSP DEFAULT implementations that a subclass may not
    // override, so a stub returning null is a behaviour change the subclass
    // cannot see. They are entirely in-process (bundle packing plus a callback),
    // so they are reproduced faithfully here, including the session-bundle keys
    // -- finishSession round-trips exactly what startAddAccountSession /
    // startUpdateCredentialsSession put in, and the key STRINGS are part of that
    // contract, typo in KEY_AUTH_TOKEN_TYPE included ("Authenticato").
    // Source: frameworks/base/core/java/android/accounts/AbstractAccountAuthenticator.java
    private static final String KEY_AUTH_TOKEN_TYPE =
            "android.accounts.AbstractAccountAuthenticato.KEY_AUTH_TOKEN_TYPE";
    private static final String KEY_REQUIRED_FEATURES =
            "android.accounts.AbstractAccountAuthenticator.KEY_REQUIRED_FEATURES";
    private static final String KEY_OPTIONS =
            "android.accounts.AbstractAccountAuthenticator.KEY_OPTIONS";
    private static final String KEY_ACCOUNT =
            "android.accounts.AbstractAccountAuthenticator.KEY_ACCOUNT";

    public Bundle startAddAccountSession(final AccountAuthenticatorResponse response,
            final String accountType, final String authTokenType, final String[] requiredFeatures,
            final Bundle options) throws NetworkErrorException {
        new Thread(new Runnable() {
            @Override public void run() {
                Bundle sessionBundle = new Bundle();
                sessionBundle.putString(KEY_AUTH_TOKEN_TYPE, authTokenType);
                sessionBundle.putStringArray(KEY_REQUIRED_FEATURES, requiredFeatures);
                sessionBundle.putBundle(KEY_OPTIONS, options);
                Bundle result = new Bundle();
                result.putBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
                response.onResult(result);
            }
        }).start();
        return null;
    }

    public Bundle startUpdateCredentialsSession(final AccountAuthenticatorResponse response,
            final Account account, final String authTokenType, final Bundle options)
            throws NetworkErrorException {
        new Thread(new Runnable() {
            @Override public void run() {
                Bundle sessionBundle = new Bundle();
                sessionBundle.putString(KEY_AUTH_TOKEN_TYPE, authTokenType);
                sessionBundle.putParcelable(KEY_ACCOUNT, account);
                sessionBundle.putBundle(KEY_OPTIONS, options);
                Bundle result = new Bundle();
                result.putBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
                response.onResult(result);
            }
        }).start();
        return null;
    }

    public Bundle finishSession(final AccountAuthenticatorResponse response,
            final String accountType, final Bundle sessionBundle) throws NetworkErrorException {
        if (accountType == null || accountType.length() == 0) {
            Bundle result = new Bundle();
            result.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_BAD_ARGUMENTS);
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "accountType cannot be empty.");
            return result;
        }
        if (sessionBundle == null) {
            Bundle result = new Bundle();
            result.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_BAD_ARGUMENTS);
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "sessionBundle cannot be null.");
            return result;
        }
        if (!sessionBundle.containsKey(KEY_AUTH_TOKEN_TYPE)) {
            // Not a session bundle this class produced, so the subclass must have
            // overridden start*Session without overriding finishSession.
            Bundle result = new Bundle();
            result.putInt(AccountManager.KEY_ERROR_CODE,
                    AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION);
            result.putString(AccountManager.KEY_ERROR_MESSAGE,
                    "Authenticator must override finishSession if startAddAccountSession"
                            + " or startUpdateCredentialsSession is overridden.");
            response.onResult(result);
            return result;
        }
        String authTokenType = sessionBundle.getString(KEY_AUTH_TOKEN_TYPE);
        Bundle options = sessionBundle.getBundle(KEY_OPTIONS);
        String[] requiredFeatures = sessionBundle.getStringArray(KEY_REQUIRED_FEATURES);
        Account account = sessionBundle.getParcelable(KEY_ACCOUNT, Account.class);
        boolean containsKeyAccount = sessionBundle.containsKey(KEY_ACCOUNT);

        Bundle sessionOptions = new Bundle(sessionBundle);
        sessionOptions.remove(KEY_AUTH_TOKEN_TYPE);
        sessionOptions.remove(KEY_REQUIRED_FEATURES);
        sessionOptions.remove(KEY_OPTIONS);
        sessionOptions.remove(KEY_ACCOUNT);

        if (options != null) {
            options.putAll(sessionOptions);
            sessionOptions = options;
        }

        if (containsKeyAccount) {
            return updateCredentials(response, account, authTokenType, options);
        }
        return addAccount(response, accountType, authTokenType, requiredFeatures, sessionOptions);
    }

    public Bundle isCredentialsUpdateSuggested(final AccountAuthenticatorResponse response,
            Account account, String statusToken) throws NetworkErrorException {
        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
        return result;
    }

    public Bundle getAccountCredentialsForCloning(final AccountAuthenticatorResponse response,
            final Account account) throws NetworkErrorException {
        new Thread(new Runnable() {
            @Override public void run() {
                Bundle result = new Bundle();
                result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
                response.onResult(result);
            }
        }).start();
        return null;
    }
}
