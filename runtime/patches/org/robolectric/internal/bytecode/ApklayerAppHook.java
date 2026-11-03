package org.robolectric.internal.bytecode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;

/**
 * apklayer patch: the app's classes live in a class loader of their own (apklayer.host.AppClassLoader), a child of the sandbox. Framework and Robolectric
 * code loads app classes by name through the sandbox loader (activities, providers, Parcel's default loader), as ART's boot loader never would but
 * Robolectric does: a name the sandbox cannot find is asked of the app's loader. SandboxLoaderPatch makes SandboxClassLoader.loadClass call {@link #load}.
 */
public final class ApklayerAppHook {
    /** The app loader's own-classes lookup (null result: not there); set by apklayer.host.Main. */
    public static volatile Function<String, Class<?>> app;

    private static final MethodHandle ORIGINAL;
    static {
        try {
            ORIGINAL = MethodHandles.lookup().findVirtual(SandboxClassLoader.class, "apklayerLoadClass", MethodType.methodType(Class.class, String.class, boolean.class));
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }

    private ApklayerAppHook() {}

    public static Class<?> load(SandboxClassLoader loader, String name, boolean resolve) throws ClassNotFoundException {
        try {
            return (Class<?>) ORIGINAL.invokeExact(loader, name, resolve);
        } catch (ClassNotFoundException missing) {
            Function<String, Class<?>> hook = app;
            Class<?> own = hook == null ? null : hook.apply(name);
            if (own == null) throw missing;
            return own;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
