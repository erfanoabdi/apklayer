// android.security.identity.IdentityCredential -- a provisioned identity
// document (mDL / ISO 18013-5) living in the device's secure element.
//
// The most-referenced missing class in the corpus: 5 apps name it, all of them
// through android.hardware.biometrics.BiometricPrompt.CryptoObject, whose
// constructor and getter take/return this type. Those apps do not create a
// credential -- they reference the TYPE, and the reference has to resolve or
// the class fails to link the moment CryptoObject is touched.
//
// ABSTRACT ON AOSP, and abstract here for a reason that is worth stating
// plainly: an IdentityCredential is only ever obtained from
// IdentityCredentialStore.getCredentialByName(), and our store's getInstance()
// correctly returns null because a Mac has no secure element. So there is no
// code path on which an instance could exist, and inventing a concrete
// implementation would be inventing a capability. The faithful abstract
// surface is the entire requirement.
//
// Mirrors identity/java/android/security/identity/IdentityCredential.java
// (android11-release, API 30) member for member, including
// getCredstoreOperationHandle(), which is @hide on AOSP but is what
// CryptoObject.getOperationHandle() calls.
package android.security.identity;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;

public abstract class IdentityCredential {

    protected IdentityCredential() {}

    public abstract KeyPair createEphemeralKeyPair();

    public abstract void setReaderEphemeralPublicKey(PublicKey readerEphemeralPublicKey)
            throws InvalidKeyException;

    public abstract byte[] encryptMessageToReader(byte[] messagePlaintext);

    public abstract byte[] decryptMessageFromReader(byte[] messageCiphertext)
            throws MessageDecryptionException;

    public abstract Collection<X509Certificate> getCredentialKeyCertificateChain();

    public abstract void setAllowUsingExhaustedKeys(boolean allowUsingExhaustedKeys);

    /** @hide on AOSP; CryptoObject.getOperationHandle() reads it. */
    public abstract long getCredstoreOperationHandle();

    public abstract ResultData getEntries(
            byte[] requestMessage,
            Map<String, Collection<String>> entriesToRequest,
            byte[] sessionTranscript,
            byte[] readerSignature)
            throws SessionTranscriptMismatchException, NoAuthenticationKeyAvailableException,
            InvalidReaderSignatureException, EphemeralPublicKeyNotFoundException,
            InvalidRequestMessageException;

    public abstract void setAvailableAuthenticationKeys(int keyCount, int maxUsesPerKey);

    public abstract Collection<X509Certificate> getAuthKeysNeedingCertification();

    public abstract void storeStaticAuthenticationData(
            X509Certificate authenticationKey,
            byte[] staticAuthData)
            throws UnknownAuthenticationKeyException;

    public abstract int[] getAuthenticationDataUsageCount();

    // --- API 31/33 additions --------------------------------------------
    // Every one of these is NON-abstract on AOSP with a bare
    // `throw new UnsupportedOperationException()` body, so that credential
    // implementations written against the API-30 surface keep compiling
    // (IdentityCredential.java:148-150, :467-469, :499-501, :525-527,
    // :551-556, :568-570, :421-426, android16-release). Mirrored exactly,
    // including the throw: an implementation that does not support the
    // operation must say so rather than return a plausible value, and NO
    // implementation exists here at all -- our store returns null, so none of
    // this is reachable. The signatures still have to link, because
    // BiometricPrompt.CryptoObject and the mDL SDKs name these types.

    @Deprecated
    public void setAllowUsingExpiredKeys(boolean allowUsingExpiredKeys) {
        throw new UnsupportedOperationException();
    }

    /** API 31: proves control of the credential key over a challenge. */
    public byte[] proveOwnership(byte[] challenge) {
        throw new UnsupportedOperationException();
    }

    /** API 31: deletes the credential, returning a proof-of-deletion blob. */
    public byte[] delete(byte[] challenge) {
        throw new UnsupportedOperationException();
    }

    /** API 31: replaces the credential's access-control profiles and data. */
    public byte[] update(PersonalizationData personalizationData) {
        throw new UnsupportedOperationException();
    }

    /** API 31: the three-argument form, adding a minimum remaining validity. */
    public void setAvailableAuthenticationKeys(int keyCount, int maxUsesPerKey,
            long minValidTimeMillis) {
        throw new UnsupportedOperationException();
    }

    /** API 31: the Instant-carrying form, so credstore knows when the static
     *  authentication data expires. */
    public void storeStaticAuthenticationData(
            java.security.cert.X509Certificate authenticationKey,
            java.time.Instant expirationDate,
            byte[] staticAuthData)
            throws UnknownAuthenticationKeyException {
        throw new UnsupportedOperationException();
    }

    /** API 31: per-key usage count and expiry. */
    public java.util.List<AuthenticationKeyMetadata> getAuthenticationKeyMetadata() {
        throw new UnsupportedOperationException();
    }
}
