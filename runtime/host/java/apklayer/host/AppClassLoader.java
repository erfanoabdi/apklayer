package apklayer.host;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * apklayer patch: the byte/resource index behind each app's genuine PathClassLoader. It defines converted bytes in that Android loader, never in this index. Its parent is the Robolectric sandbox
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
    /** Host index for the app; distinct from the genuine Android defining loader exposed above. */
    public static volatile ClassLoader index;
    private final ClassLoader definingLoader;
    private static final java.lang.reflect.Method DEFINE = loaderMethod("defineClass", String.class, byte[].class, int.class, int.class);
    private static final java.lang.reflect.Method LOADED = loaderMethod("findLoadedClass", String.class);
    private static final java.lang.reflect.Method RESOLVE = loaderMethod("resolveClass", Class.class);

    private static java.lang.reflect.Method loaderMethod(String name, Class<?>... parameters) {
        try {
            java.lang.reflect.Method method = ClassLoader.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }

    /** Actual defining loader: casts, Class.getClassLoader and JNI all see the same object. */
    public ClassLoader definingLoader() { return definingLoader; }

    /** Packages of Android's boot class path, plus the host's own: these come from the parent first. */
    private static final String[] PLATFORM = {"java.", "javax.", "android.", "dalvik.", "libcore.", "com.android.", "org.json.", "org.w3c.dom.",
            "org.xml.sax.", "org.xmlpull.", "org.apache.harmony.", "sun.", "jdk.", "org.robolectric.", "apklayer."};

    /** The shared libraries the app uses (HostSharedLibraries: org.apache.http.legacy ...), or null: as on Android their loader comes before the app's. */
    private volatile AppClassLoader libraries;

    public AppClassLoader(String name, Path[] jars, ClassLoader parent) {
        super(name, urls(jars), parent);
        try {
            Class<?> pathLoader = parent.loadClass("dalvik.system.PathClassLoader");
            definingLoader = (ClassLoader) pathLoader.getConstructor(String.class, String.class, ClassLoader.class)
                    .newInstance("", System.getProperty("apklayer.libdir", ""), bootParent(parent));
            dexElements(definingLoader, jars);
            java.lang.reflect.Method attach = pathLoader.getDeclaredMethod("setApklayerDelegate", ClassLoader.class, Function.class);
            attach.setAccessible(true);
            attach.invoke(definingLoader, this, (Function<String, String>) this::findLibrary);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot create the app's PathClassLoader", failure);
        }
    }

    /**
     * Android's PathClassLoader has java.lang.BootClassLoader as its parent (libcore ClassLoader.BootClassLoader); the patched java.base has one
     * (JdkBootLoader) that asks the sandbox for the platform's classes, without the host's own. Without the patch the sandbox stays the parent.
     */
    private static ClassLoader bootParent(ClassLoader sandbox) {
        try {
            Class<?> boot = Class.forName("java.lang.BootClassLoader");
            java.lang.reflect.Method get = boot.getDeclaredMethod("getInstance");
            get.setAccessible(true);
            ClassLoader instance = (ClassLoader) get.invoke(null);
            java.lang.reflect.Field delegate = boot.getDeclaredField("delegate"), hidden = boot.getDeclaredField("hidden");
            delegate.setAccessible(true);
            hidden.setAccessible(true);
            delegate.set(null, sandbox);
            hidden.set(null, (java.util.function.Predicate<Class<?>>) c -> hiddenName(c.getName()));
            return instance;
        } catch (ReflectiveOperationException | RuntimeException unpatched) {
            System.err.println("[apklayer] no BootClassLoader parent: " + unpatched + " " + unpatched.getCause());
            return sandbox;
        }
    }

    /**
     * dexElements and the library directories as a device's loader shows them: the base.apk with a DexFile (its cookie a long[2], as ART's), the
     * app's library directory and the system's. The DexFile is never opened (the classes come from the host index): an instance without a native
     * open, so reading `dexElements` finds what the device has, but its dex methods have nothing behind them.
     */
    private static void dexElements(ClassLoader loader, Path[] jars) {
        try {
            Path apk = jars[jars.length - 1];
            if (!apk.toString().endsWith(".apk")) return;
            Class<?> base = loader.getClass().getSuperclass();
            java.lang.reflect.Field pathList = base.getDeclaredField("pathList");
            pathList.setAccessible(true);
            Object list = pathList.get(loader);
            ClassLoader platform = loader.getClass().getClassLoader();
            Class<?> dexFileClass = platform.loadClass("dalvik.system.DexFile"), elementClass = platform.loadClass("dalvik.system.DexPathList$Element");
            Object dex = UnsafeAccess.getUnsafe().allocateInstance(dexFileClass);
            for (String name : new String[] {"mCookie", "mInternalCookie"}) {
                java.lang.reflect.Field f = dexFileClass.getDeclaredField(name);
                f.setAccessible(true);
                f.set(dex, new long[] {0x7f0000000000L, 0x7f0000001000L});
            }
            java.lang.reflect.Field fileName = dexFileClass.getDeclaredField("mFileName");
            fileName.setAccessible(true);
            UnsafeAccess.getUnsafe().putObject(dex, UnsafeAccess.getUnsafe().objectFieldOffset(fileName), apk.toString());
            java.lang.reflect.Constructor<?> make = elementClass.getConstructor(dexFileClass, java.io.File.class);
            Object element = make.newInstance(dex, apk.toFile());
            Object elements = java.lang.reflect.Array.newInstance(elementClass, 1);
            java.lang.reflect.Array.set(elements, 0, element);
            java.lang.reflect.Field target = list.getClass().getDeclaredField("dexElements");
            target.setAccessible(true);
            target.set(list, elements);
            String abi = HostBuild.appAbi(), lib = abi.equals("armeabi-v7a") ? "lib" : "lib64";
            java.util.List<java.io.File> dirs = new java.util.ArrayList<>();
            String libdir = System.getProperty("apklayer.libdir", "");
            if (!libdir.isEmpty()) dirs.add(new java.io.File(libdir));
            dirs.add(new java.io.File(apk + "!/lib/" + abi));
            java.util.List<java.io.File> system = new java.util.ArrayList<>();
            for (String d : new String[] {"/system/" + lib, "/system/system_ext/" + lib, "/system/product/" + lib}) system.add(new java.io.File(d));
            for (Object[] set : new Object[][] {{"nativeLibraryDirectories", dirs}, {"systemNativeLibraryDirectories", system}}) {
                java.lang.reflect.Field field = list.getClass().getDeclaredField((String) set[0]);
                field.setAccessible(true);
                field.set(list, set[1]);
            }
        } catch (ReflectiveOperationException | RuntimeException keep) {
            System.err.println("[apklayer] dexElements not set: " + keep + " " + keep.getCause());
        }
    }

    /** What an app's lookup must not find: the host's own packages and the JDK's classes Android has none of. */
    private static boolean hiddenName(String name) {
        return name.startsWith("org.robolectric.") || name.startsWith("apklayer.") && !appHelper(name);
    }

    /** The converter's targets in the app's classes (DenyInstrument): resolved by the app's own loader. */
    private static boolean appHelper(String name) {
        return name.startsWith("apklayer.relocated.") || name.startsWith("apklayer.loader.") || switch (name) {
            case "apklayer.host.Reflect", "apklayer.host.GuestSystemProperties", "apklayer.host.GuestEnvironment", "apklayer.host.NativeLoader",
                 "apklayer.host.Services", "apklayer.host.UnsafeAccess", "apklayer.host.GuestLoaders", "apklayer.host.AppClassLoader" -> true;
            default -> false;
        };
    }

    private static final java.util.Set<String> JDK_ONLY = java.util.Set.of("java.lang.Module", "java.lang.StackWalker", "java.lang.ProcessHandle",
            "java.lang.Record", "java.lang.invoke.LambdaForm", "java.lang.invoke.DirectMethodHandle", "java.lang.invoke.StringConcatFactory",
            "sun.misc.Signal", "jdk.internal.loader.ClassLoaders");

    private static boolean jdkOnly(String name) {
        return JDK_ONLY.contains(name) || name.startsWith("java.lang.management.") || name.startsWith("com.sun.management.") || name.startsWith("sun.management.")
                || name.startsWith("java.lang.ProcessHandle");
    }

    private static final StackWalker WALK = StackWalker.getInstance();

    /** Class.forName asked: the JVM's own linking of an app class (which names java.lang.invoke classes) is not a lookup by the app. */
    private static boolean byForName() {
        return WALK.walk(frames -> frames.map(f -> f.getClassName() + "." + f.getMethodName()).anyMatch(s -> s.equals("java.lang.Class.forName")
                || s.equals("java.lang.Class.forName0")));
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
        if (name.startsWith("[")) return Class.forName(name.replace('/', '.'), false, definingLoader);  // ART's loaders answer array names (Chromium's JNI asks)
        name = name.replace('/', '.');  // ... and binary names with slashes (Paddleboat)
        if (hiddenName(name) || (jdkOnly(name) || name.startsWith("apklayer.")) && byForName()) throw new ClassNotFoundException(name);  // not on Android's boot class path
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
        if (resolve) {
            try { RESOLVE.invoke(definingLoader, c); }
            catch (ReflectiveOperationException failure) { throw new ClassNotFoundException(name, failure); }
        }
        return c;
    }

    /** This loader's own class of that name, or null. Takes no lock: the sandbox calls it under its own, while a thread in loadClass may hold ours. */
    @Override
    public Class<?> apply(String name) {
        AppClassLoader libs = libraries;
        Class<?> c = libs == null ? null : libs.apply(name);
        if (c != null) return c;
        c = ownLoadedClass(name);
        if (c != null) return c;
        try { return defineOwnClass(name); }
        catch (ClassNotFoundException missing) { return null; }
        catch (LinkageError duplicate) {  // another thread defined it first
            c = ownLoadedClass(name);
            if (c == null) throw duplicate;
            return c;
        }
    }

    private Class<?> ownLoadedClass(String name) {
        try { return (Class<?>) LOADED.invoke(definingLoader, name); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private Class<?> defineOwnClass(String name) throws ClassNotFoundException {
        URL source = super.findResource(name.replace('.', '/') + ".class");
        if (source == null) throw new ClassNotFoundException(name);
        try (java.io.InputStream input = source.openStream()) {
            byte[] bytes = input.readAllBytes();
            return (Class<?>) DEFINE.invoke(definingLoader, name, bytes, 0, bytes.length);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error error) throw error;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new ClassNotFoundException(name, cause);
        } catch (ReflectiveOperationException | java.io.IOException failure) {
            throw new ClassNotFoundException(name, failure);
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
