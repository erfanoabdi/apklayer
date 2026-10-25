package com.google.firebase.auth;

// Concrete AuthCredential produced by EmailAuthProvider. Carries the
// caller's data so apps that re-read it behave, but we never validate
// or transmit it (no Firebase backend). getProvider()/getSignInMethod()
// mirror the Firebase ref values.
public final class EmailAuthCredential extends AuthCredential {
    private final String email;
    private final String password;
    private final String emailLink;
    private final boolean isLink;

    EmailAuthCredential(String email, String password, String emailLink, boolean isLink) {
        this.email = email;
        this.password = password;
        this.emailLink = emailLink;
        this.isLink = isLink;
    }

    @Override public String getProvider() { return EmailAuthProvider.PROVIDER_ID; }

    @Override public String getSignInMethod() {
        return isLink
            ? EmailAuthProvider.EMAIL_LINK_SIGN_IN_METHOD
            : EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD;
    }

    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getEmailLink() { return emailLink; }
}
