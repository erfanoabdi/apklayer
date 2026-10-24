// android.security.KeyChain -- the system client-certificate / private-key
// store. Enterprise apps and mTLS HTTP stacks call:
//   KeyChain.choosePrivateKeyAlias(...) to prompt the user to pick a cert,
//   KeyChain.getPrivateKey(ctx, alias) / getCertificateChain(ctx, alias) to
//     pull the chosen key+chain for a client-auth handshake,
//   KeyChain.createInstallIntent() to launch the "install a certificate" UI.
//
// AOSP source:
//   frameworks/base/keystore/java/android/security/KeyChain.java
// Signatures verified against that source (decoded from the googlesource
// base64 mirror): the two choosePrivateKeyAlias overloads
// (host/port vs Uri), getPrivateKey/getCertificateChain
// (throw KeyChainException + InterruptedException), createInstallIntent,
// isKeyAlgorithmSupported, isBoundKeyAlgorithm (deprecated), and the ACTION_*/
// EXTRA_* String constants with their exact values.
//
// Mac.apk behavior: there is no on-device system credential store or
// credential-chooser UI on macOS. We are CNF-safe and signature-faithful:
//   - the constants resolve so apps that merely reference them load,
//   - choosePrivateKeyAlias delivers null to the callback ("user chose
//     nothing"), the documented outcome when no credential is selected,
//   - getPrivateKey / getCertificateChain return null ("alias not found"),
//     which is a legal AOSP return per the @Nullable contract -- the caller's
//     mTLS path then proceeds without a client cert exactly as on a device
//     with no matching credential installed.
// This is honest absence (the surface exists, the backend genuinely has no
// credentials), not a per-app workaround.
package android.security;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public final class KeyChain {
    // ACTION_* / EXTRA_* values verified against the AOSP source.
    public static final String ACTION_CHOOSER = "com.android.keychain.CHOOSER";
    public static final String ACTION_INSTALL = "android.credentials.INSTALL";
    public static final String ACTION_STORAGE_CHANGED = "android.security.STORAGE_CHANGED";
    public static final String ACTION_KEYCHAIN_CHANGED = "android.security.action.KEYCHAIN_CHANGED";
    public static final String ACTION_TRUST_STORE_CHANGED =
            "android.security.action.TRUST_STORE_CHANGED";
    public static final String ACTION_KEY_ACCESS_CHANGED =
            "android.security.action.KEY_ACCESS_CHANGED";

    public static final String EXTRA_RESPONSE = "response";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_ALIAS = "alias";
    public static final String EXTRA_SENDER = "sender";
    public static final String EXTRA_KEY_TYPES = "key_types";
    public static final String EXTRA_ISSUERS = "issuers";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_CERTIFICATE = "CERT";
    public static final String EXTRA_PKCS12 = "PKCS12";
    public static final String EXTRA_KEY_ALIAS = "android.security.extra.KEY_ALIAS";
    public static final String EXTRA_KEY_ACCESSIBLE = "android.security.extra.KEY_ACCESSIBLE";

    /** Returned to a DelegatedAdminReceiver to mean "no key, and the user may
     *  not grant one manually either". */
    public static final String KEY_ALIAS_SELECTION_DENIED = "android:alias-selection-denied";

    // android.security.Credentials.ACTION_MANAGE_CREDENTIALS, which AOSP's
    // KeyChain static-imports (KeyChain.java:18) to build the manage-credentials
    // Intent. Value from Credentials.java:52.
    private static final String ACTION_MANAGE_CREDENTIALS =
            "android.security.MANAGE_CREDENTIALS";
    // AOSP KeyChain.java:142 / :235 -- the Settings component the Intent targets
    // and the extra the policy travels in.
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String EXTRA_AUTHENTICATION_POLICY =
            "android.security.extra.AUTHENTICATION_POLICY";

    /** AOSP declares no constructor, so `KeyChain()` is public API (it appears
     *  as `ctor public KeyChain()` in the API signature file). It was private
     *  here, which is the dangerous direction: an app compiled against real
     *  Android that instantiates KeyChain would fail to resolve. */
    public KeyChain() {}

    /** AOSP createInstallIntent(): an Intent that, on a device, launches the
     *  CertInstaller "install a certificate" activity. We return a faithfully
     *  actioned Intent; there is no installer activity to receive it on macOS,
     *  so startActivity is a no-op, but the Intent object is correct so apps
     *  that build / inspect it before launching work. */
    public static Intent createInstallIntent() {
        return new Intent(ACTION_INSTALL);
    }

    /** AOSP choosePrivateKeyAlias(activity, response, keyTypes, issuers, host,
     *  port, alias): prompts the user to select a private key. With no system
     *  chooser UI we deliver null ("nothing chosen") on the callback, the
     *  documented outcome when the user cancels. Delivered synchronously
     *  because there is no UI to await. */
    public static void choosePrivateKeyAlias(Activity activity, KeyChainAliasCallback response,
            String[] keyTypes, Principal[] issuers, String host, int port, String alias) {
        if (response != null) response.alias(null);
    }

    /** AOSP choosePrivateKeyAlias overload taking a Uri instead of host/port. */
    public static void choosePrivateKeyAlias(Activity activity, KeyChainAliasCallback response,
            String[] keyTypes, Principal[] issuers, Uri uri, String alias) {
        if (response != null) response.alias(null);
    }

    /** AOSP getPrivateKey(context, alias): the private key for the chosen
     *  alias, or null if no such alias exists. No system credential store on
     *  macOS => no aliases => null. Declares the AOSP-checked exceptions so
     *  call sites compile and catch identically to on-device. */
    public static PrivateKey getPrivateKey(Context context, String alias)
            throws KeyChainException, InterruptedException {
        if (alias == null) throw new NullPointerException("alias == null");
        return null;
    }

    /** AOSP getCertificateChain(context, alias): the cert chain for the chosen
     *  alias, or null if no such alias exists. Null here for the same reason
     *  as getPrivateKey. */
    public static X509Certificate[] getCertificateChain(Context context, String alias)
            throws KeyChainException, InterruptedException {
        if (alias == null) throw new NullPointerException("alias == null");
        return null;
    }

    /** AOSP isKeyAlgorithmSupported(algorithm): whether the keychain can store
     *  a key of the given JCA algorithm. We accept the standard asymmetric
     *  algorithms AOSP supports (RSA / EC) so capability probes succeed; the
     *  actual store is empty, which getPrivateKey reflects. */
    public static boolean isKeyAlgorithmSupported(String algorithm) {
        if (algorithm == null) return false;
        return "RSA".equalsIgnoreCase(algorithm) || "EC".equalsIgnoreCase(algorithm);
    }

    // --- credential management app (API 30) -------------------------------
    // On a device, ONE app may be granted the right to manage the user's
    // KeyChain credentials on unmanaged hardware. The grant is made by the user
    // through Settings, in response to the Intent below. Mac.apk has no Settings
    // app and no KeyChain service, so no app is or can become the credential
    // management app -- and the four answers below say exactly that. None of
    // them is a bypass: they REFUSE the capability rather than pretending the
    // caller holds it.

    /** AOSP createManageCredentialsIntent(policy): the Intent that asks the user
     *  to make the caller the credential management app. We build the identical
     *  Intent (same action, same Settings component, same policy extra) so an
     *  app that inspects or logs it before startActivityForResult sees what it
     *  expects. Nothing on macOS resolves the component, so the launch is a
     *  no-op and the app's onActivityResult sees RESULT_CANCELED -- the same
     *  outcome as a user who declines. */
    public static Intent createManageCredentialsIntent(AppUriAuthenticationPolicy policy) {
        Intent intent = new Intent(ACTION_MANAGE_CREDENTIALS);
        intent.setComponent(android.content.ComponentName.createRelative(
                SETTINGS_PACKAGE, ".security.RequestManageCredentials"));
        intent.putExtra(EXTRA_AUTHENTICATION_POLICY, policy);
        return intent;
    }

    /** AOSP isCredentialManagementApp(context): whether the CALLER holds the
     *  grant. False here, always -- the grant cannot be issued. Returning true
     *  would hand an app a privilege the user never approved. */
    public static boolean isCredentialManagementApp(Context context) {
        return false;
    }

    /** AOSP getCredentialManagementAppPolicy(context): the policy of the
     *  credential management app; AOSP documents SecurityException "if the
     *  caller is not the credential management app". No caller here ever is
     *  (see isCredentialManagementApp), so SecurityException is the specified
     *  answer rather than an invented failure. */
    public static AppUriAuthenticationPolicy getCredentialManagementAppPolicy(Context context)
            throws SecurityException {
        throw new SecurityException(
                "Caller is not the credential management app; no credential management app "
                + "exists on this device");
    }

    /** AOSP removeCredentialManagementApp(context): clears the grant and the
     *  credentials it installed. True is the honest answer: the post-condition
     *  the API promises -- no credential management app, and none of its
     *  credentials present -- already holds and still holds afterwards. */
    public static boolean removeCredentialManagementApp(Context context) {
        return true;
    }

    /** AOSP isBoundKeyAlgorithm(algorithm) (deprecated): whether keys of this
     *  algorithm are hardware-bound to the device. macOS has no keymaster, so
     *  no key is hardware-bound. */
    @Deprecated public static boolean isBoundKeyAlgorithm(String algorithm) {
        return false;
    }
}
