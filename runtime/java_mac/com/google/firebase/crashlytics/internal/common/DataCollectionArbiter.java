package com.google.firebase.crashlytics.internal.common;

// Minimal shadow of Crashlytics' internal DataCollectionArbiter. The Firebase
// Crashlytics Unity/C++ plugin reads CrashlyticsCore.dataCollectionArbiter and
// drives the collection toggle through it (setCrashlyticsDataCollectionEnabled,
// isAutomaticDataCollectionEnabled) via JNI. Missing class/field/methods throw
// NoSuchFieldError/NoSuchMethodError uncaught on the Unity main thread and
// stall Crossy's GameSceneBoot at the blue title screen. Signatures mirror the
// real SDK; bodies no-op (crash reporting silenced). App-agnostic.
public class DataCollectionArbiter {
    public void setCrashlyticsDataCollectionEnabled(Boolean enabled) {}
    public boolean isAutomaticDataCollectionEnabled() { return false; }
}
