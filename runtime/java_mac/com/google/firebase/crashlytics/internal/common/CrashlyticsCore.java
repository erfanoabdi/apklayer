package com.google.firebase.crashlytics.internal.common;

// Minimal shadow of Crashlytics' internal CrashlyticsCore. The Firebase
// Crashlytics Unity/C++ plugin reaches past the public FirebaseCrashlytics
// API and reads the package-private `FirebaseCrashlytics.core` field
// (type com.google.firebase.crashlytics.internal.common.CrashlyticsCore)
// via JNI GetFieldID, then drives logging/custom-keys through it directly.
// Without a field of exactly this type, GetFieldID throws NoSuchFieldError
// uncaught on the Unity main thread during nativeRender and stalls Crossy's
// GameSceneBoot init at the blue title screen. We model the public surface
// CrashlyticsCore exposes and no-op every body -- consistent with Mac.apk's
// "crash reporting silenced, never call Google's servers" stance. App-agnostic.
public class CrashlyticsCore {
    // The Crashlytics Unity/C++ plugin reads core.dataCollectionArbiter by JNI
    // GetFieldID and toggles collection through it. Field name + type must match
    // the real SDK; non-null so the plugin's collection-toggle path works.
    public final DataCollectionArbiter dataCollectionArbiter = new DataCollectionArbiter();

    // Diagnostic: Crossy's BootController.CrashlyticsBootStage funnels the
    // current boot stage through Crashlytics custom keys/logs. Surfacing them
    // (MACAPK_CRASHLYTICS_TRACE=1) shows exactly how far the boot progressed.
    private static final boolean TRACE =
        System.getenv("MACAPK_CRASHLYTICS_TRACE") != null;
    private static void trace(String what) {
        if (TRACE) { System.err.println("[crashlytics-trace] " + what); System.err.flush(); }
    }
    public void setInternalKey(String key, String value) { trace("key " + key + "=" + value); }
    public void setCustomKey(String key, String value) { trace("key " + key + "=" + value); }
    public void setCustomKeys(java.util.Map<String, String> keysAndValues) {}
    public void setUserId(String identifier) {}
    public void log(long timestamp, String msg) { trace("log " + msg); }
    public void log(String msg) { trace("log " + msg); }
    public void logException(Throwable throwable) {}
    // The Crashlytics Unity/C++ plugin calls the 1-arg overload (signature
    // (Ljava/lang/Throwable;)V) directly on core for its uncaught-exception
    // bridge; the 2-arg form is the internal fatal path. Provide both.
    public void logFatalException(Throwable throwable) {}
    public void logFatalException(Thread thread, Throwable throwable) {}
    public boolean didCrashOnPreviousExecution() { return false; }
    public void setCrashlyticsCollectionEnabled(boolean enabled) {}
    public void setCrashlyticsCollectionEnabled(Boolean enabled) {}
    public void sendUnsentReports() {}
    public void deleteUnsentReports() {}
}
