package com.google.firebase.crashlytics.ndk;

// Minimal FirebaseCrashlyticsNdk shadow. The Firebase Crashlytics Unity/C++
// plugin resolves the NDK crash handler via a static getInstance() (JNI
// GetStaticMethodID) during init; a missing class/method throws
// NoSuchMethodError uncaught on the Unity main thread and stalls Crossy's
// GameSceneBoot. We hand back a no-op singleton -- native crash capture is out
// of bounds for Mac.apk (we don't intercept HotSpot's signal handling and
// never call Google's servers). App-agnostic.
public final class FirebaseCrashlyticsNdk {
    private static final FirebaseCrashlyticsNdk INSTANCE = new FirebaseCrashlyticsNdk();
    private FirebaseCrashlyticsNdk() {}
    public static FirebaseCrashlyticsNdk getInstance() { return INSTANCE; }

    // The Firebase Crashlytics Unity plugin's native layer JNI-calls
    // installSignalHandler() on this instance during PlatformAccessor..cctor to
    // arm the NDK native-crash signal handler (it throws an ApplicationException
    // "Unable to find Method ... installSignalHandler ()V" if absent, which the
    // cctor wraps in a TypeInitializationException -> Provider stays null -> the
    // Crossy menu never loads). Mac.apk does not intercept HotSpot's signal
    // handling and never uploads to Google's servers, so native crash capture is
    // a no-op; arming "succeeds" from the caller's view. App-agnostic.
    public void installSignalHandler() {}

    // The real FirebaseCrashlyticsNdk (firebase-crashlytics-ndk AAR) also exposes
    // hasCrashDataForCurrentSession()/hasCrashDataForSession(String); the
    // Crashlytics init path queries these to surface a crash recorded by a prior
    // run's native signal handler. Mac.apk never captures native crashes, so
    // there is never prior native crash data: both return false. (The remaining
    // real methods -- prepareNativeSession(...,StaticSessionData) and
    // getSessionFileProvider(String):NativeSessionFileProvider -- reference
    // firebase-crashlytics-internal types not on Mac.apk's compile classpath and
    // are only reached once hasCrashData* is true, i.e. never here; add them as
    // no-ops only if a future caller's JNI lookup demands them.)
    public boolean hasCrashDataForCurrentSession() { return false; }
    public boolean hasCrashDataForSession(String sessionId) { return false; }
}
