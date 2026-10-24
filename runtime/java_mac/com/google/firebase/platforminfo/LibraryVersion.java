package com.google.firebase.platforminfo;

// Minimal LibraryVersion value type. Returned (in a Set) by
// GlobalLibraryVersionRegistrar.getRegisteredVersions(), which the Firebase
// C++ SDK calls during init. We register nothing, so the set is always empty;
// the accessors exist only so the type resolves and any reflective access is
// safe. App-agnostic.
public abstract class LibraryVersion {
    public static LibraryVersion create(String libraryName, String version) {
        return new LibraryVersion() {
            public String getLibraryName() { return libraryName; }
            public String getVersion()     { return version; }
        };
    }
    public abstract String getLibraryName();
    public abstract String getVersion();
}
