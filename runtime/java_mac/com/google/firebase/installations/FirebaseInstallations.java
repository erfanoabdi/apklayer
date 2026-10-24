package com.google.firebase.installations;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// FirebaseInstallations (FIS): the per-app-instance identity service
// that many Firebase SDKs (Messaging, RemoteConfig, Analytics, A/B,
// In-App Messaging) depend on transitively. Faithful surface from the
// Firebase ref.
//
// Honest behavior with no Firebase backend: getId() returns a STABLE
// locally-generated FID (a real device's FID is also a client-minted
// opaque id, so this matches the contract); getToken() returns a stub
// auth token (never honored by Google's backend, which is correct). We
// never register the installation with Google. This lets the dependent
// SDKs initialize and run their local code paths instead of crashing
// on a missing FirebaseInstallations class.
public final class FirebaseInstallations {
    // One instance per FirebaseApp, keyed by app name (matches the real
    // multi-component registry behavior closely enough for callers).
    private static final ConcurrentHashMap<String, FirebaseInstallations> INSTANCES =
        new ConcurrentHashMap<>();

    private final String fid;

    private FirebaseInstallations() {
        // Real FIDs are 22-char URL-safe; a UUID-derived id is opaque
        // and stable for the process, which is all callers require.
        this.fid = "macapk-" + UUID.randomUUID().toString().replace("-", "");
    }

    public static FirebaseInstallations getInstance() {
        return getInstance(FirebaseApp.getInstance());
    }

    public static FirebaseInstallations getInstance(FirebaseApp app) {
        String key = (app != null) ? app.getName() : "[DEFAULT]";
        FirebaseInstallations existing = INSTANCES.get(key);
        if (existing != null) return existing;
        FirebaseInstallations created = new FirebaseInstallations();
        FirebaseInstallations prev = INSTANCES.putIfAbsent(key, created);
        return (prev != null) ? prev : created;
    }

    public Task<String> getId() {
        return Tasks.forResult(fid);
    }

    public Task<InstallationTokenResult> getToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        // 1-hour validity window, mirroring the default FIS token TTL.
        long expiry = now + 60L * 60L * 1000L;
        return Tasks.forResult(InstallationTokenResult.create(
            "macapk-fis-" + fid, expiry, now));
    }

    // delete: drop the local instance so a subsequent getId mints a new
    // FID, mirroring the real "delete installation" lifecycle locally.
    public Task<Void> delete() {
        INSTANCES.values().remove(this);
        return Tasks.forResult(null);
    }
}
