package com.android.org.conscrypt;

import java.lang.reflect.InvocationTargetException;

/**
 * apklayer patch (docs/PATCHES.md): AOSP's NativeCryptoJni.init() is System.loadLibrary("javacrypto"), which HotSpot cannot satisfy (libjavacrypto.so is
 * Android's, a bionic library over BoringSSL's libcrypto/libssl). It loads through apklayer's guest-library loader instead, which maps it with
 * bionic_translation's linker and runs its JNI_OnLoad (registering NativeCrypto's natives), as for an app's own native library.
 */
class NativeCryptoJni {
    public static void init() {
        try {
            Class<?> loader = Class.forName("apklayer.host.NativeLoader");
            String path = (String) loader.getMethod("findLibrary", String.class).invoke(null, "javacrypto");
            if (path == null) throw new UnsatisfiedLinkError("dlopen failed: library \"libjavacrypto.so\" not found");
            loader.getMethod("load", String.class, ClassLoader.class).invoke(null, path, NativeCryptoJni.class.getClassLoader());
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error error) throw error;
            throw new UnsatisfiedLinkError(String.valueOf(e.getCause()));
        } catch (ReflectiveOperationException e) {
            throw new UnsatisfiedLinkError(String.valueOf(e));
        }
    }

    private NativeCryptoJni() {}
}
