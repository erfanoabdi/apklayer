package com.google.firebase.auth;

// Concrete OAuthCredential carrying whatever the provider factory was
// handed (id token / access token / secret). Opaque to us: never
// validated or sent anywhere. Used by GoogleAuthProvider,
// FacebookAuthProvider, GithubAuthProvider, TwitterAuthProvider, and
// OAuthProvider.getCredential.
public final class OAuthAuthCredential extends OAuthCredential {
    private final String provider;
    private final String signInMethod;
    private final String idToken;
    private final String accessToken;
    private final String secret;

    OAuthAuthCredential(String provider, String signInMethod,
                        String idToken, String accessToken, String secret) {
        this.provider = provider;
        this.signInMethod = signInMethod;
        this.idToken = idToken;
        this.accessToken = accessToken;
        this.secret = secret;
    }

    @Override public String getProvider() { return provider; }
    @Override public String getSignInMethod() { return signInMethod; }
    @Override public String getAccessToken() { return accessToken; }
    @Override public String getIdToken() { return idToken; }
    @Override public String getSecret() { return secret; }
}
