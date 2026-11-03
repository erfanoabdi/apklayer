package org.robolectric.internal.bytecode;

import java.util.function.Function;

/**
 * apklayer's own: the app's classes live in a class loader of their own (apklayer.host.AppClassLoader), a child of the sandbox. Framework and Robolectric
 * code loads app classes by name through the sandbox loader (activities, providers, Parcel's default loader), as ART's boot loader never would but
 * Robolectric does: a name the sandbox cannot find is asked of the app's loader (patches/robolectric/0012-sandbox-loader-art-names.patch calls {@link #appClass}).
 */
public final class ApklayerAppHook {
    /** The app loader's own-classes lookup (null result: not there); set by apklayer.host.Main. */
    public static volatile Function<String, Class<?>> app;

    private ApklayerAppHook() {}

    /** The app's class of that name, or null. */
    static Class<?> appClass(String name) {
        Function<String, Class<?>> hook = app;
        return hook == null ? null : hook.apply(name);
    }
}
