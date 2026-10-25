package com.google.firebase.auth;

// FacebookAuthProvider: factory for a Facebook OAuth AuthCredential
// from a Facebook access token. Faithful surface from the Firebase ref.
public final class FacebookAuthProvider {
    public static final String PROVIDER_ID = "facebook.com";
    public static final String FACEBOOK_SIGN_IN_METHOD = "facebook.com";

    private FacebookAuthProvider() {}

    public static AuthCredential getCredential(String accessToken) {
        return new OAuthAuthCredential(PROVIDER_ID, FACEBOOK_SIGN_IN_METHOD,
            null, accessToken, null);
    }
}
