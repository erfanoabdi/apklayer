package apklayer.host;

import dalvik.system.BaseDexClassLoader;

/** Compatibility entry point for converter ABI <= 5. App loaders now really are Android loaders. */
public final class HostDexClassLoaders {
    private HostDexClassLoaders() {}

    public static BaseDexClassLoader adapt(ClassLoader loader) {
        return (BaseDexClassLoader) loader;
    }
}
