package com.google.firebase.auth;

// GithubAuthProvider: factory for a GitHub OAuth AuthCredential from a
// GitHub access token. Faithful surface from the Firebase ref.
public final class GithubAuthProvider {
    public static final String PROVIDER_ID = "github.com";
    public static final String GITHUB_SIGN_IN_METHOD = "github.com";

    private GithubAuthProvider() {}

    public static AuthCredential getCredential(String token) {
        return new OAuthAuthCredential(PROVIDER_ID, GITHUB_SIGN_IN_METHOD,
            null, token, null);
    }
}
