package com.google.firebase.auth;

// FederatedAuthProvider: the abstract base the Firebase ref defines for
// providers consumed by FirebaseAuth.startActivityForSignInWithProvider
// / startActivityForReauthenticateWithProvider (currently OAuthProvider).
// It carries no public members of its own in the ref; it exists as the
// shared parameter type for those sign-in entry points.
public abstract class FederatedAuthProvider {
}
