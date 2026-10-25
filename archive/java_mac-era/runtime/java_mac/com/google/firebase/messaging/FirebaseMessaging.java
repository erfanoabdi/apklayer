package com.google.firebase.messaging;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseOptions;
import com.macapk.gms.GcmRegisterClient;

import java.util.UUID;

// FirebaseMessaging no-op shadow. FCM (Firebase Cloud Messaging) is
// push-notification delivery; without a real GMS-connected device,
// there is genuinely no FCM channel to register against. A de-Googled /
// Amazon Fire device cannot obtain an FCM token, so getToken() completes
// with a failed Task (SERVICE_NOT_AVAILABLE) -- the honest hard-limit.
// Apps are written to catch a failed getToken() and MUST NOT block their
// UI on it (a spoofed placeholder token was a lie that could send an app
// down a "push is working" path that then silently never delivers). Topic
// subscribe/unsubscribe are accepted as no-ops (harmless, non-blocking).
// MACAPK_GMS_HONEST_MISSING=0 restores the legacy placeholder-token spoof.
public final class FirebaseMessaging {
    private static final FirebaseMessaging INSTANCE = new FirebaseMessaging();
    private static final String STUB_TOKEN = "macapk-stub-token-" + UUID.randomUUID();
    // ONE source of truth (com.macapk.loader.PlayServices). Keyed on the
    // PACKAGE question, not the availability verdict: a real FCM channel needs
    // a GMS package to register against, and there is none. Availability
    // answering SUCCESS does not make a token obtainable, so these must not be
    // coupled -- a placeholder token would be the "push is working" lie this
    // shadow exists to avoid.
    private static final boolean HONEST_MISSING =
            !com.macapk.loader.PlayServices.gmsPackageInstalled();

    private boolean autoInit = false;

    private FirebaseMessaging() {}
    public static FirebaseMessaging getInstance() { return INSTANCE; }

    // Published overload (firebase-android-sdk FirebaseMessaging.getInstance
    // is the no-arg form; the app-scoped instance is fetched via
    // FirebaseApp.get(FirebaseMessaging.class)). FCM is a singleton per process
    // here -- no real per-app channel exists on a de-Googled device -- so this
    // app-scoped accessor returns the same instance. Kept so callers using the
    // FirebaseApp-scoped pattern resolve.
    public static FirebaseMessaging getInstance(com.google.firebase.FirebaseApp app) { return INSTANCE; }

    public Task<String> getToken()        {
        // MACAPK_GMS_FCM (PlayServices.pushBackendEnabled()) is THE gate for the
        // real path, and it is default OFF, so this branch is unreached and the
        // fallback below stays byte-identical to the pre-wiring shadow.
        //
        // Only an app that does NOT bundle its own Firebase SDK ever reaches
        // this class: AppClassLoader is child-first, so a bundling app's own
        // com.google.firebase.messaging.FirebaseMessaging shadows ours and
        // registers through the bound PushRegisterService Messenger instead.
        if (com.macapk.loader.PlayServices.pushBackendEnabled()) {
            return requestRealToken();
        }
        return HONEST_MISSING
                ? Tasks.<String>forException(new java.io.IOException("SERVICE_NOT_AVAILABLE"))
                : Tasks.forResult(STUB_TOKEN);
    }

    /** senderId -> the token Google already gave us. Real Firebase caches the
     *  token (FirebaseInstanceId/Store) and returns the cached value; without
     *  this, an app that calls getToken() per foreground or in a retry loop
     *  performs a fresh register3 EVERY time and walks itself into Google's
     *  TOO_MANY_REGISTRATIONS on the machine's one shared device identity. */
    private static final java.util.Map<String, String> sTokenCache =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

    /** Wall-clock before which a retry is pointless, from Google's Retry-After.
     *  Parsed by GcmRegisterClient and previously discarded here, so a
     *  rate-limited caller retried immediately and escalated. */
    private static volatile long sRetryNotBeforeMs = 0L;

    /** Guards check-then-register so concurrent callers share one round trip. */
    private static final Object REGISTER_LOCK = new Object();

    // Mint a REAL FCM token through GcmRegisterClient, the same c2dm/register3
    // call PushRegisterService runs for a bundling app. This does not weaken the
    // no-placeholder rule in this file's header: the point of that rule is never
    // to hand back a token that cannot receive, and this one can.
    //
    // The sender id comes from THIS app's own resources (gcm_defaultSenderId,
    // generated at build time from its google-services.json) via
    // FirebaseOptions.fromResource, which already reads it through
    // Resources.getIdentifier + getString. We never fabricate one: an app whose
    // project has no Cloud Messaging sender fails exactly as real Firebase does.
    //
    // Runs on a dedicated daemon thread because register3 is a blocking HTTP
    // round-trip that must never touch the caller's (usually main) thread. One
    // thread per call, same rationale as com.macapk.vending.LicensingService.run:
    // a token is fetched a handful of times per app lifetime, so a pool would be
    // machinery without a purpose, and daemon means a pending fetch can never
    // hold the process open.
    private Task<String> requestRealToken() {
        final TaskCompletionSource<String> tcs = new TaskCompletionSource<String>();
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    android.app.Application app = android.app.Application.getInstance();
                    String pkg = (app != null) ? app.getPackageName() : null;
                    FirebaseOptions opts = (app != null) ? FirebaseOptions.fromResource(app) : null;
                    String senderId = (opts != null) ? opts.getGcmSenderId() : null;
                    if (pkg == null || pkg.isEmpty() || senderId == null || senderId.isEmpty()) {
                        tcs.trySetException(new java.io.IOException(
                                // BARE codes only: Firebase tests retriability
                                // by exact equality (GmsRpc), so a parenthesised
                                // reason makes a retriable error terminal.
                                pkg == null || pkg.isEmpty()
                                    ? "SERVICE_NOT_AVAILABLE" : "INVALID_SENDER"));
                        return;
                    }
                    // Serialize the whole check-then-register. The cache lookup
                    // alone does not de-duplicate: two getToken() calls racing at
                    // startup (the app plus an analytics SDK is the common shape)
                    // both miss the empty cache and both POST, which is the
                    // TOO_MANY_REGISTRATIONS case the cache exists to avoid --
                    // on the machine's ONE shared device identity. Registrations
                    // are a handful per app lifetime, so one lock is enough.
                    synchronized (REGISTER_LOCK) {
                    String cached = sTokenCache.get(senderId);
                    if (cached != null) { tcs.trySetResult(cached); return; }
                    long notBefore = sRetryNotBeforeMs;
                    if (notBefore > 0 && System.currentTimeMillis() < notBefore) {
                        // Google told us to back off. Retrying before then earns
                        // TOO_MANY_REGISTRATIONS on the shared device identity.
                        com.macapk.loader.NativeLog.i("FirebaseMessaging",
                                "SERVICE_NOT_AVAILABLE: retry after "
                                + ((notBefore - System.currentTimeMillis()) / 1000) + "s");
                        tcs.trySetException(new java.io.IOException("SERVICE_NOT_AVAILABLE"));
                        return;
                    }
                    GcmRegisterClient.Result r = GcmRegisterClient.register(
                            pkg, senderId, certSha1Of(app, pkg),
                            versionCodeOf(app, pkg), targetSdkOf(app, pkg));
                    if (r.ok()) {
                        sTokenCache.put(senderId, r.token);
                        sRetryNotBeforeMs = 0L;
                        tcs.trySetResult(r.token);
                    } else {
                        if (r.retryAfterSeconds > 0) {
                            sRetryNotBeforeMs = System.currentTimeMillis()
                                    + r.retryAfterSeconds * 1000L;
                        }
                        tcs.trySetException(new java.io.IOException(
                                r.error != null ? r.error : "SERVICE_NOT_AVAILABLE"));
                    }
                    }
                } catch (Throwable th) {
                    // Never throw to the caller: every failure becomes a failed
                    // Task, mirroring PushRegisterService.doRegister's contract.
                    tcs.trySetException(th instanceof Exception ? (Exception) th
                            : new java.io.IOException(String.valueOf(th)));
                }
            }
        }, "macapk-fcm-token");
        t.setDaemon(true);
        // A Firebase caller expects a Task, never an exception out of getToken.
        // If the thread cannot start (OOM), complete the Task as failed instead
        // of throwing into a caller that has no catch for it.
        try {
            t.start();
        } catch (Throwable st) {
            tcs.trySetException(st instanceof Exception ? (Exception) st
                    : new java.io.IOException(String.valueOf(st)));
        }
        return tcs.getTask();
    }

    // Mirror of PushRegisterService's three helpers, taking a Context because
    // this shadow has no Service of its own. Kept local rather than shared:
    // PushRegisterService's copies are on the proven bundling path and are not
    // worth disturbing to save 25 lines.
    private static String certSha1Of(android.content.Context ctx, String pkg) {
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(
                    pkg, android.content.pm.PackageManager.GET_SIGNATURES);
            android.content.pm.Signature[] sigs = (pi != null) ? pi.signatures : null;
            if (sigs != null && sigs.length > 0 && sigs[0] != null) {
                byte[] der = sigs[0].toByteArray();
                if (der != null && der.length > 0) {
                    byte[] sha1 = java.security.MessageDigest.getInstance("SHA-1").digest(der);
                    StringBuilder sb = new StringBuilder(40);
                    for (byte x : sha1) sb.append(String.format("%02x", x));
                    return sb.toString();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int versionCodeOf(android.content.Context ctx, String pkg) {
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, 0);
            if (pi != null && pi.versionCode > 0) return pi.versionCode;
        } catch (Throwable ignored) {}
        return 1;
    }

    private static int targetSdkOf(android.content.Context ctx, String pkg) {
        try {
            android.content.pm.ApplicationInfo ai =
                    ctx.getPackageManager().getApplicationInfo(pkg, 0);
            if (ai != null && ai.targetSdkVersion > 0) return ai.targetSdkVersion;
        } catch (Throwable ignored) {}
        return 0;
    }
    public Task<Void>   deleteToken()     {
        // Invalidate the cache. An app that rotates on logout (Signal, Telegram)
        // calls deleteToken() then getToken(); without this it gets the SAME
        // string back for the rest of the process lifetime, and if the delete
        // reached Google that token is dead, so push stops silently until
        // restart. (Routing the delete itself to
        // GcmRegisterClient.register(..., delete=true) is a separate follow-up.)
        sTokenCache.clear();
        return Tasks.forResult(null);
    }
    public Task<Void>   subscribeToTopic(String topic)    { return Tasks.forResult(null); }
    public Task<Void>   unsubscribeFromTopic(String topic){ return Tasks.forResult(null); }

    public void    setAutoInitEnabled(boolean enabled) { autoInit = enabled; }
    public boolean isAutoInitEnabled()                  { return autoInit; }

    // FCM delivery-metrics-to-BigQuery toggle. The Firebase C++ SDK
    // (messaging.cc FIREBASE_MESSAGING_METHODS table) caches the JNI ids
    // for "deliveryMetricsExportToBigQueryEnabled","()Z" and
    // "setDeliveryMetricsExportToBigQuery","(Z)V"; without these methods
    // its GetMethodID throws NoSuchMethodError on the Unity main thread.
    // Mirror the real public API (AOSP
    // com.google.firebase.messaging.FirebaseMessaging); there is no FCM
    // channel on macOS so the export flag is just stored.
    private boolean deliveryMetricsExport = false;
    public boolean deliveryMetricsExportToBigQueryEnabled() { return deliveryMetricsExport; }
    public void    setDeliveryMetricsExportToBigQuery(boolean enable) { deliveryMetricsExport = enable; }

    public void send(RemoteMessage message) {}
}
