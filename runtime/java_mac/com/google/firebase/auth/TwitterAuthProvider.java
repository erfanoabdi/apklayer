package com.google.firebase.auth;

// TwitterAuthProvider: factory for a Twitter OAuth AuthCredential from
// a Twitter OAuth token + secret. Faithful surface from the Firebase
// ref. Twitter uses the token as access token and the secret carried
// in the OAuth credential's secret slot.
public final class TwitterAuthProvider {
    public static final String PROVIDER_ID = "twitter.com";
    public static final String TWITTER_SIGN_IN_METHOD = "twitter.com";

    private TwitterAuthProvider() {}

    public static AuthCredential getCredential(String token, String secret) {
        return new OAuthAuthCredential(PROVIDER_ID, TWITTER_SIGN_IN_METHOD,
            null, token, secret);
    }
}
