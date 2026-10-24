package com.google.firebase.auth;

import android.app.Activity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// FirebaseAuth no-op shadow. We pretend the user is ALWAYS signed in
// anonymously with a stable per-process UID. Apps that explicitly
// sign in (email/password, OAuth, custom token) all "succeed" by
// returning that same anonymous user wrapped in an AuthResult. Apps
// that explicitly sign OUT have no observable effect.
//
// This is the strategy doc's "anonymous always" model: most apps
// gate user content behind a "signed-in" boolean and behave fine with
// an anonymous user; the few that compare emails or check provider
// IDs against a server-side database simply won't recognize our user
// as authenticated.
public final class FirebaseAuth {
    private static volatile FirebaseAuth sInstance;

    private final List<AuthStateListener> stateListeners = new ArrayList<>();
    private final List<IdTokenListener> idTokenListeners = new ArrayList<>();
    private final FirebaseUser currentUser = new FirebaseUser(
        UUID.randomUUID().toString());
    private volatile String languageCode;
    private volatile String tenantId;

    private FirebaseAuth() {}

    public static FirebaseAuth getInstance() {
        if (sInstance == null) {
            synchronized (FirebaseAuth.class) {
                if (sInstance == null) sInstance = new FirebaseAuth();
            }
        }
        return sInstance;
    }
    public static FirebaseAuth getInstance(FirebaseApp app) { return getInstance(); }

    public FirebaseUser getCurrentUser() { return currentUser; }
    public String getUid()               { return currentUser.getUid(); }

    public Task<AuthResult> signInAnonymously() {
        return Tasks.forResult(new AuthResult(currentUser));
    }
    public Task<AuthResult> signInWithEmailAndPassword(String email, String password) {
        return Tasks.forResult(new AuthResult(currentUser));
    }
    public Task<AuthResult> signInWithCredential(AuthCredential credential) {
        return Tasks.forResult(new AuthResult(currentUser));
    }
    public Task<AuthResult> signInWithCustomToken(String token) {
        return Tasks.forResult(new AuthResult(currentUser));
    }
    public Task<AuthResult> createUserWithEmailAndPassword(String email, String password) {
        return Tasks.forResult(new AuthResult(currentUser));
    }
    public Task<Void> sendPasswordResetEmail(String email) {
        return Tasks.forResult(null);
    }

    public void signOut() {
        // No-op: we always have a signed-in anonymous user.
    }
    public void addAuthStateListener(AuthStateListener listener) {
        if (listener == null) return;
        synchronized (stateListeners) { stateListeners.add(listener); }
        listener.onAuthStateChanged(this);
    }
    public void removeAuthStateListener(AuthStateListener listener) {
        synchronized (stateListeners) { stateListeners.remove(listener); }
    }

    // --- Additional faithful surface (Firebase auth ref) ----------------

    public void addIdTokenListener(IdTokenListener listener) {
        if (listener == null) return;
        synchronized (idTokenListeners) { idTokenListeners.add(listener); }
        listener.onIdTokenChanged(this);
    }
    public void removeIdTokenListener(IdTokenListener listener) {
        synchronized (idTokenListeners) { idTokenListeners.remove(listener); }
    }

    public Task<AuthResult> signInWithEmailLink(String email, String emailLink) {
        return Tasks.forResult(new AuthResult(currentUser));
    }

    // updateCurrentUser: the real SDK swaps the active user. We keep the
    // stable anonymous user, so this is observably a no-op completion
    // (consistent with our anonymous-always model).
    public Task<Void> updateCurrentUser(FirebaseUser user) {
        return Tasks.forResult(null);
    }

    public Task<Void> applyActionCode(String code) { return Tasks.forResult(null); }
    public Task<Void> confirmPasswordReset(String code, String newPassword) {
        return Tasks.forResult(null);
    }
    public Task<String> verifyPasswordResetCode(String code) {
        return Tasks.forResult((String) null);
    }

    // fetchSignInMethodsForEmail: with no backend we cannot enumerate
    // real providers; return an empty SignInMethodQueryResult (the
    // honest "no known methods" answer for an unreachable backend).
    public Task<SignInMethodQueryResult> fetchSignInMethodsForEmail(String email) {
        return Tasks.forResult(new SignInMethodQueryResult(
            new ArrayList<String>()));
    }

    // Provider-driven sign-in (OAuthProvider). Same anonymous-success
    // path as signInWithCredential; no real OAuth round-trip.
    public Task<AuthResult> startActivityForSignInWithProvider(Activity activity,
            FederatedAuthProvider provider) {
        return Tasks.forResult(new AuthResult(currentUser));
    }
    public Task<AuthResult> getPendingAuthResult() {
        return Tasks.forResult((AuthResult) null);
    }

    public FirebaseApp getApp() { return FirebaseApp.getInstance(); }

    public String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
    public void useAppLanguage() { this.languageCode = null; }
    public void useEmulator(String host, int port) {
        // No Firebase backend or emulator to point at; accept and ignore.
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public void setFirebaseUIVersion(String version) {}

    public interface AuthStateListener {
        void onAuthStateChanged(FirebaseAuth auth);
    }

    public interface IdTokenListener {
        void onIdTokenChanged(FirebaseAuth auth);
    }
}
