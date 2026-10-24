// android.app.AppComponentFactory (AOSP API 28).
//
// The manifest-pluggable hook AOSP's ActivityThread/Instrumentation route
// component construction through: getFactory(pkg).instantiateApplication/
// instantiateActivity/instantiateService/instantiateProvider/
// instantiateReceiver/instantiateClassLoader. Apps opt in to a custom
// factory via <application android:appComponentFactory="...">, and
// androidx.core auto-merges androidx.core.app.CoreComponentFactory into
// virtually every AndroidX app's manifest. CoreComponentFactory overrides
// these methods and calls super.instantiateXxx(...) for the real
// construction step, so the default bodies below must exist with these
// EXACT signatures even before any Mac.apk call site routes through this
// class -- otherwise resolving CoreComponentFactory's super call throws
// NoSuchMethodError the first time anything invokes it.
//
// Previously this was a v1.0.92 auto-generated empty stub (ctor only, zero
// methods). Mac.apk's own loader (ActivityHost.performLaunch, Boot's
// Application/ContentProvider bring-up, ServiceRegistry) still does direct
// reflection today rather than consulting this factory or parsing the
// manifest's android:appComponentFactory attribute -- routing those call
// sites through here, and reading the manifest attribute, is a separate,
// larger cross-file change tracked outside this file. This class itself is
// now filled in to be AOSP-correct and safe to route through once that
// lands.
//
// Ref: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/app/AppComponentFactory.java
package android.app;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;

public class AppComponentFactory {

    // AOSP exposes this as the factory ActivityThread falls back to for any
    // package that declares no custom android:appComponentFactory.
    public static final AppComponentFactory DEFAULT = new AppComponentFactory();

    public AppComponentFactory() {}

    public ClassLoader instantiateClassLoader(ClassLoader cl, ApplicationInfo aInfo) {
        return cl;
    }

    // AOSP's default bodies deliberately use the deprecated Class#newInstance()
    // rather than Constructor#newInstance(): it requires a public no-arg
    // constructor and rethrows InstantiationException/IllegalAccessException
    // unwrapped, instead of Constructor#newInstance()'s
    // InvocationTargetException wrapping. Overriders that call
    // super.instantiateXxx(...) and catch these specific checked types (the
    // androidx.core.app.CoreComponentFactory pattern: wrap-and-rethrow around
    // the super call) depend on that exact exception shape, so we mirror
    // AOSP's choice verbatim rather than "modernizing" it.
    /** MACAPK_ACTIVITYTHREAD_INSTANTIATE selects the instantiation primitive.
     *  "aosp" (the default, and the only value that is AOSP-faithful) uses
     *  Class#newInstance below. "mh" uses a MethodHandle constructor invoke.
     *
     *  This exists ONLY as an A/B arm, and it must never become the default.
     *  Measured on this project's JDK 21.0.11, the number of java.base frames a
     *  class's <clinit> sees beneath it differs sharply by primitive:
     *      Class.forName(name, true, cl)              3   (the pre-pipeline shape)
     *      Class.newInstance()                       10   (AOSP's choice, arm "aosp")
     *      getDeclaredConstructor().newInstance()     8
     *      MethodHandle findConstructor().invoke()    2   (arm "mh")
     *  An app that hashes its own launch stack therefore sees a different frame
     *  COUNT per primitive even though the framework frames are identical. The
     *  arm lets a measurement separate "the android.app.* frames are present"
     *  from "the jdk.internal reflection cluster changed size". Choosing "mh"
     *  is a deliberate deviation from AOSP: it changes the exception shape the
     *  CoreComponentFactory pattern documented below depends on. */
    private static final boolean MH_INSTANTIATE =
        "mh".equalsIgnoreCase(System.getenv("MACAPK_ACTIVITYTHREAD_INSTANTIATE"));

    /** The instantiation primitive, shared by every construction site that must
     *  honour MACAPK_ACTIVITYTHREAD_INSTANTIATE -- including
     *  LoadedApk.createAppFactory, which instantiates the APP'S OWN factory
     *  class and is therefore a <clinit> trigger in its own right.
     *
     *  That sharing is not tidiness. Measured on the Play-built Minecraft: the
     *  app's declared androidx.core.app.CoreComponentFactory is ITSELF a
     *  pairip-protected class, so createAppFactory is the FIRST site whose
     *  <clinit> enters the guest VM. An "mh" arm that covered only
     *  instantiateApplication left that site on the 10-frame Class.newInstance
     *  path and silently produced a VOID A/B -- the arm confirmed as engaged
     *  while the frames it was supposed to change were untouched. */
    @SuppressWarnings("deprecation")
    static Object macapkNewInstance(Class<?> c)
            throws InstantiationException, IllegalAccessException {
        if (!MH_INSTANTIATE) return c.newInstance();
        try {
            java.lang.invoke.MethodHandle ctor =
                java.lang.invoke.MethodHandles
                    .privateLookupIn(c, java.lang.invoke.MethodHandles.lookup())
                    .findConstructor(c, java.lang.invoke.MethodType.methodType(void.class));
            return ctor.invoke();
        } catch (Throwable t) {
            if (t instanceof Error) throw (Error) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            InstantiationException ie = new InstantiationException(t.toString());
            ie.initCause(t);
            throw ie;
        }
    }

    public Application instantiateApplication(ClassLoader cl, String className)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return (Application) macapkNewInstance(cl.loadClass(className));
    }

    @SuppressWarnings("deprecation")
    public Activity instantiateActivity(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return (Activity) cl.loadClass(className).newInstance();
    }

    @SuppressWarnings("deprecation")
    public BroadcastReceiver instantiateReceiver(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return (BroadcastReceiver) cl.loadClass(className).newInstance();
    }

    @SuppressWarnings("deprecation")
    public Service instantiateService(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return (Service) cl.loadClass(className).newInstance();
    }

    @SuppressWarnings("deprecation")
    public ContentProvider instantiateProvider(ClassLoader cl, String className)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return (ContentProvider) cl.loadClass(className).newInstance();
    }
}
