// android.security.identity.CredentialDataResult -- what a PresentationSession
// returns for a CredentialDataRequest, split into device-signed and
// issuer-signed entry sets.
//
// Abstract on AOSP and unreachable here: only a live PresentationSession
// produces one, and a session is only obtainable from a credential store that
// our hardware does not have (see IdentityCredentialStore). The STATUS_*
// constants on the nested Entries interface are the values apps compare
// against, so they carry AOSP's exact numbering.
//
// Mirrors identity/java/android/security/identity/CredentialDataResult.java
// (android13-release, API 33).
package android.security.identity;

import java.util.Collection;

public abstract class CredentialDataResult {
    protected CredentialDataResult() {}

    public abstract byte[] getDeviceNameSpaces();

    public abstract byte[] getDeviceMac();

    public abstract byte[] getStaticAuthenticationData();

    public abstract Entries getDeviceSignedEntries();

    public abstract Entries getIssuerSignedEntries();

    /** API 33: the ECDSA signature over the device namespaces, present when the
     *  session authenticated with a signature rather than a MAC. AOSP declares
     *  it NON-abstract with `throw new UnsupportedOperationException()`
     *  (CredentialDataResult.java:128-130) so that pre-33 implementations keep
     *  compiling; mirrored exactly. */
    public byte[] getDeviceSignature() {
        throw new UnsupportedOperationException();
    }

    public interface Entries {
        /** Value was successfully retrieved. */
        int STATUS_OK = 0;

        /** The entry does not exist. */
        int STATUS_NO_SUCH_ENTRY = 1;

        /** The entry was not requested. */
        int STATUS_NOT_REQUESTED = 2;

        /** The entry wasn't in the request message. */
        int STATUS_NOT_IN_REQUEST_MESSAGE = 3;

        /** The entry was not retrieved because user authentication failed. */
        int STATUS_USER_AUTHENTICATION_FAILED = 4;

        /** The entry was not retrieved because reader authentication failed. */
        int STATUS_READER_AUTHENTICATION_FAILED = 5;

        /** The entry was not retrieved because it had no access control profiles. */
        int STATUS_NO_ACCESS_CONTROL_PROFILES = 6;

        Collection<String> getNamespaces();

        Collection<String> getEntryNames(String namespaceName);

        Collection<String> getRetrievedEntryNames(String namespaceName);

        int getStatus(String namespaceName, String name);

        byte[] getEntry(String namespaceName, String name);
    }
}
