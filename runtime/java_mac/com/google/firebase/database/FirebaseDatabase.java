package com.google.firebase.database;

import com.google.firebase.FirebaseApp;

import java.util.HashMap;
import java.util.Map;

// com.google.firebase.database.FirebaseDatabase -- the entry point to the
// Realtime Database SDK. On our offline-only (de-Googled) shim it is a real,
// fully-constructable client object whose references read empty and whose
// writes succeed locally (see DatabaseReference) -- never round-tripping to a
// backend and never fabricating remote data. getInstance is cached per app+url
// so identity-comparing apps see a stable instance, mirroring the published
// SDK.
//
// Signatures mirror the published Firebase RTDB Android reference.
public class FirebaseDatabase {

    private static final Map<String, FirebaseDatabase> INSTANCES =
            new HashMap<String, FirebaseDatabase>();

    private final FirebaseApp app;
    private final String url;
    private boolean persistenceEnabled = false;
    private long persistenceCacheSizeBytes = 10L * 1024L * 1024L;

    private FirebaseDatabase(FirebaseApp app, String url) {
        this.app = app;
        this.url = url == null ? "" : url;
    }

    public static synchronized FirebaseDatabase getInstance() {
        return getInstance(FirebaseApp.getInstance(), null);
    }

    public static synchronized FirebaseDatabase getInstance(String url) {
        return getInstance(FirebaseApp.getInstance(), url);
    }

    public static synchronized FirebaseDatabase getInstance(FirebaseApp app) {
        return getInstance(app, null);
    }

    public static synchronized FirebaseDatabase getInstance(FirebaseApp app, String url) {
        String key = (app == null ? "[null]" : app.getName()) + "|" + (url == null ? "" : url);
        FirebaseDatabase db = INSTANCES.get(key);
        if (db == null) {
            db = new FirebaseDatabase(app, url);
            INSTANCES.put(key, db);
        }
        return db;
    }

    public FirebaseApp getApp() { return app; }

    // ---- references -------------------------------------------------------

    public DatabaseReference getReference() {
        return new DatabaseReference(this, "");
    }

    public DatabaseReference getReference(String path) {
        if (path == null) {
            throw new NullPointerException("Can't pass null for argument 'pathString'");
        }
        return new DatabaseReference(this, path);
    }

    public DatabaseReference getReferenceFromUrl(String url) {
        if (url == null) {
            throw new NullPointerException("Can't pass null for argument 'url'");
        }
        // Strip scheme/host so the path component roots the reference. For an
        // offline shim the host is irrelevant (no connection); the path is all
        // that matters for child navigation and key derivation.
        String path = url;
        int schemeIdx = path.indexOf("://");
        if (schemeIdx >= 0) {
            path = path.substring(schemeIdx + 3);
            int slash = path.indexOf('/');
            path = slash < 0 ? "" : path.substring(slash + 1);
        }
        return new DatabaseReference(this, path);
    }

    // ---- connection / persistence (local no-ops) -------------------------

    public void goOnline() {}

    public void goOffline() {}

    public void purgeOutstandingWrites() {}

    // Persistence settings must be set before any reference is used in the real
    // SDK (it throws otherwise). On the offline shim there is no live database
    // to lock, so we record the flag and accept it without enforcing ordering.
    public synchronized void setPersistenceEnabled(boolean enabled) {
        this.persistenceEnabled = enabled;
    }

    public synchronized void setPersistenceCacheSizeBytes(long cacheSizeInBytes) {
        this.persistenceCacheSizeBytes = cacheSizeInBytes;
    }

    public void setLogLevel(com.google.firebase.database.Logger.Level logLevel) {}

    public static String getSdkVersion() { return "21.0.0"; }
}
