package com.google.firebase.auth;

// FirebaseUserMetadata: creation / last-sign-in timestamps for the
// current user. Faithful surface from the Firebase ref. Our anonymous
// user is created at process start; we report that for both.
public final class FirebaseUserMetadata {
    private final long createdAt;

    FirebaseUserMetadata() { this.createdAt = System.currentTimeMillis(); }

    public long getCreationTimestamp() { return createdAt; }
    public long getLastSignInTimestamp() { return createdAt; }
}
