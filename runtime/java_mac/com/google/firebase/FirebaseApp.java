package com.google.firebase;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// com.google.firebase.FirebaseApp -- no-op shadow for the real
// FirebaseApp the APK bundles. This class is resolved through
// Mac.apk's parent-first AppClassLoader; the bundled FirebaseApp in
// the APK's classes.jar is never loaded.
//
// Contract guarantees:
//   * initializeApp(Context) NEVER returns null on macOS. The real
//     SDK returns null when google-services.json is absent; we
//     succeed unconditionally so callers don't crash on null deref.
//   * getInstance() NEVER throws "FirebaseApp is not initialized".
//     Auto-init: any caller asking for the default instance gets one,
//     even if initializeApp was never called.
//   * All API calls are no-ops -- no network traffic, no persistent
//     state beyond the in-memory instance map (so getInstance returns
//     the same singleton on repeat calls, matching real semantics).
public final class FirebaseApp {
    public static final String DEFAULT_APP_NAME = "[DEFAULT]";

    private static final Map<String, FirebaseApp> sApps = new HashMap<>();
    private static final List<BackgroundStateChangeListener> sBgListeners =
        new ArrayList<>();
    private static Context sAppContext;

    private final String name;
    private final FirebaseOptions options;
    private final Context appContext;
    private final Map<Class<?>, Object> components = new HashMap<>();
    private boolean deleted;

    private FirebaseApp(String name, FirebaseOptions options, Context appContext) {
        this.name = name;
        this.options = options;
        this.appContext = appContext;
    }

    /** Auto-init from manifest. The real FirebaseInitProvider reads
     *  google-services.json values out of resources and constructs an
     *  app from them. If we shadow that path with our own stub the
     *  bundled provider's onCreate may still try -- and now succeeds
     *  via FirebaseOptions.fromResource against our Resources. */
    public static FirebaseApp initializeApp(Context context) {
        if (context == null) return getOrCreateDefault(null);
        sAppContext = context.getApplicationContext();
        FirebaseOptions options = FirebaseOptions.fromResource(sAppContext);
        return initializeApp(sAppContext, options, DEFAULT_APP_NAME);
    }
    public static FirebaseApp initializeApp(Context context, FirebaseOptions options) {
        return initializeApp(context, options, DEFAULT_APP_NAME);
    }
    public static FirebaseApp initializeApp(Context context,
                                             FirebaseOptions options, String name) {
        if (context != null) sAppContext = context.getApplicationContext();
        String key = (name == null || name.isEmpty()) ? DEFAULT_APP_NAME : name;
        synchronized (sApps) {
            FirebaseApp app = sApps.get(key);
            if (app == null) {
                app = new FirebaseApp(key, options, sAppContext);
                sApps.put(key, app);
            }
            return app;
        }
    }

    public static FirebaseApp getInstance() {
        return getOrCreateDefault(null);
    }
    public static FirebaseApp getInstance(String name) {
        synchronized (sApps) {
            FirebaseApp app = sApps.get(name);
            if (app != null) return app;
            app = new FirebaseApp(name, null, sAppContext);
            sApps.put(name, app);
            return app;
        }
    }
    public static java.util.List<FirebaseApp> getApps(Context context) {
        // The Context-arg form just exists to give the real SDK a
        // chance to refresh state; we don't need it.
        getOrCreateDefault(context);
        synchronized (sApps) { return new ArrayList<FirebaseApp>(sApps.values()); }
    }

    private static FirebaseApp getOrCreateDefault(Context context) {
        synchronized (sApps) {
            FirebaseApp app = sApps.get(DEFAULT_APP_NAME);
            if (app != null) return app;
            if (context != null) sAppContext = context.getApplicationContext();
            FirebaseOptions options = sAppContext != null
                ? FirebaseOptions.fromResource(sAppContext) : null;
            app = new FirebaseApp(DEFAULT_APP_NAME, options, sAppContext);
            sApps.put(DEFAULT_APP_NAME, app);
            return app;
        }
    }

    public String getName() { return name; }
    public FirebaseOptions getOptions() {
        return options != null ? options : new FirebaseOptions.Builder().build();
    }
    public Context getApplicationContext() { return appContext; }
    public boolean isDataCollectionDefaultEnabled() { return false; }
    public void setDataCollectionDefaultEnabled(boolean enabled) {}
    public void setAutomaticResourceManagementEnabled(boolean enabled) {}

    public void delete() {
        synchronized (sApps) {
            sApps.remove(name);
        }
        deleted = true;
    }

    /** Generic component lookup used by Firebase modules. We hand back
     *  null for unknown component classes -- that path lets the SDK's
     *  Component.<init> guard treat the module as absent. */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> anInterface) {
        synchronized (components) {
            Object c = components.get(anInterface);
            return (T) c;
        }
    }

    // Background-state listener plumbing some Firebase modules wire
    // (e.g. FCM's auto-token-refresh). We accept the registration but
    // never fire the callback -- our stubs don't model app foreground/
    // background transitions through this surface.
    public interface BackgroundStateChangeListener {
        void onBackgroundStateChanged(boolean background);
    }
    public static void addBackgroundStateChangeListener(BackgroundStateChangeListener l) {
        synchronized (sBgListeners) { sBgListeners.add(l); }
    }
    public static void removeBackgroundStateChangeListener(BackgroundStateChangeListener l) {
        synchronized (sBgListeners) { sBgListeners.remove(l); }
    }
}
