package apklayer.host;

import dalvik.system.BaseDexClassLoader;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Android loader view of converted classes and their actual extracted native libraries. */
public final class HostDexClassLoaders {
    private static final Map<ClassLoader, WeakReference<BaseDexClassLoader>> LOADERS = new WeakHashMap<>();
    private HostDexClassLoaders() {}

    // apklayer patch: GameActivity and other Android SDKs cast their context
    // loader to BaseDexClassLoader to find JNI libraries. Classes still load
    // through the real sandbox parent; no dex files are opened a second time.
    public static synchronized BaseDexClassLoader adapt(ClassLoader loader) {
        if (loader == null || loader instanceof BaseDexClassLoader) return (BaseDexClassLoader) loader;
        WeakReference<BaseDexClassLoader> reference = LOADERS.get(loader);
        BaseDexClassLoader result = reference == null ? null : reference.get();
        if (result == null) {
            result = new BaseDexClassLoader("", null, System.getProperty("apklayer.libdir", ""), loader) {
                @Override public String findLibrary(String name) { return NativeLoader.findLibrary(name); }
            };
            LOADERS.put(loader, new WeakReference<>(result));
        }
        return result;
    }
}
