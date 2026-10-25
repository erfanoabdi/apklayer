package com.google.firebase.auth;
// Opaque base class. Apps subclass it for provider-specific
// credentials (Google, Facebook, GitHub, etc.) -- we don't need to
// model any specific provider; signInWithCredential takes any
// AuthCredential and treats it as anonymous-sign-in for us.
public abstract class AuthCredential {
    public abstract String getProvider();
    public abstract String getSignInMethod();
}
