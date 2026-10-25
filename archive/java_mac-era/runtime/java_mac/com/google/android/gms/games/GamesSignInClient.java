package com.google.android.gms.games;

import com.google.android.gms.tasks.Task;

// com.google.android.gms.games.GamesSignInClient -- the v2 sign-in client. On a
// de-Googled device there is no Play Games account, so isAuthenticated resolves
// to an AuthenticationResult reporting NOT authenticated, and signIn fails
// cleanly (SIGN_IN_REQUIRED). NEVER fabricates a signed-in player.
// Ref: developers.google.com/android/reference/.../games/GamesSignInClient
public interface GamesSignInClient {

    Task<AuthenticationResult> isAuthenticated();

    Task<AuthenticationResult> signIn();

    Task<String> requestServerSideAccess(String serverClientId, boolean forceRefreshToken);
}
