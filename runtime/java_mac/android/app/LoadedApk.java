// android.app.LoadedApk -- the per-package state AOSP's ActivityThread binds
// an application from. Hidden API (not in the public SDK).
//
// Mac.apk implements the CONSTRUCTION subset only: the call chain
//   ActivityThread.handleBindApplication
//     -> LoadedApk.makeApplicationInner(boolean, Instrumentation)
//       -> LoadedApk.makeApplicationInner(boolean, Instrumentation, boolean)
//         -> Instrumentation.newApplication(ClassLoader, String, Context)
//           -> AppComponentFactory.instantiateApplication
//             -> Class.newInstance   <-- the app's <clinit> runs HERE
// so that an app which inspects its own launch call stack sees the frames a
// real device shows instead of our loader's Class.forName + lambda frames.
// That is a platform-fidelity gain in its own right: AppComponentFactory.java's
// own header already names "routing those call sites through here" as wanted
// work, and this file is what does the routing for the Application component.
//
// AOSP ref (android14-release):
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/core/java/android/app/LoadedApk.java
//   makeApplication          :1384-1388
//   makeApplicationInner/2   :1393-1397
//   makeApplicationInner/3   :1399  (the body)
//   createAppFactory         :269-279
//   getAppFactory            :281-283
//
// DELIBERATE OMISSIONS from AOSP's body, listed so a future reader can tell
// "scoped out" from "not noticed":
//   - initializeJavaContextClassLoader (AOSP :1438). Boot.java already sets the
//     thread context classloader during bring-up; doing it here would double-set.
//   - rewriteRValues over getAssignedPackageIdentifiers (AOSP :1443-1451).
//     ResourceIndex owns R remapping in Mac.apk.
//   - ContextImpl.createAppContext (AOSP :1454). Mac.apk has no ContextImpl; the
//     app's base Context is Application.AppBaseContext, built inside the
//     Application constructor, and Boot's macapkInit*/macapkSet* wiring must
//     interpose before attach. See the null-Context deviation at step 4 below.
//   - NetworkSecurityConfigProvider.handleNewApplication (AOSP :1457) -- not implemented.
//   - appContext.setOuterContext (AOSP :1460), mAllApplications registration
//     (AOSP :1469), and the sApplications per-package cache (AOSP :1406-1427,
//     :1471-1475) -- single-app process, nothing to key.
//   - Trace.traceBegin/End and Instrumentation.onException (AOSP :1463).
//
// NOT a goal for this class: creating a ContextImpl, binding targetSdk
// (Application.macapkInitVersion keeps that), installing ContentProviders, or
// driving Application.onCreate. Those stay in com.macapk.loader.Boot, in the
// same order they run today.
package android.app;

import android.content.Context;

public final class LoadedApk {

    private static final String TAG = "Mac.apk.LoadedApk";

    final ClassLoader mClassLoader;
    final String mPackageName;
    final String mAppClassName;
    final ActivityThread mActivityThread;
    private final AppComponentFactory mAppComponentFactory;

    /** The one Application this package binds. AOSP guards re-entry with the
     *  same field (:1401-1403); we need it for the same reason plus a sharper
     *  one -- Application's constructor publishes the process singleton
     *  unconditionally, so a second makeApplication* call would silently
     *  repoint it. */
    private Application mApplication;

    LoadedApk(ActivityThread thread, String packageName, String appClassName,
              String appComponentFactoryName, ClassLoader cl) {
        mActivityThread = thread;
        mPackageName = packageName;
        mAppClassName = appClassName;
        mClassLoader = cl;
        mAppComponentFactory = createAppFactory(appComponentFactoryName, cl);
    }

    /** AOSP createAppFactory (:269-279), verbatim in shape: a package that
     *  declares android:appComponentFactory gets that class instantiated and
     *  every component construction routed through it; everything else gets
     *  the stock factory. androidx.core auto-merges
     *  androidx.core.app.CoreComponentFactory into virtually every AndroidX
     *  app's manifest, so on the fleet this is the common path, not the rare
     *  one -- and it is why the device stack for such an app carries an
     *  androidx.core.app.CoreComponentFactory.instantiateApplication frame
     *  above android.app.AppComponentFactory's.
     *
     *  NOTE this makes the app's declared factory class load, and its <clinit>
     *  run, one step earlier than anything in Mac.apk runs it today. That is
     *  AOSP-correct but it IS new behaviour on this path. */
    private AppComponentFactory createAppFactory(String factoryClassName, ClassLoader cl) {
        if (factoryClassName != null && !factoryClassName.isEmpty() && cl != null) {
            try {
                // Shared primitive so MACAPK_ACTIVITYTHREAD_INSTANTIATE covers THIS
                // site too -- it is a <clinit> trigger for the app's own factory
                // class, and on some apps it is the FIRST one.
                AppComponentFactory f = (AppComponentFactory)
                    AppComponentFactory.macapkNewInstance(cl.loadClass(factoryClassName));
                android.util.Log.i(TAG, "appComponentFactory=" + factoryClassName);
                return f;
            } catch (InstantiationException | IllegalAccessException
                     | ClassNotFoundException | ClassCastException e) {
                android.util.Log.w(TAG, "Unable to instantiate appComponentFactory "
                                      + factoryClassName + ": " + e);
            }
        }
        return AppComponentFactory.DEFAULT;
    }

    /** AOSP getAppFactory (:281-283). Instrumentation.getFactory reads the
     *  factory from HERE -- that is what keeps this from being a getter nothing
     *  calls. */
    public AppComponentFactory getAppFactory() {
        return mAppComponentFactory;
    }

    String getPackageName() {
        return mPackageName;
    }

    /** AOSP makeApplication (:1384-1388), @UnsupportedAppUsage. NOT on the
     *  launch stack -- handleBindApplication calls makeApplicationInner. Present
     *  because reflective callers reach for this name; the mApplication guard in
     *  the body is what makes it safe to expose. */
    public Application makeApplication(boolean forceDefaultAppClass,
                                       Instrumentation instrumentation) {
        return makeApplicationInner(forceDefaultAppClass, instrumentation,
                                    /*allowDuplicateInstances=*/ true);
    }

    /** AOSP makeApplicationInner (:1393-1397) -- the 2-arg public delegator that
     *  ActivityThread.handleBindApplication actually calls (AOSP
     *  ActivityThread.java:6959). Kept as a separate frame from the body on
     *  purpose: a device stack carries BOTH makeApplicationInner frames. */
    public Application makeApplicationInner(boolean forceDefaultAppClass,
                                            Instrumentation instrumentation) {
        return makeApplicationInner(forceDefaultAppClass, instrumentation,
                                    /*allowDuplicateInstances=*/ false);
    }

    /** AOSP makeApplicationInner (:1399) -- the body. */
    private Application makeApplicationInner(boolean forceDefaultAppClass,
                                             Instrumentation instrumentation,
                                             boolean allowDuplicateInstances) {
        // 1. AOSP :1401-1403.
        if (mApplication != null) {
            return mApplication;
        }

        // 2. AOSP :1428-1431. The isEmpty() arm mirrors the guard Boot has
        //    always had -- a manifest with android:name="" is not a class name.
        String appClass = mAppClassName;
        if (forceDefaultAppClass || appClass == null || appClass.isEmpty()) {
            appClass = "android.app.Application";
        }

        // 3. TOLERANCE PRE-CHECK. Mac.apk logs and continues where AOSP would
        //    kill the process, and this preserves the two fallbacks the loader
        //    has always applied (class missing / class is not an Application)
        //    with their existing wording. loadClass does NOT run <clinit>, so
        //    no app-authored bytecode executes here -- the initialization
        //    trigger stays inside the factory's newInstance below, which is the
        //    entire point of this pipeline.
        if (mClassLoader != null && !"android.app.Application".equals(appClass)) {
            try {
                Class<?> c = mClassLoader.loadClass(appClass);
                if (!Application.class.isAssignableFrom(c)) {
                    android.util.Log.w(TAG, "Application class " + appClass
                              + " does not extend android.app.Application; falling back");
                    appClass = "android.app.Application";
                }
            } catch (ClassNotFoundException e) {
                android.util.Log.w(TAG, "Application class not found; using stock Application");
                appClass = "android.app.Application";
            }
        }

        Application app;
        try {
            // 4. AOSP :1458 -- instantiate from the Instrumentation FIELD, not
            //    from the parameter. The parameter only gates
            //    callApplicationOnCreate further down in AOSP's body, and
            //    handleBindApplication passes null for it (AOSP
            //    ActivityThread.java:6959) precisely so that block is skipped.
            //
            //    THE ONE DELIBERATE DEVIATION FROM AOSP ON THIS LINE: AOSP
            //    passes a ContextImpl built by createAppContext and lets
            //    newApplication attach it. Mac.apk passes null. Forced, not
            //    preferred: our base Context is Application.AppBaseContext,
            //    constructed inside the Application constructor, and Boot's
            //    macapkInit*/macapkSet* wiring (package name, dirs, assets,
            //    resources, classloader, targetSdk) must interpose between
            //    construction and attach. Attach therefore stays where it is
            //    today, in Boot, and routes through Application.attach so the
            //    attach-time stack gains its genuine framework frame too.
            app = mActivityThread.mInstrumentation.newApplication(
                    mClassLoader, appClass, /*context=*/ null);
        } catch (ClassNotFoundException | ClassCastException e) {
            // Tolerated, with a stock Application, exactly as the loader has
            // always tolerated these two.
            android.util.Log.w(TAG, "Could not construct " + appClass + " (" + e
                                  + ") -- using stock Application");
            try {
                app = mActivityThread.mInstrumentation.newApplication(
                        mClassLoader, "android.app.Application", /*context=*/ null);
            } catch (Exception fatal) {
                throw new RuntimeException("Unable to instantiate android.app.Application", fatal);
            }
        } catch (InstantiationException | IllegalAccessException e) {
            // NOT tolerated. AOSP wraps these in a RuntimeException (:1462-1470)
            // and so do we -- deliberately NOT falling back to a stock
            // Application. An app whose own Application class exists but cannot
            // be constructed must abort the launch, the way it does today, so
            // Boot's outer catch records the failure; silently booting a
            // different Application would be a fleet-wide behaviour change
            // hiding behind a "tolerant" label.
            throw new RuntimeException("Unable to instantiate application " + appClass
                                     + ": " + e.toString(), e);
        }
        // Everything else -- LinkageError (NoClassDefFoundError,
        // ExceptionInInitializerError from the app's own <clinit>), and every
        // RuntimeException/Error thrown by the app's constructor -- propagates
        // untouched, so the launch aborts exactly as it does today.

        mApplication = app;
        return app;
    }
}
