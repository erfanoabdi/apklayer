package com.google.firebase.crashlytics;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// FirebaseCrashlytics no-op shadow. Real Crashlytics intercepts the
// process's default UncaughtExceptionHandler and uploads crash data
// to Firebase servers. Both halves are out of bounds for Mac.apk:
// we don't want to fight HotSpot's exception machinery, and we
// absolutely don't want to call Google's servers. Every call is a
// silent drop.
//
// For internal debugging we could route .log(...) / .recordException
// to Mac.apk's NativeLog; left disabled by default to keep boot
// output readable.
public final class FirebaseCrashlytics {
    private static final FirebaseCrashlytics INSTANCE = new FirebaseCrashlytics();

    // The Firebase Crashlytics Unity/C++ plugin reads this package-private
    // field by JNI GetFieldID (signature
    // Lcom/google/firebase/crashlytics/internal/common/CrashlyticsCore;) and
    // drives logging through it directly. A missing field throws
    // NoSuchFieldError uncaught on the Unity main thread during nativeRender,
    // stalling Crossy's GameSceneBoot at the blue title screen. Name + type
    // must match the real SDK exactly. Non-null so any `core != null` init
    // check the plugin makes passes.
    final com.google.firebase.crashlytics.internal.common.CrashlyticsCore core =
        new com.google.firebase.crashlytics.internal.common.CrashlyticsCore();

    private FirebaseCrashlytics() {}

    public static FirebaseCrashlytics getInstance() { return INSTANCE; }

    private static final boolean TRACE =
        System.getenv("MACAPK_CRASHLYTICS_TRACE") != null;
    private static void trace(String w) {
        if (TRACE) { System.err.println("[crashlytics-trace] " + w); System.err.flush(); }
    }
    public void recordException(Throwable throwable) { trace("exception " + throwable); }
    public void log(String message) { trace("log " + message); }

    public void setCustomKey(String key, String value)  { trace("key " + key + "=" + value); }
    public void setCustomKey(String key, boolean value) {}
    public void setCustomKey(String key, double value)  {}
    public void setCustomKey(String key, float value)   {}
    public void setCustomKey(String key, int value)     {}
    public void setCustomKey(String key, long value)    {}

    public void setUserId(String identifier) {}

    public void setCrashlyticsCollectionEnabled(boolean enabled) {}
    public void setCrashlyticsCollectionEnabled(Boolean enabled) {}

    public boolean didCrashOnPreviousExecution() { return false; }

    public Task<Boolean> checkForUnsentReports() { return Tasks.forResult(Boolean.FALSE); }
    public void sendUnsentReports()              {}
    public void deleteUnsentReports()            {}
}
