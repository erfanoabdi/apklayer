package com.google.firebase.auth;

// OAuthCredential: the abstract credential type the Firebase ref
// exposes for generic OAuth providers (Microsoft, Apple, Yahoo, and
// the well-known google/facebook/github/twitter providers via their
// dedicated factories). Apps read getAccessToken/getIdToken/getSecret
// off it. Our concrete OAuthAuthCredential carries those values.
public abstract class OAuthCredential extends AuthCredential {
    public abstract String getAccessToken();
    public abstract String getIdToken();
    public abstract String getSecret();
}
