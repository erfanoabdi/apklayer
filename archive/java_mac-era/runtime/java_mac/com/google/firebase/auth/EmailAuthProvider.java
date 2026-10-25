package com.google.firebase.auth;

// EmailAuthProvider: factory for email/password (and email-link)
// AuthCredentials. Faithful surface from the Firebase Android ref
// (firebase.google.com/docs/reference/android/com/google/firebase/auth/EmailAuthProvider).
// The credential it produces is opaque to us: signInWithCredential
// treats every AuthCredential the same way (see FirebaseAuth). We only
// need the factory methods + provider-id constants to exist so apps
// that build a credential and pass it through link/reauthenticate/
// signIn do not NoSuchMethodError.
public class EmailAuthProvider {
    public static final String PROVIDER_ID = "password";
    public static final String EMAIL_PASSWORD_SIGN_IN_METHOD = "password";
    public static final String EMAIL_LINK_SIGN_IN_METHOD = "emailLink";

    private EmailAuthProvider() {}

    public static AuthCredential getCredential(String email, String password) {
        return new EmailAuthCredential(email, password, null, false);
    }

    public static AuthCredential getCredentialWithLink(String email, String emailLink) {
        return new EmailAuthCredential(email, null, emailLink, true);
    }
}
