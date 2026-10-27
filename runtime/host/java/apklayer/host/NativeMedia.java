package apklayer.host;

import org.robolectric.nativeruntime.DefaultNativeRuntimeLoader;

/** The runtime loader registers media JNI and finishes its deferred class initializers. */
final class NativeMedia {
    static void ensureInitialized() { DefaultNativeRuntimeLoader.injectAndLoad(); }
    private NativeMedia() {}
}
