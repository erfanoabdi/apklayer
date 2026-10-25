package com.google.firebase.installations;

// InstallationTokenResult: returned by FirebaseInstallations.getToken.
// Faithful surface from the Firebase ref (an abstract value type with
// getToken / getTokenExpirationTimestamp / getTokenCreationTimestamp).
// We carry a stub auth token; it is never honored by any Google
// backend (correct: we are not provisioned).
public abstract class InstallationTokenResult {
    public abstract String getToken();
    public abstract long getTokenExpirationTimestamp();
    public abstract long getTokenCreationTimestamp();

    // Factory used by our FirebaseInstallations impl. The real SDK uses
    // an AutoValue builder; apps only ever read the three getters.
    static InstallationTokenResult create(final String token,
                                          final long expirationTimestamp,
                                          final long creationTimestamp) {
        return new InstallationTokenResult() {
            @Override public String getToken() { return token; }
            @Override public long getTokenExpirationTimestamp() { return expirationTimestamp; }
            @Override public long getTokenCreationTimestamp() { return creationTimestamp; }
        };
    }
}
