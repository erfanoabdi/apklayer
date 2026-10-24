package android.credentials;
/** The result of prepareGetCredential -- a handle plus flags describing what a
 *  later getCredential(handle) would surface. API 34.
 *  Source: frameworks/base/core/java/android/credentials/PrepareGetCredentialResponse.java
 *
 *  The handle carries the prepared request: AOSP's handle holds the PendingIntent
 *  the system built for it and getCredential(handle) shows that UI
 *  (PendingGetCredentialHandle.show). Ours has one provider (Google sign-in,
 *  com.macapk.gms.GoogleSignInBroker), so it keeps the request and
 *  getCredential(handle) hands it to the same path as getCredential(request).
 *  has*() report only what that provider would surface (false for every other
 *  type, and for everything with MACAPK_GMS_SIGNIN=0). */
public final class PrepareGetCredentialResponse {
    /** Opaque token passed back into CredentialManager.getCredential(handle). */
    public static final class PendingGetCredentialHandle {
        final GetCredentialRequest mRequest;
        PendingGetCredentialHandle() { this(null); }
        PendingGetCredentialHandle(GetCredentialRequest request) { mRequest = request; }
    }
    private final PendingGetCredentialHandle mHandle;
    private final boolean mGoogleResults;
    PrepareGetCredentialResponse() { this(null, false); }
    PrepareGetCredentialResponse(GetCredentialRequest request, boolean googleResults) {
        mHandle = new PendingGetCredentialHandle(request);
        mGoogleResults = googleResults;
    }
    public PendingGetCredentialHandle getPendingGetCredentialHandle() { return mHandle; }
    public boolean hasCredentialResults(String credentialType) {
        return mGoogleResults && com.macapk.gms.GoogleSignInBroker.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credentialType);
    }
    public boolean hasAuthenticationResults() { return false; }
    public boolean hasRemoteResults() { return false; }
}
