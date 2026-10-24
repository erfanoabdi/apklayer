// android.security.identity.PresentationSession -- an API-33 multi-credential
// presentation session (one ephemeral key and one session transcript shared
// across several credentials, which IdentityCredential could not do).
//
// Abstract on AOSP and unreachable here for the same reason as
// IdentityCredential: a session comes only from
// IdentityCredentialStore.createPresentationSession(), and our store's
// getInstance() correctly returns null on hardware with no secure element.
// The type exists so BiometricPrompt.CryptoObject's API-33 constructor and
// getter resolve.
//
// Mirrors identity/java/android/security/identity/PresentationSession.java
// (android13-release, API 33), including getCredstoreOperationHandle(), which
// is @hide on AOSP but is what CryptoObject.getOperationHandle() reads.
package android.security.identity;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;

public abstract class PresentationSession {

    protected PresentationSession() {}

    public abstract KeyPair getEphemeralKeyPair();

    public abstract void setReaderEphemeralPublicKey(PublicKey readerEphemeralPublicKey)
            throws InvalidKeyException;

    public abstract void setSessionTranscript(byte[] sessionTranscript);

    public abstract CredentialDataResult getCredentialData(
            String credentialName, CredentialDataRequest request)
            throws NoAuthenticationKeyAvailableException, InvalidReaderSignatureException,
            InvalidRequestMessageException, EphemeralPublicKeyNotFoundException;

    /** @hide on AOSP; CryptoObject.getOperationHandle() reads it. */
    public abstract long getCredstoreOperationHandle();
}
