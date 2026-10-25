package com.google.android.gms.auth.api.signin;

import com.google.android.gms.common.api.Scope;

import java.util.List;

// com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension -- the
// interface implemented by API-specific sign-in option extensions (e.g. the
// Games sign-in options). GoogleSignInOptions.Builder accepts one via
// addExtension(GoogleSignInOptionsExtension). We expose the public interface so
// extension types compile; no extension is actually shipped here.
//
// Ref: developers.google.com/android/reference/com/google/android/gms/auth/api/
// signin/GoogleSignInOptionsExtension
public interface GoogleSignInOptionsExtension {

    // Extension-type discriminators used by the real SDK.
    int UNKNOWN = 0;
    int GAMES = 1;
    int FITNESS = 2;

    int getExtensionType();

    List<Scope> getImpliedScopes();
}
