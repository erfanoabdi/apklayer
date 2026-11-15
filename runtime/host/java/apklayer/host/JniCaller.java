package apklayer.host;

import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * JNI FindClass from an app's native code (bridge/native/runtime_shim.c lazy_find_class): ART answers through the class loader of the class whose
 * native method is running, so an app library finds the app's own copy of a library class. The sandbox's loader found the host's copy first wherever
 * the host has one: YouTube's libgoogle3 got Robolectric's protobuf ExtensionRegistryLite (no getGeneratedRegistry) and aborted.
 */
@DoNotInstrument
public final class JniCaller {
    private JniCaller() {}

    private static final StackWalker WALK = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /** The loader FindClass(name) answers through, or null for the sandbox's: outside an app, for Android's platform names, without a Java frame. */
    public static ClassLoader loaderFor(String name) {
        if (AppClassLoader.app == null || AppClassLoader.platform(name)) return null;
        // the frame below this one is the native method that called FindClass (none on a thread attached from native code)
        return WALK.walk(frames -> frames.skip(1).findFirst()).map(f -> f.getDeclaringClass().getClassLoader()).orElse(null);
    }
}
