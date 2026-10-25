// LibcoreFields -- answer an app's reflection on libcore's PRIVATE field names
// with the host JDK's field.
//
// Android's core library is libcore, and its java.* classes carry their own
// private field names. Apps (and the libraries they bundle) reach into those by
// NAME, because Android gives them no public accessor. The host here is
// OpenJDK 21's java.base, which cannot be shadowed (bootstrap loader, sealed
// module), and some of its private fields are named differently. A
// getDeclaredField on the libcore name then throws NoSuchFieldException where a
// device answers.
//
// The table, each row read from both sources:
//   java.io.FileDescriptor  libcore `private int descriptor;`
//       (platform/libcore android14-release ojluni/src/main/java/java/io/
//        FileDescriptor.java:55)
//                           OpenJDK  `private int fd;`
//       (openjdk/jdk21u src/java.base/share/classes/java/io/FileDescriptor.java:51)
//   Both hold the same thing, the POSIX descriptor number. java.io is opened to
//   the app with --add-opens (runtime/src/jvm_loader.cpp), so the caller's own
//   setAccessible(true) succeeds exactly as it does on a device.
//
// Victim: barteksc/PdfiumAndroid (com.shockwave.pdfium.PdfiumCore.getNumFd)
// reflects FileDescriptor.descriptor to hand the PDF's fd to native pdfium.
// NoSuchFieldException -> fd -1 -> nativeOpenDocument "File is empty", so every
// PDFView page rendered blank (Rangskat's How to Use / About pages).
//
// Reached only through ClassFileRewriter Pattern L, which turns the app's own
// `invokevirtual Class.getDeclaredField(String)` call sites into
// `invokestatic LibcoreFields.getDeclaredField(Class, String)`. Every name not in
// the table, and every class not in it, goes straight to Class.getDeclaredField,
// with the same exceptions (a null receiver still throws NullPointerException).
package com.macapk.loader;

import java.lang.reflect.Field;

public final class LibcoreFields {
    private LibcoreFields() {}

    /** The host name for a libcore private field, or null if not mapped. */
    static String hostName(Class<?> c, String name) {
        if (c == java.io.FileDescriptor.class && "descriptor".equals(name)) return "fd";
        return null;
    }

    public static Field getDeclaredField(Class<?> c, String name) throws NoSuchFieldException {
        if (c == null) throw new NullPointerException();
        String host = hostName(c, name);
        if (host != null) {
            try {
                return c.getDeclaredField(host);
            } catch (NoSuchFieldException absentOnThisJdk) {
                // A JDK without the host field: fall through and report the
                // name the app asked for.
            }
        }
        return c.getDeclaredField(name);
    }
}
