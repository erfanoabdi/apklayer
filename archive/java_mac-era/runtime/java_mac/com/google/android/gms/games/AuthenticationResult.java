package com.google.android.gms.games;

// com.google.android.gms.games.AuthenticationResult -- the result of
// GamesSignInClient.isAuthenticated()/signIn(). On a de-Googled device the player
// is never authenticated, so isAuthenticated() is always false. Mirrors the
// published single-accessor type. Ref: developers.google.com/android/reference
public class AuthenticationResult {

    private final boolean authenticated;

    public AuthenticationResult(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public boolean isAuthenticated() { return authenticated; }
}
