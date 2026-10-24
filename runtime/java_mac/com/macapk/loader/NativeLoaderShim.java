package com.macapk.loader;

/**
 * A one-method trampoline whose ONLY purpose is to be re-defined inside the
 * AppClassLoader so that System.load() registers a native library against THAT
 * loader.
 *
 * WHY THIS EXISTS. JNI binds a loaded library to the ClassLoader of the class
 * that called load(), via Reflection.getCallerClass(). AppClassLoader itself is
 * defined by the SYSTEM loader, so `Runtime.getRuntime().load(...)` from inside
 * it registered every preloaded DT_NEEDED dependency in the SYSTEM loader's
 * native-library set. HotSpot's NativeLookup then searches only the DECLARING
 * class's loader when resolving a native method -- and the guest's classes are
 * defined by AppClassLoader, not the system loader. So the symbols were present
 * in the process and still unresolvable.
 *
 * Measured on Signal (2026-08-24): libringrtc_rffi.so is a DT_NEEDED dep of
 * libringrtc.so and exports JNI_OnLoad plus 189 Java_org_webrtc_* symbols
 * (`nm -D`: the symbol is U in libringrtc.so and T in the _rffi one). It
 * preloaded fine, yet
 * Java_org_webrtc_PeerConnectionFactory_nativeInitializeAndroidGlobals never
 * resolved, so "AssertionError: Unable to load ringrtc library" escaped
 * AppStartup.execute()'s BLOCKING list. Its NON-blocking list -- which contains
 * EmojiSource::refresh -- was therefore never submitted, emojiLatch never
 * counted down, and the first keystroke into any EmojiEditText parked the guest
 * UI thread forever in CountDownLatch.await. The user-visible symptom was "the
 * text box doesn't work" on one screen, three layers away from the cause.
 *
 * This is GENERAL, not a Signal quirk: any APK whose JNI entry points live in a
 * transitively-loaded dependency rather than the library the app names in
 * System.loadLibrary() hits it.
 *
 * AppClassLoader defines a private copy of this class per instance and calls
 * load() reflectively; see preloadDependencies().
 */
public final class NativeLoaderShim {
    private NativeLoaderShim() {}

    /** Registers `absPath` against the ClassLoader that defined THIS class. */
    public static void load(String absPath) {
        System.load(absPath);
    }
}
