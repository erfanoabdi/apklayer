// android.security.identity.IdentityCredentialStore -- the entry point to the
// Identity Credential API (mobile driving licence / ISO 18013-5 style
// credentials held in a secure element).
//
// THIS IS THE HONEST-ANSWER CLASS FOR THE WHOLE PACKAGE. AOSP documents
// getInstance() as returning null "if the device doesn't have hardware-backed
// support for secure storage of user identity documents"
// (identity/java/android/security/identity/IdentityCredentialStore.java:105-108,
// android11-release). A Mac has no secure element and no Android credstore
// daemon, so null is not a stub -- it is the answer the API is specified to
// give on hardware like ours, and it is the same answer the majority of real
// Android devices give.
//
// That null return is also why every other abstract class in this package can
// stay abstract with no implementation: IdentityCredential,
// WritableIdentityCredential, ResultData and PresentationSession are ONLY
// reachable through a store, so an app that cannot get a store can never
// obtain one. The types exist so the app's feature-detection code LINKS and
// runs its check; the check then correctly concludes "not supported here".
//
// Mirrors the API-30 public surface. The Credstore* implementation classes are
// deliberately not mirrored: they are not public API and there is nothing for
// them to talk to.
package android.security.identity;

import android.content.Context;

public abstract class IdentityCredentialStore {

    public static final int CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256 = 1;

    protected IdentityCredentialStore() {}

    /**
     * Gets the default store, or null when the device has no hardware-backed
     * secure storage for identity documents. Always null here -- see the
     * header. AOSP delegates to CredstoreIdentityCredentialStore.getInstance,
     * which itself returns null when the credstore binder service is absent.
     */
    public static IdentityCredentialStore getInstance(Context context) {
        return null;
    }

    /**
     * Direct access requires specialized NFC hardware and is null even on many
     * devices that DO have the default store. Null here for the same reason.
     */
    public static IdentityCredentialStore getDirectAccessInstance(Context context) {
        return null;
    }

    public abstract String[] getSupportedDocTypes();

    public abstract WritableIdentityCredential createCredential(
            String credentialName, String docType)
            throws AlreadyPersonalizedException, DocTypeNotSupportedException;

    public abstract IdentityCredential getCredentialByName(String credentialName,
            int cipherSuite)
            throws CipherSuiteNotSupportedException;

    public abstract byte[] deleteCredentialByName(String credentialName);

    /** API 33: a session that can present several credentials under one
     *  ephemeral key and session transcript. AOSP declares it NON-abstract with
     *  `throw new UnsupportedOperationException()`
     *  (IdentityCredentialStore.java:226-229) so that stores predating the API
     *  keep compiling; mirrored exactly. Unreachable here anyway -- there is no
     *  store instance to call it on. */
    public PresentationSession createPresentationSession(int cipherSuite)
            throws CipherSuiteNotSupportedException {
        throw new UnsupportedOperationException();
    }
}
