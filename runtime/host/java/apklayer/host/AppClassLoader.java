package apklayer.host;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * apklayer patch: an app's own class loader, as Android's LoadedApk gives every app a PathClassLoader of its own. Its parent is the Robolectric sandbox
 * (the framework, Robolectric and the host). What Android's boot class path owns (java, android, dalvik, com.android...) comes from the parent first, as on
 * a device; everything else is the app's own first, so an app's Guava, BouncyCastle, Conscrypt, Kotlin or Chromium never mixes with the host's or another
 * package's copy. The WebView provider gets one of these too (ShadowHostWebViewFactory).
 *
 * <p>The sandbox loader asks {@link #apply} for names it cannot find (patches/robolectric/0012-sandbox-loader-art-names.patch, ApklayerAppHook): framework and Robolectric code that
 * loads app classes by name with its own loader (activities, services, providers, Parcel's default loader) finds them here.
 */
public final class AppClassLoader extends URLClassLoader implements Function<String, Class<?>> {
    static { registerAsParallelCapable(); }

    /** The app's loader as seen inside the sandbox (set by Main; null in the hub). */
    public static volatile ClassLoader app;

    /** Packages of Android's boot class path, plus the host's own: these come from the parent first. */
    private static final String[] PLATFORM = {"java.", "javax.", "android.", "dalvik.", "libcore.", "com.android.", "org.json.", "org.w3c.dom.",
            "org.xml.sax.", "org.xmlpull.", "org.apache.harmony.", "sun.", "jdk.", "org.robolectric.", "apklayer."};

    /** The shared libraries the app uses (HostSharedLibraries: org.apache.http.legacy ...), or null: as on Android their loader comes before the app's. */
    private volatile AppClassLoader libraries;

    public AppClassLoader(String name, Path[] jars, ClassLoader parent) {
        super(name, urls(jars), parent);
    }

    /**
     * Puts the app's shared libraries in front of its own classes and of the sandbox: the sandbox's jars carry "Stub!" copies of their classes
     * (org.apache.http.*, android.net.http.*), which must not win. None of them is a class of the framework itself. Called before any app class loads.
     */
    public void addLibraries(Path[] jars) {
        libraries = new AppClassLoader(getName() + "-libraries", jars, getParent());
    }

    private static URL[] urls(Path[] jars) {
        URL[] urls = new URL[jars.length];
        try { for (int i = 0; i < jars.length; i++) urls[i] = jars[i].toUri().toURL(); }
        catch (MalformedURLException impossible) { throw new IllegalArgumentException(impossible); }
        return urls;
    }

    static boolean platform(String name) {
        for (String p : PLATFORM) if (name.startsWith(p)) return true;
        return false;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("[")) return Class.forName(name.replace('/', '.'), false, this);  // ART's loaders answer array names (Chromium's JNI asks)
        name = name.replace('/', '.');  // ... and binary names with slashes (Paddleboat)
        // No lock of ours is held while the parent loads: a thread in the sandbox's loadClass (holding its lock) may be defining an app class here through
        // apply, whose superclass then comes through this method; apply itself copes with two threads defining one class.
        Class<?> c = libraries == null || name.startsWith("java.") ? null : libraries.apply(name);
        if (c != null) {
            // a shared library's class
        } else if (platform(name)) {
            try { c = getParent().loadClass(name); }
            catch (ClassNotFoundException missing) {
                c = apply(name);  // a platform-named class the platform lacks (android.support.*, javax.inject)
                if (c == null) throw missing;
            }
        } else {
            c = apply(name);
            if (c == null) c = getParent().loadClass(name);
        }
        if (resolve) resolveClass(c);
        return c;
    }

    /** This loader's own class of that name, or null. Takes no lock: the sandbox calls it under its own, while a thread in loadClass may hold ours. */
    @Override
    public Class<?> apply(String name) {
        AppClassLoader libs = libraries;
        Class<?> c = libs == null ? null : libs.apply(name);
        if (c != null) return c;
        c = findLoadedClass(name);
        if (c != null) return c;
        try { return findClass(name); }
        catch (ClassNotFoundException missing) { return null; }
        catch (LinkageError duplicate) {  // another thread defined it first
            c = findLoadedClass(name);
            if (c == null) throw duplicate;
            return c;
        }
    }

    /** The app's native libraries, as a PathClassLoader finds them: native code asks this loader through JNI (Unity's il2cpp lookup), past the converter. */
    @Override
    protected String findLibrary(String name) {
        return NativeLoader.findLibrary(name);
    }

    /** The app's own resources first (META-INF/services, kotlin builtins), as for classes. */
    @Override
    public URL getResource(String name) {
        URL own = findResource(name);
        if (own == null && libraries != null) own = libraries.findResource(name);
        return own != null ? own : super.getResource(name);
    }
}
