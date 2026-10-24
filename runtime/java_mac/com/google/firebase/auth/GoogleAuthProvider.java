package com.google.firebase.auth;

// GoogleAuthProvider: factory for a Google OAuth AuthCredential. Apps
// hand us the Google ID token + access token they obtained from the
// (absent here) GoogleSignIn flow; we wrap them opaquely. Faithful
// surface from the Firebase ref.
public final class GoogleAuthProvider {
    public static final String PROVIDER_ID = "google.com";
    public static final String GOOGLE_SIGN_IN_METHOD = "google.com";

    private GoogleAuthProvider() {}

    public static AuthCredential getCredential(String googleIdToken, String googleAccessToken) {
        return new OAuthAuthCredential(PROVIDER_ID, GOOGLE_SIGN_IN_METHOD,
            googleIdToken, googleAccessToken, null);
    }
}
