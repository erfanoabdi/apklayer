// android.security.identity.AuthenticationKeyMetadata -- usage count and
// expiry of one dynamic authentication key of an IdentityCredential.
//
// AOSP source: frameworks/base/identity/java/android/security/identity/
// AuthenticationKeyMetadata.java (android16-release). The constructor is
// package-private there: only the credstore implementation mints these, and
// the app just reads the two getters. Unreachable here for the same reason as
// the rest of the package -- IdentityCredentialStore.getInstance() correctly
// returns null on hardware with no secure element -- but the type must resolve
// for IdentityCredential.getAuthenticationKeyMetadata()'s signature to link.
package android.security.identity;

import java.time.Instant;

public final class AuthenticationKeyMetadata {
    private final int mUsageCount;
    private final Instant mExpirationDate;

    AuthenticationKeyMetadata(int usageCount, Instant expirationDate) {
        mUsageCount = usageCount;
        mExpirationDate = expirationDate;
    }

    public int getUsageCount() { return mUsageCount; }

    public Instant getExpirationDate() { return mExpirationDate; }
}
