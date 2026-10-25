// dalvik.system.DelegateLastClassLoader -- a PathClassLoader that inverts the
// normal parent-first delegation: it looks up classes/resources in itself
// (and the boot classpath) BEFORE asking the parent. Used to load a plugin that
// must shadow a class the host also defines (e.g. a newer support-library copy).
// Added in API 27.
//
// AOSP (libcore/dalvik/src/main/java/dalvik/system/DelegateLastClassLoader.java)
// loadClass order, quoted from the class javadoc:
//   1. Invoke findLoadedClass(String) to check if the class has already been
//      loaded.
//   2. Invoke the findClass(String) method of the boot class loader.
//   3. Invoke the findClass(String) method of this class loader.
//   4. Invoke the loadClass(String) method of the parent class loader.
// extends PathClassLoader; ctors:
//   public DelegateLastClassLoader(String dexPath, ClassLoader parent)
//   public DelegateLastClassLoader(String dexPath, String librarySearchPath, ClassLoader parent)
//   public DelegateLastClassLoader(String dexPath, String librarySearchPath, ClassLoader parent, boolean delegateResourceLoading)  // API 28
// and overrides loadClass(String,boolean), getResource(String), getResources(String).
//
// We mirror the delegation order on top of our URL-based PathClassLoader.
package dalvik.system;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

public final class DelegateLastClassLoader extends PathClassLoader {

    /** When false, RESOURCE lookups (getResource/getResources) are done
     *  child-first like classes; when true (the API-28+ default for the legacy
     *  ctors) resource lookups stay parent-first even though classes are
     *  child-first. AOSP defaults this to true for the 2- and 3-arg ctors. */
    private final boolean delegateResourceLoading;

    public DelegateLastClassLoader(String dexPath, ClassLoader parent) {
        this(dexPath, null, parent, true);
    }

    public DelegateLastClassLoader(String dexPath, String librarySearchPath,
                                   ClassLoader parent) {
        this(dexPath, librarySearchPath, parent, true);
    }

    public DelegateLastClassLoader(String dexPath, String librarySearchPath,
                                   ClassLoader parent,
                                   boolean delegateResourceLoading) {
        super(dexPath, librarySearchPath, parent);
        this.delegateResourceLoading = delegateResourceLoading;
    }

    // AOSP's "boot class loader" step is written as
    // Object.class.getClassLoader(), which on a real device is the
    // BootClassLoader. On HotSpot Object.class.getClassLoader() is null
    // (the bootstrap loader is represented by null), so we cannot invoke
    // loadClass/getResource on it directly. The platform class loader is the
    // JDK's stand-in for "the loader that defines java.*/javax.* and the
    // framework classes we ship" -- it is the nearest faithful analogue of
    // AOSP's boot loader for this child-first ordering, and crucially it is
    // the loader that holds our runtime/java framework reimpl, so a plugin
    // cannot shadow android.*/dalvik.* either.
    private static final ClassLoader BOOT = ClassLoader.getPlatformClassLoader();

    /** Child-first class loading, mirroring the AOSP 4-step order exactly:
     *  (1) findLoadedClass, (2) boot class loader, (3) THIS loader's findClass,
     *  (4) the parent. On a CNFE from the parent, AOSP rethrows the exception
     *  from step 3 (so the message names THIS loader's dex paths); we mirror
     *  that. */
    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // 1. already loaded?
            Class<?> c = findLoadedClass(name);

            if (c == null) {
                // 2. boot class loader (platform/JDK/framework classes must
                //    never be shadowed by a plugin).
                try {
                    c = BOOT.loadClass(name);
                } catch (ClassNotFoundException ignored) {
                    // 3. this loader (child-first for app/plugin classes)
                    ClassNotFoundException fromSelf = null;
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException ex) {
                        fromSelf = ex;
                    }
                    if (c == null) {
                        // 4. the parent. AOSP rethrows the step-3 exception so
                        //    the message describes THIS loader's class path.
                        ClassLoader parent = getParent();
                        try {
                            if (parent == null) throw fromSelf;
                            c = parent.loadClass(name);
                        } catch (ClassNotFoundException fromParent) {
                            throw fromSelf != null ? fromSelf : fromParent;
                        }
                    }
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    /** Child-first single-resource lookup, mirroring AOSP getResource:
     *  boot first, then this loader's findResource, then (only when
     *  delegateResourceLoading) the parent. The flag controls ONLY whether the
     *  parent is consulted at the end -- it never makes the lookup parent-first
     *  (the prior super.getResource path was parent-first, which inverted the
     *  child-first contract). */
    @Override
    public URL getResource(String name) {
        URL resource = BOOT.getResource(name);
        if (resource != null) return resource;
        resource = findResource(name);
        if (resource != null) return resource;
        if (delegateResourceLoading) {
            ClassLoader cl = getParent();
            return cl == null ? null : cl.getResource(name);
        }
        return null;
    }

    /** Child-first resource enumeration, mirroring AOSP getResources:
     *  concatenate boot resources, then this loader's findResources, then
     *  (only when delegateResourceLoading and a parent exists) the parent's. */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> boot = BOOT.getResources(name);
        Enumeration<URL> mine = findResources(name);
        ClassLoader parent = getParent();
        Enumeration<URL> theirs =
                (parent != null && delegateResourceLoading)
                        ? parent.getResources(name)
                        : Collections.emptyEnumeration();
        return concat(boot, concat(mine, theirs));
    }

    private static Enumeration<URL> concat(final Enumeration<URL> a,
                                           final Enumeration<URL> b) {
        return new Enumeration<URL>() {
            @Override public boolean hasMoreElements() {
                return a.hasMoreElements() || b.hasMoreElements();
            }
            @Override public URL nextElement() {
                return a.hasMoreElements() ? a.nextElement() : b.nextElement();
            }
        };
    }
}
