// android.security.identity.WritableIdentityCredential -- a not-yet-
// personalized credential, returned by IdentityCredentialStore.createCredential().
//
// Abstract on AOSP and unreachable here: our store's getInstance() correctly
// returns null on hardware with no secure element, so createCredential() can
// never be called. See IdentityCredentialStore for the full reasoning.
//
// Mirrors identity/java/android/security/identity/WritableIdentityCredential.java
// (android11-release, API 30).
package android.security.identity;

import java.security.cert.X509Certificate;
import java.util.Collection;

public abstract class WritableIdentityCredential {

    protected WritableIdentityCredential() {}

    public abstract Collection<X509Certificate> getCredentialKeyCertificateChain(byte[] challenge);

    public abstract byte[] personalize(PersonalizationData personalizationData);
}
