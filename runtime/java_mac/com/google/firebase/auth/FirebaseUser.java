package com.google.firebase.auth;

import android.net.Uri;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.Collections;
import java.util.List;

// FirebaseUser stub. Always anonymous, always signed in, stable UID
// for the lifetime of the process. Real Firebase persists UID across
// app launches via the AndroidX SecureSharedPreferences-backed
// FirebaseInstanceId; we don't persist.
public final class FirebaseUser {
    private final String uid;
    private final String token;

    FirebaseUser(String uid) {
        this.uid   = uid;
        this.token = "macapk-anon-" + uid;
    }

    public String getUid()              { return uid; }
    public String getEmail()            { return null; }
    public String getDisplayName()      { return null; }
    public String getPhoneNumber()      { return null; }
    public Uri    getPhotoUrl()         { return null; }
    public boolean isAnonymous()        { return true; }
    public boolean isEmailVerified()    { return false; }
    public String getProviderId()       { return "firebase"; }
    public String getTenantId()         { return null; }

    public List<? extends UserInfo> getProviderData() { return Collections.emptyList(); }

    public Task<GetTokenResult> getIdToken(boolean forceRefresh) {
        return Tasks.forResult(new GetTokenResult(token));
    }
    public Task<Void> delete()      { return Tasks.forResult(null); }
    public Task<Void> reload()      { return Tasks.forResult(null); }
    public Task<Void> updateEmail(String email)       { return Tasks.forResult(null); }
    public Task<Void> updatePassword(String password) { return Tasks.forResult(null); }
    public Task<Void> sendEmailVerification()         { return Tasks.forResult(null); }

    // --- Additional faithful surface (Firebase auth ref) ----------------

    public Task<Void> updateProfile(UserProfileChangeRequest request) {
        return Tasks.forResult(null);
    }
    public Task<Void> updatePhoneNumber(PhoneAuthCredential credential) {
        return Tasks.forResult(null);
    }
    public Task<Void> verifyBeforeUpdateEmail(String newEmail) {
        return Tasks.forResult(null);
    }

    // link/reauthenticate: keep the same stable anonymous user. The
    // real SDK returns AuthResult/Void Tasks; we complete cleanly with
    // the existing user since signInWithCredential is anonymous-success.
    public Task<AuthResult> linkWithCredential(AuthCredential credential) {
        return Tasks.forResult(new AuthResult(this));
    }
    public Task<AuthResult> unlink(String providerId) {
        return Tasks.forResult(new AuthResult(this));
    }
    public Task<Void> reauthenticate(AuthCredential credential) {
        return Tasks.forResult(null);
    }
    public Task<AuthResult> reauthenticateAndRetrieveData(AuthCredential credential) {
        return Tasks.forResult(new AuthResult(this));
    }

    public java.util.List<String> getProviderIds() {
        return java.util.Collections.emptyList();
    }
    public FirebaseUserMetadata getMetadata() { return new FirebaseUserMetadata(); }
}
