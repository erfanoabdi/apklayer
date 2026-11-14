package apklayer.host;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.function.Predicate;

/**
 * Template of {@code java.lang.BootClassLoader}, which JdkIdentityPatch copies into java.base (the class renamed): the parent an app's
 * PathClassLoader names, as on Android (libcore java/lang/ClassLoader.java: a singleton without a parent). The classes Android's boot class
 * path holds are the JDK's and the framework's here: it asks the Robolectric sandbox ({@link #delegate}, set by AppClassLoader). It defines
 * nothing itself; the JVM never links through it. Names the app must not see (Robolectric, apklayer, JDK-only classes: {@link #hidden}) are
 * not found through it; the JDK's own class files are no resources of it (Android's finds none either).
 */
class JdkBootLoader extends ClassLoader {
    private static JdkBootLoader instance;
    public static volatile ClassLoader delegate;
    public static volatile Predicate<Class<?>> hidden;

    public static synchronized JdkBootLoader getInstance() {
        if (instance == null) instance = new JdkBootLoader();
        return instance;
    }

    public JdkBootLoader() { super(null); }

    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (delegate == null) throw new ClassNotFoundException(name);
        return loadClass(name, false);
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        ClassLoader to = delegate;
        Class<?> c = to == null ? super.loadClass(name, false) : to.loadClass(name);
        Predicate<Class<?>> filter = hidden;
        if (filter != null && filter.test(c)) throw new ClassNotFoundException(name);
        return c;
    }

    @Override protected URL findResource(String name) {
        ClassLoader to = delegate;
        return to == null || jdkFile(name) ? null : to.getResource(name);
    }

    @Override protected Enumeration<URL> findResources(String name) throws IOException {
        ClassLoader to = delegate;
        return to == null || jdkFile(name) ? Collections.emptyEnumeration() : to.getResources(name);
    }

    @Override public URL getResource(String name) { return findResource(name); }
    @Override public Enumeration<URL> getResources(String name) throws IOException { return findResources(name); }

    private static boolean jdkFile(String name) {
        return name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/") || name.startsWith("sun/") || name.startsWith("com/sun/")
                || name.startsWith("org/robolectric/") || name.startsWith("apklayer/") || name.startsWith("META-INF/versions/");
    }
}
