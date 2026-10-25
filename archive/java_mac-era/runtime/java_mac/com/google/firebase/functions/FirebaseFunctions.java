package com.google.firebase.functions;

import com.google.firebase.FirebaseApp;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

// com.google.firebase.functions.FirebaseFunctions -- entry point to the Cloud
// Functions client SDK. On Mac.apk it is a real, fully-constructable client; the
// callable references it hands out fail cleanly when invoked (no backend, no
// fabricated results). getInstance is cached per app+region so identity-comparing
// apps see a stable instance, mirroring the published SDK.
//
// Signatures mirror the published Firebase Functions Android reference
// (developer.android.com/reference/com/google/firebase/functions/FirebaseFunctions).
public class FirebaseFunctions {

    private static final String DEFAULT_REGION = "us-central1";

    private static final Map<String, FirebaseFunctions> INSTANCES =
            new HashMap<String, FirebaseFunctions>();

    private final FirebaseApp app;
    private final String region;

    private FirebaseFunctions(FirebaseApp app, String region) {
        this.app = app;
        this.region = region;
    }

    public static synchronized FirebaseFunctions getInstance() {
        return getInstance(FirebaseApp.getInstance(), DEFAULT_REGION);
    }

    public static synchronized FirebaseFunctions getInstance(FirebaseApp app) {
        return getInstance(app, DEFAULT_REGION);
    }

    public static synchronized FirebaseFunctions getInstance(String regionOrCustomDomain) {
        return getInstance(FirebaseApp.getInstance(),
                regionOrCustomDomain == null ? DEFAULT_REGION : regionOrCustomDomain);
    }

    public static synchronized FirebaseFunctions getInstance(FirebaseApp app,
                                                             String regionOrCustomDomain) {
        String r = regionOrCustomDomain == null ? DEFAULT_REGION : regionOrCustomDomain;
        String key = (app == null ? "[null]" : app.getName()) + "|" + r;
        FirebaseFunctions f = INSTANCES.get(key);
        if (f == null) {
            f = new FirebaseFunctions(app, r);
            INSTANCES.put(key, f);
        }
        return f;
    }

    public FirebaseApp getApp() { return app; }

    // ---- callable references ---------------------------------------------

    public HttpsCallableReference getHttpsCallable(String name) {
        return new HttpsCallableReference(this, name);
    }

    public HttpsCallableReference getHttpsCallable(String name, HttpsCallableOptions options) {
        return new HttpsCallableReference(this, name);
    }

    public HttpsCallableReference getHttpsCallableFromUrl(URL url) {
        return new HttpsCallableReference(this, url);
    }

    public HttpsCallableReference getHttpsCallableFromUrl(URL url, HttpsCallableOptions options) {
        return new HttpsCallableReference(this, url);
    }

    public HttpsCallableReference getHttpsCallableFromUrl(String url) {
        return new HttpsCallableReference(this, toUrl(url));
    }

    public HttpsCallableReference getHttpsCallableFromUrl(String url, HttpsCallableOptions options) {
        return new HttpsCallableReference(this, toUrl(url));
    }

    private static URL toUrl(String url) {
        if (url == null) return null;
        try { return new URL(url); }
        catch (MalformedURLException e) { return null; }
    }

    public void useEmulator(String host, int port) {
        // No backend, no emulator to point at; recorded as a no-op.
    }

    /** @deprecated use {@link #useEmulator(String, int)} instead. */
    @Deprecated
    public void useFunctionsEmulator(String origin) {
        // No-op for the same reason.
    }
}
