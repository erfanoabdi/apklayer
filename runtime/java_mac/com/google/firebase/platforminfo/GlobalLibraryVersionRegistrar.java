package com.google.firebase.platforminfo;

// Minimal GlobalLibraryVersionRegistrar shadow. The Firebase C++ SDK fetches
// the singleton (static getInstance(), JNI GetStaticMethodID) to register the
// native library's version string during init; a missing class/method throws
// NoSuchMethodError uncaught on the Unity main thread and stalls Crossy's
// GameSceneBoot. registerVersion is a no-op (version telemetry is irrelevant
// off-device). App-agnostic.
public final class GlobalLibraryVersionRegistrar {
    private static final GlobalLibraryVersionRegistrar INSTANCE =
        new GlobalLibraryVersionRegistrar();
    private GlobalLibraryVersionRegistrar() {}
    public static GlobalLibraryVersionRegistrar getInstance() { return INSTANCE; }
    public void registerVersion(String libraryName, String version) {}
    // The Firebase C++ SDK calls this after getInstance() to read the registry.
    // We register nothing, so an empty (immutable) set is correct.
    public java.util.Set<LibraryVersion> getRegisteredVersions() {
        return java.util.Collections.emptySet();
    }
}
