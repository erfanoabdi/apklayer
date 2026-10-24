// Diagnostic ClassLoader wrapper -- logs every loadClass attempt so we
// can confirm whether axmol / cocos2d-x is actually asking us for
// class lookups, and whether the lookups succeed. Delegates everything
// else to the wrapped real loader.
package com.macapk.loader;

public final class LoggingClassLoader extends ClassLoader {
    private static final String TAG = "Mac.apk.CL";
    private final ClassLoader real;

    public LoggingClassLoader(ClassLoader real) {
        super(real);
        this.real = real;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        try {
            Class<?> c = real.loadClass(name);
            if (resolve) resolveClass(c);
            NativeLog.i(TAG, "loadClass(" + name + ") -> ok");
            return c;
        } catch (ClassNotFoundException e) {
            NativeLog.w(TAG, "loadClass(" + name + ") -> ClassNotFound");
            throw e;
        }
    }

    @Override
    public Class<?> loadClass(String name)
            throws ClassNotFoundException {
        return loadClass(name, false);
    }
}
