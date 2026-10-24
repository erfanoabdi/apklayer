package com.google.firebase.auth;

// AuthResult: the return type for every signIn/createUser Task. Real
// SDK exposes getUser + getAdditionalUserInfo + getCredential; we
// model only getUser since the other two are mostly used for new-
// user detection, which we always answer as "existing user."
public final class AuthResult {
    private final FirebaseUser user;
    AuthResult(FirebaseUser user) { this.user = user; }
    public FirebaseUser getUser() { return user; }
    public AdditionalUserInfo getAdditionalUserInfo() {
        return new AdditionalUserInfo() {
            @Override public boolean isNewUser() { return false; }
            @Override public String  getProviderId() { return "firebase"; }
            @Override public String  getUsername() { return null; }
        };
    }
    public AuthCredential getCredential() { return null; }
}
