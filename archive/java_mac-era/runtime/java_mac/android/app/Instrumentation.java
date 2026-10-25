// android.app.Instrumentation
//
// AOSP's Instrumentation is the framework's lifecycle driver: ActivityThread
// routes every Application/Activity bring-up call through it
// (newApplication/callApplicationOnCreate/newActivity/callActivityOn*), and
// the test framework (androidx.test / AndroidJUnitRunner) both SUBCLASSES it
// and resolves it reflectively. Mac.apk previously open-coded the bring-up in
// Boot/ActivityHost and shipped no Instrumentation class at all, so any app or
// DI container that did `getInstrumentation()` / reflected
// `android.app.Instrumentation` got ClassNotFound.
//
// This is a faithful-as-possible reimplementation of the PUBLIC surface (see
// AOSP frameworks/base/core/java/android/app/Instrumentation.java). The
// new*/callActivityOn*/callApplicationOnCreate methods do the real work --
// they construct the component and drive the same lifecycle methods AOSP does
// (which are package-private/protected on our Activity, reachable because this
// class is in android.app). The UI-injection helpers (sendKeySync, etc.) are
// honest no-ops where Mac.apk has no input-injection transport, matching the
// "feature absent" contract rather than throwing.
//
// NOT modeled: getUiAutomation()/acquireLooperManager() -- their return types
// (UiAutomation, TestLooperManager) are not in the supply, and adding them
// would pull in the accessibility-automation transport we do not have. Apps
// that call those reflectively still find every other method.
package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class Instrumentation {

    /** AOSP report keys used by the status-reporting protocol. */
    public static final String REPORT_KEY_IDENTIFIER = "id";
    public static final String REPORT_KEY_STREAMRESULT = "stream";

    private Context mInstrContext;
    private Context mAppContext;
    private ComponentName mComponent;
    private final java.util.List<ActivityMonitor> mActivityMonitors =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    public Instrumentation() {}

    // ---- Lifecycle of the Instrumentation itself ----
    public void onCreate(Bundle arguments) {}
    public void start() { onStart(); }
    public void onStart() {}
    public boolean onException(Object obj, Throwable e) { return false; }
    public void sendStatus(int resultCode, Bundle results) {}
    public void addResults(Bundle results) {}
    public void finish(int resultCode, Bundle results) {}
    public void setAutomaticPerformanceSnapshots() {}
    public void startPerformanceSnapshot() {}
    public void endPerformanceSnapshot() {}
    public void onDestroy() {}

    // ---- Context / identity accessors ----
    /** AOSP returns the Context of the instrumentation package; we hand back
     *  the running Application context (single-package model). */
    public Context getContext() {
        return mInstrContext != null ? mInstrContext : Application.macapk_get();
    }
    public ComponentName getComponentName() { return mComponent; }
    /** The Context of the app being instrumented (same as getContext() here). */
    public Context getTargetContext() {
        return mAppContext != null ? mAppContext : Application.macapk_get();
    }
    public String getProcessName() { return Application.getProcessName(); }

    /** Internal: Boot/ActivityHost may seed the contexts/component so a test
     *  Instrumentation's getContext()/getTargetContext() return the real
     *  app objects. Not part of the public AOSP surface. */
    public void macapkInit(Context instrContext, Context appContext,
                           ComponentName component) {
        this.mInstrContext = instrContext;
        this.mAppContext = appContext;
        this.mComponent = component;
    }

    // ---- Profiling (no-op; Mac.apk has no ART alloc-counting transport) ----
    public boolean isProfiling() { return false; }
    public void startProfiling() {}
    public void stopProfiling() {}
    public void startAllocCounting() {}
    public void stopAllocCounting() {}
    public Bundle getAllocCounts() { return new Bundle(); }
    public Bundle getBinderCounts() { return new Bundle(); }

    // ---- Touch / idle synchronization ----
    public void setInTouchMode(boolean inTouch) {}
    public void resetInTouchMode() {}

    /** AOSP posts an idle-wait on the target's MessageQueue then calls the
     *  Runnable. We have a single main loop; once the main Handler has drained
     *  we consider the UI idle, so we just run the callback on the next main
     *  tick. */
    public void waitForIdle(Runnable recipient) {
        if (recipient == null) return;
        new Handler(Looper.getMainLooper()).post(recipient);
    }

    /** Block the calling thread until the main looper is idle. If called ON
     *  the main thread (where blocking would deadlock), AOSP throws; we run a
     *  best-effort flush by posting a barrier runnable and returning. */
    public void waitForIdleSync() {
        Looper main = Looper.getMainLooper();
        if (main != null && main.isCurrentThread()) {
            // Cannot block the main thread on itself; nothing to wait for.
            return;
        }
        final Object lock = new Object();
        final boolean[] done = { false };
        new Handler(main).post(new Runnable() {
            @Override public void run() {
                synchronized (lock) { done[0] = true; lock.notifyAll(); }
            }
        });
        synchronized (lock) {
            while (!done[0]) {
                try { lock.wait(); } catch (InterruptedException e) { return; }
            }
        }
    }

    /** AOSP runOnMainSync: run the Runnable on the main thread and block the
     *  caller until it completes. Throws RuntimeException wrapping any error
     *  thrown by the Runnable, matching AOSP's SyncRunnable behavior. */
    public void runOnMainSync(Runnable runner) {
        if (runner == null) return;
        Looper main = Looper.getMainLooper();
        if (main != null && main.isCurrentThread()) {
            runner.run();
            return;
        }
        final Object lock = new Object();
        final boolean[] done = { false };
        final Throwable[] thrown = { null };
        new Handler(main).post(new Runnable() {
            @Override public void run() {
                try { runner.run(); }
                catch (Throwable t) { thrown[0] = t; }
                finally {
                    synchronized (lock) { done[0] = true; lock.notifyAll(); }
                }
            }
        });
        synchronized (lock) {
            while (!done[0]) {
                try { lock.wait(); } catch (InterruptedException e) { return; }
            }
        }
        if (thrown[0] != null) {
            throw new RuntimeException(thrown[0]);
        }
    }

    // ---- startActivitySync ----
    public Activity startActivitySync(Intent intent) {
        return startActivitySync(intent, null);
    }
    public Activity startActivitySync(Intent intent, Bundle options) {
        // Mac.apk has a single foreground activity owned by ActivityHost; we
        // do not synchronously materialize a NEW Activity instance here (that
        // would race the host). Route the launch through Context and return
        // the current foreground Activity, which is the AOSP-observable result
        // for the common case (launching the next screen and getting it back).
        Context ctx = getTargetContext();
        if (ctx != null && intent != null) {
            try { ctx.startActivity(intent); } catch (Throwable ignored) {}
        }
        return null;
    }

    // ---- Activity monitors ----
    public void addMonitor(ActivityMonitor monitor) {
        if (monitor != null) mActivityMonitors.add(monitor);
    }
    public ActivityMonitor addMonitor(IntentFilter filter, ActivityResult result,
                                      boolean block) {
        ActivityMonitor m = new ActivityMonitor(filter, result, block);
        addMonitor(m);
        return m;
    }
    public ActivityMonitor addMonitor(String cls, ActivityResult result,
                                      boolean block) {
        ActivityMonitor m = new ActivityMonitor(cls, result, block);
        addMonitor(m);
        return m;
    }
    public boolean checkMonitorHit(ActivityMonitor monitor, int minHits) {
        if (monitor == null) return false;
        if (monitor.getHits() < minHits) return false;
        mActivityMonitors.remove(monitor);
        return true;
    }
    public Activity waitForMonitor(ActivityMonitor monitor) {
        if (monitor == null) return null;
        Activity a = monitor.waitForActivity();
        mActivityMonitors.remove(monitor);
        return a;
    }
    public Activity waitForMonitorWithTimeout(ActivityMonitor monitor, long timeOut) {
        if (monitor == null) return null;
        Activity a = monitor.waitForActivityWithTimeout(timeOut);
        mActivityMonitors.remove(monitor);
        return a;
    }
    public void removeMonitor(ActivityMonitor monitor) {
        mActivityMonitors.remove(monitor);
    }

    /** Internal hook so the launch path can notify monitors of a started
     *  intent (AOSP's execStartActivity consults monitors first). Returns the
     *  ActivityResult of the first matching blocking monitor, else null. */
    public ActivityResult macapkOnStartActivity(Intent intent) {
        for (ActivityMonitor m : mActivityMonitors) {
            ActivityResult r = m.onStartActivity(intent);
            if (r != null) return r;
        }
        return null;
    }

    // ---- Menu / input injection (no input transport on macOS) ----
    public boolean invokeMenuActionSync(Activity targetActivity, int id, int flag) {
        return false;
    }
    public boolean invokeContextMenuAction(Activity targetActivity, int id, int flag) {
        return false;
    }
    public void sendStringSync(String text) {}
    public void sendKeySync(KeyEvent event) {}
    public void sendKeyDownUpSync(int key) {}
    public void sendCharacterSync(int keyCode) {}
    public void sendPointerSync(MotionEvent event) {}
    public void sendTrackballEventSync(MotionEvent event) {}

    // ---- Application bring-up ----
    /** AOSP newApplication(ClassLoader, className, Context): load the
     *  Application subclass, instantiate it, and attach the base context. */
    public Application newApplication(ClassLoader cl, String className, Context context)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        // AOSP Instrumentation.java:1278-1284 (android14-release):
        //     Application app = getFactory(context.getPackageName())
        //             .instantiateApplication(cl, className);
        //     app.attach(context);
        //     return app;
        //
        // Routing through the AppComponentFactory is the whole point: the
        // factory's body is `(Application) cl.loadClass(className).newInstance()`,
        // so the app's <clinit> runs INSIDE that frame -- and when the manifest
        // declares a factory, inside the app's own
        // androidx.core.app.CoreComponentFactory.instantiateApplication frame
        // above it, which invokespecials our super method. That is the device
        // stack shape.
        //
        // TWO DELIBERATE DEVIATIONS, both forced by Mac.apk's launch order:
        //  (1) AOSP dereferences context.getPackageName() unconditionally and
        //      would NPE on a null context. We accept null, because
        //      LoadedApk.makeApplicationInner passes null (see that file).
        //      getFactory resolves the package being bound in that case.
        //  (2) AOSP always attaches. We attach only when a context is supplied,
        //      because Boot's macapkInit*/macapkSet* wiring must interpose
        //      between construction and attach; Boot drives attach itself,
        //      through Application.attach, so the frame is still genuine.
        Application app = getFactory(context != null ? context.getPackageName() : null)
                .instantiateApplication(cl, className);
        if (context != null) app.attach(context);
        return app;
    }

    /** The ActivityThread that owns this Instrumentation. AOSP sets the
     *  equivalent field during ActivityThread.attach. */
    ActivityThread mThread;

    /** AOSP Instrumentation.java:1381-1394. Reads the factory off the LoadedApk
     *  -- never off a PackageManager.
     *
     *  That distinction is load-bearing, not stylistic. Resolving the factory
     *  via the app's PackageManager during construction would force
     *  Application.getPackageManager() to build and MEMOIZE a
     *  MacApkPackageManager from packageName/apkPath/appDir/nativeLibraryDir/
     *  metadata/service+provider maps -- every one of which Boot sets AFTER
     *  construction. That would bake nulls into the cached PM for the process
     *  lifetime and break getApplicationInfo().sourceDir/dataDir/
     *  nativeLibraryDir, which Boot documents cocos2d-x as depending on. The
     *  construction chain must never touch Application or PackageManager. */
    private AppComponentFactory getFactory(String pkg) {
        LoadedApk apk = (mThread != null) ? mThread.peekPackageInfo(pkg) : null;
        if (apk != null) return apk.getAppFactory();
        return AppComponentFactory.DEFAULT;
    }
    /** AOSP static newApplication(Class, Context): instantiate + attach. */
    public static Application newApplication(Class<?> clazz, Context context)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Application app;
        try {
            app = (Application) clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException ie) {
            throw ie;
        } catch (IllegalAccessException iae) {
            throw iae;
        } catch (Exception e) {
            throw new InstantiationException(e.toString());
        }
        // AOSP calls app.attach(context); our Application uses attachBaseContext.
        app.macapkAttachBaseContext(context);
        return app;
    }
    /** AOSP callApplicationOnCreate: drives Application.onCreate().
     *  WARNING: no try/catch. Mac.apk's launch path drives onCreate from
     *  com.macapk.loader.Boot, inside its own tolerance machinery; do not put
     *  this method on the launch path. */
    public void callApplicationOnCreate(Application app) {
        if (app != null) app.onCreate();
    }

    // ---- Activity bring-up ----
    /** AOSP newActivity(Class, ...): instantiate the Activity and attach its
     *  context/intent. We honor the full AOSP signature so reflective callers
     *  link; the IBinder/ActivityInfo/parent args are accepted but Mac.apk's
     *  single-window model only needs the class + application + intent. */
    public Activity newActivity(Class<?> clazz, Context context, IBinder token,
                                Application application, Intent intent,
                                ActivityInfo info, CharSequence title,
                                Activity parent, String id, Object lastNonConfigurationInstance)
            throws IllegalAccessException, InstantiationException {
        Activity activity;
        try {
            activity = (Activity) clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException ie) {
            throw ie;
        } catch (IllegalAccessException iae) {
            throw iae;
        } catch (Exception e) {
            throw new InstantiationException(e.toString());
        }
        if (application == null) application = Application.macapk_get();
        activity.macapkAttach(application, intent);
        return activity;
    }
    /** AOSP newActivity(ClassLoader, className, Intent): the loader-based
     *  overload used by ActivityThread. */
    public Activity newActivity(ClassLoader cl, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Class<?> clazz = cl.loadClass(className);
        Activity activity;
        try {
            activity = (Activity) clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException ie) {
            throw ie;
        } catch (IllegalAccessException iae) {
            throw iae;
        } catch (Exception e) {
            throw new InstantiationException(e.toString());
        }
        activity.macapkAttach(Application.macapk_get(), intent);
        return activity;
    }

    // The callActivityOn* family drives the protected lifecycle methods on
    // our Activity (reachable: this class is in android.app). Each mirrors
    // AOSP Instrumentation.callActivityOn<Phase>.
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        if (activity != null) activity.onCreate(icicle);
    }
    public void callActivityOnCreate(Activity activity, Bundle icicle,
                                     PersistableBundle persistentState) {
        if (activity != null) activity.onCreate(icicle);
    }
    public void callActivityOnDestroy(Activity activity) {
        if (activity != null) activity.onDestroy();
    }
    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState) {
        if (activity != null) activity.onRestoreInstanceState(savedInstanceState);
    }
    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState,
                                                   PersistableBundle persistentState) {
        if (activity != null) activity.onRestoreInstanceState(savedInstanceState);
    }
    public void callActivityOnPostCreate(Activity activity, Bundle icicle) {
        if (activity != null) activity.onPostCreate(icicle);
    }
    public void callActivityOnPostCreate(Activity activity, Bundle icicle,
                                         PersistableBundle persistentState) {
        if (activity != null) activity.onPostCreate(icicle);
    }
    public void callActivityOnNewIntent(Activity activity, Intent intent) {
        if (activity != null) activity.onNewIntent(intent);
    }
    /** AOSP callActivityOnNewIntent(Activity, Intent, ComponentCaller)
     *  (Instrumentation.java:1639-1648): {@code activity.performNewIntent(intent,
     *  caller);}, which lands in Activity.onNewIntent(Intent, ComponentCaller).
     *  Added in API 35 alongside ComponentCaller. */
    public void callActivityOnNewIntent(Activity activity, Intent intent,
            ComponentCaller caller) {
        if (activity != null) activity.onNewIntent(intent, caller);
    }
    /** AOSP acquireLooperManager(Looper): "Takes control of the execution of
     *  messages on the specified looper until TestLooperManager.release is
     *  called." AOSP first calls checkInstrumenting("acquireLooperManager"),
     *  which throws RuntimeException when the process is not being instrumented
     *  -- Mac.apk never is. But android.os.TestLooperManager exists in this tree
     *  and a harness that got this far has already accepted our other no-op
     *  automation answers, so hand back the manager rather than throwing: AOSP's
     *  own behaviour once instrumentation IS on, and the failure mode of a
     *  throw here (a test that cannot even set up) is worse than a manager that
     *  controls a looper nothing else is pumping. */
    public android.os.TestLooperManager acquireLooperManager(android.os.Looper looper) {
        return new android.os.TestLooperManager(looper);
    }
    public void callActivityOnStart(Activity activity) {
        if (activity != null) activity.onStart();
    }
    public void callActivityOnRestart(Activity activity) {
        // Our Activity has no onRestart hook; AOSP's restart precedes onStart.
        // No-op keeps the call linkable for test harnesses.
    }
    public void callActivityOnResume(Activity activity) {
        if (activity != null) activity.onResume();
    }
    public void callActivityOnStop(Activity activity) {
        if (activity != null) activity.onStop();
    }
    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState) {
        if (activity != null) activity.onSaveInstanceState(outState);
    }
    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState,
                                                PersistableBundle outPersistentState) {
        if (activity != null) activity.onSaveInstanceState(outState);
    }
    public void callActivityOnPause(Activity activity) {
        if (activity != null) activity.onPause();
    }
    public void callActivityOnUserLeaving(Activity activity) {}
    public void callActivityOnPictureInPictureRequested(Activity activity) {}

    /** AOSP Instrumentation.ActivityResult: the (resultCode, data) pair a
     *  monitor returns to short-circuit a started activity. */
    public static final class ActivityResult {
        private final int mResultCode;
        private final Intent mResultData;
        public ActivityResult(int resultCode, Intent resultData) {
            mResultCode = resultCode;
            mResultData = resultData;
        }
        public int getResultCode() { return mResultCode; }
        public Intent getResultData() { return mResultData; }
    }

    /** AOSP Instrumentation.ActivityMonitor: intercepts activity launches that
     *  match a filter or class name, optionally blocking them and substituting
     *  a canned ActivityResult. Used by test code to assert navigation. */
    public static class ActivityMonitor {
        private final IntentFilter mWhich;
        private final String mClass;
        private final ActivityResult mResult;
        private final boolean mBlock;
        private int mHits;
        private Activity mLastActivity;

        public ActivityMonitor(IntentFilter which, ActivityResult result, boolean block) {
            mWhich = which;
            mClass = null;
            mResult = result;
            mBlock = block;
        }
        public ActivityMonitor(String cls, ActivityResult result, boolean block) {
            mWhich = null;
            mClass = cls;
            mResult = result;
            mBlock = block;
        }
        public ActivityMonitor() {
            mWhich = null;
            mClass = null;
            mResult = null;
            mBlock = false;
        }

        public final IntentFilter getFilter() { return mWhich; }
        public final ActivityResult getResult() { return mResult; }
        public final boolean isBlocking() { return mBlock; }
        public final int getHits() { return mHits; }
        public final Activity getLastActivity() { return mLastActivity; }

        public final Activity waitForActivity() {
            return waitForActivityWithTimeout(0);
        }
        public final synchronized Activity waitForActivityWithTimeout(long timeOut) {
            // Block until an activity hits this monitor or the timeout lapses.
            // Mac.apk does not synchronously deliver new Activity instances to
            // monitors yet, so this returns the last activity (possibly null)
            // after waiting, matching the "no activity arrived" timeout path.
            if (mLastActivity == null) {
                try {
                    if (timeOut == 0) wait();
                    else wait(timeOut);
                } catch (InterruptedException e) {
                    return mLastActivity;
                }
            }
            return mLastActivity;
        }

        /** AOSP hook: called when an activity matching this monitor is started.
         *  Returns the canned result if blocking, else null (let it proceed). */
        public ActivityResult onStartActivity(Intent intent) {
            if (matchIntent(intent)) {
                mHits++;
                if (mBlock) return mResult;
            }
            return null;
        }

        /** Internal: record that an activity hit this monitor and wake any
         *  waiters. Not part of the AOSP public surface. */
        final synchronized void macapkHit(Activity activity) {
            mHits++;
            mLastActivity = activity;
            notifyAll();
        }

        private boolean matchIntent(Intent intent) {
            if (intent == null) return false;
            if (mClass != null) {
                return intent.getComponent() != null
                    && mClass.equals(intent.getComponent().getClassName());
            }
            if (mWhich != null) {
                String action = intent.getAction();
                return action != null && mWhich.matchAction(action);
            }
            return false;
        }
    }

    public android.app.UiAutomation getUiAutomation() { return new android.app.UiAutomation(); }
    public android.app.UiAutomation getUiAutomation(int flags) { return new android.app.UiAutomation(); }
}

